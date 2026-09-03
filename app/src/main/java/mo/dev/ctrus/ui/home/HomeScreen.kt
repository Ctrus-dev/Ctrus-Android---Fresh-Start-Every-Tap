package mo.dev.ctrus.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import mo.dev.ctrus.ui.session.HoldToConfirmButton
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mo.dev.ctrus.R
import mo.dev.ctrus.data.BlockedProfileEntity
import mo.dev.ctrus.data.SessionRepository
import mo.dev.ctrus.theme.FixedLightPrimaryText
import mo.dev.ctrus.theme.FixedLightSecondaryText
import mo.dev.ctrus.theme.ThemeManager
import mo.dev.ctrus.theme.pastelBackground
import mo.dev.ctrus.ui.dashboard.DefaultModel3DSize
import mo.dev.ctrus.ui.dashboard.RotatingModel3DView
import mo.dev.ctrus.ui.insights.ProfileUsageMiniBarChart
import mo.dev.ctrus.util.DateFormatters

// This screen has a light pastel background regardless of the app-wide dark
// theme used elsewhere (Settings, Active Session), so its text needs fixed
// dark colors instead of the (white) theme defaults meant for dark surfaces.
private val HomeOnPastel = FixedLightPrimaryText
private val HomeOnPastelVariant = FixedLightSecondaryText

/** Mirrors HomeView.swift's pageBody: 3D mascot, then either the empty-state Welcome or the profile list + bottom launcher. */
@Composable
fun HomeScreen(
    themeManager: ThemeManager,
    sessionRepository: SessionRepository,
    profiles: List<BlockedProfileEntity>,
    activeProfile: BlockedProfileEntity?,
    isBlocking: Boolean,
    displaySeconds: Double,
    onOpenSettings: () -> Unit,
    onAddProfile: () -> Unit,
    onEditProfile: (BlockedProfileEntity) -> Unit,
    onStartProfile: (BlockedProfileEntity) -> Unit,
    onStopProfile: (BlockedProfileEntity) -> Unit,
    onInsightsTapped: (BlockedProfileEntity) -> Unit,
    onManageTapped: () -> Unit,
    onLauncherTapped: () -> Unit,
    showAccessibilityAlert: Boolean = false,
    onAccessibilityAlertTapped: () -> Unit = {},
) {
    val themeColor = themeManager.selectedColorOption.color

    Box(modifier = Modifier.fillMaxSize().background(pastelBackground(themeColor))) {
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            if (showAccessibilityAlert) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
                    AccessibilityAlertPill(onClick = onAccessibilityAlertTapped)
                }
            }

            RotatingModel3DView(
                themeColor = themeColor,
                modifier = Modifier.size(DefaultModel3DSize).align(Alignment.CenterHorizontally).padding(top = 16.dp),
            )

            if (profiles.isEmpty()) {
                WelcomeSection(themeColor = themeColor, onAddProfile = onAddProfile)
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            HomeGlassIconButton(onClick = onManageTapped, icon = Icons.Filled.AccountCircle, contentDescription = stringResource(R.string.home_manage_content_description))
                            Spacer(Modifier.width(8.dp))
                            HomeGlassIconButton(onClick = onOpenSettings, icon = Icons.Filled.Settings, contentDescription = stringResource(R.string.home_settings_content_description))
                        }
                    }
                    items(profiles, key = { it.id }) { profile ->
                        ProfileCard(
                            profile = profile,
                            sessionRepository = sessionRepository,
                            isActive = profile.id == activeProfile?.id,
                            isBlocking = isBlocking,
                            themeColor = themeColor,
                            onEdit = { onEditProfile(profile) },
                            onStart = { onStartProfile(profile) },
                            onStop = { onStopProfile(profile) },
                            onInsights = { onInsightsTapped(profile) },
                        )
                    }
                }

                LauncherBar(
                    activeProfile = activeProfile,
                    themeColor = themeColor,
                    displaySeconds = displaySeconds,
                    onTapped = onLauncherTapped,
                )
            }
        }
    }
}

