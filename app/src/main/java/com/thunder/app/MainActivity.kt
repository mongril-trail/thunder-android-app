package com.thunder.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.view.View
import android.webkit.*
import android.widget.Button
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var enterButton: Button
    private lateinit var shareButton: Button
    private lateinit var modeSwitch: Switch

    private var targetUrl: String = "https://moviesbooster.rf.gd/"
    private var adUrl: String = ""
    private var toggleContentUrl: String = ""
    private var isAppActive: Boolean = true

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        enterButton = findViewById(R.id.enterButton)
        shareButton = findViewById(R.id.shareButton)
        modeSwitch = findViewById(R.id.modeSwitch)

        setupWebView()
        fetchRemoteConfig()

        enterButton.setOnClickListener {
            // Trigger ad tab in background if configured
            if (adUrl.isNotEmpty()) {
                val adIntent = Intent(Intent.ACTION_VIEW, Uri.parse(adUrl))
                try {
                    startActivity(adIntent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            // Load target main site
            webView.loadUrl(targetUrl)
        }

        shareButton.setOnClickListener {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Check out this app: $targetUrl")
            }
            startActivity(Intent.createChooser(shareIntent, "Share via"))
        }

        modeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // TOGGLE ON (Restricted/Sandbox Mode)
                if (toggleContentUrl.isNotEmpty()) {
                    webView.loadUrl(toggleContentUrl)
                } else {
                    webView.loadData("<h3>Restricted Mode Active</h3>", "text/html", "UTF-8")
                }
            } else {
                // TOGGLE OFF (Entertainment Mode)
                webView.loadUrl(targetUrl)
            }
        }
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url.toString()
                // Keep navigation inside WebView unless external
                return false
            }
        }

        // Handle background window opens / popups (Ad tabs)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                val transport = resultMsg?.obj as WebView.WebViewTransport
                val dummyWebView = WebView(this@MainActivity)
                dummyWebView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val openedUrl = request?.url.toString()
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(openedUrl))
                        startActivity(browserIntent)
                        return true
                    }
                }
                transport.webView = dummyWebView
                resultMsg.sendToTarget()
                return true
            }
        }
    }

    private fun fetchRemoteConfig() {
        scope.launch(Dispatchers.IO) {
            try {
                // Add timestamp query parameter to bypass GitHub Raw CDN caching
                const val rawConfigUrl = "https://raw.githubusercontent.com/mongril-trail/thunder-android-app/main/config.json"
                val cacheBusterUrl = "$rawConfigUrl?t=${System.currentTimeMillis()}"
                
                val url = URL(cacheBusterUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == 200) {
                    val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(jsonString)

                    isAppActive = json.optBoolean("app_active", true)
                    targetUrl = json.optString("target_url", targetUrl)
                    adUrl = json.optString("ad_url", "")
                    toggleContentUrl = json.optString("toggle_content_url", "")

                    withContext(Dispatchers.Main) {
                        if (!isAppActive) {
                            Toast.makeText(this@MainActivity, json.optString("notice_message", "App inactive"), Toast.LENGTH_LONG).show()
                        }
                        // Initial load
                        webView.loadUrl(targetUrl)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    // Fallback to default target if offline/error
                    webView.loadUrl(targetUrl)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
