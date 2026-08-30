package com.hiability.lifekit

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private var pendingGeoOrigin: String? = null
    private var pendingGeoCallback: GeolocationPermissions.Callback? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val callback = pendingGeoCallback
        val origin = pendingGeoOrigin
        pendingGeoCallback = null
        pendingGeoOrigin = null
        if (granted && callback != null) callback.invoke(origin, true, false)
        else {
            callback?.invoke(origin, false, false)
            webView.post {
                webView.evaluateJavascript(
                    "window.dispatchEvent(new CustomEvent('lifekitPermission',{detail:{location:false}}));",
                    null
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                @Suppress("DEPRECATION")
                setAllowUniversalAccessFromFileURLs(true)
                @Suppress("DEPRECATION")
                setAllowFileAccessFromFileURLs(true)
                javaScriptCanOpenWindowsAutomatically = false
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                userAgentString = "$userAgentString LifeKit/3.0"
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false
                override fun onPageFinished(view: WebView, url: String?) {
                    super.onPageFinished(view, url)
                    view.evaluateJavascript(
                        """
                        (function(){
                          var scripts=['bridge.js','fixes.js','schoolpatch.js','runtime-v3.js'];
                          function next(i){
                            if(i>=scripts.length){window.dispatchEvent(new Event('lifekitRuntimeReady'));return;}
                            var s=document.createElement('script');
                            s.src=scripts[i];
                            s.onload=function(){next(i+1)};
                            s.onerror=function(){next(i+1)};
                            document.body.appendChild(s);
                          }
                          next(0);
                        })();
                        """.trimIndent(), null
                    )
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?, callback: GeolocationPermissions.Callback?
                ) {
                    val fine = ContextCompat.checkSelfPermission(
                        this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    val coarse = ContextCompat.checkSelfPermission(
                        this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    if (fine || coarse) callback?.invoke(origin, true, false)
                    else {
                        pendingGeoOrigin = origin
                        pendingGeoCallback = callback
                        requestLocationPermissionInternal()
                    }
                }
            }
            addJavascriptInterface(NativeBridge(this@MainActivity), "Android")
            loadUrl("file:///android_asset/public/index.html")
        }
        setContentView(webView)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    fun requestLocationPermission() = requestLocationPermissionInternal()

    private fun requestLocationPermissionInternal() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) {
            webView.post {
                webView.evaluateJavascript("window.dispatchEvent(new CustomEvent('lifekitPermission',{detail:{location:true}}));", null)
            }
            return
        }
        permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    override fun onDestroy() {
        pendingGeoCallback = null
        pendingGeoOrigin = null
        webView.stopLoading()
        webView.removeAllViews()
        webView.destroy()
        super.onDestroy()
    }

    class NativeBridge(private val activity: Activity) {
        private val prefs = activity.getSharedPreferences("lifekit_native", Context.MODE_PRIVATE)
        @JavascriptInterface fun saveData(json: String?) { prefs.edit().putString("data", json ?: "").apply() }
        @JavascriptInterface fun loadData(): String = prefs.getString("data", "") ?: ""
        @JavascriptInterface fun requestLocationPermission() {
            activity.runOnUiThread { (activity as? MainActivity)?.requestLocationPermission() }
        }
    }
}
