package com.youngs.picview.ui.guide

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.youngs.picview.R
import com.youngs.picview.ui.base.BaseActivity

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
            // MEmu's legacy GPU path can leave a hardware WebView surface black.
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            settings.javaScriptEnabled = true
            // The lesson is packaged in the APK. A file URL is more reliable on
            // older WebView providers (including MEmu) than a custom HTTPS loader.
            settings.allowFileAccess = true
            settings.allowContentAccess = false
            settings.allowFileAccessFromFileURLs = true
            settings.allowUniversalAccessFromFileURLs = false
            settings.blockNetworkLoads = true
            settings.textZoom = (resources.configuration.fontScale * 100).toInt()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
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
        webView.loadUrl("file:///android_asset/light-studio/index.html")
    }

    override fun onPause() { webView.onPause(); super.onPause() }
    override fun onResume() { super.onResume(); webView.onResume() }
    override fun onDestroy() {
        (webView.parent as? LinearLayout)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }
}
