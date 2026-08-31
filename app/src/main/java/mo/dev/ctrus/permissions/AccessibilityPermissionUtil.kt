package mo.dev.ctrus.permissions

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import mo.dev.ctrus.blocking.BlockingAccessibilityService

/**
 * Checks whether [BlockingAccessibilityService] is enabled. Android has no callback for "the
 * user just enabled this in Settings" the way iOS's FamilyControls authorization has, so this is
 * polled from the UI (on resume) — mirrors Switchly's `PermissionUtils.isAccessibilityServiceEnabled`:
 * try the live `AccessibilityManager` list first, then fall back to reading `Settings.Secure`
 * directly (some OEMs report a service as enabled in Settings before/without it showing up in
 * the manager's list).
 */
object AccessibilityPermissionUtil {
    fun isEnabled(context: Context): Boolean {
        val expected = ComponentName(context, BlockingAccessibilityService::class.java)

        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val enabledViaManager = manager
            ?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            ?.any { info ->
                val serviceInfo = info.resolveInfo.serviceInfo
                serviceInfo.packageName == expected.packageName && serviceInfo.name == expected.className
            }
            ?: false
        if (enabledViaManager) return true

        return isEnabledInSettings(context, expected)
    }

    private fun isEnabledInSettings(context: Context, expected: ComponentName): Boolean {
        val masterSwitchOn = Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
        if (!masterSwitchOn) return false

        val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabledServices) }
        while (splitter.hasNext()) {
            if (ComponentName.unflattenFromString(splitter.next()) == expected) return true
        }
        return false
    }
}
