package com.example.yangshiptv

import android.content.Context
import android.os.Build
import android.webkit.WebView
import java.util.regex.Pattern

/**
 * Helpers around the system WebView:
 *  - build a desktop Chrome UA whose Chrome version is read from the device's real
 *    WebView kernel (never hard-coded), so yangshipin serves the desktop page instead
 *    of the "download our App" interstitial.
 *  - read the installed WebView version to warn the user if it is too old.
 */
object WebViewSetup {

    /** Below this Chromium major version we warn the user at startup. */
    const val MIN_WEBVIEW_MAJOR = 70

    private val CHROME_VERSION = Pattern.compile("Chrome/([0-9]+(?:\\.[0-9]+)*)")

    /**
     * Desktop Windows-Chrome UA, but with the Chrome version copied from the device's
     * own WebView UA. If the device UA has no Chrome token we fall back to a recent
     * version string only as a last resort (this should basically never happen).
     */
    fun buildDesktopUserAgent(context: Context): String {
        val deviceUa = try {
            WebSettings.getDefaultUserAgent(context)
        } catch (e: Exception) {
            ""
        }
        val matcher = CHROME_VERSION.matcher(deviceUa)
        val chrome = if (matcher.find()) matcher.group(1) else "120.0.0.0"
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/$chrome Safari/537.36"
    }

    /**
     * Returns the version name of the system WebView provider, or null if it cannot be
     * determined (very old devices without Android System WebView).
     */
    fun getWebViewVersion(context: Context): String? {
        val candidates = listOf(
            "com.google.android.webview",
            "com.android.webview",
            "com.google.android.webview.dev",
            "com.android.chrome"
        )
        for (pkg in candidates) {
            try {
                val pi = context.packageManager.getPackageInfo(pkg, 0)
                if (!pi.versionName.isNullOrBlank()) return pi.versionName
            } catch (ignored: Exception) {
                // package not present, try next
            }
        }
        // Last resort: WebView.getCurrentWebViewPackage() (API 21+, hidden on some builds)
        try {
            val m = WebView::class.java.getMethod("getCurrentWebViewPackage")
            val info = m.invoke(null)
            if (info != null) {
                val vn = info.javaClass.getMethod("getVersionName").invoke(info) as? String
                if (!vn.isNullOrBlank()) return vn
            }
        } catch (ignored: Exception) {
        }
        return null
    }

    /** True when the WebView is missing or older than [MIN_WEBVIEW_MAJOR]. */
    fun isWebViewTooOld(version: String?): Boolean {
        if (version.isNullOrBlank()) return true
        val m = Pattern.compile("^(\\d+)").matcher(version)
        if (!m.find()) return true
        val major = m.group(1).toIntOrNull() ?: return true
        return major < MIN_WEBVIEW_MAJOR
    }
}
