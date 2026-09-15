package mo.dev.ctrus.ui.home

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import mo.dev.ctrus.ui.common.CenteredActionBlock
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
    isAccessibilityEnabled: Boolean = true,
    onPermissionsAlertTapped: () -> Unit = {},
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
            // Accessibility only: it's the sole permission the app can't function without.
            // Battery-optimization exemption is a separate, non-blocking recommendation — see its
            // own one-time dialog (MainActivity's showBatteryOptimizationPrompt) and Settings row.
            if (profiles.isNotEmpty() && !isAccessibilityEnabled) {
                item {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        AccessibilityAlertPill(onClick = onPermissionsAlertTapped)
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
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                if (!hasCompletedFirstSession) {
                    item {
                        Text(
                            stringResource(R.string.home_first_session_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = HomeOnPastelVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        )
                    }
                }
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
            .background(EmergencyRed.copy(alpha = 0.85f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(R.string.home_permissions_alert_pill),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
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
    CenteredActionBlock(
        title = stringResource(R.string.home_getting_started_title),
        subtitle = stringResource(R.string.home_getting_started_body),
        buttonText = stringResource(R.string.home_create_profile_button),
        buttonIcon = Icons.Filled.AccountCircle,
        themeColor = themeColor,
        onButtonClick = onAddProfile,
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
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember(profile.id) { mutableStateOf(false) }
    val canStart = !isBlocking
    val hold = rememberHoldFeedback(enabled = !isActive && canStart, key = profile, onConfirm = onStart)

    LaunchedEffect(isActive) {
        if (!isActive) isExpanded = false
    }

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
            onHeaderTapped = { if (isActive) isExpanded = !isExpanded },
            onEdit = onEdit,
            onStart = onStart,
            onStop = onStop,
            onInsights = onInsights,
        )

        AnimatedVisibility(
            visible = isActive && isExpanded,
            enter = fadeIn(pressSpring()) + expandVertically(pressSpring(), expandFrom = Alignment.Top),
            exit = fadeOut(pressSpring()) + shrinkVertically(pressSpring(), shrinkTowards = Alignment.Top),
        ) {
            ExpandedActions(
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
    onHeaderTapped: () -> Unit,
    onEdit: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onInsights: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isActive) Modifier.clickable(onClick = onHeaderTapped) else Modifier)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(profile.name, style = MaterialTheme.typography.titleMedium, color = HomeOnPastel)
            Spacer(Modifier.height(4.dp))
            val appsLabel = pluralStringResource(R.plurals.apps_count, profile.selectedPackages.size, profile.selectedPackages.size)
            val domainsLabel = pluralStringResource(R.plurals.domains_count, profile.domains.orEmpty().size, profile.domains.orEmpty().size)
            Text("$appsLabel | $domainsLabel", style = MaterialTheme.typography.bodySmall, color = HomeOnPastelVariant)
        }

        Box(
            modifier = Modifier
                .width(118.dp)
                .height(62.dp)
                .then(if (isActive) Modifier.clickable(onClick = onHeaderTapped) else Modifier.clickable(onClick = onInsights))
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isActive) {
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
            onInsights = onInsights,
            onEdit = onEdit,
            onStart = onStart,
            onStop = onStop,
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

@Composable
private fun BalloonMenu(
    profileName: String,
    isActive: Boolean,
    canStart: Boolean,
    onInsights: () -> Unit,
    onEdit: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { showMenu = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.home_more_actions_content_description, profileName), tint = HomeOnPastelVariant)
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
                    enabled = canStart,
                    onClick = { showMenu = false; onStart() },
                )
            }
        }
    }
}

@Composable
private fun ExpandedActions(
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
                    icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = EmergencyRed, modifier = Modifier.size(18.dp)) },
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
                icon = { Box(Modifier.size(14.dp).background(Color.Black, RoundedCornerShape(2.dp))) },
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
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .border(borderWidth, themeColor, RoundedCornerShape(20.dp))
            .then(hold.gestureModifier)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Coffee, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
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
            // 20.dp, matching this app's other buttons/cards — was a full 50% stadium shape,
            // whose rounded semicircle ends read as much heavier than the corners on every other
            // "bubble" in the app.
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .border(BaseBorderWidth, borderColor, RoundedCornerShape(20.dp))
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
