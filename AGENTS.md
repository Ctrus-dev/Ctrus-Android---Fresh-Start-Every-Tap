# Ctrus for Android

Android port of Ctrus (the iOS focus/app-blocking app at github.com/Ctrus-dev/Ctrus---Fresh-Start-Every-Tap).
Separate repo from the iOS project on purpose — no Xcode/Swift tooling here.

- Package: `mo.dev.ctrus`
- Stack: Kotlin + Jetpack Compose, minSdk 27, compileSdk/targetSdk 35
- UI is meant to visually match the iOS app (same theme colors, same screen layouts),
  not to reuse iOS code — this is a from-scratch Kotlin implementation.

## Blocking architecture

Android has no equivalent of iOS's FamilyControls/ManagedSettings/DeviceActivity. After
reviewing Switchly (github: gitlab.com/Saltyy/switchly-public), an open-source, Play
Store-published Android app blocker with the same NFC-tag-unblock concept as Ctrus, this
project follows the same approach:

- **`blocking/BlockingAccessibilityService`** is the primary detection mechanism —
  listens for `TYPE_WINDOW_STATE_CHANGED` accessibility events to know which app just
  came to the foreground. This was a deliberate choice over polling
  `UsageStatsManager` in a foreground service: Switchly's own source comments note
  AccessibilityService is kept alive far more reliably across OEMs than a polling
  foreground service, and they moved off polling for that reason. `UsageStatsManager`
  is worth keeping only as an auxiliary foreground-resolution signal for edge cases
  (e.g. split-screen), not as the primary mechanism.
- Using `AccessibilityService` for this is Play Store policy-sensitive (Google
  tightened enforcement around AccessibilityService misuse in 2026). It requires a
  proper accessibility declaration in the Play Console and clear in-app disclosure of
  why the permission is requested before submitting. Switchly is a live proof this is
  approvable for a genuinely-disclosed focus/blocking use case — don't skip the
  disclosure step when this gets close to a real release.
- **`blocking/BlockSafetyPolicy`** — never-block list (Settings, dialer, system UI, the
  default launcher, Ctrus itself) so a misconfigured profile can't lock the user out of
  their phone. Extend this before wiring up real blocking logic.
- **`blocking/BlockerActivity`** — the full-screen surface shown instead of a blocked
  app, launched instantly by the accessibility service (`Intent` + `FLAG_ACTIVITY_NEW_TASK`,
  no `performGlobalAction(GLOBAL_ACTION_HOME)` kick beforehand — see the note below on
  why that was removed). Mirrors `ShieldConfigurationExtension.swift`: solid theme-color
  background, a 🔒 icon, and one of 6 citrus-themed title/subtitle/button messages chosen
  deterministically per app per day (same FNV-1a-hash-of-name XOR day-key scheme as
  `getFunBlockMessage`). Its dismiss button — and the system back gesture — navigate to
  the home screen (`ACTION_MAIN`/`CATEGORY_HOME`) rather than just finishing, since
  finishing alone would reveal the blocked app's still-alive task underneath.
- **`blocking/BlockDecisionEngine`** is fully wired to `BlockingStateHolder`'s
  Room-derived snapshot of the active session's profile — a package is blocked when it's
  in (or, in allow-mode, not in) the profile's `selectedPackages`.
  the next real milestone.

## What's real vs. still pending

Full plan lives in `/Users/martimoliveira/.claude/plans/dreamy-snacking-cocoa.md`. **Ported-feature
rule, learned the hard way**: only port what a real user can actually reach in the iOS app's UI.
The iOS *source* contains strategies, a schedule model, and a debug/dashboard surface that are
never user-selectable or user-visible (`StrategyManager.availableStrategies` is a superset of
`StrategyManager.pickerStrategies` — the picker is what the app actually offers); an earlier pass
of this port mistakenly implemented the full superset, including a whole scheduling subsystem
with no UI anywhere to create a schedule. That's been ripped back out. Before porting anything,
check where in the *Views/Components* it's actually reachable, not just whether the Swift type
exists.

