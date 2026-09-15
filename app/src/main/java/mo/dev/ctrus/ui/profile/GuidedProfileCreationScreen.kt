package mo.dev.ctrus.ui.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import mo.dev.ctrus.R
import mo.dev.ctrus.data.PhysicalUnblockItem
import mo.dev.ctrus.nfc.NfcScanController
import mo.dev.ctrus.strategy.BlockingStrategy
import mo.dev.ctrus.theme.CtrusSystemColors
import mo.dev.ctrus.ui.common.GlassIconButton

private enum class GuidedStep(
    @androidx.annotation.StringRes val titleRes: Int,
    @androidx.annotation.StringRes val introTitleRes: Int,
    @androidx.annotation.StringRes val introDescriptionRes: Int,
) {
    NAME(R.string.guided_step_name, R.string.guided_intro_title_name, R.string.guided_intro_desc_name),
    STRATEGY(R.string.guided_step_method, R.string.guided_intro_title_method, R.string.guided_intro_desc_method),
    APPS(R.string.guided_step_apps, R.string.guided_intro_title_apps, R.string.guided_intro_desc_apps),
    DOMAINS(R.string.guided_step_websites, R.string.guided_intro_title_websites, R.string.guided_intro_desc_websites),
    STRICT_UNLOCKS(R.string.guided_step_unlocks, R.string.guided_intro_title_unlocks, R.string.guided_intro_desc_unlocks),
    BREAKS(R.string.guided_step_breaks, R.string.guided_intro_title_breaks, R.string.guided_intro_desc_breaks),
    STRICT_SAFEGUARDS(R.string.guided_step_protection, R.string.guided_intro_title_protection, R.string.guided_intro_desc_protection),
    REVIEW(R.string.guided_step_review, R.string.guided_intro_title_review, R.string.guided_intro_desc_review),
}

