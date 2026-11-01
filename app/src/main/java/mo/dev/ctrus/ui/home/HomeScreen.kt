package mo.dev.ctrus.ui.home

import mo.dev.ctrus.strategy.StrategyIds
import mo.dev.ctrus.alarm.WakeAlarmManager
import mo.dev.ctrus.CtrusApp
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.animation.core.animateFloat
import mo.dev.ctrus.data.ProfileSchedule
import mo.dev.ctrus.data.ScheduleSkip
import java.time.ZonedDateTime
import kotlinx.coroutines.delay
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GppMaybe
import mo.dev.ctrus.childmode.TamperLogEntry
import mo.dev.ctrus.ui.common.IntroCard
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import mo.dev.ctrus.R
import mo.dev.ctrus.data.BlockedProfileEntity
import mo.dev.ctrus.data.SessionRepository
import mo.dev.ctrus.theme.FixedLightPrimaryText
import mo.dev.ctrus.theme.FixedLightSecondaryText
import mo.dev.ctrus.theme.ThemeManager
import mo.dev.ctrus.theme.pastelBackground
import mo.dev.ctrus.ui.common.AutoSizeText
import mo.dev.ctrus.ui.dashboard.DefaultModel3DSize
import mo.dev.ctrus.ui.dashboard.RotatingModel3DView
import mo.dev.ctrus.ui.insights.ProfileUsageMiniBarChart
import mo.dev.ctrus.util.DateFormatters

// This screen has a light pastel background regardless of the app-wide dark
// theme used elsewhere (Settings, Active Session), so its text needs fixed
// dark colors instead of the (white) theme defaults meant for dark surfaces.
private val HomeOnPastel = FixedLightPrimaryText
private val HomeOnPastelVariant = FixedLightSecondaryText
private val EmergencyRed = Color(0xFFFF3B30)
// SessionActionButton.swift draws its SF Symbols at 15pt; Material glyphs fill more of their box,
// so 16dp lands on about the same visual size next to the 18sp label.
private val SessionActionIconSize = 16.dp

private const val HOLD_TO_START_MS = 800
private val BaseBorderWidth = 3.5.dp
private val HeldBorderWidth = 5.dp

// Caps the profile card / action buttons at roughly phone width on a foldable's much wider
// unfolded inner display, instead of letting them stretch edge-to-edge — a normal phone (or a
// foldable's cover screen) is already narrower than this, so the constraint is a no-op there and
// only kicks in once the available width goes past what a folded/cover screen would offer.
private val HomeContentMaxWidth = 480.dp

// The same spring physics driving the balloon's press-scale feedback, reused for the
// expand/collapse of its Break/Emergency/Stop actions so the whole card feels like one
// consistent, tactile surface instead of switching to a flat linear fade partway through.
private fun <T> pressSpring(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.74f, stiffness = 700f)

/** [rememberHoldFeedback]'s output: the gesture to attach, plus what it's currently doing. */
private class HoldFeedback(val gestureModifier: Modifier, val isPressed: Boolean, val progress: Float)

/**
 * The balloon card's own press-and-hold-to-start feedback (grow the border while holding, shrink
 * slightly on press), factored out so the Break button can share the exact same feel instead of
 * the color-fill sweep [mo.dev.ctrus.ui.session.HoldToConfirmButton] used for it before — this
 * hold gesture belongs to the card's own visual language, not a separate button style.
 */