**3D mascot is now the real iOS size, and profile creation is now the real 8-step wizard.**
`RotatingModel3DView.swift`'s default is `size: CGFloat = 380` (no override on either the Home or
intro screen) — the Android view now defaults to the same `380.dp` (`DefaultModel3DSize` in
`ui/dashboard/RotatingModel3DView.kt`), up from an undersized `200.dp`/`220.dp` guess. Profile
*creation* is now `GuidedProfileCreationScreen`, a real port of
`GuidedBlockedProfileCreationView.swift`'s 8 steps (Name → Method → Apps → Websites → Unlocks →
Breaks → Protection → Review) with the same per-step `canContinue` gating and a review screen
summarizing the draft — not the single-scroll form. That single-scroll form
(`ProfileFormScreen`) is now edit-only, matching the real iOS split: `HomeView.swift` presents
`GuidedBlockedProfileCreationView` for creation and `BlockedProfileView` (the accordion form) for
editing — they were never the same screen on iOS, and conflating them was the mistake in the
previous pass. The actual field content (name/strategy/apps/domains/physical-unlocks/breaks/
safeguards) is factored into `ui/profile/ProfileFormFields.kt`, shared by both screens so they
can't drift apart.

**Bug fixed this pass — the 3D model was rendering as scrambled shards**: `ObjParser` computed
`triangleCount` as `verts.size / OBJ_FLOATS_PER_VERTEX` (that's actually the *vertex* count), and
the renderer then multiplied it by 3 again for `glDrawArrays`'s vertex count — asking the GPU to
read 3x past the end of each group's VBO, pulling in garbage memory as positions/normals. Fixed to
`vertexCount / 3` with a `check()` guard against non-triangulated input. If the model ever looks
wrong again, verify triangle/vertex counts line up before suspecting the OBJ data itself — that
class of bug reads as "bad data" but was a Kotlin arithmetic error.

**Home screen and profile creation were also rebuilt this pass** — the first version was too
minimal (bare profile list, no 3D mascot, no domains/physical-unlocks/breaks/safeguards in profile
creation). Read HomeView.swift, HomeProfilesListView.swift, HomeProfileLauncher.swift,
BlockedProfileView.swift, BlockedProfileFormSections.swift, BlockedProfileDraft.swift,
DomainPicker.swift, and BlockedProfilePhysicalUnblockSelector.swift directly (not from memory) to
rebuild these accurately. `HomeScreen` now matches HomeView's real layout order: settings gear,
3D mascot, Welcome-or-profile-list, theme-bordered profile cards (name + Active chip + apps/domains
count + Breaks/Deletion-Blocked indicators + "⋮" menu), bottom launcher (Start pill when idle, a
themed live-timer bar when active) — `StartProfilePickerView` handles the case of >1 profile.
`ProfileFormScreen` (edit) and `GuidedProfileCreationScreen` (create, 8 steps — see above) both
cover every real `BlockedProfileView.swift`/`BlockedProfileFormSections.swift` section — Name,
Strategy, Apps, Domains (with the exact validation regex from `DomainPicker.swift`), Physical
Unlocks (wired to real NFC scanning via `NfcScanController`, **required to save — matches iOS's
`.missingPhysicalUnlock` guard**), Breaks, Session Protection. `ProfileFormScreen` also handles
duplicate/delete for an existing profile.

**Still simplified vs. iOS, called out explicitly rather than left implicit**: no aurora/blob
animated card background (`CardBackground.swift`) on the active launcher bar — flat theme color
instead; no `HomeAlertsView` alert chips row (e.g. "Accessibility disabled" nudge after the intro
screen is dismissed); no `Manage` profiles screen (Home's own list covers create/edit/delete, but
not iOS's separate reorder/swipe-delete list view).

**Insights (this pass)** — full per-profile analytics port, plus the Home mini sparkline the user
calls "a barrinha dos dias". Read `WeeklySessionAggregator.swift`, `MonthlySessionAggregator.swift`,
`ProfileInsightsView.swift`, `WeeklyInsightsUtil.swift`, `MonthlyInsightsUtil.swift`,
`ProfileInsightsUtil.swift`, `WeeklySessionChart.swift`, `MonthlySessionChart.swift`,
`InsightsSummaryView.swift`, `SessionRow.swift`, `SessionDetailsView.swift`,
`ProfileSummaryRow.swift` in full before porting. Key finding: `ProfileInsightsUtil`'s hourly/streak
aggregation functions (`dailyAggregates`, `hourlyAggregates`, streak-length calculators, etc.) have
**zero call sites** in `ProfileInsightsView.swift` — only `totalFocusTime`/`totalBreakTime` are
actually shown — so only those two metrics were ported, not the full aggregation surface (same
"only what's reachable" rule as everywhere else in this port).
- `data/WeeklySessionAggregator.kt` / `MonthlySessionAggregator.kt` — near-verbatim ports of the
  Swift aggregators (clip each session interval to each day's window; locale-aware
  `startOfWeek`/`startOfMonth` via `Calendar.firstDayOfWeek`, mirroring `Calendar.current`).
- `data/InsightsSummary.kt` — pure functions (`weeklySummary`/`monthlySummary`/`metrics`), no
  ViewModel wrapper, called directly from Compose via `remember` — matches this codebase's existing
  lightweight pattern elsewhere. Break-duration-per-session reuses the existing
  `usedBreakDurationIncludingActive(profile, now)` extension (already correctly handles both the
  single-break and `allowMultipleBreaks` cases) rather than reimplementing iOS's
  `breakEnd - breakStart`, which is only correct for the single-break case.
- `ui/insights/WeeklyBarChart.kt` / `MonthlyHeatmapGrid.kt` — hand-drawn `Canvas` charts (Swift
  Charts has no Android equivalent) with tap/drag day-selection, same 5-bucket color scale as
  `MonthlySessionChart.swift` (0 / <1h / 1-3h / 3-5h / >5h over the theme color).
- `ui/insights/ProfileInsightsScreen.kt` — top bar filter menu (This/Last Week, This/Last Month,
  Select Week/Month via Material 3 `DatePickerDialog` in place of iOS's native graphical picker,
  All Sessions, Delete All Sessions), the chart, a Summary section (hidden when a specific day is
  selected, matching iOS), and the day-grouped session list with per-row delete.
- `ui/insights/SessionDetailsSheet.kt` — a `ModalBottomSheet` port of `SessionDetailsView.swift`
  **minus its Pause section** — pause is only ever set by `NFCPauseTimerBlockingStrategy`, one of
  the five strategies already cut as unreachable (see the `strategy/` bullet below), so a session
  can never actually have pause data in this build.
- `ui/insights/ProfileUsageMiniBarChart.kt` — the Home-row sparkline, ported field-for-field from
  `ProfileSummaryRow.swift`'s `ProfileUsageMiniBarChart` (118×62dp per `HomeProfilesListView.swift`,
  opacity `0.36 + normalized*0.64` when a day has data else flat `0.14`, bar height
  `max(5, normalized*25)dp` else `3dp`, narrow single-letter day labels). Wired into `HomeScreen`'s
  `ProfileCard` (tappable, plus a new "Insights" entry at the top of the "⋮" menu — matching iOS's
  menu order) and into `ProfileFormScreen`'s toolbar (`chart.line.uptrend.xyaxis` → `Icons.Filled.Insights`)
  and `ActiveSessionScreen`'s chart tap, both of which previously had no-op/placeholder handlers.
  Not ported: iOS's `DeviceLayoutUtil.hasCompactEffectiveWidth` check that hides the mini chart on
  narrow devices — no Android equivalent existed in this codebase yet, and phone widths vary enough
  that this is a minor layout nicety, not a functionality gap; flagged here rather than silently
  dropped.

**Bug fixed this pass — bottom content hidden behind the 3-button navigation bar.** `MainActivity`
calls `enableEdgeToEdge()`, which draws every screen behind the system bars; Scaffold-based screens
handle this for free (its `contentWindowInsets` default already reserves space, consumed via the
`innerPadding` callers already apply), but the handful of screens that paint a raw full-bleed
`Box`/`Column` instead of `Scaffold` did not account for insets at all — their bottom-anchored
buttons/bars only cleared gesture-nav (a thin transparent strip) and got covered by the classic
3-button nav bar, which reserves real screen height. Fixed by adding
`.windowInsetsPadding(WindowInsets.systemBars)` to the outermost `Column` in
`ui/intro/AccessibilityPermissionScreen.kt` (the "Allow Accessibility Access" button — the one
reported), `ui/home/HomeScreen.kt` (settings gear + bottom launcher bar), and
`ui/session/ActiveSessionScreen.kt` (Stop/Emergency actions). If a new full-screen composable is
added outside `Scaffold`, it needs this same treatment — don't assume edge-to-edge is handled by
default.

**Bug fixed this pass — auto-rotate reset the profile-creation wizard to step 1.** The iOS app is
portrait-only (`INFOPLIST_KEY_UISupportedInterfaceOrientations = UIInterfaceOrientationPortrait` in
the Xcode project, no landscape variant declared anywhere), but `MainActivity` had no orientation
lock, so an Android device with auto-rotate on would recreate the Activity on rotation — and
`GuidedProfileCreationScreen`'s `stepIndex`/draft state, held in plain `remember`, doesn't survive
that. Fixed at the root by adding `android:screenOrientation="portrait"` to `MainActivity` in the
manifest, matching iOS's real supported-orientation set exactly rather than papering over the
symptom with `rememberSaveable` on every affected screen (`stepIndex` was also switched to
`rememberSaveable` as cheap extra insurance against process death, which portrait-locking doesn't
prevent).

**Bug fixed this pass — dragging the 3D model tilted it backwards vs. iOS.** iOS's
`RotatingModel3DView.swift` composes `pitch += deltaY * dragRotationSpeed` (SceneKit, right-handed,
+Z toward camera) directly into a `simd_quatf(angle: pitch, axis: (1,0,0))`. Android's OpenGL scene
uses the identical camera/axis convention (`Matrix.setLookAtM` with the same up vector, and
`CtrusModelGlRenderer` applies `Matrix.rotateM(pitchDegrees, 1, 0, 0)` in the same order as iOS's
quaternion composition) — so the physical rotation for a given pitch sign is the same on both
platforms, but `RotatingModel3DView.kt`'s touch handler had the sign flipped
(`pitchDegrees - dy * ...` instead of `+`), inverting the drag direction end-to-end. Fixed by
removing the negation.

**Bug fixed this pass — an app already open when blocking started (or when a break ended) stayed
open.** `BlockingAccessibilityService` only re-evaluated `BlockDecisionEngine` on a fresh
`TYPE_WINDOW_STATE_CHANGED` event. If the target app was already the foreground window when a
session started — nothing about the *window* changed, only the in-memory `BlockingStateHolder`
state did — no such event ever fired, so the already-open app was never shielded until the user
happened to switch away and back. Fixed by having the service also collect
`BlockingStateHolder.state` directly (`serviceScope` collector added in `onServiceConnected`) and
re-check the *current* foreground app via `rootInActiveWindow?.packageName` on every state change,
not just on window transitions — covers session start, break end, and any other transition into
"actively blocking" while the target app is already frontmost.

**Bug fixed this pass — closing the active-session screen with the X made it unreachable again.**
`MainActivity`'s `LaunchedEffect(activeSession?.id, activeSession?.endTimeEpochMilli)` only
re-navigates to `"session"` when one of those two keys changes — so tapping the header's X
(`popBackStack` back to Home, session still running) leaves the effect with nothing to react to, and
Home's launcher bar tap handler was a no-op for the "already active" case (a stale comment claiming
navigation was "already handled"). Fixed by making that tap handler explicitly
`navController.navigate("session")` instead of relying on the one-shot effect, so the launcher bar
always gets you back to the running session.

