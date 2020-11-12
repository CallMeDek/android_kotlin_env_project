package com.example.cameraxapp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.android.synthetic.main.activity_information.*

class InformationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_information)

        val parrotName: String = intent.getStringExtra("name")

        val webViewSettings: WebSettings = webView.settings
        webViewSettings.javaScriptEnabled = true
        webViewSettings.setSupportMultipleWindows(false)
        webViewSettings.javaScriptCanOpenWindowsAutomatically = false
        webViewSettings.loadWithOverviewMode = true
        webViewSettings.useWideViewPort = true
        webViewSettings.setSupportZoom(false)
        webView.scrollBarStyle = WebView.SCROLLBARS_OUTSIDE_OVERLAY
        webViewSettings.builtInZoomControls = false
        webViewSettings.cacheMode = WebSettings.LOAD_NO_CACHE
        webViewSettings.domStorageEnabled = true

        webView.webViewClient = object: WebViewClient(){
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                webView.loadUrl(request!!.url.toString())
                return true
            }
        }
        webView.loadUrl("https://en.wikipedia.org/wiki/".plus(parrotName))

        backButton2.setOnClickListener {
            finish()
        }
    }
}