package mo.dev.ctrus

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import mo.dev.ctrus.theme.CtrusTheme
import mo.dev.ctrus.theme.ThemeManager
import mo.dev.ctrus.ui.home.HomeScreen
import mo.dev.ctrus.ui.session.ActiveSessionScreen
import mo.dev.ctrus.ui.session.ActiveSessionUiState
import mo.dev.ctrus.ui.settings.SettingsScreen

/** Master unlock code shared with the iOS app's "Locked Out and Lost Your Ctrus?" flow. */
private const val MASTER_UNLOCK_CODE = "3530-CtrusUnblock!"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val themeManager = ThemeManager.getInstance(applicationContext)

        setContent {
            CtrusTheme(themeManager) {
                val navController = rememberNavController()
                CtrusNavHost(navController, themeManager)
            }
        }
    }
}

@Composable
private fun CtrusNavHost(navController: NavHostController, themeManager: ThemeManager) {
    val context = LocalContext.current

    // Placeholder session state until the real BlockedProfileSession/StrategyManager port lands.
    var isBreakActive by remember { mutableStateOf(false) }

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                themeManager = themeManager,
                onOpenSettings = { navController.navigate("settings") },
                onStartFocus = { navController.navigate("session") }
            )
        }

        composable("settings") {
            SettingsScreen(
                themeManager = themeManager,
                isBlocking = false,
                isUsageAccessGranted = false,
                appVersion = "1.0",
                onDismiss = { navController.popBackStack() },
                onOpenUrl = { url ->
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                },
                onResetBlockingState = { /* TODO: wire to BlockingRuntime once ported */ },
                onValidateUnlockCode = { code -> code == MASTER_UNLOCK_CODE }
            )
        }

        composable("session") {
            ActiveSessionScreen(
                state = ActiveSessionUiState(
                    profileName = "Fresh Start",
                    statusMessage = if (isBreakActive) "On a Break" else null,
                    displayTime = "00:00:42",
                    focusMessage = "Great work requires deep attention",
                    isBreakActive = isBreakActive,
                    isBreakAvailable = true,
                ),
                themeColor = themeManager.selectedColorOption.color,
                onChartTapped = { /* TODO: stats screen */ },
                onCloseTapped = { navController.popBackStack() },
                onBreakHeld = { isBreakActive = !isBreakActive },
                onEmergencyTapped = { /* TODO: emergency unblock */ },
                onStopTapped = { navController.popBackStack() }
            )
        }
    }
}