**Apps step is now a dedicated picker screen, matching iOS.** iOS's Apps step
(`BlockedProfileAppSelector.swift`) is just a "Select Apps to Restrict/Allow" row with a chevron
and a "N selected" caption that opens `.familyActivityPicker` as its own sheet — not an inline
checkbox list mixed into the rest of the form. The Android wizard/edit-form previously rendered
every installed app's checkbox directly inline in the Apps section. `AppsFields` in
`ProfileFormFields.kt` now matches the iOS row exactly (same copy, same allow/restrict-mode label
switch), and opens a new full-screen `ui/profile/AppPickerDialog.kt` (a `Dialog` with
`usePlatformDefaultWidth = false`) with the checkbox list — plus a search field, since Android's
list is real installed apps rather than iOS's category tree and can run much longer.

**Home now has the Manage-profiles button next to Settings, both restyled to match iOS.**
`HomeProfilesListView.swift` renders `RoundedButton(iconName: "person.crop.circle")` (Manage) and
`RoundedButton(iconName: "gear")` (Settings) as a pair of icon-only translucent
("`.ultraThinMaterial`") rounded-rect buttons, right above the profile list — not a single plain
gear icon pinned to the very top of the page above the 3D model, which is where the Android
Settings button used to live. `HomeScreen.kt` now has a `HomeGlassIconButton` (rounded-rect,
translucent white background + subtle border, matching the RoundedButton look) and places both
buttons in that same position, inside the profile-list `LazyColumn`'s first item. Faithfully
matching the source also means faithfully matching its one quirk: like iOS, **neither button shows
when there are zero profiles** (`HomeProfilesListView` — and therefore this button row — only
renders `if !profiles.isEmpty()` in the Swift source), so a fresh install reaches Settings only
after creating a first profile via the Welcome screen. Not a bug — flagged here so it doesn't read
as an accidental omission.

