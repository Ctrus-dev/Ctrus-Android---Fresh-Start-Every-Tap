package mo.dev.ctrus.blocking

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.res.stringResource
import mo.dev.ctrus.R
import mo.dev.ctrus.theme.CtrusTheme
import mo.dev.ctrus.theme.ThemeManager

/**
 * Full-screen block surface shown in place of a blocked app — see [BlockerScreen] for the visual
 * content. An earlier revision drew this as a TYPE_ACCESSIBILITY_OVERLAY window from
 * BlockingAccessibilityService directly instead of an Activity, specifically to dodge MIUI's
 * extra "start activities from background" permission; that traded one reliable mechanism for a
 * less reliable one (the notification shade and screen lock/unlock routinely left the overlay
 * hidden or mis-stacked, since none of that gets handled for free the way a real Activity's
 * window/task lifecycle handles it).
 *
 * Instead, this mirrors Switchly's approach (gitlab.com/Saltyy/switchly-public,
 * BlockerActivity+BlockLaunchController): rather than assuming startActivity() from
 * BlockingAccessibilityService always works — some OEMs, MIUI confirmed, can silently swallow
 * it — this Activity records its own visibility/focus state so the service can verify shortly
 * after whether it actually showed up, and retry (bouncing Home first) if not. See
 * BlockingAccessibilityService's showBlocker()/verifyBlockerVisible().
 */
class BlockerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Without this, the status bar area shows the system's own default background instead of
        // BlockerScreen's themed color extending under it — BlockerScreen already insets its
        // content away from the system bars via windowInsetsPadding(WindowInsets.systemBars).
        enableEdgeToEdge()

        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        val appLabel = packageName
            ?.let { runCatching { packageManager.getApplicationInfo(it, 0) }.getOrNull() }
            ?.let { packageManager.getApplicationLabel(it).toString() }
        val themeManager = ThemeManager.getInstance(applicationContext)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })

        setContent {
            CtrusTheme(themeManager) {
                BlockerScreen(
                    appLabel = appLabel ?: stringResource(R.string.blocker_app_fallback),
                    variantIndex = blockerScreenVariantIndex(appLabel ?: packageName.orEmpty()),
                    onDismiss = ::goHome,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        hasWindowFocusNow = hasFocus
        if (hasFocus) {
            isVisible = true
            visiblePackage = intent?.getStringExtra(EXTRA_PACKAGE_NAME)
            lastFocusedAtRealtime = SystemClock.elapsedRealtime()
        }
    }

    override fun onPause() {
        super.onPause()
        hasWindowFocusNow = false
        isVisible = false
        visiblePackage = null
    }

    // Matches iOS's shield: its dismiss button doesn't reveal the app underneath — it returns
    // to the home screen. Android can't intercept the blocked app before it opens (unlike iOS's
    // ManagedSettings), so its task is still alive behind this activity; going home on dismiss
    // (instead of just finishing back into it) keeps that invisible. Wrapped like Switchly's
    // sendHome(): some OEMs (Samsung's FOTA/setup-update wrapper, per their source comments) can
    // resolve ACTION_MAIN/CATEGORY_HOME to a protected system surface instead of the launcher,
    // throwing a SecurityException — falling back to moveTaskToBack keeps that from crashing here.
    private fun goHome() {
        try {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: SecurityException) {
            moveTaskToBack(true)
        } catch (_: RuntimeException) {
            moveTaskToBack(true)
        }
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_PROFILE_NAME = "profile_name"

        private const val VISIBLE_TTL_MILLIS = 2_500L

        @Volatile private var isVisible: Boolean = false
        @Volatile private var visiblePackage: String? = null
        @Volatile private var hasWindowFocusNow: Boolean = false
        @Volatile private var lastFocusedAtRealtime: Long = 0L

        /** True if this Activity is currently focused and showing the block screen for [packageName],
         *  as of at most [ttlMillis] ago — used by BlockingAccessibilityService to verify a
         *  startActivity() call actually resulted in a visible block screen. */
        fun isRecentlyFocusedFor(packageName: String, ttlMillis: Long = VISIBLE_TTL_MILLIS): Boolean {
            if (packageName.isBlank() || visiblePackage != packageName || !isVisible || !hasWindowFocusNow) return false
            val age = SystemClock.elapsedRealtime() - lastFocusedAtRealtime
            return age in 0..ttlMillis
        }
    }
}
