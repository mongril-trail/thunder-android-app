package com.thunder.app

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface

import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    
    // Remote configuration variables populated from config.json
    private var targetUrl = "https://moviesbooster.rf.gd/"
    private var adUrl = ""
    private var shareUrl = "https://moviesbooster.rf.gd/"
    private var toggleContentUrl = ""
    private var isAppActive = true
    private var appVersion = "v 3.9"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize WebView programmatically (no XML layout file needed for UI wrapper)
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
        }

        setContentView(webView)

        // Register the JavaScript interface bridge
        webView.addJavascriptInterface(WebAppInterface(), "AndroidBridge")

        // Set default WebViewClient to handle page loading locally
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                // If Toggle is ON, block external redirects/navigation inside webview
                // If Toggle is OFF, allow normal navigation or handle externally if needed
                return false 
            }
        }

        // Load local index.html assets on start
        webView.loadUrl("file:///android_asset/index.html")

        // Fetch remote config.json from GitHub
        fetchRemoteConfig()
    }

    private fun fetchRemoteConfig() {
        // REPLACE with your actual raw GitHub URL pointing to your config.json file
        val configJsonUrl = "https://raw.githubusercontent.com/mongril-trail/thunder-android-app/main/config.json"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL(configJsonUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val stringBuilder = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = reader.readLine() } != null) {
                        stringBuilder.append(line)
                    }
                    reader.close()

                    val json = JSONObject(stringBuilder.toString())
                    isAppActive = json.optBoolean("app_active", true)
                    appVersion = json.optString("app_version", "v 3.9")
                    targetUrl = json.optString("target_url", targetUrl)
                    adUrl = json.optString("ad_url", "")
                    shareUrl = json.optString("share_url", targetUrl)
                    toggleContentUrl = json.optString("toggle_content_url", "")

                    val jsonString = json.toString()
                    val buildMeta = "Build: GitHub Actions"

                    withContext(Dispatchers.Main) {
                        webView.evaluateJavascript("applyAppConfig('$jsonString', '$buildMeta')", null)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun fetchToggleContent() {
        if (toggleContentUrl.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL(toggleContentUrl)
                val connection = url.openConnection() as HttpURLConnection
                if (connection.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val stringBuilder = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = reader.readLine() } != null) {
                        stringBuilder.append(line)
                    }
                    reader.close()
                    val htmlContent = stringBuilder.toString()
                        .replace("'", "\\'")
                        .replace("\n", " ")

                    withContext(Dispatchers.Main) {
                        webView.evaluateJavascript("updateContainerContent('$htmlContent')", null)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // JavaScript Bridge implementation to receive events from HTML buttons
    inner class WebAppInterface {

        @JavascriptInterface
        fun onEnterClicked(isActive: Boolean, isToggleOn: Boolean) {
            if (!isActive) {
                // App is inactive: open targetUrl (download/install link) directly
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                startActivity(intent)
                return
            }

            if (!isToggleOn) {
                // Toggle OFF: Open background ad tab if available, then targetUrl
                if (adUrl.isNotEmpty()) {
                    try {
                        val adIntent = Intent(Intent.ACTION_VIEW, Uri.parse(adUrl))
                        startActivity(adIntent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                val mainIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                startActivity(mainIntent)
            } else {
                // Toggle ON: Sandboxed/Blocked mode (prevent popup/external navigation actions)
                // Action can be customized or ignored completely as requested
            }
        }

        @JavascriptInterface
        fun onShareClicked() {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Check out Thunder App: $shareUrl")
            }
            val chooser = Intent.createChooser(shareIntent, "Share via")
            chooser.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(chooser)
        }

        @JavascriptInterface
        fun onToggleChanged(isToggleOn: Boolean) {
            if (isToggleOn) {
                // Fetch and replace container with alternate toggle HTML content
                fetchToggleContent()
                // Enable strict sandboxing / popup blocking in WebView clients
                webView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        // Block all navigation/redirects entirely when toggle is ON
                        return true 
                    }
                }
                webView.webChromeClient = object : WebChromeClient() {
                    // Block window.open() script popups
                    override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
                        return true
                    }
                }
            } else {
                // Revert back to normal view and reload default index.html body state
                webView.loadUrl("file:///android_asset/index.html")
                webView.webViewClient = WebViewClient()
                webView.webChromeClient = WebChromeClient()
            }
        }
    }
}
