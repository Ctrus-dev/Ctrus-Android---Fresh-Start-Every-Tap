package mo.dev.ctrus

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mo.dev.ctrus.data.BlockedProfileEntity
import mo.dev.ctrus.data.PhysicalUnblockItem
import mo.dev.ctrus.data.isBreakActive
import mo.dev.ctrus.data.isBreakAvailable
import mo.dev.ctrus.icon.AppIconController
import mo.dev.ctrus.icon.DefaultAppIcon
import mo.dev.ctrus.network.RecoveryCodeVerification
import mo.dev.ctrus.settings.AppPreferences
import mo.dev.ctrus.nfc.NfcScanController
import mo.dev.ctrus.permissions.AccessibilityPermissionUtil
import mo.dev.ctrus.permissions.BatteryOptimizationUtil
import mo.dev.ctrus.permissions.ExactAlarmPermissionUtil
import mo.dev.ctrus.permissions.XiaomiAutostartUtil
import mo.dev.ctrus.permissions.XiaomiBackgroundPopupUtil
import mo.dev.ctrus.permissions.XiaomiLockScreenUtil
import mo.dev.ctrus.session.SessionOrchestrator
import mo.dev.ctrus.strategy.StrategyCapabilities
import mo.dev.ctrus.strategy.StrategyInput
import mo.dev.ctrus.strategy.StrategyRequirement
import mo.dev.ctrus.theme.CtrusTheme
import mo.dev.ctrus.theme.ThemeManager
import mo.dev.ctrus.ui.home.PermissionsAlertSheet
import mo.dev.ctrus.ui.home.HomeScreen
import mo.dev.ctrus.ui.home.ManageProfilesScreen
import mo.dev.ctrus.ui.insights.InsightsScreen
import mo.dev.ctrus.ui.insights.ProfileInsightsScreen
import mo.dev.ctrus.ui.intro.AccessibilityDisclosureDialog
import mo.dev.ctrus.ui.intro.BatteryOptimizationDialog
import mo.dev.ctrus.ui.intro.PermissionsIntroScreen
import mo.dev.ctrus.ui.profile.GuidedProfileCreationScreen
import mo.dev.ctrus.ui.profile.ProfileDraft
import mo.dev.ctrus.ui.profile.ProfileFormScreen
import mo.dev.ctrus.ui.profile.applyTo
import mo.dev.ctrus.ui.session.EmergencyView
import mo.dev.ctrus.ui.settings.SettingsScreen
import mo.dev.ctrus.ui.strategy.PendingRequirementDialog
import mo.dev.ctrus.util.resolve
import mo.dev.ctrus.childmode.AppMode
import mo.dev.ctrus.childmode.ChildModeStore
import mo.dev.ctrus.childmode.ChildSessionLimit
import mo.dev.ctrus.childmode.ParentCredentials
import mo.dev.ctrus.childmode.TamperLogEntry
import mo.dev.ctrus.ui.childmode.ChildModeSetupScreen
import mo.dev.ctrus.ui.childmode.ChildModeSettingsScreen
import mo.dev.ctrus.ui.childmode.ParentConfirmationHost
import mo.dev.ctrus.ui.childmode.ParentConfirmationRequest
import mo.dev.ctrus.ui.common.TagReadDialog
import mo.dev.ctrus.childmode.ChildModeSwitcher
import androidx.compose.material3.SheetValue
import androidx.compose.material3.ModalBottomSheetProperties
import mo.dev.ctrus.ui.intro.ModeChoiceScreen
import mo.dev.ctrus.alarm.WakeAlarmManager
import mo.dev.ctrus.permissions.AlarmPermissionUtil
import androidx.lifecycle.repeatOnLifecycle
import mo.dev.ctrus.data.ScheduleSkip
import mo.dev.ctrus.data.canUnblockWithNfcCode
import mo.dev.ctrus.ui.common.NfcScanDialog
import mo.dev.ctrus.ui.common.ToastBanner
import mo.dev.ctrus.ui.home.SkipUntilSheet
import mo.dev.ctrus.util.DateFormatters
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