@Composable
private fun rememberHoldFeedback(enabled: Boolean, key: Any? = Unit, onConfirm: () -> Unit): HoldFeedback {
    var isPressed by remember { mutableStateOf(false) }
    val holdProgress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // Mirrors the balloon card's own reset: if `enabled` flips off mid-hold (e.g. the profile
    // became active, or blocking started elsewhere), don't leave the border/scale stuck grown.
    LaunchedEffect(enabled) {
        if (!enabled) {
            isPressed = false
            holdProgress.snapTo(0f)
        }
    }

    // Keyed on `key` too (not just `enabled`, which rarely changes) — otherwise this gesture's
    // `onConfirm` stays whatever it captured the first time this card composed, ignoring the
    // fact that `onConfirm` is a fresh closure over new data on every recomposition. Concretely:
    // editing a profile's strategy from Ctrus NFC to Manual + Ctrus NFC didn't change `enabled`,
    // so held-over card kept calling the *old* NFC-requiring start until something else forced a
    // fresh composition — pass the profile (or another value that changes when it matters) as
    // `key` so a relevant edit restarts the gesture with the current `onConfirm` right away.
    val modifier = Modifier.pointerInput(enabled, key) {
        detectTapGestures(
            onPress = {
                if (!enabled) return@detectTapGestures
                isPressed = true
                val holdJob = scope.launch {
                    holdProgress.snapTo(0f)
                    holdProgress.animateTo(1f, tween(HOLD_TO_START_MS, easing = LinearEasing))
                    isPressed = false
                    onConfirm()
                    holdProgress.snapTo(0f)
                }
                tryAwaitRelease()
                holdJob.cancel()
                isPressed = false
                if (holdProgress.value < 0.999f) {
                    scope.launch { holdProgress.animateTo(0f, tween(200)) }
                }
            }
        )
    }
    return HoldFeedback(modifier, isPressed, holdProgress.value)
}

/**
 * Mirrors HomeView.swift's pageBody: 3D mascot, then either the empty-state Welcome or the
 * profile list. Each profile is its own hold-to-start card (mirrors ProfileBalloonRow.swift) —
 * there's no separate launcher bar or full-screen active-session surface anymore; an active
 * profile's card expands in place to reveal Break/Emergency/Stop.
 */
