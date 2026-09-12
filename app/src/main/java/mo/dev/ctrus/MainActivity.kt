package mo.dev.ctrus

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
import mo.dev.ctrus.session.SessionOrchestrator
import mo.dev.ctrus.strategy.StrategyCapabilities
import mo.dev.ctrus.strategy.StrategyInput
import mo.dev.ctrus.strategy.StrategyRequirement
import mo.dev.ctrus.theme.CtrusTheme
import mo.dev.ctrus.theme.ThemeManager
import mo.dev.ctrus.ui.home.PermissionsAlertSheet
import mo.dev.ctrus.ui.home.HomeScreen
import mo.dev.ctrus.ui.home.ManageProfilesScreen
import mo.dev.ctrus.ui.insights.ProfileInsightsScreen
import mo.dev.ctrus.ui.intro.AccessibilityDisclosureDialog
import mo.dev.ctrus.ui.intro.BatteryOptimizationDialog
import mo.dev.ctrus.ui.intro.AccessibilityPermissionScreen
import mo.dev.ctrus.ui.profile.GuidedProfileCreationScreen
import mo.dev.ctrus.ui.profile.ProfileFormScreen
import mo.dev.ctrus.ui.session.EmergencyView
import mo.dev.ctrus.ui.settings.SettingsScreen
import mo.dev.ctrus.ui.strategy.PendingRequirementDialog
import mo.dev.ctrus.util.resolve

class MainActivity : ComponentActivity() {
    private lateinit var nfcScanController: NfcScanController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val themeManager = ThemeManager.getInstance(applicationContext)
        nfcScanController = NfcScanController(this, lifecycleScope)

