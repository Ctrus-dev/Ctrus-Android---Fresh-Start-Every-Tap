package mo.dev.ctrus.permissions

import android.app.AppOpsManager
import android.content.Context

/**
 * Xiaomi's "Show on Lock screen" (MIUI/HyperOS), off by default for apps installed outside the
 * store. Without it MIUI won't put an activity over the lock screen, so Alarm mode's alarm screen
 * only showed once the phone was unlocked, though the full-screen intent was allowed. Stored as the
 * hidden app-op 10020, read like [XiaomiAutostartUtil]'s; it's on the same MIUI permission page as
 * the background pop-ups. Required to save an Alarm mode profile (spec D3.6's Android checks).
 */
object XiaomiLockScreenUtil {
    private const val OP_SHOW_WHEN_LOCKED = 10020

    fun isApplicable(context: Context): Boolean = readMiuiAppOp(context, OP_SHOW_WHEN_LOCKED) != null

    /** True when it's allowed, or when this isn't a Xiaomi device / the state can't be read. */
    fun isGranted(context: Context): Boolean =
        readMiuiAppOp(context, OP_SHOW_WHEN_LOCKED)?.let { it == AppOpsManager.MODE_ALLOWED } ?: true

    /** Same MIUI permission editor page as the background pop-ups. */
    fun request(context: Context) = XiaomiBackgroundPopupUtil.request(context)
}