@Composable
fun HomeScreen(
    themeManager: ThemeManager,
    sessionRepository: SessionRepository,
    profiles: List<BlockedProfileEntity>,
    activeProfile: BlockedProfileEntity?,
    isBlocking: Boolean,
    displaySeconds: Double,
    isBreakActive: Boolean,
    isBreakAvailable: Boolean,
    useLeftHandedLayout: Boolean,
    hasCompletedFirstSession: Boolean,
    onOpenSettings: () -> Unit,
    onAddProfile: () -> Unit,
    onEditProfile: (BlockedProfileEntity) -> Unit,
    onStartProfile: (BlockedProfileEntity) -> Unit,
    onStopProfile: (BlockedProfileEntity) -> Unit,
    onBreakTapped: () -> Unit,
    onEmergencyTapped: () -> Unit,
    onInsightsTapped: (BlockedProfileEntity) -> Unit,
    onManageTapped: () -> Unit,
    onSkipNextTapped: (BlockedProfileEntity) -> Unit = {},
    onSkipUntilTapped: (BlockedProfileEntity) -> Unit = {},
    onResumeScheduleTapped: (BlockedProfileEntity) -> Unit = {},
    isAccessibilityEnabled: Boolean = true,
    isExactAlarmGranted: Boolean = true,
    isAutostartGranted: Boolean = true,
    isBackgroundPopupsGranted: Boolean = true,
    onPermissionsAlertTapped: () -> Unit = {},
    /** Child Mode's newest unseen attempt-log entry, shown as one more red pill. */
    tamperEntry: TamperLogEntry? = null,
    onTamperTapped: () -> Unit = {},
    /** Normal Mode with no profiles yet: a mistaken pick of the mode can still be undone. */
    canGoBackToModeChoice: Boolean = false,
    onBackToModeChoice: () -> Unit = {},
) {
    val themeColor = themeManager.selectedColorOption.color

    Box(
        modifier = Modifier.fillMaxSize().background(pastelBackground(themeColor)),
        contentAlignment = Alignment.TopCenter,
    ) {
        // One scrollable list for the whole page — the 3D model, icon row, and profile cards all
        // scroll together instead of the model staying pinned above a separately-scrolling list,
        // which on a short screen left the cards (and their expanded Break/Emergency/Stop actions)
        // fighting a cramped, separately-clipped region below a fixed header.
        LazyColumn(
            // widthIn MUST come before fillMaxWidth: fillMaxWidth first pins min=max=the parent's
            // full width, and a widthIn(max=...) applied after that can no longer shrink it back
            // down since the minimum is already locked at the larger value — widthIn has to narrow
            // the constraint *before* fillMaxWidth turns "at most this wide" into "exactly this wide".
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = HomeContentMaxWidth)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Only surfaced once there's something to actually protect — the empty First Steps
            // screen shouldn't nag about permissions before a profile even exists to use them.
            // Accessibility and "Alarms & reminders": the permissions a session can't start
            // without. Battery-optimization exemption is a separate, non-blocking recommendation —
            // see its own one-time dialog (MainActivity's showBatteryOptimizationPrompt) and Settings row.
            val showsPermissionsPill = profiles.isNotEmpty() && (!isAccessibilityEnabled || !isExactAlarmGranted || !isAutostartGranted || !isBackgroundPopupsGranted)
            // Missing permissions come first; what else was logged shows once they're back.
            val visibleTamperEntry = tamperEntry.takeIf { !showsPermissionsPill }
            if (showsPermissionsPill || visibleTamperEntry != null || canGoBackToModeChoice) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (canGoBackToModeChoice) {
                            HomeGlassIconButton(
                                onClick = onBackToModeChoice,
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.guided_back_content_description),
                            )
                        }
                        if (showsPermissionsPill) AccessibilityAlertPill(onClick = onPermissionsAlertTapped)
                        visibleTamperEntry?.let {
                            HomeAlertPill(icon = Icons.Filled.GppMaybe, title = stringResource(it.type.titleRes), onClick = onTamperTapped)
                        }
                    }
                }
            }

            item {
                // LazyItemScope's own `align` takes a full (2-axis) Alignment rather than
                // ColumnScope's Alignment.Horizontal, so centering here goes through a Box instead.
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    RotatingModel3DView(themeColor = themeColor, modifier = Modifier.size(DefaultModel3DSize))
                }
            }

            if (profiles.isEmpty()) {
                item { WelcomeSection(themeColor = themeColor, onAddProfile = onAddProfile) }
            } else {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = if (useLeftHandedLayout) Arrangement.Start else Arrangement.End,
                    ) {
                        if (useLeftHandedLayout) {
                            HomeGlassIconButton(onClick = onOpenSettings, icon = Icons.Filled.Settings, contentDescription = stringResource(R.string.home_settings_content_description))
                            Spacer(Modifier.width(8.dp))
                            HomeGlassIconButton(onClick = onManageTapped, icon = Icons.Filled.AccountCircle, contentDescription = stringResource(R.string.home_manage_content_description))
                        } else {
                            HomeGlassIconButton(onClick = onManageTapped, icon = Icons.Filled.AccountCircle, contentDescription = stringResource(R.string.home_manage_content_description))
                            Spacer(Modifier.width(8.dp))
                            HomeGlassIconButton(onClick = onOpenSettings, icon = Icons.Filled.Settings, contentDescription = stringResource(R.string.home_settings_content_description))
                        }
                    }
                }
                items(profiles, key = { it.id }) { profile ->
                    val isActive = profile.id == activeProfile?.id
                    ProfileBalloonCard(
                        profile = profile,
                        sessionRepository = sessionRepository,
                        isActive = isActive,
                        isBlocking = isBlocking,
                        themeColor = themeColor,
                        displaySeconds = displaySeconds,
                        isBreakActive = isActive && isBreakActive,
                        isBreakAvailable = isActive && isBreakAvailable,
                        onEdit = { onEditProfile(profile) },
                        onStart = { onStartProfile(profile) },
                        onStop = { onStopProfile(profile) },
                        onBreak = onBreakTapped,
                        onEmergency = onEmergencyTapped,
                        onInsights = { onInsightsTapped(profile) },
                        onSkipNext = { onSkipNextTapped(profile) },
                        onSkipUntil = { onSkipUntilTapped(profile) },
                        onResumeSchedule = { onResumeScheduleTapped(profile) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                if (!hasCompletedFirstSession) {
                    item {
                        // Schedule profiles can't be held to start — they start on their own — so
                        // the hint says so when there are any (iOS d439d32).
                        val scheduleCount = profiles.count { it.blockingStrategyId in StrategyIds.SCHEDULED_IDS }
                        Text(
                            stringResource(
                                when (scheduleCount) {
                                    profiles.size -> R.string.home_hint_schedule_only
                                    0 -> R.string.home_first_session_hint
                                    else -> R.string.home_hint_schedule_mixed
                                },
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = HomeOnPastelVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        )
                    }
                }
            }
        }

        // A thin strip on the left edge, above the list: dragging from it to the right goes back
        // to the mode choice too (iOS's swipe from First Steps).
        if (canGoBackToModeChoice) {
            var dragged by remember { mutableStateOf(0f) }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(24.dp)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragged = 0f },
                            onDragEnd = { if (dragged > 60.dp.toPx()) onBackToModeChoice() },
                        ) { _, amount -> dragged += amount }
                    },
            )
        }
    }
}

