package mo.dev.ctrus.ui.childmode

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mo.dev.ctrus.R
import mo.dev.ctrus.childmode.AppMode
import mo.dev.ctrus.childmode.ChildModeStore
import mo.dev.ctrus.childmode.ParentCredentials
import mo.dev.ctrus.childmode.ParentTag
import mo.dev.ctrus.nfc.NfcScanController
import mo.dev.ctrus.ui.common.GlassIconButton
import mo.dev.ctrus.ui.common.TagReadDialog

private enum class SetupStep(val titleRes: Int, val descriptionRes: Int) {
    CODE(R.string.child_pin_create, R.string.child_pin_create_hint),
    CONFIRM_CODE(R.string.child_pin_again, R.string.child_pin_again_hint),
    PARENT_TAGS(R.string.child_parent_tag_title, R.string.child_parent_tag_hint),
    RECOVERY_KEY(R.string.child_recovery_title, R.string.child_recovery_body),
}

/** "I've saved it" only unlocks this long after the key appears. */
const val RECOVERY_KEY_CONFIRM_DELAY_MILLIS = 5_000L

/**
 * Port of iOS's ChildModeSetupView (spec Part B, 5.2), laid out like the guided profile creation:
 * the parent code (typed twice), the parents' Ctrus (at least one), then the recovery key, shown
 * once. "Back" on the first step calls [onCancel], which drops anything already saved; finishing
 * stores the parents' Ctrus, turns Child Mode on and calls [onFinished].
 */
@Composable
fun ChildModeSetupScreen(
    themeColor: Color,
    nfcScanController: NfcScanController,
    onCancel: () -> Unit,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(SetupStep.CODE) }
    var code by remember { mutableStateOf("") }
    var confirmedCode by remember { mutableStateOf("") }
    var parentTags by remember { mutableStateOf<List<ParentTag>>(emptyList()) }
    var recoveryKey by remember { mutableStateOf("") }
    var canConfirmRecoveryKey by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isReadingTag by remember { mutableStateOf(false) }
    var isWorking by remember { mutableStateOf(false) }

    LaunchedEffect(step, recoveryKey) {
        canConfirmRecoveryKey = false
        if (step == SetupStep.RECOVERY_KEY && recoveryKey.isNotEmpty()) {
            delay(RECOVERY_KEY_CONFIRM_DELAY_MILLIS)
            canConfirmRecoveryKey = true
        }
    }

    val canContinue = !isWorking && when (step) {
        SetupStep.CODE, SetupStep.CONFIRM_CODE -> code.length == ParentCredentials.CODE_LENGTH
        SetupStep.PARENT_TAGS -> parentTags.isNotEmpty()
        SetupStep.RECOVERY_KEY -> canConfirmRecoveryKey
    }

    fun goNext() {
        when (step) {
            SetupStep.CODE -> {
                if (ParentCredentials.isWeakCode(code)) {
                    message = context.getString(R.string.child_pin_weak)
                    code = ""
                    return
                }
                message = null
                confirmedCode = code
                code = ""
                step = SetupStep.CONFIRM_CODE
            }
            SetupStep.CONFIRM_CODE -> {
                if (code != confirmedCode) {
                    message = context.getString(R.string.child_pin_mismatch)
                    code = ""
                    confirmedCode = ""
                    step = SetupStep.CODE
                    return
                }
                message = null
                val entered = code
                isWorking = true
                scope.launch {
                    withContext(Dispatchers.Default) { ParentCredentials.setParentCode(entered) }
                    isWorking = false
                    code = ""
                    step = SetupStep.PARENT_TAGS
                }
            }
            SetupStep.PARENT_TAGS -> {
                if (parentTags.isEmpty()) return
                message = null
                isWorking = true
                scope.launch {
                    recoveryKey = withContext(Dispatchers.Default) { ParentCredentials.makeRecoveryKey() }
                    isWorking = false
                    step = SetupStep.RECOVERY_KEY
                }
            }
            SetupStep.RECOVERY_KEY -> {
                ChildModeStore.parentTags = parentTags
                ChildModeStore.appMode = AppMode.CHILD
                onFinished()
            }
        }
    }

    fun goBack() {
        message = null
        when (step) {
            SetupStep.CODE -> onCancel()
            // The code is typed again rather than kept half-confirmed.
            SetupStep.CONFIRM_CODE, SetupStep.PARENT_TAGS -> {
                code = ""
                confirmedCode = ""
                step = SetupStep.CODE
            }
            // A new key is made on the way forward again, so this one is useless.
            SetupStep.RECOVERY_KEY -> {
                recoveryKey = ""
                step = SetupStep.PARENT_TAGS
            }
        }
    }

    BackHandler(onBack = ::goBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding(),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            GlassIconButton(
                onClick = ::goBack,
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.guided_back_content_description),
            )
        }

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 24.dp)) {
                Text(
                    stringResource(R.string.guided_step_indicator, step.ordinal + 1, SetupStep.entries.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(stringResource(step.titleRes), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(step.descriptionRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (step) {
                    // Keyed so each step gets a fresh, focused field.
                    SetupStep.CODE, SetupStep.CONFIRM_CODE -> androidx.compose.runtime.key(step) {
                        ParentCodeField(
                            code = code,
                            onCodeChange = { code = it },
                            themeColor = themeColor,
                            enabled = !isWorking,
                            onComplete = { goNext() },
                        )
                    }
                    SetupStep.PARENT_TAGS -> {
                        parentTags.forEach { tag ->
                            ParentTagRow(tag, themeColor, onDelete = { parentTags = parentTags.filterNot { it.uid == tag.uid } })
                        }
                        AddParentTagButton(
                            text = stringResource(if (parentTags.isEmpty()) R.string.child_parent_tag_scan else R.string.child_parent_tag_add_another),
                            onClick = { isReadingTag = true },
                        )
                    }
                    SetupStep.RECOVERY_KEY -> SelectionContainer {
                        Text(
                            ParentCredentials.formattedRecoveryKey(recoveryKey),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 26.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        )
                    }
                }
            }

            message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }

        Button(
            onClick = ::goNext,
            enabled = canContinue,
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = themeColor, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 16.dp).height(56.dp),
        ) {
            Text(stringResource(if (step == SetupStep.RECOVERY_KEY) R.string.child_recovery_saved else R.string.guided_next_button))
        }
    }

    if (isReadingTag) {
        TagReadDialog(
            nfcScanController = nfcScanController,
            message = stringResource(R.string.child_parent_tag_scan),
            onRead = { read ->
                isReadingTag = false
                val uid = ChildModeStore.normalizedUid(read.uid)
                // Reading one that's already listed does nothing.
                if (parentTags.none { it.uid == uid }) {
                    message = null
                    val label = ChildModeStore.nextParentTagLabel(parentTags) { context.getString(R.string.child_parent_tag_label, it) }
                    parentTags = parentTags + ParentTag(uid = uid, url = read.url, label = label)
                }
            },
            onDismiss = { isReadingTag = false },
        )
    }
}

@Composable
private fun ParentTagRow(tag: ParentTag, themeColor: Color, onDelete: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(tag.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(tag.uid.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Box {
            Icon(
                Icons.Filled.MoreHoriz,
                contentDescription = null,
                tint = themeColor,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { showMenu = true }.padding(6.dp),
            )
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { showMenu = false; onDelete() },
                )
            }
        }
    }
}

@Composable
internal fun AddParentTagButton(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