Manage opens the new `ui/home/ManageProfilesScreen.kt`, porting `BlockedProfileListView.swift`: tap
a row to edit, "+" to create (disabled while a profile is actively blocking, matching iOS's
`canCreateProfiles`), a pencil toggle for edit mode showing a red delete button per row (guarded
against deleting the active profile, same alert copy as iOS), and reordering. **Simplified vs.
iOS**: reordering uses per-row up/down arrow buttons instead of iOS's native drag handle — this
project has no drag-reorder library dependency, and a hand-rolled pointer-drag reimplementation
wasn't worth the fragility for a rarely-used affordance. `ProfileRepository.reorder` (already used
elsewhere) is the same call either way, so swapping in real drag-and-drop later is a pure UI change,
not a data-layer one.

**Bug fixed this pass — the 3D model read dark, and was slow to appear/disappear on every Home
visit.** Three related issues in `model3d/`:
- *Dark*: the fragment shader used one directional light with a hard 0.45 ambient floor, so faces
  angled away from that single light dropped close to half brightness — reads dark/muddy compared
  to SceneKit's soft, even default lighting rig on iOS. Added a second, weaker fill light from
  roughly the opposite side and raised the floor to 0.6 (still clamped to 1.0), in
  `CtrusModelGlRenderer`'s fragment shader.