/** Android equivalent of HomeAlertsView's HomeAlertCard: a red capsule pill, tapped to open the detail sheet. */
@Composable
private fun AccessibilityAlertPill(onClick: () -> Unit) =
    HomeAlertPill(icon = Icons.Filled.Warning, title = stringResource(R.string.home_permissions_alert_pill), onClick = onClick)

/** HomeAlertsView's HomeAlertPill: a red capsule with a white icon and one line of white text. */
@Composable
private fun HomeAlertPill(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(EmergencyRed.copy(alpha = 0.85f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            maxLines = 1,
        )
    }
}

/** Android equivalent of RoundedButton's icon-only "ultraThinMaterial" glass style. */
@Composable
private fun HomeGlassIconButton(onClick: () -> Unit, icon: ImageVector, contentDescription: String) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.35f))
            .border(1.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = HomeOnPastelVariant)
    }
}

@Composable
private fun WelcomeSection(themeColor: Color, onAddProfile: () -> Unit) {
    // Spec Part B, 5.1 / 14: a card like the mode choice's, title and subtitle inside it.
    IntroCard(
        title = stringResource(R.string.home_getting_started_title),
        subtitle = stringResource(R.string.home_getting_started_body),
        icon = Icons.Filled.AccountCircle,
        themeColor = themeColor,
        onClick = onAddProfile,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 22.dp),
    )
}

/**
 * Mirrors ProfileBalloonRow.swift: hold the card to start the session, tap it while active to
 * reveal break/emergency/stop, tap again to collapse.
 */
@Composable
private fun ProfileBalloonCard(
    profile: BlockedProfileEntity,
    sessionRepository: SessionRepository,
    isActive: Boolean,
    isBlocking: Boolean,
    themeColor: Color,
    displaySeconds: Double,
    isBreakActive: Boolean,
    isBreakAvailable: Boolean,
    onEdit: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onBreak: () -> Unit,
    onEmergency: () -> Unit,
    onInsights: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipUntil: () -> Unit,
    onResumeSchedule: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember(profile.id) { mutableStateOf(false) }
    // Schedule profiles only ever start on their own, at the scheduled time — never by hand.
    val canStart = !isBlocking && profile.blockingStrategyId !in StrategyIds.SCHEDULED_IDS
    val hold = rememberHoldFeedback(enabled = !isActive && canStart, key = profile, onConfirm = onStart)

    LaunchedEffect(isActive) {
        if (!isActive) isExpanded = false
    }

    // Alarm mode (spec D6): opened by sliding the alarm, the card opens with its buttons and, a
    // moment later, reading the Ctrus starts as if Stop had been pressed. Cancelling the reading
    // leaves the buttons open.
    val isAlarmProfile = profile.blockingStrategyId == StrategyIds.ALARM
    val expandRequest by WakeAlarmManager.expandRequestProfileId.collectAsState()
    val stopRequest by WakeAlarmManager.stopRequestProfileId.collectAsState()
    LaunchedEffect(isActive, expandRequest) {
        if (isActive && expandRequest == profile.id) {
            isExpanded = true
            WakeAlarmManager.expandRequestProfileId.value = null
        }
    }
    // Cleared only after the wait: clearing it first would restart this effect and cancel the wait.
    val stopRequested = stopRequest == profile.id
    LaunchedEffect(isActive, stopRequested) {
        if (isActive && stopRequested) {
            isExpanded = true
            delay(600)
            WakeAlarmManager.stopRequestProfileId.value = null
            onStop()
        }
    }
    val alarm = rememberAlarmCardState(profile.id, isActive && isAlarmProfile)
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val scale by animateFloatAsState(
        targetValue = if (hold.isPressed) 0.97f else 1f,
        animationSpec = pressSpring(),
        label = "balloonScale",
    )
    val borderWidth = if (isActive) BaseBorderWidth else lerp(BaseBorderWidth, HeldBorderWidth, hold.progress)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .border(borderWidth, themeColor, RoundedCornerShape(20.dp))
            .then(if (!isActive) hold.gestureModifier else Modifier),
    ) {
        BalloonHeader(
            profile = profile,
            sessionRepository = sessionRepository,
            isActive = isActive,
            isBlocking = isBlocking,
            themeColor = themeColor,
            displaySeconds = displaySeconds,
            isBreakActive = isBreakActive,
            alarm = alarm,
            onHeaderTapped = { if (isActive) isExpanded = !isExpanded },
            onEdit = onEdit,
            onStart = onStart,
            onStop = onStop,
            onInsights = onInsights,
            onSkipNext = onSkipNext,
            onSkipUntil = onSkipUntil,
            onResumeSchedule = onResumeSchedule,
        )

        AnimatedVisibility(
            visible = isActive && isExpanded,
            enter = fadeIn(pressSpring()) + expandVertically(pressSpring(), expandFrom = Alignment.Top),
            exit = fadeOut(pressSpring()) + shrinkVertically(pressSpring(), shrinkTowards = Alignment.Top),
        ) {
            ExpandedActions(
                // Only while it rings: a snooze counts down in the clock instead, and the button
                // comes back once it's over.
                showsSnooze = alarm?.isRinging == true && profile.enableSnooze,
                onSnooze = { scope.launch { WakeAlarmManager.snooze(CtrusApp.from(context), profile.id) } },
                isBreakAvailable = isBreakAvailable,
                isBreakActive = isBreakActive,
                hasEmergencyUnblock = profile.enableEmergencyUnblock,
                themeColor = themeColor,
                onBreak = onBreak,
                onEmergency = onEmergency,
                onStop = { isExpanded = false; onStop() },
            )
        }
    }
}

