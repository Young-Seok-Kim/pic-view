package com.youngs.picview.ui.guide

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.youngs.picview.R
import com.youngs.picview.ui.base.BaseActivity
import java.io.ByteArrayInputStream

/** Offline, opt-in lighting lesson. No remote content or JavaScript bridge. */
class LightStudioActivity : BaseActivity() {
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val toolbar = MaterialToolbar(this).apply {
            setTitle(R.string.light_studio_title)
            setNavigationIcon(R.drawable.ic_arrow_back)
            setNavigationContentDescription(R.string.light_studio_back)
            setNavigationOnClickListener { finish() }
        }
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.blockNetworkLoads = true
            settings.textZoom = (resources.configuration.fontScale * 100).toInt()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true

                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                    val uri = request.url
                    val name = uri.path?.removePrefix("/light-studio/")
                    val mime = when (name) {
                        "index.html" -> "text/html"
                        "studio.css" -> "text/css"
                        "studio.js", "three.min.js" -> "application/javascript"
                        else -> null
                    }
                    if (uri.scheme != "https" || uri.host != "appassets.androidplatform.net" ||
                        uri.port != -1 || uri.path != "/light-studio/$name" || mime == null) {
                        return WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(byteArrayOf()))
                    }
                    return WebResourceResponse(mime, "UTF-8", assets.open("light-studio/$name"))
                }
            }
        }
        root.addView(toolbar)
        root.addView(webView, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        webView.loadUrl("https://appassets.androidplatform.net/light-studio/index.html")
    }

    override fun onPause() { webView.onPause(); super.onPause() }
    override fun onResume() { super.onResume(); webView.onResume() }
    override fun onDestroy() {
        (webView.parent as? LinearLayout)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }
}
