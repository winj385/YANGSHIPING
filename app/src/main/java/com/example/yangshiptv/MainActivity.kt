package com.example.yangshiptv

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.yangshiptv.databinding.ActivityMainBinding
import org.json.JSONArray

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var webView: WebView
    private lateinit var adapter: ChannelAdapter

    private val channels = ChannelRepository.channels
    private var currentIndex = 0
    private var listVisible = false
    private var onFallbackSource = false

    private val handler = Handler(Looper.getMainLooper())
    private var readyRunnable: Runnable? = null
    private var fallbackRunnable: Runnable? = null
    private var epgRunnable: Runnable? = null

    private val panelWidthPx by lazy { (420f * resources.displayMetrics.density).toInt() }

    companion object {
        const val START_URL = "https://www.yangshipin.cn/tv/home"
        private const val TAG = "YangshipinTV"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        hideSystemUi()
        checkWebViewVersionThenInit()
    }

    // -------------------------------------------------------------------------
    // Startup: warn if the system WebView is too old, then build the WebView.
    // -------------------------------------------------------------------------
    private fun checkWebViewVersionThenInit() {
        val ver = WebViewSetup.getWebViewVersion(this)
        if (WebViewSetup.isWebViewTooOld(ver)) {
            val msg = if (ver == null) {
                getString(R.string.webview_missing_msg)
            } else {
                getString(R.string.webview_too_old_msg, ver)
            }
            AlertDialog.Builder(this)
                .setTitle(R.string.webview_too_old_title)
                .setMessage(msg)
                .setPositiveButton("继续尝试") { _, _ -> initWebView() }
                .setNegativeButton("退出") { _, _ -> finish() }
                .setCancelable(false)
                .show()
        } else {
            Log.i(TAG, "WebView version: $ver")
            initWebView()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        webView = binding.webview

        adapter = ChannelAdapter(channels)
        binding.channelList.adapter = adapter
        binding.channelList.setOnItemClickListener { _, _, pos, _ ->
            // Tapping a list item also switches channel; the panel stays open.
            switchTo(pos)
        }

        val ws: WebSettings = webView.settings
        ws.javaScriptEnabled = true
        ws.domStorageEnabled = true
        ws.databaseEnabled = true
        ws.mediaPlaybackRequiresUserGesture = false
        ws.allowFileAccess = false
        ws.allowContentAccess = false
        ws.javaScriptCanOpenWindowsAutomatically = false
        ws.useWideViewPort = true
        ws.loadWithOverviewMode = true
        // Live streams are often served over http from an https page.
        ws.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        // Desktop UA, but with the device's REAL Chrome version (not hard-coded).
        ws.userAgentString = WebViewSetup.buildDesktopUserAgent(this)

        webView.addJavascriptInterface(
            WebAppBridge { title ->
                channels[currentIndex].nowPlaying = title
                if (listVisible) adapter.notifyDataSetChanged()
            },
            "AndroidBridge"
        )

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectScript()
                startReadyPolling()
                startEpgPolling()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: ConsoleMessage?): Boolean {
                Log.d("YSP-Web", m?.message() ?: "")
                return super.onConsoleMessage(m)
            }
        }

        WebView.setWebContentsDebuggingEnabled(true)

        // Initial mask while the first channel loads.
        showMask(channels[currentIndex])
        webView.loadUrl(START_URL)
    }

    /** Read inject.js from assets, run it, then hand the channel list to the page. */
    private fun injectScript() {
        val js = assets.open("inject.js").bufferedReader().use { it.readText() }
        webView.evaluateJavascript(js, null)

        val full = JSONArray()
        channels.forEach { c -> full.put(JSONArray(c.aliases)) }
        webView.evaluateJavascript("YSP.setChannels($full)", null)
    }

    // -------------------------------------------------------------------------
    // Channel switching: keep the page loaded, drive its own UI (no full reload).
    // -------------------------------------------------------------------------
    private fun switchTo(index: Int) {
        val i = (index + channels.size) % channels.size
        currentIndex = i
        val ch = channels[i]

        showMask(ch)
        adapter.currentIndex = i
        adapter.notifyDataSetChanged()
        binding.channelList.smoothScrollToPosition(i)

        // Try to switch inside the SPA by clicking the matching control.
        val arr = JSONArray(ch.aliases)
        webView.evaluateJavascript("YSP.switchTo($arr)", ValueCallback { value ->
            val ok = value?.contains("true") == true
            if (!ok) {
                // No matching control found -> navigate to the channel URL.
                // onPageFinished re-injects; mask stays until the player is ready.
                webView.loadUrl(ch.fallbackUrl)
            }
        })

        startReadyPolling()
        startFallbackTimer()
    }

    private fun switchByDelta(delta: Int) = switchTo(currentIndex + delta)

    // -------------------------------------------------------------------------
    // Mask + "no picture -> fallback" logic.
    // -------------------------------------------------------------------------
    private fun showMask(ch: Channel) {
        binding.maskNumber.text = String.format("%02d", ch.number)
        binding.maskName.text = ch.name
        binding.mask.visibility = View.VISIBLE
    }

    private fun hideMask() {
        binding.mask.visibility = View.GONE
    }

    private fun startReadyPolling() {
        cancelReadyPolling()
        val runnable = object : Runnable {
            override fun run() {
                webView.evaluateJavascript("YSP.isPlayerReady()", ValueCallback { v ->
                    if (v?.contains("true") == true) {
                        hideMask()
                        cancelFallbackTimer()
                        return@ValueCallback
                    }
                    handler.postDelayed(runnable, 400)
                })
            }
        }
        readyRunnable = runnable
        handler.post(runnable)
    }

    private fun cancelReadyPolling() {
        readyRunnable?.let { handler.removeCallbacks(it) }
        readyRunnable = null
    }

    private fun startFallbackTimer() {
        cancelFallbackTimer()
        fallbackRunnable = Runnable {
            if (onFallbackSource) return@Runnable
            onFallbackSource = true
            val ch = channels[currentIndex]
            Toast.makeText(this, getString(R.string.fallback_source), Toast.LENGTH_LONG).show()
            webView.loadUrl(ch.fallbackUrl)
            // Give the backup source a moment, then drop the mask regardless.
            handler.postDelayed({ hideMask() }, 8000)
        }
        handler.postDelayed(fallbackRunnable!!, 20000)
    }

    private fun cancelFallbackTimer() {
        fallbackRunnable?.let { handler.removeCallbacks(it) }
        fallbackRunnable = null
    }

    private fun startEpgPolling() {
        if (epgRunnable != null) return
        val runnable = object : Runnable {
            override fun run() {
                webView.evaluateJavascript("YSP.getNowPlaying()", ValueCallback { v ->
                    val t = (v ?: "").trim('"')
                    if (t.isNotBlank()) {
                        channels[currentIndex].nowPlaying = t
                        if (listVisible) adapter.notifyDataSetChanged()
                    }
                })
                handler.postDelayed(runnable, 3000)
            }
        }
        epgRunnable = runnable
        handler.post(runnable)
    }

    // -------------------------------------------------------------------------
    // Remote control.
    //   OK / Enter : toggle the left channel panel
    //   Up / Down  : change channel directly (mask -> ready -> drop)
    //   Back       : close panel first, otherwise let WebView handle it
    // -------------------------------------------------------------------------
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                toggleChannelList()
                true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                switchByDelta(-1)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                switchByDelta(1)
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (listVisible) {
                    hideChannelList()
                    true
                } else {
                    super.onKeyDown(keyCode, event)
                }
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun toggleChannelList() {
        if (listVisible) hideChannelList() else showChannelList()
    }

    private fun showChannelList() {
        listVisible = true
        adapter.currentIndex = currentIndex
        adapter.notifyDataSetChanged()
        binding.channelList.smoothScrollToPosition(currentIndex)
        binding.channelPanel.visibility = View.VISIBLE
        binding.channelPanel.translationX = -panelWidthPx.toFloat()
        binding.channelPanel.animate().translationX(0f).setDuration(200).start()
    }

    private fun hideChannelList() {
        listVisible = false
        binding.channelPanel.animate().translationX(-panelWidthPx.toFloat()).setDuration(200)
            .withEndAction { binding.channelPanel.visibility = View.GONE }
            .start()
    }

    // -------------------------------------------------------------------------
    // Fullscreen / immersive UI.
    // -------------------------------------------------------------------------
    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    override fun onDestroy() {
        cancelReadyPolling()
        cancelFallbackTimer()
        if (::webView.isInitialized) {
            webView.loadUrl("about:blank")
            webView.destroy()
        }
        super.onDestroy()
    }
}