@Composable
private fun BalloonHeader(
    profile: BlockedProfileEntity,
    sessionRepository: SessionRepository,
    isActive: Boolean,
    isBlocking: Boolean,
    themeColor: Color,
    displaySeconds: Double,
    isBreakActive: Boolean,
    alarm: AlarmCardState?,
    onHeaderTapped: () -> Unit,
    onEdit: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onInsights: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipUntil: () -> Unit,
    onResumeSchedule: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isActive) Modifier.clickable(onClick = onHeaderTapped) else Modifier)
            .padding(16.dp),
        // ProfileBalloonRow.swift's HStack(spacing: 12).
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(profile.name, style = MaterialTheme.typography.titleMedium, color = HomeOnPastel)
            Spacer(Modifier.height(4.dp))
            val appsLabel = pluralStringResource(R.plurals.apps_count, profile.selectedPackages.size, profile.selectedPackages.size)
            val domainsLabel = pluralStringResource(R.plurals.domains_count, profile.domains.orEmpty().size, profile.domains.orEmpty().size)
            Text("$appsLabel | $domainsLabel", style = MaterialTheme.typography.bodySmall, color = HomeOnPastelVariant)
            // Hidden while the session runs (iOS 44ccf0b): next to the live timer, "Tomorrow at
            // 22:00" is noise. It only matters once the session has ended.
            profile.schedule?.takeIf { !isActive && it.isActive && profile.blockingStrategyId in StrategyIds.SCHEDULED_IDS }?.let {
                Spacer(Modifier.height(2.dp))
                NextScheduleLine(it, profile.scheduleSkip)
            }
        }

        Box(
            modifier = Modifier
                .width(118.dp)
                // A little taller during an Alarm mode break, to fit the break's clock under the alarm's.
                .height(if (alarm != null && isBreakActive) 84.dp else 62.dp)
                .then(if (isActive) Modifier.clickable(onClick = onHeaderTapped) else Modifier.clickable(onClick = onInsights))
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isActive && alarm != null) {
                AlarmSessionTimeAccessory(
                    alarm = alarm,
                    breakSeconds = displaySeconds.takeIf { isBreakActive },
                    fallbackSeconds = displaySeconds,
                    themeColor = themeColor,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (isActive) {
                SessionTimeAccessory(
                    displaySeconds = displaySeconds,
                    isBreakActive = isBreakActive,
                    themeColor = themeColor,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                val sessions by sessionRepository.observeForProfile(profile.id).collectAsState(initial = emptyList())
                ProfileUsageMiniBarChart(sessions = sessions, themeColor = themeColor, forcedLight = true)
            }
        }

        BalloonMenu(
            profileName = profile.name,
            isActive = isActive,
            canStart = !isBlocking,
            startsOnSchedule = profile.blockingStrategyId in StrategyIds.SCHEDULED_IDS,
            onInsights = onInsights,
            onEdit = onEdit,
            onStart = onStart,
            onStop = onStop,
            canSkip = profile.canSkipSessions,
            hasActiveSkip = profile.scheduleSkip.isActive(),
            onSkipNext = onSkipNext,
            onSkipUntil = onSkipUntil,
            onResumeSchedule = onResumeSchedule,
        )
    }
}