// androidx.activity's own defaults for the 3-button navigation bar scrim.
private val LightNavigationBarScrim = android.graphics.Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DarkNavigationBarScrim = android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b)

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_ALARM_PROFILE_ID = "alarm_profile_id"

        /** Ctrus is on screen; AlarmService doesn't bring the alarm screen back over it. */
        @Volatile
        var isVisible = false
            private set
    }

    private lateinit var nfcScanController: NfcScanController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Home and the intro screen always paint a light pastel background, even in system dark
        // mode, so the activity's own bars always use dark icons. Left on auto, dark mode turned
        // the clock/battery white on that pastel. Sheets (Settings, Edit, Insights…) are separate
        // windows that pick their own icon color from their content, so they still get light icons
        // when they're dark. The nav-bar scrim matches what light mode already shows with 3-button
        // navigation.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(LightNavigationBarScrim, DarkNavigationBarScrim),
        )

        val themeManager = ThemeManager.getInstance(applicationContext)
        nfcScanController = NfcScanController(this, lifecycleScope)

        handleAlarmIntent(intent)

        setContent {
            CtrusTheme(themeManager) {
                val navController = rememberNavController()
                CtrusNavHost(navController, themeManager, nfcScanController)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAlarmIntent(intent)
    }

    /** Opened by sliding the alarm screen (spec D6.1): the profile's card opens and reads the Ctrus. */
    private fun handleAlarmIntent(intent: Intent?) {
        val profileId = intent?.getStringExtra(EXTRA_ALARM_PROFILE_ID) ?: return
        intent.removeExtra(EXTRA_ALARM_PROFILE_ID)
        val app = CtrusApp.from(this)
        lifecycleScope.launch { WakeAlarmManager.alarmOpenedApp(app, profileId) }
    }

    // Claims NFC reads for the entire time Ctrus is in the foreground, not just while a scan
    // dialog is open — see NfcScanController's kdoc.
    override fun onStart() {
        super.onStart()
        isVisible = true
    }

    override fun onStop() {
        isVisible = false
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        nfcScanController.attach()
    }

    override fun onPause() {
        nfcScanController.detach()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) nfcScanController.reassert()
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CtrusNavHost(
    navController: NavHostController,
    themeManager: ThemeManager,
    nfcScanController: NfcScanController,
) {
    val context = LocalContext.current
    val app = remember { CtrusApp.from(context) }
    val orchestrator: SessionOrchestrator = viewModel(factory = SessionOrchestrator.Factory(app))
    val coroutineScope = rememberCoroutineScope()

    val profiles by app.profileRepository.observeAll().collectAsState(initial = emptyList())
    val activeSession by orchestrator.activeSession.collectAsState()
    val activeProfile by orchestrator.activeProfile.collectAsState()
    val pendingRequirement by orchestrator.pendingRequirement.collectAsState()
    val errorMessage by orchestrator.errorMessage.collectAsState()
    val displaySeconds by orchestrator.displayedSeconds.collectAsState()

    // Mirrors HomeView's `@AppStorage("showIntroScreen")` fullScreenCover: shown once, on first
    // launch, until the Accessibility service is enabled — then dismissed for good. A later
    // revocation shows a HomeAlertsView-style banner instead (isAccessibilityEnabled below), not
    // this screen again.
    var showIntroScreen by remember { mutableStateOf<Boolean?>(null) }
    // Spec Part B, 5.1: permissions, then the mode choice, then — for Child Mode — its setup.
    var pendingChildModeSetup by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        pendingChildModeSetup = app.preferences.pendingChildModeSetup()
        showIntroScreen = app.preferences.showIntroScreen()
    }
    val isChildMode = ChildModeStore.appModeFlow.collectAsState().value == AppMode.CHILD
    fun finishIntro() {
        showIntroScreen = false
        pendingChildModeSetup = false
        coroutineScope.launch {
            app.preferences.setPendingChildModeSetup(false)
            app.preferences.setShowIntroScreen(false)
        }
    }
    // Picked the wrong mode: back to the choice, dropping anything a half-done setup saved.
    fun backToModeChoice() {
        if (!ChildModeStore.isChildMode) ParentCredentials.clearAll()
        pendingChildModeSetup = false
        showIntroScreen = true
        coroutineScope.launch {
            app.preferences.setPendingChildModeSetup(false)
            app.preferences.setShowIntroScreen(true)
        }
    }

    // Mirrors HomeProfilesListView's `@AppStorage("useLeftHandedLayout")` and HomeView's
    // `@AppStorage("hasCompletedFirstSession")`.
    var useLeftHandedLayout by remember { mutableStateOf(false) }
    var hasCompletedFirstSession by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        useLeftHandedLayout = app.preferences.useLeftHandedLayout()
        hasCompletedFirstSession = app.preferences.hasCompletedFirstSession()
    }
    // Any of the three ways a session can end — Stop, Emergency unblock, or a recovery-code
    // unlock — counts as "completed a first session" for dismissing the how-to hint for good.
    fun markFirstSessionCompleted() {
        if (!hasCompletedFirstSession) {
            hasCompletedFirstSession = true
            coroutineScope.launch { app.preferences.setHasCompletedFirstSession(true) }
        }
    }

    // Mirrors HomeView's `.onChange(of: scenePhase)` refreshing authorization status on every
    // foreground — Android has no push notification for "the user flipped this off in Settings",
    // so re-checking on resume is the only way Home's alert banner (and Settings' own status row)
    // ever finds out a previously-granted service was revoked.
    var isAccessibilityEnabled by remember { mutableStateOf(AccessibilityPermissionUtil.isEnabled(context)) }
    // Re-checked on every resume too — mainly so returning from the system's own "exempt this
    // app" dialog (or from having toggled it manually in system Settings) updates Settings' own
    // status row without needing to reopen it.
    var isBatteryOptimizationExempt by remember { mutableStateOf(true) }
    // "Alarms & reminders" — re-checked on resume for the same reason, e.g. after coming back from
    // the system toggle that ExactAlarmPermissionUtil.request opens.
    var isExactAlarmGranted by remember { mutableStateOf(ExactAlarmPermissionUtil.isGranted(context)) }
    // Xiaomi's Autostart (always true elsewhere) — see XiaomiAutostartUtil.
    var isAutostartGranted by remember { mutableStateOf(XiaomiAutostartUtil.isGranted(context)) }
    // Xiaomi's background pop-ups (always true elsewhere) — see XiaomiBackgroundPopupUtil.
    var isBackgroundPopupsGranted by remember { mutableStateOf(XiaomiBackgroundPopupUtil.isGranted(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val enabled = AccessibilityPermissionUtil.isEnabled(context)
                isAccessibilityEnabled = enabled
                isBatteryOptimizationExempt = BatteryOptimizationUtil.isIgnoringBatteryOptimizations(context)
                isExactAlarmGranted = ExactAlarmPermissionUtil.isGranted(context)
                isAutostartGranted = XiaomiAutostartUtil.isGranted(context)
                isBackgroundPopupsGranted = XiaomiBackgroundPopupUtil.isGranted(context)
                // Mirrors HomeView's loadApp/onAppearApp: bring the unlock cycles up to date and
                // drop schedule skips whose days are over.
                coroutineScope.launch {
                    app.preferences.checkUnlockCycles()
                    app.profileRepository.clearExpiredScheduleSkips()
                    // Child Mode catches up on anything missed while the app was closed: the
                    // Accessibility service turned off, a moved date, an automatic end that's due.
                    if (app.preferences.swapLastKnownAccessibilityEnabled(enabled) && !enabled) {
                        ChildModeStore.logTamper(TamperLogEntry.Kind.PERMISSION_REVOKED)
                    }
                    if (ChildModeStore.isChildMode) ChildModeStore.checkForClockChange()
                    ChildSessionLimit.sync(app)
                    // Alarm mode (spec D4, D6.4, D8): catch up on a session the system didn't
                    // start, bring every alarm in line, and ring again if it's due.
                    WakeAlarmManager.startMissedAlarmSession(app)
                    WakeAlarmManager.refreshAll(app)
                    WakeAlarmManager.ensureRinging(app)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // While the app is open, a due alarm (its time, or the end of a snooze) starts ringing here
    // straight away, without waiting for the system alarm.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                WakeAlarmManager.ensureRinging(app)
                kotlinx.coroutines.delay(1_000)
            }
        }
    }

    when (showIntroScreen) {
        null -> {
            // Loading the persisted flag — render just the background to avoid a Home-then-intro flash.
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            return
        }
        true -> {
            // The intro asks for every permission that gates a start, not just Accessibility.
            if (isAccessibilityEnabled && isExactAlarmGranted && isAutostartGranted && isBackgroundPopupsGranted) {
                if (pendingChildModeSetup) {
                    ChildModeSetupScreen(
                        themeColor = themeManager.selectedColorOption.color,
                        nfcScanController = nfcScanController,
                        onCancel = ::backToModeChoice,
                        onFinished = ::finishIntro,
                    )
                } else {
                    ModeChoiceScreen(themeColor = themeManager.selectedColorOption.color) { mode ->
                        if (mode == AppMode.CHILD) {
                            pendingChildModeSetup = true
                            coroutineScope.launch { app.preferences.setPendingChildModeSetup(true) }
                        } else {
                            finishIntro()
                        }
                    }
                }
                return
            }
            PermissionsIntroScreen(
                themeColor = themeManager.selectedColorOption.color,
                isAccessibilityEnabled = isAccessibilityEnabled,
                isExactAlarmGranted = isExactAlarmGranted,
                isAutostartGranted = isAutostartGranted,
                onRequestAccessibility = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                onRequestExactAlarm = { ExactAlarmPermissionUtil.request(context) },
                onRequestAutostart = { XiaomiAutostartUtil.request(context) },
                isBackgroundPopupsGranted = isBackgroundPopupsGranted,
                onRequestBackgroundPopups = { XiaomiBackgroundPopupUtil.request(context) },
            )
            return
        }
        false -> Unit
    }

    // Shown once, right after the first profile exists (not on the empty First Steps screen —
    // nothing to protect yet) — see BatteryOptimizationUtil's kdoc for why this matters on several
    // OEM skins. Not shown again once dismissed either way, but the Settings row below stays
    // available for the user to grant it later.
    var showBatteryOptimizationPrompt by remember { mutableStateOf(false) }
    LaunchedEffect(profiles.isEmpty()) {
        isBatteryOptimizationExempt = BatteryOptimizationUtil.isIgnoringBatteryOptimizations(context)
        if (profiles.isNotEmpty() && !isBatteryOptimizationExempt && !app.preferences.hasShownBatteryOptimizationPrompt()) {
            showBatteryOptimizationPrompt = true
        }
    }
    if (showBatteryOptimizationPrompt) {
        BatteryOptimizationDialog(
            onAllow = {
                showBatteryOptimizationPrompt = false
                coroutineScope.launch { app.preferences.setHasShownBatteryOptimizationPrompt(true) }
                BatteryOptimizationUtil.requestIgnoreBatteryOptimizations(context)
            },
            onDismiss = {
                showBatteryOptimizationPrompt = false
                coroutineScope.launch { app.preferences.setHasShownBatteryOptimizationPrompt(true) }
            },
        )
    }

    // Reader mode itself is always on while the Activity is resumed (see MainActivity's
    // onResume/onPause); this only registers which handler reacts to the next tag while a scan
    // is actually pending here — see NfcScanController's kdoc.
    DisposableEffect(pendingRequirement?.requirement) {
        if (pendingRequirement?.requirement == StrategyRequirement.ScanNfcTag) {
            nfcScanController.startScan { read ->
                val input = StrategyInput.NfcTag(read.code, read.uid)
                when (val pending = pendingRequirement) {
                    is mo.dev.ctrus.session.PendingRequirement.Start -> orchestrator.provideStartInput(input)
                    is mo.dev.ctrus.session.PendingRequirement.Stop -> orchestrator.provideStopInput(input)
                    null -> Unit
                }
            }
        }
        onDispose { nfcScanController.stopScan() }
    }

    pendingRequirement?.let { pending ->
        PendingRequirementDialog(
            pending = pending,
            nfcAvailability = nfcScanController.availability,
            onOpenNfcSettings = { mo.dev.ctrus.nfc.openNfcSettings(context) },
            onCancel = orchestrator::cancelPendingRequirement,
        )
    }

    errorMessage?.let { message ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = orchestrator::clearError,
            title = { androidx.compose.material3.Text(stringResource(R.string.error_dialog_title)) },
            text = { androidx.compose.material3.Text(message.resolve()) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = orchestrator::clearError) {
                    androidx.compose.material3.Text(stringResource(R.string.common_ok))
                }
            },
        )
    }

    // Mirrors HomeView presenting SettingsView/the profile list as `.sheet`s rather than pushing
    // a new screen: a NavHost destination pop has an extra frame or two of layout settling before
    // it visually finishes (most noticeable on close), where a ModalBottomSheet's own slide
    // animation is smooth and self-contained regardless of the NavHost's own enter/exit transition
    // (kept at None elsewhere in this file to avoid the 3D mascot lingering during navigation).
    var showSettings by remember { mutableStateOf(false) }
    // Child Mode (spec Part B): one parent confirmation at a time, for one action.
    var parentConfirmationRequest by remember { mutableStateOf<ParentConfirmationRequest?>(null) }
    var showTamperLog by remember { mutableStateOf(false) }
    var showChildModeSettings by remember { mutableStateOf(false) }
    var showChildModeSetup by remember { mutableStateOf(false) }
    var isReadingParentTagToEnd by remember { mutableStateOf(false) }
    var childModeMessage by remember { mutableStateOf<String?>(null) }
    val tamperLog by ChildModeStore.tamperLogFlow.collectAsState()
    val tamperLogUnseen by ChildModeStore.tamperLogUnseenFlow.collectAsState()
    fun endSessionConfirmedByParents() {
        coroutineScope.launch {
            orchestrator.endSessionConfirmedByParents()
            markFirstSessionCompleted()
        }
    }
    var showManageProfiles by remember { mutableStateOf(false) }
    var showCreateProfile by remember { mutableStateOf(false) }

    // Mirrors DeviceActivityCenterUtil.scheduleUpcomingSessionReminders, which requests
    // notification authorization whenever a schedule is saved — without it, the "starts in 5
    // minutes" and "couldn't start" notifications are silently skipped (the schedule itself
    // still starts sessions either way).
    val scheduleNotificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op: reminders are simply skipped if denied, same as iOS */ }
    val requestScheduleNotificationPermissionIfNeeded: (ProfileDraft) -> Unit = { draft ->
        if (draft.schedule != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            scheduleNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    // Alarm mode (spec D3.6): a profile isn't saved until notifications, "Alarms & reminders" and
    // (Android 14+) full-screen notifications are allowed. Each tap of Save checks again.
    var missingAlarmPermission by remember { mutableStateOf<AlarmPermissionUtil.Missing?>(null) }
    val alarmNotificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (!granted || !AlarmPermissionUtil.notificationsAllowed(context)) AlarmPermissionUtil.openNotificationSettings(context) }
    fun checkAlarmSetup(draft: ProfileDraft, proceed: () -> Unit) {
        val missing = if (draft.useAlarm) AlarmPermissionUtil.firstMissing(context) else null
        if (missing == null) proceed() else missingAlarmPermission = missing
    }
    missingAlarmPermission?.let { missing ->
        AlertDialog(
            onDismissRequest = { missingAlarmPermission = null },
            title = { Text(stringResource(R.string.error_dialog_title)) },
            text = {
                Text(
                    stringResource(
                        when (missing) {
                            AlarmPermissionUtil.Missing.FULL_SCREEN -> R.string.alarm_full_screen_required
                            AlarmPermissionUtil.Missing.LOCK_SCREEN -> R.string.alarm_lock_screen_required
                            else -> R.string.alarm_access_denied
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    missingAlarmPermission = null
                    when (missing) {
                        AlarmPermissionUtil.Missing.NOTIFICATIONS ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) {
                                alarmNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                AlarmPermissionUtil.openNotificationSettings(context)
                            }
                        AlarmPermissionUtil.Missing.EXACT_ALARMS -> ExactAlarmPermissionUtil.request(context)
                        AlarmPermissionUtil.Missing.FULL_SCREEN -> AlarmPermissionUtil.openFullScreenSettings(context)
                        AlarmPermissionUtil.Missing.LOCK_SCREEN -> XiaomiLockScreenUtil.request(context)
                    }
                }) { Text(stringResource(R.string.nfc_open_settings)) }
            },
            dismissButton = { TextButton(onClick = { missingAlarmPermission = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
    var editingProfileId by remember { mutableStateOf<String?>(null) }
    var insightsProfileId by remember { mutableStateOf<String?>(null) }
    var showAllInsights by remember { mutableStateOf(false) }
    var showLastRecoveryCodeWarning by remember { mutableStateOf(false) }

    if (showLastRecoveryCodeWarning) {
        AlertDialog(
            onDismissRequest = { showLastRecoveryCodeWarning = false },
            title = { Text(stringResource(R.string.profile_form_missing_unlock_title)) },
            text = { Text(stringResource(R.string.settings_last_recovery_code_warning)) },
            confirmButton = { TextButton(onClick = { showLastRecoveryCodeWarning = false }) { Text(stringResource(R.string.common_ok)) } },
        )
    }
    // Shared by every entry point that needs the disclosure-then-Settings flow — Home's alert
    // pill/sheet and Settings' own Accessibility Access row (Play policy requires this consent
    // screen before every trip to the system Accessibility settings, not just the first one).
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }

    if (showManageProfiles) {
        ModalBottomSheet(onDismissRequest = { showManageProfiles = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            ManageProfilesScreen(
                profiles = profiles,
                activeProfileId = activeProfile?.id,
                themeColor = themeManager.selectedColorOption.color,
                onDismiss = { showManageProfiles = false },
                onEditProfile = { profile -> showManageProfiles = false; editingProfileId = profile.id },
                onAddProfile = { showManageProfiles = false; showCreateProfile = true },
                onDeleteProfile = { profile ->
                    val delete = { coroutineScope.launch { app.profileRepository.delete(profile) }; Unit }
                    // Child Mode: deleting a profile needs the parents (spec Part B, 6.2).
                    if (ChildModeStore.isChildMode) parentConfirmationRequest = ParentConfirmationRequest(R.string.parent_confirm_reason_delete, delete)
                    else delete()
                },
                // Reordering doesn't loosen anything, so Child Mode doesn't ask the parents.
                onReorderProfiles = { reordered -> coroutineScope.launch { app.profileRepository.reorder(reordered) } },
                onAllInsights = { showAllInsights = true },
            )
        }
    }

    if (showCreateProfile) {
        ModalBottomSheet(onDismissRequest = { showCreateProfile = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            GuidedProfileCreationScreen(
                availableStrategies = app.strategyRegistry.pickerStrategies,
                themeColor = themeManager.selectedColorOption.color,
                nfcScanController = nfcScanController,
                onDismiss = { showCreateProfile = false },
                onCreate = { draft -> checkAlarmSetup(draft) {
                    requestScheduleNotificationPermissionIfNeeded(draft)
                    val create = { coroutineScope.launch {
                        app.profileRepository.create(
                            name = draft.name.trim(),
                            selectedPackages = draft.selectedPackages.toList(),
                            blockingStrategyId = draft.strategyId,
                            domains = draft.domains,
                            physicalUnblockItems = draft.physicalUnblockItems,
                            enableAllowMode = draft.enableAllowMode,
                            enableBrowserBlocking = draft.enableBrowserBlocking,
                            enableAllowModeDomains = draft.enableAllowModeDomains,
                            enableAdultContentBlocking = draft.enableAdultContentBlocking,
                            enableBreaks = draft.enableBreaks,
                            breakTimeInMinutes = draft.breakTimeInMinutes,
                            allowMultipleBreaks = draft.allowMultipleBreaks,
                            enableStrictMode = draft.enableStrictMode,
                            enableBlockAppInstallation = draft.enableBlockAppInstallation,
                            schedule = draft.schedule,
                            allowSkip = draft.allowSkip,
                            enableSnooze = draft.enableSnooze,
                            isOneTimeAlarm = draft.useOneTimeAlarm,
                            childTag = draft.childTag,
                            childTagCanStop = draft.childTagCanStop,
                        )
                        showCreateProfile = false
                    }; Unit }
                    // In Child Mode a new profile could be started (or scheduled) ahead of the
                    // parents' schedule to keep it from starting, so the parents confirm creating
                    // one too (iOS ac37116).
                    if (ChildModeStore.isChildMode) parentConfirmationRequest = ParentConfirmationRequest(R.string.parent_confirm_reason_create, create)
                    else create()
                } },
            )
        }
    }

    if (showSettings) {
        val deviceId by produceState<String?>(initialValue = null) { value = app.preferences.deviceId() }
        var selectedAppIcon by remember { mutableStateOf(DefaultAppIcon) }
        LaunchedEffect(Unit) { selectedAppIcon = app.preferences.selectedAppIcon() }

        // Mirrors SettingsView's `.onAppear { strategyManager.checkAndResetRecoveryUnlocks() }`
        // — remainingRecoveryUnlocks() itself applies the reset-window check.
        var remainingUnlocks by remember { mutableStateOf(AppPreferences.DEFAULT_RECOVERY_UNLOCKS) }
        var recoveryResetDateMillis by remember { mutableStateOf<Long?>(null) }
        LaunchedEffect(Unit) {
            remainingUnlocks = app.preferences.remainingRecoveryUnlocks()
            recoveryResetDateMillis = app.preferences.nextRecoveryResetDate()?.toEpochMilli()
        }

        ModalBottomSheet(onDismissRequest = { showSettings = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            SettingsScreen(
                themeManager = themeManager,
                isUsageAccessGranted = isAccessibilityEnabled,
                isBatteryOptimizationExempt = isBatteryOptimizationExempt,
                isExactAlarmGranted = isExactAlarmGranted,
                onRequestExactAlarm = { ExactAlarmPermissionUtil.request(context) },
                isAutostartGranted = isAutostartGranted,
                onRequestAutostart = { XiaomiAutostartUtil.request(context) },
                isBackgroundPopupsGranted = isBackgroundPopupsGranted,
                onRequestBackgroundPopups = { XiaomiBackgroundPopupUtil.request(context) },
                onRequestAccessibility = { showAccessibilityDisclosure = true },
                onRequestBatteryOptimizationExemption = { BatteryOptimizationUtil.requestIgnoreBatteryOptimizations(context) },
                appVersion = "1.0",
                deviceId = deviceId,
                selectedAppIcon = selectedAppIcon,
                onSelectAppIcon = { icon ->
                    selectedAppIcon = icon
                    AppIconController.select(context, icon)
                    coroutineScope.launch { app.preferences.setSelectedAppIcon(icon) }
                },
                onDismiss = { showSettings = false },
                onOpenUrl = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                remainingRecoveryUnlocks = remainingUnlocks,
                recoveryResetDateMillis = recoveryResetDateMillis,
                useLeftHandedLayout = useLeftHandedLayout,
                onUseLeftHandedLayoutChange = { enabled ->
                    useLeftHandedLayout = enabled
                    coroutineScope.launch { app.preferences.setUseLeftHandedLayout(enabled) }
                },
                isChildMode = isChildMode,
                isBlocking = activeSession != null,
                onUnlockWithParentCode = {
                    parentConfirmationRequest = ParentConfirmationRequest(R.string.parent_confirm_reason_end_session, ::endSessionConfirmedByParents)
                },
                onUseParentTag = { isReadingParentTagToEnd = true },
                onOpenChildModeSettings = {
                    parentConfirmationRequest = ParentConfirmationRequest(R.string.parent_confirm_reason_settings) { showChildModeSettings = true }
                },
                onEnableChildMode = { showChildModeSetup = true },
                onValidateUnlockCode = { code ->
                    // Child Mode never contacts recover.ctrus.pt — only the parents end sessions there.
                    if (remainingUnlocks <= 0 || ChildModeStore.isChildMode) {
                        false
                    } else {
                        val id = app.preferences.deviceId()
                        when (val result = app.recoveryCodeClient.verifyCode(id, code)) {
                            is RecoveryCodeVerification.Valid -> {
                                app.preferences.consumeRecoveryUnlock()
                                orchestrator.recoveryUnblock()
                                markFirstSessionCompleted()
                                remainingUnlocks = app.preferences.remainingRecoveryUnlocks()
                                // iOS a5fb750: no emergencies left and just one code to go.
                                if (app.preferences.remainingEmergencyUnblocks() == 0 && remainingUnlocks == 1) {
                                    showLastRecoveryCodeWarning = true
                                }
                                recoveryResetDateMillis = app.preferences.nextRecoveryResetDate()?.toEpochMilli()
                                true
                            }
                            else -> false
                        }
                    }
                },
            )
        }
    }

    editingProfileId?.let { profileId ->
        val profile = profiles.firstOrNull { it.id == profileId }
        if (profile != null) {
            ModalBottomSheet(onDismissRequest = { editingProfileId = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                ProfileFormScreen(
                    existingProfile = profile,
                    availableStrategies = app.strategyRegistry.pickerStrategies,
                    isBlockingGlobally = activeSession != null,
                    nfcScanController = nfcScanController,
                    onDismiss = { editingProfileId = null },
                    onSave = { draft -> checkAlarmSetup(draft) {
                        requestScheduleNotificationPermissionIfNeeded(draft)
                        val save = {
                            coroutineScope.launch {
                                app.profileRepository.update(draft.applyTo(profile))
                                editingProfileId = null
                            }
                            Unit
                        }
                        // Changing a profile can loosen it, so in Child Mode the parents confirm
                        // (spec Part B, 6.2). Creating one needs them too (see onCreate above).
                        if (ChildModeStore.isChildMode) parentConfirmationRequest = ParentConfirmationRequest(R.string.parent_confirm_reason_edit, save)
                        else save()
                    } },
                    onDelete = {
                        val delete = {
                            coroutineScope.launch {
                                app.profileRepository.delete(profile)
                                editingProfileId = null
                            }
                            Unit
                        }
                        if (ChildModeStore.isChildMode) parentConfirmationRequest = ParentConfirmationRequest(R.string.parent_confirm_reason_delete, delete)
                        else delete()
                    },
                    onDuplicate = { newName ->
                        coroutineScope.launch {
                            app.profileRepository.clone(profile, newName)
                            editingProfileId = null
                        }
                    },
                    onInsightsTapped = { insightsProfileId = profile.id },
                )
            }
        }
    }

    if (showAllInsights) {
        val allSessions by app.sessionRepository.observeAll().collectAsState(initial = emptyList())
        ModalBottomSheet(onDismissRequest = { showAllInsights = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            InsightsScreen(
                subtitle = stringResource(R.string.manage_title),
                sessions = allSessions,
                profilesById = profiles.associateBy { it.id },
                themeColor = themeManager.selectedColorOption.color,
                onDismiss = { showAllInsights = false },
            )
        }
    }

    insightsProfileId?.let { profileId ->
        val profile = profiles.firstOrNull { it.id == profileId }
        if (profile != null) {
            val sessions by app.sessionRepository.observeForProfile(profile.id).collectAsState(initial = emptyList())
            ModalBottomSheet(onDismissRequest = { insightsProfileId = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                ProfileInsightsScreen(
                    profile = profile,
                    sessions = sessions,
                    themeColor = themeManager.selectedColorOption.color,
                    onDismiss = { insightsProfileId = null },
                )
            }
        }
    }

    // Mirrors AlertsManager.presentScreenTimeAccessAlertIfNeeded(), called as a guard before
    // every "start a profile" action (manual or Ctrus NFC): shows the alert sheet instead of
    // starting while Accessibility, "Alarms & reminders" or (Xiaomi) Autostart is missing. Battery-optimization
    // exemption is a separate, non-blocking recommendation (see BatteryOptimizationUtil's kdoc),
    // never something that prevents starting a session.
    var showPermissionsAlertSheet by remember { mutableStateOf(false) }
    fun requireStartPermissions(action: () -> Unit) {
        // Re-read here rather than trusting the last resume, so a toggle flipped in a split-screen
        // or from the notification shade still counts.
        isAccessibilityEnabled = AccessibilityPermissionUtil.isEnabled(context)
        isExactAlarmGranted = ExactAlarmPermissionUtil.isGranted(context)
        isAutostartGranted = XiaomiAutostartUtil.isGranted(context)
        isBackgroundPopupsGranted = XiaomiBackgroundPopupUtil.isGranted(context)
        if (isAccessibilityEnabled && isExactAlarmGranted && isAutostartGranted && isBackgroundPopupsGranted) action() else showPermissionsAlertSheet = true
    }

    if (showPermissionsAlertSheet) {
        PermissionsAlertSheet(
            isAccessibilityEnabled = isAccessibilityEnabled,
            isExactAlarmGranted = isExactAlarmGranted,
            isAutostartGranted = isAutostartGranted,
            onDismiss = { showPermissionsAlertSheet = false },
            onFixAccessibility = {
                showPermissionsAlertSheet = false
                showAccessibilityDisclosure = true
            },
            onFixExactAlarm = {
                showPermissionsAlertSheet = false
                ExactAlarmPermissionUtil.request(context)
            },
            onFixAutostart = {
                showPermissionsAlertSheet = false
                XiaomiAutostartUtil.request(context)
            },
            isBackgroundPopupsGranted = isBackgroundPopupsGranted,
            onFixBackgroundPopups = {
                showPermissionsAlertSheet = false
                XiaomiBackgroundPopupUtil.request(context)
            },
        )
    }
    if (showAccessibilityDisclosure) {
        AccessibilityDisclosureDialog(
            onAgree = {
                showAccessibilityDisclosure = false
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            onDismiss = { showAccessibilityDisclosure = false },
        )
    }

    // Mirrors HomeProfilesListView's break/emergency wiring, now driven from the profile's own
    // expanded balloon card instead of a separate full-screen ActiveProfileSessionView.
    val currentSession = activeSession
    val currentProfile = activeProfile
    val allowsBreaks = currentProfile?.let { StrategyCapabilities.allowsTimedBreaks(it.blockingStrategyId) } ?: false
    val isBreakActive = currentSession != null && currentProfile != null && currentSession.isBreakActive(currentProfile, allowsBreaks)
    val isBreakAvailable = currentSession != null && currentProfile != null && currentSession.isBreakAvailable(currentProfile, allowsBreaks)

    // Mirrors StrategyManager's TimersUtil.scheduleNotification, which asks for notification
    // authorization every time a break-end reminder is scheduled — the OS itself only shows the
    // system dialog once and silently remembers the answer.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op: the reminder is simply skipped if denied, same as iOS */ }
    val onBreakHeldWithNotificationPrompt: () -> Unit = {
        if (!isBreakActive &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        orchestrator.toggleBreak()
    }

    // Mirrors ActiveProfileSessionView's `showEmergencyView` sheet: the "Emergency" button used
    // to call emergencyUnblock() directly on tap, with no confirmation and no visibility into
    // the limited-use count iOS enforces via EmergencyView.
    var showEmergencySheet by remember { mutableStateOf(false) }
    var emergencyRemaining by remember { mutableStateOf(AppPreferences.DEFAULT_EMERGENCY_UNBLOCKS) }
    var emergencyResetDateMillis by remember { mutableStateOf<Long?>(null) }
    suspend fun refreshEmergencyState() {
        emergencyRemaining = app.preferences.remainingEmergencyUnblocks()
        emergencyResetDateMillis = app.preferences.nextEmergencyResetDate().toEpochMilli()
    }
    // Mirrors EmergencyView's `.onAppear { strategyManager.checkAndResetEmergencyUnblocks() }`.
    LaunchedEffect(showEmergencySheet) { if (showEmergencySheet) refreshEmergencyState() }

    if (showEmergencySheet) {
        EmergencyView(
            remaining = emergencyRemaining,
            resetDateMillis = emergencyResetDateMillis,
            onConfirmUnblock = {
                orchestrator.emergencyUnblock()
                markFirstSessionCompleted()
                showEmergencySheet = false
            },
            onDismiss = { showEmergencySheet = false },
        )
    }

    // Skipping schedule sessions (iOS's ScheduleSkipUtil + HomeView wiring). Both skips are
    // authorized by a tap of one of the profile's own tags, the same ones that end its session;
    // resuming only adds blocking back, so it doesn't ask for one.
    var pendingSkip by remember { mutableStateOf<PendingSkip?>(null) }
    var skipUntilProfileId by remember { mutableStateOf<String?>(null) }
    var skipUntilErrorMessage by remember { mutableStateOf<String?>(null) }
    var skipNextErrorMessage by remember { mutableStateOf<String?>(null) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    fun skipFailed(request: PendingSkip, message: String) {
        // With "Skip until…" open the error goes inside that sheet, which covers Home.
        if (request is PendingSkip.Until) skipUntilErrorMessage = message else skipNextErrorMessage = message
    }

    /** [code] is the tag that was read, or null once the parents confirmed it (Child Mode). */
    fun applySkip(request: PendingSkip, code: String?) {
        val profile = profiles.firstOrNull { it.id == request.profileId } ?: return
        if (code != null && !profile.canUnblockWithNfcCode(code)) {
            skipFailed(request, context.getString(R.string.skip_wrong_tag))
            return
        }
        coroutineScope.launch {
            val zone = ZoneId.systemDefault()
            val (skip, message) = when (request) {
                is PendingSkip.Next -> profile.scheduleSkip.copy(skipNextOccurrence = request.occurrenceStart.toInstant()) to
                    context.getString(R.string.skip_done_next, DateFormatters.formatSkipOccurrence(context, request.occurrenceStart))
                is PendingSkip.Until -> profile.scheduleSkip.copy(skipUntil = ScheduleSkip.startOfDay(request.lastDay, zone)) to
                    context.getString(R.string.skip_done_until, DateFormatters.formatSkipShortDay(context, request.lastDay.plusDays(1).atStartOfDay(zone)))
            }
            runCatching { app.profileRepository.updateScheduleSkip(profile, skip) }
                .onSuccess {
                    if (request is PendingSkip.Until) skipUntilProfileId = null
                    toastMessage = message
                }
                .onFailure { skipFailed(request, context.getString(R.string.skip_save_failed, it.localizedMessage.orEmpty())) }
        }
    }

    DisposableEffect(pendingSkip) {
        val request = pendingSkip
        if (request != null) {
            nfcScanController.startScan { read ->
                pendingSkip = null
                applySkip(request, read.code)
            }
        }
        onDispose { if (request != null) nfcScanController.stopScan() }
    }

    if (pendingSkip != null) {
        // Cancelling the scan isn't an error, so it just closes.
        NfcScanDialog(
            availability = nfcScanController.availability,
            onDismiss = { pendingSkip = null },
            onOpenNfcSettings = { mo.dev.ctrus.nfc.openNfcSettings(context) },
        )
    }

    skipUntilProfileId?.let { profileId ->
        val schedule = profiles.firstOrNull { it.id == profileId }?.schedule
        if (schedule != null) {
            ModalBottomSheet(onDismissRequest = { skipUntilProfileId = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                SkipUntilSheet(
                    schedule = schedule,
                    themeColor = themeManager.selectedColorOption.color,
                    errorMessage = skipUntilErrorMessage,
                    onDismissError = { skipUntilErrorMessage = null },
                    onClose = { skipUntilProfileId = null },
                    buttonText = stringResource(if (isChildMode) R.string.skip_sheet_button_child else R.string.skip_sheet_button),
                    onSkip = { day ->
                        val request = PendingSkip.Until(profileId, day)
                        // Child Mode: the parents confirm, over this sheet, instead of a tag.
                        if (isChildMode) {
                            parentConfirmationRequest = ParentConfirmationRequest(R.string.parent_confirm_reason_skip) { applySkip(request, code = null) }
                        } else {
                            pendingSkip = request
                        }
                    },
                )
            }
        }
    }

    skipNextErrorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { skipNextErrorMessage = null },
            title = { Text(stringResource(R.string.error_dialog_title)) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { skipNextErrorMessage = null }) { Text(stringResource(R.string.common_ok)) } },
        )
    }

    // No animated enter/exit: with one, leaving "home" keeps its GLSurfaceView (the 3D mascot,
    // setZOrderOnTop so its translucent format composites correctly) on-screen for the whole
    // crossfade — on a real device, slow enough to read as the model lingering into Settings for
    // a couple of seconds, well past RotatingModel3DView's own onRelease hiding it. An instant
    // swap removes "home" from composition immediately, so there's no window for it to show in.
    NavHost(
        navController = navController,
        startDestination = "home",
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
    ) {
        composable("home") {
            HomeScreen(
                themeManager = themeManager,
                sessionRepository = app.sessionRepository,
                profiles = profiles,
                activeProfile = activeProfile,
                isBlocking = activeSession != null,
                displaySeconds = displaySeconds,
                isBreakActive = isBreakActive,
                isBreakAvailable = isBreakAvailable,
                useLeftHandedLayout = useLeftHandedLayout,
                hasCompletedFirstSession = hasCompletedFirstSession,
                onOpenSettings = { showSettings = true },
                onAddProfile = { showCreateProfile = true },
                onEditProfile = { profile -> editingProfileId = profile.id },
                onStartProfile = { profile -> requireStartPermissions { orchestrator.requestStart(profile) } },
                onStopProfile = {
                    orchestrator.requestStop()
                    markFirstSessionCompleted()
                },
                onBreakTapped = onBreakHeldWithNotificationPrompt,
                onEmergencyTapped = { showEmergencySheet = true },
                onInsightsTapped = { profile -> insightsProfileId = profile.id },
                onManageTapped = { showManageProfiles = true },
                onSkipNextTapped = { profile ->
                    // The next occurrence that hasn't started yet, even if another skip already
                    // covers it, matching iOS's nextStartDate(bufferSeconds: 0).
                    profile.schedule?.nextTrigger()?.let { start ->
                        val request = PendingSkip.Next(profile.id, start)
                        if (isChildMode) {
                            parentConfirmationRequest = ParentConfirmationRequest(R.string.parent_confirm_reason_skip) { applySkip(request, code = null) }
                        } else {
                            pendingSkip = request
                        }
                    }
                },
                onSkipUntilTapped = { profile ->
                    skipUntilErrorMessage = null
                    skipUntilProfileId = profile.id
                },
                onResumeScheduleTapped = { profile ->
                    coroutineScope.launch { runCatching { app.profileRepository.updateScheduleSkip(profile, ScheduleSkip()) } }
                },
                isAccessibilityEnabled = isAccessibilityEnabled,
                isExactAlarmGranted = isExactAlarmGranted,
                isAutostartGranted = isAutostartGranted,
                isBackgroundPopupsGranted = isBackgroundPopupsGranted,
                onPermissionsAlertTapped = { showPermissionsAlertSheet = true },
                tamperEntry = tamperLog.lastOrNull()?.takeIf { isChildMode && tamperLogUnseen },
                onTamperTapped = {
                    parentConfirmationRequest = ParentConfirmationRequest(R.string.parent_confirm_reason_log) { showTamperLog = true }
                },
                canGoBackToModeChoice = !isChildMode && profiles.isEmpty() && isAccessibilityEnabled,
                onBackToModeChoice = ::backToModeChoice,
            )
        }
    }

    // Child Mode's sheets. Each is its own window, so whichever opens last sits on top — the
    // parent confirmation always opens last, over whatever asked for it.
    if (showChildModeSetup) {
        val setupSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != SheetValue.Hidden })
        ModalBottomSheet(
            onDismissRequest = {},
            sheetState = setupSheetState,
            // Doesn't close by swiping or Back; its own "Back" leaves it from the first step.
            properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
        ) {
            ChildModeSetupScreen(
                themeColor = themeManager.selectedColorOption.color,
                nfcScanController = nfcScanController,
                onCancel = {
                    // Nothing half-made is kept if setup is left before the end.
                    if (!ChildModeStore.isChildMode) ParentCredentials.clearAll()
                    showChildModeSetup = false
                },
                onFinished = {
                    showChildModeSetup = false
                    coroutineScope.launch { ChildSessionLimit.sync(app) }
                },
            )
        }
    }

    if (showChildModeSettings || showTamperLog) {
        ModalBottomSheet(
            onDismissRequest = { showChildModeSettings = false; showTamperLog = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            ChildModeSettingsScreen(
                themeColor = themeManager.selectedColorOption.color,
                nfcScanController = nfcScanController,
                startsOnLog = showTamperLog,
                onDismiss = { showChildModeSettings = false; showTamperLog = false },
                onSwitchToNormal = {
                    showChildModeSettings = false
                    coroutineScope.launch { ChildModeSwitcher.switchToNormal(app) { context.getString(R.string.tag_default_name, it) } }
                },
            )
        }
    }

    if (isReadingParentTagToEnd) {
        TagReadDialog(
            nfcScanController = nfcScanController,
            message = stringResource(R.string.child_parent_tag_scan),
            onRead = { read ->
                isReadingParentTagToEnd = false
                if (ChildModeStore.isParentTag(read.uid)) endSessionConfirmedByParents()
                else childModeMessage = context.getString(R.string.parent_confirm_not_parent_tag)
            },
            onDismiss = { isReadingParentTagToEnd = false },
        )
    }

    childModeMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { childModeMessage = null },
            title = { Text(stringResource(R.string.error_dialog_title)) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { childModeMessage = null }) { Text(stringResource(R.string.common_ok)) } },
        )
    }

    ParentConfirmationHost(
        request = parentConfirmationRequest,
        onDismiss = { parentConfirmationRequest = null },
        nfcScanController = nfcScanController,
        themeColor = themeManager.selectedColorOption.color,
    )

    ToastBanner(message = toastMessage, onDismiss = { toastMessage = null })
}

/** A skip waiting for the profile's Ctrus to be tapped. */
private sealed interface PendingSkip {
    val profileId: String

    data class Next(override val profileId: String, val occurrenceStart: ZonedDateTime) : PendingSkip

    data class Until(override val profileId: String, val lastDay: LocalDate) : PendingSkip
}
