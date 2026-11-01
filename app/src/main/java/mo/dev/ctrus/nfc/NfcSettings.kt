package mo.dev.ctrus.nfc

import android.content.Context
import android.content.Intent
import android.provider.Settings
import mo.dev.ctrus.alarm.AlarmService

/**
 * Opens the system's NFC settings so NFC can be turned on for a read. While an alarm rings, the
 * alarm screen isn't brought back over them for a while, or NFC could never be turned on there.
 */
fun openNfcSettings(context: Context) {
    AlarmService.holdAlarmScreenBack(NFC_SETTINGS_GRACE_MILLIS)
    runCatching { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private const val NFC_SETTINGS_GRACE_MILLIS = 60_000L
