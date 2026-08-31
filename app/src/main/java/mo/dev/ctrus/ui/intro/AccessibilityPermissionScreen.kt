package mo.dev.ctrus.ui.intro

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import mo.dev.ctrus.R
import mo.dev.ctrus.theme.FixedLightPrimaryText
import mo.dev.ctrus.theme.FixedLightSecondaryText
import mo.dev.ctrus.theme.pastelBackground
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
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(authorizationRequested) {
        if (authorizationRequested) {
            delay(3_000)
            showNudge = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pastelBackground(themeColor))
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(top = 48.dp, bottom = 32.dp, start = 24.dp, end = 24.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.intro_welcome_title),
                color = FixedLightPrimaryText,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.intro_welcome_subtitle),
                color = FixedLightSecondaryText,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.weight(1f))

        RotatingModel3DView(
            themeColor = themeColor,
            modifier = Modifier
                .size(DefaultModel3DSize)
                .align(Alignment.CenterHorizontally),
        )

        Spacer(Modifier.weight(1f))

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.intro_open_source_prefix) + stringResource(R.string.intro_open_source_link) + stringResource(R.string.intro_open_source_suffix),
                color = FixedLightSecondaryText,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable {
                    uriHandler.openUri("https://github.com/Ctrus-dev/Ctrus-Android---Fresh-Start-Every-Tap")
                },
            )

            AnimatedVisibility(visible = showNudge) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 16.dp)
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

            Button(
                onClick = { showDisclosure = true },
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = themeColor, contentColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(top = 16.dp),
            ) {
                Text(stringResource(R.string.intro_allow_button))
            }
        }
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
