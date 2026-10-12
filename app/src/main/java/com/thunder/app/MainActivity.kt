package com.thunder.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

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

        setupWebView()
        fetchRemoteConfig()
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.addJavascriptInterface(WebAppInterface(), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                if (isToggleActive) {
                    return true
                }
                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                if (isToggleActive) return false

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

    inner class WebAppInterface {
        @JavascriptInterface
        fun triggerEnterAction() {
            runOnUiThread {
                if (adUrl.isNotEmpty()) {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(adUrl)))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                if (targetUrl.isNotEmpty()) {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        @JavascriptInterface
        fun triggerShareAction() {
            runOnUiThread {
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareUrl)
                }
                startActivity(Intent.createChooser(shareIntent, "Share via"))
            }
        }

        @JavascriptInterface
        fun toggleMode() {
            runOnUiThread {
                isToggleActive = !isToggleActive
                if (isToggleActive) {
                    loadToggleContent()
                } else {
                    renderHomeView()
                }
            }
        }
    }

    private fun fetchRemoteConfig() {
        scope.launch(Dispatchers.IO) {
            try {
                val cacheBusterUrl = "$rawConfigUrl?t=${System.currentTimeMillis()}"
                val connection = URL(cacheBusterUrl).openConnection() as HttpURLConnection
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

    private fun loadToggleContent() {
        scope.launch(Dispatchers.IO) {
            var rawHtml = "<h3>Restricted Mode Active</h3>"
            if (toggleContentUrl.isNotEmpty()) {
                try {
                    val connection = URL("$toggleContentUrl?t=${System.currentTimeMillis()}").openConnection() as HttpURLConnection
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    if (connection.responseCode == 200) {
                        rawHtml = connection.inputStream.bufferedReader().use { it.readText() }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val overlayCssAndJs = """
                <style>
                .toggle-circle-btn { position: fixed; top: 10px; left: 10px; width: 40px; height: 40px; border-radius: 50%; background: #444; border: 1px solid #555; cursor: pointer; display: flex; align-items: center; justify-content: center; transition: background 0.3s; z-index: 999999; }
                .toggle-circle-btn:hover { background: black; }
                .toggle-svg { width: 20px; height: 20px; }
                .track-path { fill: red; stroke: #fff; stroke-width: 40px; transition: fill 0.3s ease; }
                .slider-circle { fill: #fff; transform-origin: center; transform: translate(192px, 0); transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1); }
                </style>
                <button id="toggleBtn" class="toggle-circle-btn active" title="Toggle Switch" onclick="AndroidBridge.toggleMode()">
                  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 640" class="toggle-svg">
                    <path class="track-path" d="M608 320C608 214 522 128 416 128L224 128C118 128 32 214 32 320C32 426 118 512 224 512L416 512C522 512 608 426 608 320z"></path>
                    <path class="slider-circle" d="M224 400C268.2 400 304 364.2 304 320C304 275.8 268.2 240 224 240C179.8 240 144 275.8 144 320C144 364.2 179.8 400 224 400z"></path>
                  </svg>
                </button>
            """.trimIndent()

            val completeToggleHtml = rawHtml + overlayCssAndJs

            withContext(Dispatchers.Main) {
                webView.loadDataWithBaseURL("https://thunder.app", completeToggleHtml, "text/html", "UTF-8", null)
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
            
            .share-circle-btn { position: fixed; top: 10px; right: 10px; width: 40px; height: 40px; border-radius: 50%; background: #444; border: 1px solid #555; cursor: pointer; display: flex; align-items: center; justify-content: center; transition: background 0.3s; z-index: 9999; }
            .share-circle-btn:hover { background: black; }
            .share-circle-btn svg { width: 20px; height: 20px; }
            .toggle-circle-btn { position: fixed; top: 10px; left: 10px; width: 40px; height: 40px; border-radius: 50%; background: #444; border: 1px solid #555; cursor: pointer; display: flex; align-items: center; justify-content: center; transition: background 0.3s; z-index: 9999; }
            .toggle-circle-btn:hover { background: black; }
            .toggle-svg { width: 20px; height: 20px; }
            .track-path { fill: none; stroke: #fff; stroke-width: 40px; transition: fill 0.3s ease; }
            .slider-circle { fill: #fff; transform-origin: center; transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1); }

            @media only screen and (max-width:600px){.container{width:90%}#country{width:100%;margin-right:0}.bottom{margin-top:1em;max-width:90%}}
            @media only screen and (max-width:480px){.container{padding:20px}.bottom h2{font-size:24px}.buttons button{margin-right:0}}
            </style>
            </head>
            <body>

            <button id="toggleBtn" class="toggle-circle-btn" title="Toggle Switch" onclick="AndroidBridge.toggleMode()">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 640" class="toggle-svg">
                <path class="track-path" d="M608 320C608 214 522 128 416 128L224 128C118 128 32 214 32 320C32 426 118 512 224 512L416 512C522 512 608 426 608 320z"></path>
                <path class="slider-circle" d="M224 400C268.2 400 304 364.2 304 320C304 275.8 268.2 240 224 240C179.8 240 144 275.8 144 320C144 364.2 179.8 400 224 400z"></path>
              </svg>
            </button>

            <button id="shareBtn" class="share-circle-btn" title="Share Link" onclick="AndroidBridge.triggerShareAction()">
                <svg fill="#fff" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 640"><path d="M371.8 82.4C359.8 87.4 352 99 352 112L352 192L240 192C142.8 192 64 270.8 64 368C64 481.3 145.5 531.9 164.2 542.1C166.7 543.5 169.5 544 172.3 544C183.2 544 192 535.1 192 524.3C192 516.8 187.7 509.9 182.2 504.8C172.8 496 160 478.4 160 448.1C160 395.1 203 352.1 256 352.1L352 352.1L352 432.1C352 445 359.8 456.7 371.8 461.7C383.8 466.7 397.5 463.9 406.7 454.8L566.7 294.8C579.2 282.3 579.2 262 566.7 249.5L406.7 89.5C397.5 80.3 383.8 77.6 371.8 82.6z"></path></svg>
            </button>

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
                            <button id="country" onclick="AndroidBridge.triggerEnterAction()">$enterBtnContent</button>
                        </div>
                        <div class="desc">
                            <p>Watch &amp; download <a href="$adUrl">Thunder Telugu movies</a> in an excellent quality <br>@ smallest file size for smartphones, tablets, PC's.</p>
                        </div>
                    </div>
                </div>
            </div>
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
