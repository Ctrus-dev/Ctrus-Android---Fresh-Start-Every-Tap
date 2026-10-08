package mo.dev.ctrus.blocking

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Reads the site currently open in a supported browser from its address bar, for per-site
 * blocking. Android has no system API for "which URL is open" (iOS's ManagedSettings shields
 * domains at the OS level instead), so like Switchly this looks up each browser's own address-bar
 * view by resource id through the Accessibility service.
 *
 * Only these browsers are covered; a browser missing here simply isn't site-blocked (block the
 * browser app itself if that matters). The address bar is skipped while it has focus, since then
 * it holds whatever the user is typing rather than the page that's actually open.
 */
object BrowserUrlReader {
    private val URL_BAR_IDS: Map<String, List<String>> = mapOf(
        "com.android.chrome" to listOf("url_bar"),
        "com.chrome.beta" to listOf("url_bar"),
        "com.chrome.dev" to listOf("url_bar"),
        "com.chrome.canary" to listOf("url_bar"),
        "com.sec.android.app.sbrowser" to listOf("location_bar_edit_text"),
        "com.sec.android.app.sbrowser.beta" to listOf("location_bar_edit_text"),
        "org.mozilla.firefox" to listOf("mozac_browser_toolbar_url_view", "url_bar_title"),
        "org.mozilla.firefox_beta" to listOf("mozac_browser_toolbar_url_view", "url_bar_title"),
        "org.mozilla.focus" to listOf("mozac_browser_toolbar_url_view", "display_url"),
        "com.microsoft.emmx" to listOf("url_bar"),
        "com.brave.browser" to listOf("url_bar"),
        "com.opera.browser" to listOf("url_field"),
        "com.opera.gx" to listOf("url_field"),
        "com.opera.mini.native" to listOf("url_field"),
        "com.duckduckgo.mobile.android" to listOf("omnibarTextInput"),
        "com.vivaldi.browser" to listOf("url_bar"),
        "com.kiwibrowser.browser" to listOf("url_bar"),
    )

    fun isSupportedBrowser(packageName: String): Boolean = packageName in URL_BAR_IDS

    /** The host open in [packageName]'s address bar (e.g. "m.youtube.com"), or null if unknown. */
    fun readHost(root: AccessibilityNodeInfo, packageName: String): String? {
        val ids = URL_BAR_IDS[packageName] ?: return null
        for (id in ids) {
            val nodes = root.findAccessibilityNodeInfosByViewId("$packageName:id/$id")
            val node = nodes.firstOrNull() ?: continue
            if (node.isFocused) return null
            val text = node.text?.toString() ?: continue
            return hostOf(text)
        }
        return null
    }

    /**
     * "https://www.example.com:443/path?q" → "www.example.com". Returns null for anything that
     * isn't a web address (a search query, "Search or type URL", chrome://newtab…).
     */
    fun hostOf(text: String): String? {
        val trimmed = text.trim().lowercase()
        if (trimmed.isEmpty() || ' ' in trimmed) return null
        val schemeEnd = trimmed.indexOf("://")
        if (schemeEnd >= 0 && !trimmed.startsWith("http")) return null
        val afterScheme = if (schemeEnd >= 0) trimmed.substring(schemeEnd + 3) else trimmed
        val authority = afterScheme.takeWhile { it != '/' && it != '?' && it != '#' }
        val host = authority.substringAfterLast('@').substringBefore(':').trimEnd('.')
        return host.takeIf { '.' in it && it.all { c -> c.isLetterOrDigit() || c == '.' || c == '-' } }
    }
}
