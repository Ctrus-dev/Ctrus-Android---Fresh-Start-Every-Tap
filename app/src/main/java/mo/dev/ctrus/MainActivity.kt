package mo.dev.ctrus

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
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
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mo.dev.ctrus.data.BlockedProfileEntity
import mo.dev.ctrus.data.PhysicalUnblockItem
import mo.dev.ctrus.data.isBreakActive
import mo.dev.ctrus.data.isBreakAvailable
import mo.dev.ctrus.icon.AppIconController
import mo.dev.ctrus.icon.DefaultAppIcon
import mo.dev.ctrus.network.RecoveryCodeVerification
import mo.dev.ctrus.nfc.NfcScanController
import mo.dev.ctrus.permissions.AccessibilityPermissionUtil
import mo.dev.ctrus.session.SessionOrchestrator
import mo.dev.ctrus.strategy.StrategyCapabilities
import mo.dev.ctrus.strategy.StrategyInput
import mo.dev.ctrus.strategy.StrategyRequirement
import mo.dev.ctrus.theme.CtrusTheme
import mo.dev.ctrus.theme.ThemeManager
import mo.dev.ctrus.ui.home.HomeScreen
import mo.dev.ctrus.ui.home.ManageProfilesScreen
import mo.dev.ctrus.ui.home.StartProfilePickerView
import mo.dev.ctrus.ui.insights.ProfileInsightsScreen
import mo.dev.ctrus.ui.intro.AccessibilityPermissionScreen
import mo.dev.ctrus.ui.profile.GuidedProfileCreationScreen
import mo.dev.ctrus.ui.profile.ProfileFormScreen
import mo.dev.ctrus.ui.session.ActiveSessionScreen
import mo.dev.ctrus.ui.session.ActiveSessionUiState
import mo.dev.ctrus.ui.settings.SettingsScreen
import mo.dev.ctrus.ui.strategy.PendingRequirementDialog
import mo.dev.ctrus.util.DateFormatters
import mo.dev.ctrus.util.resolve

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val themeManager = ThemeManager.getInstance(applicationContext)
        val nfcScanController = NfcScanController(this, lifecycleScope)

        setContent {
            CtrusTheme(themeManager) {
                val navController = rememberNavController()
                CtrusNavHost(navController, themeManager, nfcScanController)
            }
        }
    }
}

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
    // launch, until the Accessibility service is enabled — then dismissed for good (a later
    // revocation shows a HomeAlertsView-style banner instead, not this screen again).
    var showIntroScreen by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { showIntroScreen = app.preferences.showIntroScreen() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && showIntroScreen == true && AccessibilityPermissionUtil.isEnabled(context)) {
                showIntroScreen = false
                coroutineScope.launch { app.preferences.setShowIntroScreen(false) }
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

    // Mirrors HomeView presenting ActiveProfileSessionView as a fullScreenCover whenever a
    // session is active, and dismissing it the moment the session ends.
    LaunchedEffect(activeSession?.id, activeSession?.endTimeEpochMilli) {
        val isBlocking = activeSession?.endTimeEpochMilli == null && activeSession != null
        val onSessionRoute = navController.currentBackStackEntry?.destination?.route == "session"
        if (isBlocking && !onSessionRoute) {
            navController.navigate("session")
        } else if (!isBlocking && onSessionRoute) {
            navController.popBackStack("home", inclusive = false)
        }
    }

    // Runs NFC reader mode for exactly as long as a scan is actually pending — see
    // NfcScanController's kdoc for why this replaces iOS's system NFC sheet.
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

    var showStartPicker by remember { mutableStateOf(false) }
    if (showStartPicker) {
        StartProfilePickerView(
            profiles = profiles,
            themeColor = themeManager.selectedColorOption.color,
            onDismiss = { showStartPicker = false },
            onProfileChosen = { profile ->
                showStartPicker = false
                orchestrator.requestStart(profile)
            },
        )
    }

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                themeManager = themeManager,
                sessionRepository = app.sessionRepository,
                profiles = profiles,
                activeProfile = activeProfile,
                isBlocking = activeSession != null,
                displaySeconds = displaySeconds,
                onOpenSettings = { navController.navigate("settings") },
                onAddProfile = { navController.navigate("createProfile") },
                onEditProfile = { profile -> navController.navigate("editProfile/${profile.id}") },
                onStartProfile = { profile -> orchestrator.requestStart(profile) },
                onStopProfile = { orchestrator.requestStop() },
                onInsightsTapped = { profile -> navController.navigate("insights/${profile.id}") },
                onManageTapped = { navController.navigate("manageProfiles") },
                onLauncherTapped = {
                    when {
                        activeProfile != null -> navController.navigate("session") { launchSingleTop = true }
                        profiles.size == 1 -> orchestrator.requestStart(profiles.first())
                        profiles.size > 1 -> showStartPicker = true
                    }
                },
            )
        }

        composable("createProfile") {
            GuidedProfileCreationScreen(
                availableStrategies = app.strategyRegistry.pickerStrategies,
                themeColor = themeManager.selectedColorOption.color,
                nfcScanController = nfcScanController,
                onDismiss = { navController.popBackStack() },
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
                        navController.popBackStack()
                    }
                },
            )
        }

        composable("manageProfiles") {
            ManageProfilesScreen(
                profiles = profiles,
                activeProfileId = activeProfile?.id,
                themeColor = themeManager.selectedColorOption.color,
                onDismiss = { navController.popBackStack() },
                onEditProfile = { profile -> navController.navigate("editProfile/${profile.id}") },
                onAddProfile = { navController.navigate("createProfile") },
                onReorder = { reordered -> coroutineScope.launch { app.profileRepository.reorder(reordered) } },
                onDeleteProfile = { profile -> coroutineScope.launch { app.profileRepository.delete(profile) } },
            )
        }

        composable(
            route = "editProfile/{profileId}",
            arguments = listOf(navArgument("profileId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val profile = profiles.firstOrNull { it.id == backStackEntry.arguments?.getString("profileId") }
            if (profile != null) {
                ProfileFormScreen(
                    existingProfile = profile,
                    availableStrategies = app.strategyRegistry.pickerStrategies,
                    isBlockingGlobally = activeSession != null,
                    nfcScanController = nfcScanController,
                    onDismiss = { navController.popBackStack() },
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
                            navController.popBackStack()
                        }
                    },
                    onDelete = {
                        coroutineScope.launch {
                            app.profileRepository.delete(profile)
                            navController.popBackStack()
                        }
                    },
                    onDuplicate = { newName ->
                        coroutineScope.launch {
                            app.profileRepository.clone(profile, newName)
                            navController.popBackStack()
                        }
                    },
                    onInsightsTapped = { navController.navigate("insights/${profile.id}") },
                )
            }
        }

        composable(
            route = "insights/{profileId}",
            arguments = listOf(navArgument("profileId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val profile = profiles.firstOrNull { it.id == backStackEntry.arguments?.getString("profileId") }
            if (profile != null) {
                val sessions by app.sessionRepository.observeForProfile(profile.id).collectAsState(initial = emptyList())
                ProfileInsightsScreen(
                    profile = profile,
                    sessions = sessions,
                    themeColor = themeManager.selectedColorOption.color,
                    onDismiss = { navController.popBackStack() },
                    onDeleteSession = { session -> coroutineScope.launch { app.sessionRepository.delete(session) } },
                    onDeleteAllSessions = { coroutineScope.launch { app.sessionRepository.deleteAllForProfile(profile.id) } },
                )
            }
        }

        composable("settings") {
            val deviceId by produceState<String?>(initialValue = null) { value = app.preferences.deviceId() }
            var selectedAppIcon by remember { mutableStateOf(DefaultAppIcon) }
            LaunchedEffect(Unit) { selectedAppIcon = app.preferences.selectedAppIcon() }

            SettingsScreen(
                themeManager = themeManager,
                isUsageAccessGranted = true,
                appVersion = "1.0",
                deviceId = deviceId,
                selectedAppIcon = selectedAppIcon,
                onSelectAppIcon = { icon ->
                    selectedAppIcon = icon
                    AppIconController.select(context, icon)
                    coroutineScope.launch { app.preferences.setSelectedAppIcon(icon) }
                },
                onDismiss = { navController.popBackStack() },
                onOpenUrl = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                onValidateUnlockCode = { code ->
                    val id = app.preferences.deviceId()
                    when (val result = app.recoveryCodeClient.verifyCode(id, code)) {
                        is RecoveryCodeVerification.Valid -> {
                            orchestrator.emergencyUnblock()
                            true
                        }
                        else -> false
                    }
                },
            )
        }

        composable("session") {
            val session = activeSession
            val profile = activeProfile
            if (session != null && profile != null) {
                val focusMessageIndex by orchestrator.focusMessage.collectAsState()
                val allowsBreaks = StrategyCapabilities.allowsTimedBreaks(profile.blockingStrategyId)
                val isBreakActive = session.isBreakActive(profile, allowsBreaks)

                ActiveSessionScreen(
                    state = ActiveSessionUiState(
                        profileName = profile.name,
                        statusMessage = if (isBreakActive) stringResource(R.string.session_on_break) else null,
                        displayTime = DateFormatters.formatDurationClock(displaySeconds),
                        focusMessageIndex = focusMessageIndex,
                        isBreakActive = isBreakActive,
                        isBreakAvailable = session.isBreakAvailable(profile, allowsBreaks),
                    ),
                    themeColor = themeManager.selectedColorOption.color,
                    onChartTapped = { navController.navigate("insights/${profile.id}") },
                    onCloseTapped = { navController.popBackStack("home", inclusive = false) },
                    onBreakHeld = orchestrator::toggleBreak,
                    onEmergencyTapped = orchestrator::emergencyUnblock,
                    onStopTapped = orchestrator::requestStop,
                )
            }
        }
    }
}