- *Slow every time, not just once*: `onSurfaceCreated` re-read and float-parsed the ~3MB OBJ text
  from the asset file from scratch on every single surface creation — and Compose Navigation
  disposes+recreates the `GLSurfaceView` (a fresh `onSurfaceCreated` call) every time Home leaves
  and re-enters composition, so this ran again on every visit, not just app cold start. Added
  `model3d/ObjModelCache.kt`, a process-scoped cache of the parsed mesh (`CtrusApp.onCreate` also
  warms it in the background so the very first visit doesn't stall either); each renderer still
  uploads its own VBOs from the cached float data since a GL context can't outlive its own
  `GLSurfaceView`. If the "disappear" side of the lag isn't fully gone after this, the next suspect
  is `setZOrderOnTop(true)` on the `GLSurfaceView` in `RotatingModel3DView.kt` — needed today to
  keep the translucent format compositing correctly, but `SurfaceView`-backed views are known not
  to participate cleanly in Compose's own transition animations, which could show up as exactly
  this kind of lingering-during-navigation symptom; not touched this pass since it's a much larger,
  riskier change (would mean moving to a custom `TextureView`-based GL view) that needs on-device
  confirmation the cache fix didn't already resolve it.

**Bug fixed this pass — blocking an app visibly flashed the home screen before the shield
appeared.** An earlier version of `BlockingAccessibilityService.showBlocker` called
`performGlobalAction(GLOBAL_ACTION_HOME)`, waited 250ms for that transition to settle, then
`startActivity(BlockerActivity)` — added because starting the two back-to-back in the same tick
raced the HOME transition and sometimes left the launcher on top instead of the shield. That fix
traded one bug for a more noticeable one: the blocked app now reliably reached the shield, but only
after a visible detour through the home screen, reading as "the app abruptly closes, then the block
screen appears" instead of an instant shield. Fixed properly by dropping `GLOBAL_ACTION_HOME` from
`showBlocker` entirely — `BlockerActivity` now starts immediately on top of the blocked app — and
moving the "go home" behavior to `BlockerActivity` itself: its dismiss button and back gesture both
launch the home intent before finishing, which is what actually needs to keep the blocked app's
task from being revealed, without any detour on the way *in*. On-device (emulator) testing with
`com.google.android.deskclock` as the target confirmed the shield now appears immediately with no
home-screen flash, re-blocks reliably on repeated attempts, and dismissing lands on home rather
than back in the blocked app.

## Localization

The app ships in English (default), Portuguese — Portugal (`values-pt-rPT`), and Spanish
(`values-es`), matching the iOS app's three languages exactly (`Ctrus/Localizable.xcstrings`:
`en` source, `pt`, `es` — Apple's unqualified `pt` locale *is* Portugal Portuguese, not Brazilian,
so it's a direct match). No in-app language switcher — like iOS, the app just follows the phone's
system locale automatically (no `Locale.setDefault`/`AppCompatDelegate` override anywhere; Android's
normal resource-qualifier resolution handles it), falling back to English for any other language,
including generic/Brazilian Portuguese (`pt` without `-rPT`) since only Portugal Portuguese was
requested.

**Translation source**: wherever the English copy in this port matches (or closely matches) an
iOS source string, the real iOS translation was copied over rather than retranslated — queried
directly from `Localizable.xcstrings`'s JSON (`localizations.pt/es.stringUnit.value`) rather than
guessed, so terminology stays consistent with the real app a bilingual user may already know
(e.g. "Cancelar"/"Eliminar", the "tu"/"tú" informal register throughout). Strings with no iOS
equivalent — Android-only UI like the Accessibility-service disclosure dialog, the Manage-profiles
screen, or the app picker's search field — were translated fresh, matching that same informal
register and the terminology already established by the reused strings (e.g. "Definições"/
"Ajustes" for Settings, "Apps" left as a loanword in both PT and ES exactly as iOS's own
translators chose to).

**`FocusMessages.kt`'s 42 rotating messages are deliberately left English-only** — verified
against `FocusMessages.swift`, which hardcodes the array with no `String(localized:)` wrapper, so
iOS itself never localizes these; matching that exactly is the correct port, not a gap.

**Architecture**: plain `stringResource()`/`pluralStringResource()` everywhere it's called from a
`@Composable`. Two non-Composable layers needed a Context-free way to carry localized text:
- `BlockingStrategy.displayNameRes`/`descriptionRes` are now `@StringRes Int` instead of raw
  `String` (`NfcBlockingStrategy`, `NfcManualBlockingStrategy`) — resolved via `stringResource()`
  only at the UI call sites that render them (`ProfileFormFields.StrategyFields`,
  `GuidedProfileCreationScreen.ReviewContent`).
- `util/UiText.kt` — a small `data class UiText(@StringRes resId, args)` plus a `@Composable
  UiText.resolve()` extension, used wherever a plain-Kotlin layer (the two strategy
  implementations' `StrategyResult.Error`/`NeedsInput` messages, `SessionOrchestrator`'s
  `_errorMessage`/`PendingRequirement.message`) needs to produce localized, sometimes-parameterized
  text without holding a `Context`. This is the standard Android pattern for keeping ViewModels
  and domain logic Context-free while still supporting localized strings with format args (e.g.
  `error_background_stops_disabled`'s `%1$s` for the profile name).

**Plurals**: real `<plurals>` resources (`apps_count`, `domains_count`, `apps_selected_count`,
etc.) for one/other counts (App/Apps, Domain/Domains, "N selected", "N minutes reusable", …) —
iOS's own xcstrings just collapses these to an English `(s)` suffix trick (`"%lld app(s)
selected"`) rather than real ICU plural rules, but Android has first-class plural support so this
port uses it properly instead of copying that shortcut; the PT/ES wording still matches iOS's
existing translations' word choice and gender agreement (e.g. "app" is feminine — "selecionada(s)"
— matching iOS's own translated plural strings, which was cross-checked before writing the
Android `one`/`other` forms).

Regenerating the three `strings.xml` files (if new keys are added) uses a small one-off Python
script that was written for this pass (not checked into the repo — it lived in the session's
scratchpad) that queries `Localizable.xcstrings` for exact-string matches and emits all three
locale files from one Python dict keyed by resource name; there's no ongoing build-time dependency
on it, it was just the fastest way to author ~260 correctly-cross-referenced strings across three
languages in one pass without them drifting out of sync with each other.

As of the current pass:

- **`data/`** — Room persistence (`BlockedProfileEntity`, `BlockedProfileSessionEntity`),
  `ProfileRepository`/`SessionRepository` mirroring the static methods on
  `BlockedProfiles.swift`/`BlockedProfileSessions.swift`, `SessionTimeCalculator` ported from the
  iOS source (trimmed to the break-only case — see below).
- **`strategy/`** — exactly the two strategies `StrategyManager.pickerStrategies` offers: **Ctrus
  NFC** (`NfcBlockingStrategy`, NFC required to start and stop, same tag to stop) and **Manual +
  Ctrus NFC** (`NfcManualBlockingStrategy`, manual start, NFC to stop). The other iOS strategy
  classes (`ManualBlockingStrategy`, `NFCTimerBlockingStrategy`, `NFCPauseTimerBlockingStrategy`,
  `NFCSoftUnblockBlockingStrategy`, `ShortcutTimerBlockingStrategy`) are real Swift files but
  never reachable through the app's create/edit-profile UI — not ported. Because of this, pause
  state, soft-unblock grants, session timers, and `BlockedProfileSchedule` are all gone too (they
  only ever existed to serve the strategies/feature that got cut) — breaks are the *only* thing
  left that can put a session in a non-`isActive` "restrictions suspended" state.
- **`blocking/`** — `BlockDecisionEngine` resolves real deny-list/allow-list decisions from
  `BlockingStateHolder`, an in-memory cache reactively derived from Room (the in-process
  replacement for iOS's App-Group `SharedData` snapshot layer).
- **`scheduling/`** — `AlarmSchedulingGateway`/`ExpiryReceiver`, trimmed to the one thing that's
  actually reachable: auto-resuming blocking when a break (the Breaks section in the profile
  form) elapses.
- **`session/SessionOrchestrator`** — the `StrategyManager.swift` equivalent: owns the active
  session/profile, the once-a-second timer, focus-message rotation, break/emergency-unblock
  orchestration. No "Reset Blocking State" action — iOS's version clears `ManagedSettings`
  restrictions and ghost `DeviceActivity` schedules, neither of which exist in this trimmed
  architecture (blocking state is 100% derived reactively from Room), so keeping the Settings row
  would've been a button that does nothing — cut instead of faked.
- **`network/RecoveryCodeClient`** — calls the real production `recover.ctrus.net` worker
  (`/verify-code`), matching its request/response shape exactly.
- **`settings/AppPreferences`** — DataStore-backed emergency/recovery-unlock counters (with
  weekly reset), device id, intro-seen flag, review-prompt gate.
- **`picker/InstalledAppsRepository`** — the Android replacement for iOS's `FamilyActivityPicker`:
  real installed-app list via `PackageManager`, profiles store plain package names.
- **UI** — `HomeScreen` (3D mascot, styled profile cards, launcher bar — see above),
  `ProfileFormScreen` (full create/edit form — see above), `ActiveSessionScreen` (live
  timer/break/emergency, bound to `SessionOrchestrator`), `SettingsScreen` (theme, app icon, real
  recovery-code verification, device id).

**`nfc/`** — `NfcTagDecoder` + `NfcScanController` port the read side of NFCScannerUtil.swift:
`NfcAdapter.enableReaderMode` scoped to exactly when a scan is pending (started/stopped from a
`DisposableEffect` on `pendingRequirement` in `MainActivity`), preferring a well-known-type NDEF
URI record whose host is `ctrus.net` and falling back to the tag's UID hex, matching iOS byte for
byte — a tag written by the iOS app should read identically here. **Only reading is ported**:
`NFCWriter.swift` and `NFCScannerUtil.writeURL` have zero call sites anywhere in the iOS
Views/Components (confirmed by grep) — nothing in the shipped app ever writes an NFC tag, so
there's no write feature to build. `PendingRequirementDialog` shows live scan status (with
NFC-hardware-missing / NFC-disabled states, the latter deep-linking to
`Settings.ACTION_NFC_SETTINGS`) instead of the old "not wired up yet" placeholder.

**`permissions/AccessibilityPermissionUtil`** + **`ui/intro/`** — the onboarding screen, following
Switchly's pattern: `AccessibilityDisclosureDialog` is a mandatory, checkbox-gated consent dialog
(Play policy requires prominent disclosure before sending the user to
`Settings.ACTION_ACCESSIBILITY_SETTINGS` — no attempted deep link to the specific toggle, just the
plain intent, matching Switchly) shown before `AccessibilityPermissionScreen` launches Settings;
status is re-checked via a `Lifecycle.Event.ON_RESUME` observer (`AccessibilityManager
.getEnabledAccessibilityServiceList`, falling back to `Settings.Secure` directly), not polling.
Gated on the same `showIntroScreen`-once-then-permanent-dismiss flag as
`HomeView.swift`/`AnimatedIntroContainer.swift`; the 3-second "still here?" nudge is ported too,
reworded for Android's actual flow (find-Ctrus-in-the-list) instead of iOS's Screen-Time-passcode
message, which doesn't apply here.

**`model3d/`** + **`ui/dashboard/RotatingModel3DView`** — the rotating 3D mascot, no longer
blocked: the user exported the actual Fusion 360 source (not the iOS `.usdz`) directly to
`app/src/main/assets/models/VF_Ctrus_Cima_FV.obj`+`.mtl` as one Body per named group, which
sidesteps the earlier "no usdz→glTF converter available" problem entirely. No maintained Android
library loads OBJ directly (Filament/SceneView need glTF; older engines like Rajawali are
unmaintained), so `ObjParser` is a small hand-rolled parser and `CtrusModelGlRenderer` a ~150-line
OpenGL ES 2.0 renderer (VBOs, one draw call per group, simple Lambert shading) — genuinely simpler
than pulling in and converting for a full 3D engine for one static, untextured model. Recoloring:
the source file's two materials are `Verde_PLA` (green — the fruit, recolored to the current theme
color every frame) and `Brnaco_PLA` (white — the segment dividers, always fixed), matching onto
groups named `V_Baixo`/`V_Cima` vs `B_Cima`/`B_Baixo`; the renderer picks accent-vs-static purely
by the `V_`/`B_` name prefix, so a re-export with the same group-naming convention (any color
variant — the source model happens to be the Lime-green one, but the app always recolors it) drops
in without code changes. Rotation is free drag (yaw unrestricted, pitch clamped ±80°) via a plain
`View.OnTouchListener` on the `GLSurfaceView`, `RENDERMODE_WHEN_DIRTY` (no idle auto-spin, matching
iOS). **Not yet visually verified on a device/emulator** — compiles clean, but nobody has actually
looked at the rendered model yet; check on first run that it appears, rotates smoothly, and
recolors correctly when the theme changes in Settings.

