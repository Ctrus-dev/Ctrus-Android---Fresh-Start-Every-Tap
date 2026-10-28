package mo.dev.ctrus.ui.childmode

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mo.dev.ctrus.R
import mo.dev.ctrus.childmode.ChildModeStore
import mo.dev.ctrus.childmode.ParentCredentials
import mo.dev.ctrus.childmode.ParentCredentials.Secret
import mo.dev.ctrus.childmode.ParentCredentials.VerifyResult
import mo.dev.ctrus.nfc.NfcScanController
import mo.dev.ctrus.ui.common.GlassIconButton
import mo.dev.ctrus.ui.common.TagReadDialog
import java.util.Locale

/**
 * A request for the parents to confirm one action (spec Part B, section 4). The confirmation
 * covers only that action — nothing stays unlocked afterwards.
 */
data class ParentConfirmationRequest(@StringRes val reasonRes: Int, val action: () -> Unit)

/**
 * Shows the parent confirmation for [request] in a sheet, and runs its action only after the
 * sheet has closed, so the action can open screens of its own (iOS's `.parentConfirmation`).
 * [onDismiss] clears the request either way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentConfirmationHost(
    request: ParentConfirmationRequest?,
    onDismiss: () -> Unit,
    nfcScanController: NfcScanController,
    themeColor: Color,
) {
    request ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        ParentConfirmationContent(
            reason = stringResource(request.reasonRes),
            nfcScanController = nfcScanController,
            themeColor = themeColor,
            onClose = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } },
            onConfirmed = {
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    onDismiss()
                    request.action()
                }
            },
        )
    }
}

private enum class ConfirmStep { CODE, RECOVERY_KEY, NEW_CODE, CONFIRM_NEW_CODE }

/**
 * Port of iOS's ParentConfirmationView: the parents' Ctrus, the parent code, or — if the code is
 * forgotten — the recovery key and a new code.
 */
