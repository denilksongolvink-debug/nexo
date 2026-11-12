package dev.nexo.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.*
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat

/** Activity principal - WebView para o Nexo frontend */
class MainActivity : ComponentActivity() {

    private var webView: WebView? = null
    private val secretStore = SecretStore(this)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicia BackendService
        val serviceIntent = Intent(this, BackendService::class.java)
        startForegroundService(serviceIntent)

        // Layout com WebView
        val rootLayout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.parseColor("#0F172A"))
        }

        val wv = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                allowFileAccess = false
                allowContentAccess = false
                allowFileAccessFromFileURLs = false
                allowUniversalAccessFromFileURLs = false
                mediaPlaybackRequiresUserGesture = false
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()
                    if (url.startsWith("http://127.0.0.1") || url.startsWith("http://localhost")) {
                        return false
                    }
                    Intent(Intent.ACTION_VIEW, Uri.parse(url)).also { startActivity(it) }
                    return true
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest?, error: WebResourceError) {
                    Log.w("NexoWebView", "WebView error: ${error?.description}")
                }
                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
                    handler.cancel()
                }
            }
            webChromeClient = WebChromeClient()
        }

        rootLayout.addView(wv)
        setContentView(rootLayout)
        webView = wv

        // Aguarda backend ficar pronto antes de carregar URL
        waitForBackendAndLoad(wv)
    }

    private fun waitForBackendAndLoad(wv: WebView) {
        Thread {
            var attempts = 0
            while (attempts < 60) { // 30 segundos max
                try {
                    val port = secretStore.getEdgePort()
                    val url = "http://127.0.0.1:$port/healthz"
                    val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 2000
                    conn.readTimeout = 2000
                    if (conn.responseCode == 200) {
                        runOnUiThread {
                            val nonce = secretStore.getNonce()
                            wv.loadUrl("http://127.0.0.1:$port/?k=$nonce")
                        }
                        return@Thread
                    }
                } catch (e: Exception) {
                    // Backend ainda não pronto
                }
                Thread.sleep(500)
                attempts++
            }
            runOnUiThread {
                wv.loadUrl("about:blank")
            }
        }.start()
    }

    override fun onDestroy() {
        webView?.destroy()
        super.onDestroy()
    }
}