Still pending, in the order the plan tackles them next:
- Home-screen widget (`androidx.glance`) and the ongoing-notification replacement for iOS's Live
  Activity.
- Play In-App Review prompt.

**`icon/`** — app-icon switching is real: `AppIconController.select` enables exactly one of four
`<activity-alias>` entries in AndroidManifest.xml (Orange/Lime/Lemon/Dark, each targeting
`.MainActivity`, reusing the existing `drawable/ic_app_icon_*` art directly rather than duplicating
it into `mipmap/`) and disables the rest via `PackageManager.setComponentEnabledSetting`, the
Android analog of iOS's `UIApplication.setAlternateIconName`. `.MainActivity` itself no longer
carries the LAUNCHER intent-filter (moved onto the aliases) — it keeps only the `ctrus.net` deep
link filter. Selection persists via `AppPreferences.selectedAppIcon`/`setSelectedAppIcon`, wired
into `SettingsScreen`'s existing icon picker. **Known Android platform limitation, not a bug**:
most launchers only redraw the home-screen icon after the next launcher refresh or app
relaunch/reboot — the underlying alias switch is applied immediately (verified in the merged
manifest: exactly one alias `enabled="true"` at a time), but don't expect an instant icon flip the
moment you tap a swatch in Settings.

Deliberately out of scope (dead/inapplicable code on the iOS side, or no Android equivalent worth
building) — see the plan file for the full reasoning: the iOS Home dashboard's Activity/heatmap
card (feature-flagged off in the shipped iOS app), the Debug screen, AppIntents/Shortcuts, CSV
export (confirmed unused in the iOS source itself), and — per the rule above — every strategy
outside `pickerStrategies` and the whole `BlockedProfileSchedule`/soft-unblock-grant subsystem.