/**
 * Android equivalent of GuidedBlockedProfileCreationView.swift: the 8-step first-time creation
 * flow (Name → Method → Apps → Websites → Unlocks → Breaks → Protection → Review), one section
 * per screen with a spring-ish slide/fade transition between steps, matching the iOS step order,
 * titles, and per-step `canContinue` gating (name required on step 1, at least one physical
 * unlock tag required on the Unlocks step) exactly. Editing an existing profile still uses
 * [ProfileFormScreen]'s single-scroll form — iOS only paginates for creation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuidedProfileCreationScreen(
    availableStrategies: List<BlockingStrategy>,
    themeColor: Color,
    nfcScanController: NfcScanController,
    onDismiss: () -> Unit,
    onCreate: (
        name: String,
        selectedPackages: List<String>,
        strategyId: String,
        domains: List<String>,
        enableAllowMode: Boolean,
        enableBrowserBlocking: Boolean,
        enableAllowModeDomains: Boolean,
        enableAdultContentBlocking: Boolean,
        physicalUnblockItems: List<PhysicalUnblockItem>,
        enableBreaks: Boolean,
        breakTimeInMinutes: Int,
        allowMultipleBreaks: Boolean,
        enableStrictMode: Boolean,
        enableBlockAppInstallation: Boolean,
    ) -> Unit,
) {
    val steps = GuidedStep.entries
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    var draft by remember { mutableStateOf(ProfileDraft(strategyId = availableStrategies.first().id)) }
    val currentStep = steps[stepIndex]
    val isFirstStep = stepIndex == 0
    val isLastStep = stepIndex == steps.lastIndex

    val canContinue = when (currentStep) {
        GuidedStep.NAME -> draft.name.isNotBlank()
        GuidedStep.STRICT_UNLOCKS -> draft.physicalUnblockItems.isNotEmpty()
        else -> true
    }

    fun createProfile() {
        onCreate(
            draft.name.trim(), draft.selectedPackages.toList(), draft.strategyId, draft.domains,
            draft.enableAllowMode, draft.enableBrowserBlocking, draft.enableAllowModeDomains, draft.enableAdultContentBlocking,
            draft.physicalUnblockItems, draft.enableBreaks, draft.breakTimeInMinutes, draft.allowMultipleBreaks,
            draft.enableStrictMode, draft.enableBlockAppInstallation,
        )
    }

    Scaffold(
        topBar = {
            // Plain Row instead of a Material TopAppBar — that has its own, much tighter default
            // padding around navigationIcon/actions, which pinned these icons closer to the screen
            // edge than the matching ones on Perfis/Definições/Editar Perfil's custom headers.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(
                    onClick = { if (isFirstStep) onDismiss() else stepIndex-- },
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.guided_back_content_description),
                )
                GlassIconButton(onClick = onDismiss, icon = Icons.Filled.Close, contentDescription = stringResource(R.string.guided_cancel_content_description))
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            AnimatedContent(
                targetState = stepIndex,
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                transitionSpec = {
                    val forward = targetState > initialState
                    val enter = slideInVertically(tween(220)) { height -> if (forward) height / 6 else -height / 6 } + fadeIn(tween(220))
                    val exit = slideOutVertically(tween(180)) { height -> if (forward) -height / 6 else height / 6 } + fadeOut(tween(180))
                    enter togetherWith exit
                },
                label = "guided-step",
            ) { index ->
                val step = steps[index]
                // The 36dp trailing gap before the Next button lives here (not inside GuidedCard)
                // so it trails whatever this step renders last, instead of always hugging the card.
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 36.dp)) {
                    StepHeader(index = index, total = steps.size, step = step, draft = draft)
                    GuidedCard {
                        StepContent(step, draft, { draft = it }, availableStrategies, nfcScanController)
                    }
                }
            }

            Button(
                onClick = { if (isLastStep) createProfile() else stepIndex++ },
                enabled = canContinue,
                // 20.dp app-wide "bubble" radius — was 28.dp, which on this 56.dp-tall button was
                // exactly half its height, so both ends drew as full semicircles (a stadium/pill)
                // rather than a normal rounded rectangle with the same corner treatment as the
                // cards around it. See SettingsSection's kdoc.
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = themeColor, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 16.dp).height(56.dp),
            ) {
                Text(stringResource(if (isLastStep) R.string.guided_create_profile_button else R.string.guided_next_button))
            }
        }
    }
}

@Composable
private fun StepHeader(index: Int, total: Int, step: GuidedStep, draft: ProfileDraft) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 24.dp)) {
        Text(stringResource(R.string.guided_step_indicator, index + 1, total), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(step.introTitleRes), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(step.introDescriptionRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GuidedCard(content: @Composable () -> Unit) {
    // 12.dp on all four sides (matching SettingsSection's own) — every field composable this
    // hosts (NameField, StrategyFields, AppsFields, CustomToggleRow, ...) already supplies its
    // own top/bottom padding per row, but that alone left this card's edge-to-content gap much
    // tighter top/bottom than left/right, unlike iOS's bubbles, which keep the same inset on all
    // four sides. Kept equal to SettingsSection's own so guided-flow fields still match their
    // edit-screen counterparts.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
            .padding(12.dp),
    ) {
        content()
    }
}

@Composable
private fun StepContent(
    step: GuidedStep,
    draft: ProfileDraft,
    onDraftChange: (ProfileDraft) -> Unit,
    availableStrategies: List<BlockingStrategy>,
    nfcScanController: NfcScanController,
) {
    when (step) {
        GuidedStep.NAME -> NameField(draft, onDraftChange, disabled = false)
        GuidedStep.STRATEGY -> StrategyFields(draft, onDraftChange, availableStrategies, disabled = false)
        GuidedStep.APPS -> AppsFields(draft, onDraftChange, disabled = false)
        GuidedStep.DOMAINS -> DomainsFields(draft, onDraftChange, disabled = false)
        GuidedStep.STRICT_UNLOCKS -> PhysicalUnlocksFields(draft, onDraftChange, nfcScanController, disabled = false)
        GuidedStep.BREAKS -> BreaksFields(draft, onDraftChange, disabled = false)
        GuidedStep.STRICT_SAFEGUARDS -> SafeguardsFields(draft, onDraftChange, disabled = false)
        GuidedStep.REVIEW -> ReviewContent(draft, availableStrategies)
    }
}

@Composable
private fun ReviewContent(draft: ProfileDraft, availableStrategies: List<BlockingStrategy>) {
    val strategyName = availableStrategies.firstOrNull { it.id == draft.strategyId }?.let { stringResource(it.displayNameRes) }
        ?: stringResource(R.string.guided_review_strategy_fallback)
    val appsSummary = if (draft.selectedPackages.isEmpty()) {
        stringResource(R.string.guided_review_no_apps_selected)
    } else {
        pluralStringResource(R.plurals.guided_review_apps_selected_count, draft.selectedPackages.size, draft.selectedPackages.size)
    }
    val domainsSummary = when (draft.domains.size) {
        0 -> stringResource(R.string.guided_review_no_domains_selected)
        else -> pluralStringResource(R.plurals.guided_review_domains_added_count, draft.domains.size, draft.domains.size)
    }
    val breaksSummary = when {
        !draft.enableBreaks -> stringResource(R.string.guided_review_breaks_disabled)
        draft.allowMultipleBreaks -> pluralStringResource(R.plurals.guided_review_breaks_minutes_reusable, draft.breakTimeInMinutes, draft.breakTimeInMinutes)
        else -> pluralStringResource(R.plurals.guided_review_breaks_minutes, draft.breakTimeInMinutes, draft.breakTimeInMinutes)
    }
    val deletionBlockedText = stringResource(R.string.guided_review_safeguard_deletion_blocked)
    val installsBlockedText = stringResource(R.string.guided_review_safeguard_installs_blocked)
    val safeguards = buildList {
        if (draft.enableStrictMode) add(deletionBlockedText)
        if (draft.enableBlockAppInstallation) add(installsBlockedText)
    }
    val safeguardsSummary = if (safeguards.isEmpty()) stringResource(R.string.guided_review_safeguards_default) else safeguards.joinToString(", ")

    Column {
        ReviewRow(stringResource(R.string.guided_review_name), draft.name)
        ReviewRow(stringResource(R.string.guided_review_strategy), strategyName)
        ReviewRow(stringResource(R.string.guided_review_apps), appsSummary)
        ReviewRow(stringResource(R.string.guided_review_domains), domainsSummary)
        ReviewRow(stringResource(R.string.guided_review_breaks), breaksSummary)
        ReviewRow(stringResource(R.string.guided_review_safeguards), safeguardsSummary, showDivider = false)
    }
}

@Composable
private fun ReviewRow(title: String, value: String, showDivider: Boolean = true) {
    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(end = 12.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
    if (showDivider) {
        // Inset to match this row's own 12.dp padding above — a flush, edge-to-edge divider
        // started/ended past where "Nome"/"Estratégia"/etc. actually begin and end.
        androidx.compose.material3.HorizontalDivider(
            modifier = Modifier.padding(horizontal = 12.dp),
            color = if (isSystemInDarkTheme()) CtrusSystemColors.separatorDark else CtrusSystemColors.separatorLight,
        )
    }
}
