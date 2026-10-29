package mo.dev.ctrus.ui.intro

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mo.dev.ctrus.R
import mo.dev.ctrus.permissions.XiaomiAutostartUtil
import mo.dev.ctrus.permissions.XiaomiBackgroundPopupUtil
import mo.dev.ctrus.theme.FixedLightPrimaryText
import mo.dev.ctrus.theme.FixedLightSecondaryText
import mo.dev.ctrus.theme.pastelBackground
import mo.dev.ctrus.ui.common.IntroCard
import androidx.compose.material.icons.filled.HourglassEmpty
import mo.dev.ctrus.ui.dashboard.DefaultModel3DSize
import mo.dev.ctrus.ui.dashboard.RotatingModel3DView

private enum class IntroStep(val buttonRes: Int, val icon: ImageVector) {
    ACCESSIBILITY(R.string.intro_allow_button, Icons.Filled.Accessibility),
    EXACT_ALARM(R.string.intro_allow_alarms_button, Icons.Filled.Alarm),
    AUTOSTART(R.string.intro_allow_autostart_button, Icons.Filled.RestartAlt),
    BACKGROUND_POPUPS(R.string.intro_allow_background_popups_button, Icons.Filled.RestartAlt),
}

/**
 * Android equivalent of Ctrus/Components/Intro/PermissionsIntroScreen.swift +
 * AnimatedIntroContainer.swift. iOS only needs Screen Time access here; Android needs every
 * permission that gates starting a session (the same "Required" set as Settings' PermissionsSheet
 * and Home's PermissionsAlertSheet): Accessibility, "Alarms & reminders" from Android 12, and
 * Autostart on Xiaomi. They're listed with their status, and the one button always asks for the
 * next missing one, so it's one tap per permission. Accessibility keeps its Play-policy
 * disclosure ([AccessibilityDisclosureDialog]) first. MainActivity dismisses this screen for good
 * once all of them are granted.
 */
@Composable
fun PermissionsIntroScreen(
    themeColor: Color,
    isAccessibilityEnabled: Boolean,
    isExactAlarmGranted: Boolean,
    isAutostartGranted: Boolean,
    isBackgroundPopupsGranted: Boolean,
    onRequestAccessibility: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onRequestAutostart: () -> Unit,
    onRequestBackgroundPopups: () -> Unit,
) {
    val context = LocalContext.current
    val showsExactAlarm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val showsAutostart = remember { XiaomiAutostartUtil.isApplicable(context) }
    val showsBackgroundPopups = remember { XiaomiBackgroundPopupUtil.isApplicable(context) }
    val step = when {
        !isAccessibilityEnabled -> IntroStep.ACCESSIBILITY
        showsExactAlarm && !isExactAlarmGranted -> IntroStep.EXACT_ALARM
        showsAutostart && !isAutostartGranted -> IntroStep.AUTOSTART
        showsBackgroundPopups && !isBackgroundPopupsGranted -> IntroStep.BACKGROUND_POPUPS
        // Everything's granted; MainActivity swaps this screen out on the same resume.
        else -> IntroStep.ACCESSIBILITY
    }

    var showDisclosure by remember { mutableStateOf(false) }
    // No "still here?" nudge: iOS dropped its own one too (it cut into the button on small screens).
    fun request(target: IntroStep) {
        when (target) {
            IntroStep.ACCESSIBILITY -> onRequestAccessibility()
            IntroStep.EXACT_ALARM -> onRequestExactAlarm()
            IntroStep.AUTOSTART -> onRequestAutostart()
            IntroStep.BACKGROUND_POPUPS -> onRequestBackgroundPopups()
        }
    }

    // Mirrors HomeView's own layout (see HomeScreen's pageBody): the model at the same spot as on
    // an alert-free Home, and CenteredActionBlock is the same block Welcome uses.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pastelBackground(themeColor))
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        RotatingModel3DView(
            themeColor = themeColor,
            modifier = Modifier
                .size(DefaultModel3DSize)
                .align(Alignment.CenterHorizontally)
                .padding(top = 16.dp),
        )

        // Spec Part B, 5.1: the title, subtitle and button are one card now; tapping it asks for
        // the next missing permission. The checklist stays below it.
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 22.dp)) {
            IntroCard(
                title = stringResource(R.string.intro_welcome_title),
                subtitle = stringResource(R.string.intro_welcome_subtitle),
                icon = Icons.Filled.HourglassEmpty,
                themeColor = themeColor,
                onClick = { if (step == IntroStep.ACCESSIBILITY) showDisclosure = true else request(step) },
            )
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(top = 20.dp, bottom = 16.dp)) {
                ChecklistRow(stringResource(R.string.settings_accessibility_access), isAccessibilityEnabled, themeColor)
                if (showsExactAlarm) ChecklistRow(stringResource(R.string.settings_exact_alarms), isExactAlarmGranted, themeColor)
                if (showsAutostart) ChecklistRow(stringResource(R.string.settings_autostart), isAutostartGranted, themeColor)
                if (showsBackgroundPopups) ChecklistRow(stringResource(R.string.settings_background_popups), isBackgroundPopupsGranted, themeColor)
            }
        }
    }

    if (showDisclosure) {
        AccessibilityDisclosureDialog(
            onAgree = {
                showDisclosure = false
                request(IntroStep.ACCESSIBILITY)
            },
            onDismiss = { showDisclosure = false },
        )
    }
}

@Composable
private fun ChecklistRow(title: String, granted: Boolean, themeColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (granted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (granted) themeColor else FixedLightSecondaryText,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (granted) FixedLightSecondaryText else FixedLightPrimaryText,
        )
    }
}
