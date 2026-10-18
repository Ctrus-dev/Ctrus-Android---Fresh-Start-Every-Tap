package mo.dev.ctrus.blocking

import android.content.Intent
import android.os.Build
import android.view.WindowManager
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import mo.dev.ctrus.R
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
    // Held as Compose state (not plain onCreate-local vals) because this Activity is
    // launchMode="singleInstance" and every later blocked-app tap reuses the same instance via
    // onNewIntent rather than a fresh onCreate — without state here, setContent's closure kept
    // showing whichever app/message variant was current the first time this Activity was ever
    // created, no matter which app the *next* block was actually for.
    // Named blockedPackageName (not packageName) to avoid clashing with Context.getPackageName().
    private var blockedPackageName: String? by mutableStateOf(null)
    private var appLabel: String? by mutableStateOf(null)
    private var blurEnabled: Boolean by mutableStateOf(false)
    private val blurListener = java.util.function.Consumer<Boolean> { blurEnabled = it }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Lets the background extend under the system bars — BlockerScreen already insets its
        // content away from them via windowInsetsPadding(WindowInsets.systemBars).
        enableEdgeToEdge()
        // Like iOS's shield, the blocked app shows through, blurred. Cross-window blur is
        // Android 12+ and can be off (battery saver, some OEMs, low-end GPUs); BlockerScreen then
        // stays almost opaque so the app underneath is never readable.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply {
                blurBehindRadius = (BLUR_RADIUS_DP * resources.displayMetrics.density).toInt()
            }
        }

        updateForIntent(intent)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })

        val themeManager = ThemeManager.getInstance(applicationContext)
        setContent {
            BlockerScreen(
                appLabel = appLabel ?: stringResource(R.string.blocker_app_fallback),
                themeColor = themeManager.selectedColorOption.color,
                backgroundAlpha = if (blurEnabled) BLURRED_BACKGROUND_ALPHA else OPAQUE_BACKGROUND_ALPHA,
                onDismiss = ::goHome,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        updateForIntent(intent)
    }

    private fun updateForIntent(intent: Intent) {
        val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        blockedPackageName = pkg
        appLabel = intent.getStringExtra(EXTRA_SITE_DOMAIN)?.removePrefix("www.") ?: pkg
            ?.let { runCatching { packageManager.getApplicationInfo(it, 0) }.getOrNull() }
            ?.let { packageManager.getApplicationLabel(it).toString() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            windowManager.addCrossWindowBlurEnabledListener(blurListener)
        }
    }

    override fun onDetachedFromWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            windowManager.removeCrossWindowBlurEnabledListener(blurListener)
        }
        super.onDetachedFromWindow()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // A finishing instance can lose focus after the next one got it; only the newest one
        // may clear the shared flags.
        if (!hasFocus && current?.get() !== this) return
        hasWindowFocusNow = hasFocus
        if (hasFocus) {
            current = java.lang.ref.WeakReference(this)
            isVisible = true
            visiblePackage = intent?.getStringExtra(EXTRA_PACKAGE_NAME)
            lastFocusedAtRealtime = SystemClock.elapsedRealtime()
        }
    }

    // A second launch for an already-showing block screen (singleInstance → onNewIntent) pauses
    // and resumes it without any focus change, so onWindowFocusChanged doesn't run again and the
    // state onPause cleared stayed cleared: the service then read the block screen as missing and
    // bounced Home, flashing the home screen and re-showing the block screen over it.
    override fun onResume() {
        super.onResume()
        if (hasWindowFocus()) onWindowFocusChanged(true)
    }

    override fun onPause() {
        super.onPause()
        if (current?.get() !== this) return
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
        /** Set when a website is being blocked; shown instead of the browser's own name. */
        const val EXTRA_SITE_DOMAIN = "site_domain"

        private const val VISIBLE_TTL_MILLIS = 2_500L
        private const val BLUR_RADIUS_DP = 40
        private const val BLURRED_BACKGROUND_ALPHA = 0.9f
        private const val OPAQUE_BACKGROUND_ALPHA = 0.97f

        @Volatile private var current: java.lang.ref.WeakReference<BlockerActivity>? = null
        @Volatile private var isVisible: Boolean = false
        @Volatile private var visiblePackage: String? = null
        @Volatile private var hasWindowFocusNow: Boolean = false
        @Volatile private var lastFocusedAtRealtime: Long = 0L

        /** True while the block screen for [packageName] is showing and focused, however long ago it
         *  got focus. */
        fun isShowingFor(packageName: String): Boolean =
            packageName.isNotBlank() && visiblePackage == packageName && isVisible && hasWindowFocusNow

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