/**
 * Fills the exact same 118x62 slot [ProfileUsageMiniBarChart] occupies when inactive. The
 * [AutoSizeText] is required, not decorative: at a fixed 8-character "00:00:00" width, a bold
 * monospaced clock this size doesn't reliably fit next to the icon in that slot — it needs to
 * shrink instead of overflowing past the card's edge into the "…" menu beside it.
 */
@Composable
private fun SessionTimeAccessory(displaySeconds: Double, isBreakActive: Boolean, themeColor: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = if (isBreakActive) Icons.Filled.Coffee else Icons.Filled.AccessTime,
            contentDescription = null,
            tint = themeColor,
            modifier = Modifier.size(16.dp),
        )
        AutoSizeText(
            text = DateFormatters.formatDurationClock(displaySeconds),
            color = themeColor,
            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
            minFontSize = 11.sp,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** Alarm mode's state for an active card: when it rings, whether it's ringing, and a snooze. */
private data class AlarmCardState(val alarmDateMillis: Long?, val snoozedUntilMillis: Long?, val nowMillis: Long) {
    val isSnoozed: Boolean get() = (snoozedUntilMillis ?: 0) > nowMillis
    val isRinging: Boolean get() = alarmDateMillis != null && alarmDateMillis <= nowMillis && !isSnoozed
}

/** Null unless [enabled] (an active Alarm mode card); ticks every second while it is. */
@Composable
private fun rememberAlarmCardState(profileId: String, enabled: Boolean): AlarmCardState? {
    if (!enabled) return null
    val records by WakeAlarmManager.records.collectAsState()
    val snoozedUntil by WakeAlarmManager.snoozedUntil.collectAsState()
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1_000L - System.currentTimeMillis() % 1_000L)
            value = System.currentTimeMillis()
        }
    }
    return AlarmCardState(records[profileId]?.alarmDate, snoozedUntil, now)
}

/**
 * Alarm mode's take on [SessionTimeAccessory] (spec D6.2): counts down to the alarm, says it's
 * ringing once it's due, and counts a snooze down in its place. During a break, the break's own
 * countdown sits underneath, a little smaller and fainter.
 */
