package com.example.yangshiptv

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface

/**
 * JavaScript bridge object exposed to the page as `window.AndroidBridge`.
 * The injected script calls these methods to push state back to the app.
 */
class WebAppBridge(
    private val onNowPlaying: (String) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun reportNowPlaying(title: String?) {
        val t = (title ?: "").trim()
        if (t.isBlank()) return
        handler.post { onNowPlaying(t) }
    }

    @JavascriptInterface
    fun log(msg: String?) {
        Log.d("YSP", msg ?: "")
    }
}