The launcher icon and app-icon preview assets (`res/drawable/ic_app_icon_*`,
`res/mipmap/ic_launcher*`) are the real ported Ctrus artwork, not placeholders. **Source of truth
is `Ctrus/Assets.xcassets/AppIcon{Orange,Lime,Lemon,Dark}Preview.imageset/*.png` on the iOS
side** — those are Xcode's pre-rendered flat exports of the actual layered `.icon` bundles
(`Ctrus-icon-*.icon/`, Apple's iOS 26 "Liquid Glass" composited-icon format: per-layer PNGs +
`icon.json` describing scale/translation/fill/blend per layer). The `.icon` bundles' raw layer
PNGs are NOT usable directly — they're uncomposited inputs to Xcode's icon renderer, not finished
icons — so always copy from the `*Preview.imageset` flat exports, never the `.icon` bundle
`Assets/` folders. If the iOS artwork changes again, re-copy from there (same filenames, direct
`cp`, no image processing needed) rather than assuming what's already in the Android repo is
current — it drifted out of date once already (an 11-day-stale copy caught in this session).

## Build

Builds clean: `./gradlew :app:assembleDebug` succeeds (verified using Android Studio's
bundled JBR as JAVA_HOME, since there's no separate system JDK). Only verified to
compile and package, not run on a device/emulator yet — visually check the screens
against the iOS app once you can launch it, since none of the layout has been eyeballed
outside of reading the source.

Toolchain is current-stable as of when this was last built: Gradle 9.7.0, AGP 9.3.1,
Kotlin 2.4.10, Compose BOM 2026.06.01. AGP 9 has Kotlin support built in, so there's no
separate `org.jetbrains.kotlin.android` plugin or `kotlinOptions` block — don't add them
back if a migration guide/older tutorial suggests it.