/** Android equivalent of HomeAlertsView's HomeAlertCard: a red capsule pill, tapped to open the detail sheet. */
@Composable
private fun AccessibilityAlertPill(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFFFF3B30).copy(alpha = 0.85f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(R.string.home_accessibility_alert_title),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
        )
    }
}

/** Android equivalent of RoundedButton's icon-only "ultraThinMaterial" glass style. */
@Composable
private fun HomeGlassIconButton(onClick: () -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String) {
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
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.home_getting_started_title), style = MaterialTheme.typography.headlineMedium, color = HomeOnPastel)
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.home_getting_started_body),
            style = MaterialTheme.typography.bodyMedium,
            color = HomeOnPastelVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onAddProfile,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = themeColor, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Icon(Icons.Filled.AccountCircle, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.home_create_profile_button))
        }
    }
}

@Composable
private fun ProfileCard(
    profile: BlockedProfileEntity,
    sessionRepository: SessionRepository,
    isActive: Boolean,
    isBlocking: Boolean,
    themeColor: Color,
    onEdit: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onInsights: () -> Unit,
) {
    Card(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(3.5.dp, themeColor),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(profile.name, style = MaterialTheme.typography.titleMedium, color = HomeOnPastel)
                    if (isActive) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.08f), RoundedCornerShape(50))
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                        ) {
                            Text(stringResource(R.string.common_active), style = MaterialTheme.typography.labelSmall, color = HomeOnPastel)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                val appsLabel = pluralStringResource(R.plurals.apps_count, profile.selectedPackages.size, profile.selectedPackages.size)
                val domainsLabel = pluralStringResource(R.plurals.domains_count, profile.domains.orEmpty().size, profile.domains.orEmpty().size)
                Text("$appsLabel | $domainsLabel", style = MaterialTheme.typography.bodySmall, color = HomeOnPastelVariant)
            }

            val sessions by sessionRepository.observeForProfile(profile.id).collectAsState(initial = emptyList())
            Box(
                modifier = Modifier
                    .width(118.dp)
                    .height(62.dp)
                    .clickable(onClick = onInsights)
                    .padding(horizontal = 4.dp),
            ) {
                ProfileUsageMiniBarChart(sessions = sessions, themeColor = themeColor, forcedLight = true)
            }

            var showMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.home_more_actions_content_description, profile.name), tint = HomeOnPastelVariant)
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
                    } else {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.home_menu_start)) },
                            leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                            enabled = !isBlocking,
                            onClick = { showMenu = false; onStart() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LauncherBar(activeProfile: BlockedProfileEntity?, themeColor: Color, displaySeconds: Double, onTapped: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (activeProfile == null) {
            // Same hold-to-confirm gesture as the break/emergency buttons, but the resting look
            // stays identical to a plain filled Button (fully opaque theme color) — only a white
            // sweep is added as press feedback, restingAlpha=1f keeps the base opaque.
            HoldToConfirmButton(
                title = stringResource(R.string.home_launcher_start),
                backgroundColor = themeColor,
                contentColor = Color.White,
                icon = Icons.Filled.PlayArrow,
                textStyle = MaterialTheme.typography.titleMedium,
                height = 64.dp,
                restingAlpha = 1f,
                fillColor = Color.White,
                fillAlpha = 0.25f,
                iconSize = 24.dp,
                onConfirm = onTapped,
            )
        } else {
            Card(
                onClick = onTapped,
                modifier = Modifier.fillMaxWidth().height(72.dp),
                colors = CardDefaults.cardColors(containerColor = themeColor),
                shape = RoundedCornerShape(24.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(activeProfile.name, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            DateFormatters.formatDurationClock(displaySeconds),
                            fontSize = 20.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = Color.White,
                        )
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White)
                    }
                }
            }
        }
    }
}
