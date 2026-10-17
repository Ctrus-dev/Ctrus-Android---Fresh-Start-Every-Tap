package mo.dev.ctrus.ui.intro

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import mo.dev.ctrus.R
import mo.dev.ctrus.theme.pastelBackground
import mo.dev.ctrus.ui.common.CenteredActionBlock
import mo.dev.ctrus.ui.dashboard.DefaultModel3DSize
import mo.dev.ctrus.ui.dashboard.RotatingModel3DView

/**
 * Android equivalent of Ctrus/Components/Intro/PermissionsIntroScreen.swift +
 * AnimatedIntroContainer.swift, adapted for Accessibility-service onboarding instead of iOS's
 * built-in FamilyControls prompt (which needs no separate disclosure step — Android's does, per
 * Play policy, hence [AccessibilityDisclosureDialog]). The rotating 3D mascot is a static
 * launcher-icon placeholder until a `.glb` export of the real model is available (see AGENTS.md).
 */
@Composable
fun AccessibilityPermissionScreen(themeColor: Color, onRequestAuthorization: () -> Unit) {
    var showDisclosure by remember { mutableStateOf(false) }
    var authorizationRequested by remember { mutableStateOf(false) }
    var showNudge by remember { mutableStateOf(false) }

    LaunchedEffect(authorizationRequested) {
        if (authorizationRequested) {
            delay(3_000)
            showNudge = true
        }
    }

    // Mirrors HomeView's own layout exactly (see HomeScreen's pageBody): no alerts row here (there's
    // nothing to alert about pre-authorization), so the model sits at the same 16dp-top-padding
    // spot it does on an alert-free Home, and CenteredActionBlock is the same title/subtitle/button
    // block Welcome uses — landing this required-permissions screen in the identical position the
    // empty Home screen will be in the moment access is granted, matching PermissionsIntroScreen.swift's
    // intent of lining up with Welcome.swift's spot.
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

        CenteredActionBlock(
            title = stringResource(R.string.intro_welcome_title),
            subtitle = stringResource(R.string.intro_welcome_subtitle),
            buttonText = stringResource(R.string.intro_allow_button),
            buttonIcon = Icons.Filled.Accessibility,
            themeColor = themeColor,
            onButtonClick = { showDisclosure = true },
            aboveButtonContent = {
                AnimatedVisibility(visible = showNudge) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(bottom = 16.dp)
                            .background(Color(0xFFFF9500).copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFFF9500))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.intro_nudge),
                            color = Color(0xFFFF9500),
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            },
        )
    }

    if (showDisclosure) {
        AccessibilityDisclosureDialog(
            onAgree = {
                showDisclosure = false
                authorizationRequested = true
                showNudge = false
                onRequestAuthorization()
            },
            onDismiss = { showDisclosure = false },
        )
    }
}