        setContent {
            CtrusTheme(themeManager) {
                val navController = rememberNavController()
                CtrusNavHost(navController, themeManager, nfcScanController)
            }
        }
    }

    // Claims NFC reads for the entire time Ctrus is in the foreground, not just while a scan
    // dialog is open — see NfcScanController's kdoc.
    override fun onResume() {
        super.onResume()
        nfcScanController.attach()
    }

    override fun onPause() {
        nfcScanController.detach()
        super.onPause()
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
    LaunchedEffect(Unit) { showIntroScreen = app.preferences.showIntroScreen() }

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
    var isAccessibilityEnabled by remember { mutableStateOf(true) }
    // Re-checked on every resume too — mainly so returning from the system's own "exempt this
    // app" dialog (or from having toggled it manually in system Settings) updates Settings' own
    // status row without needing to reopen it.
    var isBatteryOptimizationExempt by remember { mutableStateOf(true) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val enabled = AccessibilityPermissionUtil.isEnabled(context)
                isAccessibilityEnabled = enabled
                isBatteryOptimizationExempt = BatteryOptimizationUtil.isIgnoringBatteryOptimizations(context)
                if (showIntroScreen == true && enabled) {
                    showIntroScreen = false
                    coroutineScope.launch { app.preferences.setShowIntroScreen(false) }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when (showIntroScreen) {
        null -> {
            // Loading the persisted flag — render just the background to avoid a Home-then-intro flash.
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            return
        }
        true -> {
            AccessibilityPermissionScreen(
                themeColor = themeManager.selectedColorOption.color,
                onRequestAuthorization = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
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
            nfcScanController.startScan { code ->
                when (val pending = pendingRequirement) {
                    is mo.dev.ctrus.session.PendingRequirement.Start -> orchestrator.provideStartInput(StrategyInput.NfcTag(code))
                    is mo.dev.ctrus.session.PendingRequirement.Stop -> orchestrator.provideStopInput(StrategyInput.NfcTag(code))
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
            onOpenNfcSettings = { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) },
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
    var showManageProfiles by remember { mutableStateOf(false) }
    var showCreateProfile by remember { mutableStateOf(false) }
    var editingProfileId by remember { mutableStateOf<String?>(null) }
    var insightsProfileId by remember { mutableStateOf<String?>(null) }

    if (showManageProfiles) {
        ModalBottomSheet(onDismissRequest = { showManageProfiles = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            ManageProfilesScreen(
                profiles = profiles,
                activeProfileId = activeProfile?.id,
                themeColor = themeManager.selectedColorOption.color,
                onDismiss = { showManageProfiles = false },
                onEditProfile = { profile -> showManageProfiles = false; editingProfileId = profile.id },
                onAddProfile = { showManageProfiles = false; showCreateProfile = true },
                onReorder = { reordered -> coroutineScope.launch { app.profileRepository.reorder(reordered) } },
                onDeleteProfile = { profile -> coroutineScope.launch { app.profileRepository.delete(profile) } },
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
                onCreate = { name, packages, strategyId, domains, allowMode, browserBlocking, allowModeDomains, adultContent, physicalUnblockItems, enableBreaks, breakMinutes, allowMultipleBreaks, strictMode, blockInstalls ->
                    coroutineScope.launch {
                        app.profileRepository.create(
                            name = name,
                            selectedPackages = packages,
                            blockingStrategyId = strategyId,
                            domains = domains,
                            physicalUnblockItems = physicalUnblockItems,
                            enableAllowMode = allowMode,
                            enableBrowserBlocking = browserBlocking,
                            enableAllowModeDomains = allowModeDomains,
                            enableAdultContentBlocking = adultContent,
                            enableBreaks = enableBreaks,
                            breakTimeInMinutes = breakMinutes,
                            allowMultipleBreaks = allowMultipleBreaks,
                            enableStrictMode = strictMode,
                            enableBlockAppInstallation = blockInstalls,
                        )
                        showCreateProfile = false
                    }
                },
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
                onValidateUnlockCode = { code ->
                    if (remainingUnlocks <= 0) {
                        false
                    } else {
                        val id = app.preferences.deviceId()
                        when (val result = app.recoveryCodeClient.verifyCode(id, code)) {
                            is RecoveryCodeVerification.Valid -> {
                                app.preferences.consumeRecoveryUnlock()
                                orchestrator.emergencyUnblock()
                                markFirstSessionCompleted()
                                remainingUnlocks = app.preferences.remainingRecoveryUnlocks()
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
                    onSave = { name, packages, strategyId, domains, allowMode, browserBlocking, allowModeDomains, adultContent, physicalUnblockItems, enableBreaks, breakMinutes, allowMultipleBreaks, strictMode, blockInstalls ->
                        coroutineScope.launch {
                            app.profileRepository.update(
                                profile.copy(
                                    name = name,
                                    selectedPackages = packages,
                                    blockingStrategyId = strategyId,
                                    domains = domains,
                                    enableAllowMode = allowMode,
                                    enableBrowserBlocking = browserBlocking,
                                    enableAllowModeDomains = allowModeDomains,
                                    enableAdultContentBlocking = adultContent,
                                    physicalUnblockItems = physicalUnblockItems,
                                    enableBreaks = enableBreaks,
                                    breakTimeInMinutes = breakMinutes,
                                    allowMultipleBreaks = allowMultipleBreaks,
                                    enableStrictMode = strictMode,
                                    enableBlockAppInstallation = blockInstalls,
                                ),
                            )
                            editingProfileId = null
                        }
                    },
                    onDelete = {
                        coroutineScope.launch {
                            app.profileRepository.delete(profile)
                            editingProfileId = null
                        }
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
    // every "start a profile" action: shows the alert sheet instead of starting when access is
    // currently missing. Only Accessibility gates the action itself — battery-optimization
    // exemption is surfaced in the same sheet as a secondary, non-blocking recommendation (see
    // BatteryOptimizationUtil's kdoc), never something that prevents starting a session.
    var showPermissionsAlertSheet by remember { mutableStateOf(false) }
    var showAccessibilityDisclosureFromHome by remember { mutableStateOf(false) }
    fun requireAccessibility(action: () -> Unit) {
        if (isAccessibilityEnabled) action() else showPermissionsAlertSheet = true
    }

    if (showPermissionsAlertSheet) {
        PermissionsAlertSheet(
            isAccessibilityEnabled = isAccessibilityEnabled,
            isBatteryOptimizationExempt = isBatteryOptimizationExempt,
            onDismiss = { showPermissionsAlertSheet = false },
            onFixAccessibility = {
                showPermissionsAlertSheet = false
                showAccessibilityDisclosureFromHome = true
            },
            onFixBattery = {
                showPermissionsAlertSheet = false
                BatteryOptimizationUtil.requestIgnoreBatteryOptimizations(context)
            },
        )
    }
    if (showAccessibilityDisclosureFromHome) {
        AccessibilityDisclosureDialog(
            onAgree = {
                showAccessibilityDisclosureFromHome = false
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            onDismiss = { showAccessibilityDisclosureFromHome = false },
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
    var emergencyResetWeeks by remember { mutableStateOf(AppPreferences.DEFAULT_RESET_PERIOD_WEEKS) }
    var emergencyResetDateMillis by remember { mutableStateOf<Long?>(null) }
    suspend fun refreshEmergencyState() {
        emergencyRemaining = app.preferences.remainingEmergencyUnblocks()
        emergencyResetWeeks = app.preferences.resetPeriodWeeksEmergency()
        emergencyResetDateMillis = app.preferences.nextEmergencyResetDate()?.toEpochMilli()
    }
    // Mirrors EmergencyView's `.onAppear { strategyManager.checkAndResetEmergencyUnblocks() }`.
    LaunchedEffect(showEmergencySheet) { if (showEmergencySheet) refreshEmergencyState() }

    if (showEmergencySheet) {
        EmergencyView(
            remaining = emergencyRemaining,
            resetPeriodWeeks = emergencyResetWeeks,
            resetDateMillis = emergencyResetDateMillis,
            onResetPeriodSelected = { weeks ->
                coroutineScope.launch {
                    app.preferences.setResetPeriodWeeksEmergency(weeks)
                    refreshEmergencyState()
                }
            },
            onConfirmUnblock = {
                orchestrator.emergencyUnblock()
                markFirstSessionCompleted()
                showEmergencySheet = false
            },
            onDismiss = { showEmergencySheet = false },
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
                onStartProfile = { profile -> requireAccessibility { orchestrator.requestStart(profile) } },
                onStopProfile = {
                    orchestrator.requestStop()
                    markFirstSessionCompleted()
                },
                onBreakTapped = onBreakHeldWithNotificationPrompt,
                onEmergencyTapped = { showEmergencySheet = true },
                onInsightsTapped = { profile -> insightsProfileId = profile.id },
                onManageTapped = { showManageProfiles = true },
                isAccessibilityEnabled = isAccessibilityEnabled,
                isBatteryOptimizationExempt = isBatteryOptimizationExempt,
                onPermissionsAlertTapped = { showPermissionsAlertSheet = true },
            )
        }
    }
}