@Composable
private fun AlarmSessionTimeAccessory(
    alarm: AlarmCardState,
    breakSeconds: Double?,
    fallbackSeconds: Double,
    themeColor: Color,
    modifier: Modifier = Modifier,
) {
    val topFontSize = if (breakSeconds == null) 20.sp else 16.sp
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        when {
            alarm.isSnoozed -> AccessoryClockRow(Icons.Filled.Snooze, (alarm.snoozedUntilMillis!! - alarm.nowMillis) / 1000.0, topFontSize, themeColor)
            alarm.isRinging -> {
                val pulse by androidx.compose.animation.core.rememberInfiniteTransition(label = "ringing").animateFloat(
                    initialValue = 1f,
                    targetValue = 0.35f,
                    animationSpec = androidx.compose.animation.core.infiniteRepeatable(tween(700), androidx.compose.animation.core.RepeatMode.Reverse),
                    label = "ringingPulse",
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.Alarm, contentDescription = null, tint = themeColor.copy(alpha = pulse), modifier = Modifier.size(16.dp))
                    AutoSizeText(
                        text = stringResource(R.string.alarm_ringing),
                        color = themeColor,
                        style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                        minFontSize = 11.sp,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
            else -> AccessoryClockRow(
                Icons.Filled.Alarm,
                alarm.alarmDateMillis?.let { (it - alarm.nowMillis) / 1000.0 } ?: fallbackSeconds,
                topFontSize,
                themeColor,
            )
        }
        if (breakSeconds != null) {
            AccessoryClockRow(Icons.Filled.Coffee, breakSeconds, 14.sp, themeColor.copy(alpha = 0.75f))
        }
    }
}

@Composable
private fun AccessoryClockRow(icon: ImageVector, seconds: Double, fontSize: androidx.compose.ui.unit.TextUnit, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        AutoSizeText(
            text = DateFormatters.formatDurationClock(maxOf(0.0, seconds)),
            color = color,
            style = TextStyle(fontSize = fontSize, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
            minFontSize = 10.sp,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
private fun BalloonMenu(
    profileName: String,
    isActive: Boolean,
    canStart: Boolean,
    startsOnSchedule: Boolean,
    onInsights: () -> Unit,
    onEdit: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    canSkip: Boolean,
    hasActiveSkip: Boolean,
    onSkipNext: () -> Unit,
    onSkipUntil: () -> Unit,
    onResumeSchedule: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    Box {
        // iOS's horizontal "ellipsis" in a 32x44 frame, so the dots sit as far from the card's
        // edge as on iOS instead of being centered in a 48dp IconButton.
        Box(
            modifier = Modifier
                .size(width = 32.dp, height = 44.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { showMenu = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.MoreHoriz,
                contentDescription = stringResource(R.string.home_more_actions_content_description, profileName),
                tint = HomeOnPastelVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.90f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.home_menu_insights)) },
                leadingIcon = { Icon(Icons.Filled.BarChart, contentDescription = null) },
                onClick = { showMenu = false; onInsights() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.home_menu_edit)) },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = { showMenu = false; onEdit() },
            )
            if (isActive) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.home_menu_stop)) },
                    leadingIcon = { Icon(Icons.Filled.Stop, contentDescription = null) },
                    onClick = { showMenu = false; onStop() },
                )
            } else if (!startsOnSchedule) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.home_menu_start)) },
                    leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                    enabled = canStart,
                    onClick = { showMenu = false; onStart() },
                )
            }
            // With or without a session running, after the usual options (iOS's skipMenuItems).
            if (canSkip) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.skip_next)) },
                    leadingIcon = { Icon(Icons.Filled.SkipNext, contentDescription = null) },
                    onClick = { showMenu = false; onSkipNext() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.skip_until)) },
                    leadingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
                    onClick = { showMenu = false; onSkipUntil() },
                )
                if (hasActiveSkip) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.skip_resume)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null) },
                        onClick = { showMenu = false; onResumeSchedule() },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpandedActions(
    showsSnooze: Boolean,
    onSnooze: () -> Unit,
    isBreakAvailable: Boolean,
    isBreakActive: Boolean,
    hasEmergencyUnblock: Boolean,
    themeColor: Color,
    onBreak: () -> Unit,
    onEmergency: () -> Unit,
    onStop: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showsSnooze) {
            SessionPillButton(
                title = stringResource(R.string.alarm_snooze),
                borderColor = themeColor,
                contentColor = Color.Black,
                modifier = Modifier.fillMaxWidth(),
                onClick = onSnooze,
                icon = { Icon(Icons.Filled.Snooze, contentDescription = null, tint = Color.Black, modifier = Modifier.size(SessionActionIconSize)) },
            )
        }
        if (isBreakAvailable) {
            BreakHoldButton(
                title = stringResource(if (isBreakActive) R.string.session_hold_stop_break else R.string.session_hold_start_break),
                themeColor = themeColor,
                onConfirm = onBreak,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (hasEmergencyUnblock) {
                SessionPillButton(
                    title = stringResource(R.string.session_emergency_button),
                    borderColor = EmergencyRed,
                    contentColor = EmergencyRed,
                    modifier = Modifier.weight(1f),
                    onClick = onEmergency,
                    // Rounded variant: SF Symbols' exclamationmark.triangle.fill has soft corners.
                    icon = { Icon(Icons.Rounded.Warning, contentDescription = null, tint = EmergencyRed, modifier = Modifier.size(SessionActionIconSize)) },
                )
            }
            SessionPillButton(
                title = stringResource(R.string.session_stop_button),
                borderColor = themeColor,
                contentColor = Color.Black,
                modifier = Modifier.weight(1f),
                onClick = onStop,
                // Icons.Filled.Stop's actual square glyph sits well inside its 24dp viewport
                // (Material icons keep a visual-balance margin baked into every glyph's bounds),
                // so sizing the *icon* to match the "P" in "Parar" still rendered a visibly
                // smaller square. Drawing the square directly sidesteps that built-in margin.
                icon = { Box(Modifier.size(11.dp).background(Color.Black, RoundedCornerShape(2.dp))) },
            )
        }
    }
}