@Composable
fun ParentConfirmationContent(
    reason: String,
    nfcScanController: NfcScanController,
    themeColor: Color,
    onClose: () -> Unit,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(ConfirmStep.CODE) }
    var code by remember { mutableStateOf("") }
    var recoveryKey by remember { mutableStateOf("") }
    var newCode by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var lockRemaining by remember { mutableFloatStateOf(0f) }
    var isReadingTag by remember { mutableStateOf(false) }
    var isChecking by remember { mutableStateOf(false) }

    val lockSecret = if (step == ConfirmStep.RECOVERY_KEY) Secret.RECOVERY_KEY else Secret.PARENT_CODE
    suspend fun refreshLock() {
        lockRemaining = withContext(Dispatchers.IO) { ParentCredentials.lockRemainingSeconds(lockSecret) }
    }
    val isLocked = lockRemaining > 0
    // Counts the wait down once a second while it lasts; restarts when a new wait begins.
    LaunchedEffect(lockSecret, isLocked) {
        refreshLock()
        while (lockRemaining > 0) {
            delay(1_000)
            refreshLock()
        }
    }

    fun checkCode(entered: String) {
        isChecking = true
        scope.launch {
            val result = withContext(Dispatchers.Default) { ParentCredentials.verify(entered, Secret.PARENT_CODE) }
            isChecking = false
            code = ""
            when (result) {
                VerifyResult.Valid -> onConfirmed()
                VerifyResult.Invalid -> message = context.getString(R.string.parent_confirm_wrong_code)
                is VerifyResult.Locked -> {
                    message = null
                    lockRemaining = result.remainingSeconds
                }
            }
        }
    }

    fun checkRecoveryKey() {
        isChecking = true
        scope.launch {
            val result = withContext(Dispatchers.Default) { ParentCredentials.verify(recoveryKey, Secret.RECOVERY_KEY) }
            isChecking = false
            when (result) {
                VerifyResult.Valid -> {
                    message = null
                    newCode = ""
                    code = ""
                    step = ConfirmStep.NEW_CODE
                }
                VerifyResult.Invalid -> message = context.getString(R.string.parent_confirm_wrong_key)
                is VerifyResult.Locked -> {
                    message = null
                    lockRemaining = result.remainingSeconds
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            GlassIconButton(
                onClick = onClose,
                icon = Icons.Filled.Close,
                contentDescription = stringResource(R.string.settings_close_content_description),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(
                    when (step) {
                        ConfirmStep.CODE -> R.string.parent_confirm_code
                        ConfirmStep.RECOVERY_KEY -> R.string.parent_confirm_enter_key
                        ConfirmStep.NEW_CODE, ConfirmStep.CONFIRM_NEW_CODE -> R.string.child_pin_create
                    },
                ),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(reason, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Button(
                onClick = { isReadingTag = true },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = themeColor, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Filled.Nfc, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.parent_confirm_use_tag))
            }

            HorizontalDivider()

            when (step) {
                ConfirmStep.CODE -> Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ParentCodeField(
                        code = code,
                        onCodeChange = { code = it },
                        themeColor = themeColor,
                        enabled = !isLocked && !isChecking,
                        onComplete = ::checkCode,
                    )
                    if (isLocked) LockText(lockRemaining)
                    TextButton(onClick = {
                        message = null
                        step = ConfirmStep.RECOVERY_KEY
                    }) {
                        Text(stringResource(R.string.parent_confirm_forgot), color = themeColor)
                    }
                }

                ConfirmStep.RECOVERY_KEY -> Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    RecoveryKeyField(recoveryKey, { recoveryKey = it }, enabled = !isLocked)
                    if (isLocked) LockText(lockRemaining)
                    Button(
                        onClick = ::checkRecoveryKey,
                        enabled = !isLocked && !isChecking &&
                            ParentCredentials.normalizedRecoveryKey(recoveryKey).length == ParentCredentials.RECOVERY_KEY_LENGTH,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = themeColor, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) { Text(stringResource(R.string.parent_confirm_continue)) }
                }

                ConfirmStep.NEW_CODE -> ParentCodeField(
                    code = newCode,
                    onCodeChange = { newCode = it },
                    themeColor = themeColor,
                    onComplete = { entered ->
                        if (ParentCredentials.isWeakCode(entered)) {
                            message = context.getString(R.string.child_pin_weak)
                            newCode = ""
                        } else {
                            message = null
                            code = ""
                            step = ConfirmStep.CONFIRM_NEW_CODE
                        }
                    },
                )

                ConfirmStep.CONFIRM_NEW_CODE -> Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.child_pin_again), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ParentCodeField(
                        code = code,
                        onCodeChange = { code = it },
                        themeColor = themeColor,
                        enabled = !isChecking,
                        onComplete = { entered ->
                            if (entered != newCode) {
                                message = context.getString(R.string.child_pin_mismatch)
                                code = ""
                                newCode = ""
                                step = ConfirmStep.NEW_CODE
                            } else {
                                isChecking = true
                                scope.launch {
                                    withContext(Dispatchers.Default) { ParentCredentials.setParentCode(entered) }
                                    isChecking = false
                                    onConfirmed()
                                }
                            }
                        },
                    )
                }
            }

            message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (isReadingTag) {
        TagReadDialog(
            nfcScanController = nfcScanController,
            message = stringResource(R.string.child_parent_tag_scan),
            onRead = { read ->
                isReadingTag = false
                if (ChildModeStore.isParentTag(read.uid)) {
                    // The parents' Ctrus always works, even during a wait, and clears both.
                    ParentCredentials.resetAttempts(Secret.PARENT_CODE)
                    ParentCredentials.resetAttempts(Secret.RECOVERY_KEY)
                    onConfirmed()
                } else {
                    message = context.getString(R.string.parent_confirm_not_parent_tag)
                }
            },
            onDismiss = { isReadingTag = false },
        )
    }
}

@Composable
private fun LockText(remainingSeconds: Float) {
    Text(
        stringResource(R.string.parent_confirm_locked, formattedWait(remainingSeconds)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun RecoveryKeyField(value: String, onValueChange: (String) -> Unit, enabled: Boolean) {
    val style = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 20.sp,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface,
    )
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(it.uppercase().take(14)) },
        enabled = enabled,
        singleLine = true,
        textStyle = style,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) Text("XXXX-XXXX-XXXX", style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)), modifier = Modifier.fillMaxWidth())
            inner()
        },
    )
}

/** "4 min, 59 s" / "1 h" — abbreviated, minutes and seconds under an hour like iOS. */
fun formattedWait(seconds: Float): String {
    val total = maxOf(1, kotlin.math.ceil(seconds).toInt())
    val format = MeasureFormat.getInstance(Locale.getDefault(), MeasureFormat.FormatWidth.SHORT)
    return if (total >= 3600) {
        val minutes = (total % 3600) / 60
        if (minutes == 0) format.formatMeasures(Measure(total / 3600, MeasureUnit.HOUR))
        else format.formatMeasures(Measure(total / 3600, MeasureUnit.HOUR), Measure(minutes, MeasureUnit.MINUTE))
    } else {
        val minutes = total / 60
        val secs = total % 60
        when {
            minutes == 0 -> format.formatMeasures(Measure(secs, MeasureUnit.SECOND))
            secs == 0 -> format.formatMeasures(Measure(minutes, MeasureUnit.MINUTE))
            else -> format.formatMeasures(Measure(minutes, MeasureUnit.MINUTE), Measure(secs, MeasureUnit.SECOND))
        }
    }
}
