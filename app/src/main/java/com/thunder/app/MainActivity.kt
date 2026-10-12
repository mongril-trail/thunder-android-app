package com.thunder.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.view.View
import android.webkit.*
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var toggleCircleBtn: ImageButton
    private lateinit var shareCircleBtn: ImageButton

    private var targetUrl: String = "https://moviesbooster.rf.gd/"
    private var adUrl: String = ""
    private var shareUrl: String = "https://moviesbooster.rf.gd/"
    private var toggleContentUrl: String = ""
    private var isAppActive: Boolean = true
    private var appVersion: String = "v 1.0"
    private var noticeMessage: String = "App no longer available."

    private var isToggleActive: Boolean = false
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val rawConfigUrl = "https://raw.githubusercontent.com/mongril-trail/thunder-android-app/main/config.json"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        toggleCircleBtn = findViewById(R.id.toggleCircleBtn)
        shareCircleBtn = findViewById(R.id.shareCircleBtn)

        setupWebView()
        fetchRemoteConfig()

        shareCircleBtn.setOnClickListener {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareUrl)
            }
            startActivity(Intent.createChooser(shareIntent, "Share via"))
        }

        toggleCircleBtn.setOnClickListener {
            isToggleActive = !isToggleActive
            if (isToggleActive) {
                // TOGGLE ON: Load sandbox content, block popup ads
                shareCircleBtn.visibility = View.GONE
                if (toggleContentUrl.isNotEmpty()) {
                    webView.loadUrl(toggleContentUrl)
                } else {
                    webView.loadData("<h3>Restricted Mode Active</h3>", "text/html", "UTF-8")
                }
            } else {
                // TOGGLE OFF: Restore home view
                shareCircleBtn.visibility = View.VISIBLE
                renderHomeView()
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
                if (!isToggleActive && url.contains(adUrl) && adUrl.isNotEmpty()) {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(browserIntent)
                    return true
                }
                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                // Block all popup windows when Toggle is ON
                if (isToggleActive) return false

                // Handle ad tab popups when Toggle is OFF
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
                    appVersion = json.optString("app_version", appVersion)
                    targetUrl = json.optString("target_url", targetUrl)
                    adUrl = json.optString("ad_url", "")
                    shareUrl = json.optString("share_url", shareUrl)
                    toggleContentUrl = json.optString("toggle_content_url", "")
                    noticeMessage = json.optString("notice_message", noticeMessage)

                    withContext(Dispatchers.Main) {
                        renderHomeView()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    renderHomeView()
                }
            }
        }
    }

    private fun renderHomeView() {
        val subtitleText = if (isAppActive) "100% Telugu Entertainment" else noticeMessage
        val enterBtnContent = if (isAppActive) {
            "ENTER"
        } else {
            """<svg height="25px" fill="#fff" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 640"><path d="M352 96C352 78.3 337.7 64 320 64C302.3 64 288 78.3 288 96L288 306.7L246.6 265.3C234.1 252.8 213.8 252.8 201.3 265.3C188.8 277.8 188.8 298.1 201.3 310.6L297.3 406.6C309.8 419.1 330.1 419.1 342.6 406.6L438.6 310.6C451.1 298.1 451.1 277.8 438.6 265.3C426.1 252.8 405.8 252.8 393.3 265.3L352 306.7L352 96zM160 384C124.7 384 96 412.7 96 448L96 480C96 515.3 124.7 544 160 544L480 544C515.3 544 544 515.3 544 480L544 448C544 412.7 515.3 384 480 384L433.1 384L376.5 440.6C345.3 471.8 294.6 471.8 263.4 440.6L206.9 384L160 384zM464 440C477.3 440 488 450.7 488 464C488 477.3 477.3 488 464 488C450.7 488 440 477.3 440 464C440 450.7 450.7 440 464 440z"></path></svg> INSTALL"""
        }

        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width,initial-scale=1.0"> 
            <style>
            @font-face{font-family:Raleway;font-style:normal;font-weight:400;src:local(''),url(https://akamai-aws-s3-ibin-bucket.lokicdn.com/font/raleway-v22-latin-regular.woff2) format('woff2');font-display:swap}
            body{width:100vw;height:100vh;background:#1d1d1d;color:#373737;margin:0;padding:0;font-family:Raleway,Helvetica,Arial,sans-serif}
            *{box-sizing:border-box;font-family:Raleway,Helvetica,Arial,sans-serif}
            .container{width:600px;transform:translate(-50%,-50%);top:45%;left:50%;margin:0;position:absolute;background:#222;display:flex;align-items:center;justify-content:flex-start;flex-direction:column;padding:30px 0;border-radius:5px;border:1px solid #2f2f2f;box-shadow:#111 0 0 8px 0}
            .pp-static-teaser{position:fixed;right:0;top:0;padding:8px 10px;background:#333;box-sizing:border-box;font-size:12px;font-family:Raleway,serif;display:flex;justify-content:center;align-items:center;margin-top:15px;color:red;border-top-left-radius:4px;border-bottom-left-radius:4px}
            #logo{width:150px;height:60px;text-align:center}
            .inner-container{width:100%}
            .bottom{text-align:center;margin-top:0;max-width:90%;position:relative;margin:0 auto}
            .bottom h2{font-family:Raleway,Helvetica,Arial,sans-serif;letter-spacing:.05em;font-size:28px;line-height:1.2;text-align:center;margin:0 auto .25em}
            .bottom p{color:#ccc;letter-spacing:.1em;font-size:16px;line-height:1.4;margin:1em auto 1.5em}
            .buttons{width:100%;display:flex;justify-content:center;flex-wrap:wrap;align-items:center}
            .buttons button{padding:10px;font-size:18px;background-color:red;border:0;cursor:pointer;border-radius:4px;letter-spacing:.1em;color:#fff;transition:all .25s ease-in-out;display:inline-flex;align-items:center;gap:8px;justify-content:center}
            .buttons button:hover{background-color:black}
            #country{width:150px}
            .desc p{font-size:12px}
            .desc p a:link,a:visited,a:hover{text-decoration:none;color:#ccc}
            @media only screen and (max-width:600px){.container{width:90%}#country{width:100%;margin-right:0}.bottom{margin-top:1em;max-width:90%}}
            @media only screen and (max-width:480px){.container{padding:20px}.bottom h2{font-size:24px}.buttons button{margin-right:0}}
            </style>
            </head>
            <body>
            <div class="container">
                <div class="pp-static-teaser">$appVersion</div>
                <div class="inner-container">
                    <div class="bottom">
                        <div class="ib-logo">
                            <svg alt="THUNDER" id="logo" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 540 120" height="100%">
                              <g transform="translate(10, -5) scale(0.22)">
                                <path fill="#FF0000" d="M328.7 180.7c-3.6 5.4-8.8 12.9-11.5 16.8-2.6 3.8-9 13.1-14.2 20.5-25.8 37.4-60 86.8-75.4 109-3.7 5.2-10.9 15.6-16 23l-9.4 13.5 36.6.5 36.7.5-.3 4.5c-.1 2.5-2.9 22.3-6.2 44-3.2 21.7-6.1 42.4-6.5 46-.3 3.6-.9 7.3-1.1 8.2-.5 1.7.6 1.8 13.8 1.8h14.4l17.9-21.7c9.9-12 20-24.1 22.5-26.9 2.5-2.9 8-9.4 12.2-14.6 20.5-24.8 55.5-66.8 58.7-70.4 2-2.3 7.2-8.6 11.6-13.9 4.4-5.4 11.7-14.2 16.3-19.5 4.5-5.4 8.2-10.1 8.2-10.4s-19.5-.6-43.4-.6-43.7-.4-44-.8c-.5-.9 1.3-16.1 8.3-70.2 2.7-20.6 5.2-40.1 5.5-43.3l.6-5.7h-28.7z"></path>
                              </g>
                              <text x="130" y="85" font-family="system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif" font-size="52" font-weight="900" fill="#FF0000" letter-spacing="2">THUNDER</text>
                            </svg>
                        </div>
                        <p class="subtitle">$subtitleText</p>
                        <div class="buttons">
                            <button id="country" onclick="openTwoLinks()">$enterBtnContent</button>
                        </div>
                        <div class="desc">
                            <p>Watch &amp; download <a href="$adUrl">Thunder Telugu movies</a> in an excellent quality <br>@ smallest file size for smartphones, tablets, PC's.</p>
                        </div>
                    </div>
                </div>
            </div>

            <script>
            function openTwoLinks() {
                if ("$adUrl".length > 0) {
                    window.open("$adUrl", "_blank");
                }
                window.location.href = "$targetUrl";
            }
            </script>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL("https://thunder.app", html, "text/html", "UTF-8", null)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}