/**
 * Mirrors SessionActionButton.swift's hold-to-confirm role for Break — same border-grow +
 * press-scale feedback as the balloon card's own hold-to-start (via [rememberHoldFeedback]),
 * not [mo.dev.ctrus.ui.session.HoldToConfirmButton]'s color-fill sweep, so holding this button
 * feels like the same gesture as holding the card itself rather than a different button style.
 */
@Composable
private fun BreakHoldButton(title: String, themeColor: Color, onConfirm: () -> Unit) {
    // Keyed on `title` (it flips between "Hold to Start/Stop Break") so this restarts and picks
    // up a fresh `onConfirm` right when the action it confirms actually changes.
    val hold = rememberHoldFeedback(enabled = true, key = title, onConfirm = onConfirm)
    val scale by animateFloatAsState(
        targetValue = if (hold.isPressed) 0.97f else 1f,
        animationSpec = pressSpring(),
        label = "breakButtonScale",
    )
    val borderWidth = lerp(BaseBorderWidth, HeldBorderWidth, hold.progress)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Color.White)
            .border(borderWidth, themeColor, CircleShape)
            .then(hold.gestureModifier)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Coffee, contentDescription = null, tint = Color.Black, modifier = Modifier.size(SessionActionIconSize))
        Spacer(Modifier.width(8.dp))
        AutoSizeText(
            text = title,
            color = Color.Black,
            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp),
            minFontSize = 12.sp,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** Mirrors SessionActionButton.swift's plain (non-hold) role: a white pill with a colored border. */
@Composable
private fun SessionPillButton(
    title: String,
    borderColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .height(56.dp)
            // A full capsule, like SessionActionButton.swift's `Capsule()`.
            .clip(CircleShape)
            .background(Color.White)
            .border(BaseBorderWidth, borderColor, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(8.dp))
        AutoSizeText(
            text = title,
            color = contentColor,
            style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp),
            minFontSize = 13.sp,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/**
 * Port of ProfileSummaryRow.swift's `ProfileSummaryNextScheduleLine` (`nextStartMessage(includePrefix:
 * false)`): "Today at 22:00" / "Tomorrow at 9:00" / a full weekday-date-time further out. Unlike iOS,
 * there's no 15-minute buffer. iOS skips starts less than 15 minutes away only because
 * DeviceActivity can't schedule that soon, but the AlarmManager start here fires on time, so this
 * shows the start that will actually happen. It re-reads the clock every minute so "Today" rolls
 * over to the next occurrence once a start has passed. Skipped occurrences are passed over, so
 * with a skip active this names the first session that will actually start.
 */
@Composable
private fun NextScheduleLine(schedule: ProfileSchedule, skip: ScheduleSkip) {
    val context = LocalContext.current
    val now by produceState(initialValue = ZonedDateTime.now()) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            value = ZonedDateTime.now()
        }
    }
    val next = schedule.nextTrigger(now, skip = skip) ?: return
    val time = DateFormatters.formatTimeOfDay(context, next.hour, next.minute)
    val message = when (next.toLocalDate()) {
        now.toLocalDate() -> stringResource(R.string.home_next_start_today, time)
        now.toLocalDate().plusDays(1) -> stringResource(R.string.home_next_start_tomorrow, time)
        else -> {
            val skeleton = if (android.text.format.DateFormat.is24HourFormat(context)) "EEEEMMMdHm" else "EEEEMMMdhma"
            val locale = context.resources.configuration.locales[0]
            val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton)
            next.format(java.time.format.DateTimeFormatter.ofPattern(pattern, locale))
        }
    }
    // iOS uses .caption2: regular weight, a notch smaller than the "Apps | Domains" line above.
    Text(message, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = HomeOnPastelVariant, maxLines = 1)
}
