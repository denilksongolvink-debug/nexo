package dev.nexo.app

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import dev.nexo.app.BootstrapManager.BootstrapState
import dev.nexo.app.BackendService.BackendHealth

/** Activity principal - WebView para o Nexo frontend */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var webView: WebView? = null
    private val webViewClient = NexoWebViewClient()
    private val webChromeClient = NexoWebChromeClient()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Tema de splash enquanto bootstrap
        setTheme(R.style.Theme_Nexo_Splash)
        
        // Inicia BackendService
        val serviceIntent = Intent(this, BackendService::class.java)
        startForegroundService(serviceIntent)
        
        // UI Compose para estados de bootstrap
        setContent {
            NexoTheme {
                val bootstrapState by viewModel.bootstrapState.collectAsStateWithLifecycle()
                val backendHealth by viewModel.backendHealth.collectAsStateWithLifecycle()
                
                when {
                    bootstrapState is BootstrapState.Ready -> {
                        // WebView full screen
                        AndroidView(
                            factory = { createWebView(it) },
                            update = { it.settings.javaScriptEnabled = true }
                        )
                    }
                    bootstrapState is BootstrapState.Error -> {
                        BootstrapErrorScreen(state = bootstrapState, onRetry = { viewModel.retryBootstrap() })
                    }
                    else -> {
                        BootstrapProgressScreen(state = bootstrapState, health = backendHealth)
                    }
                }
            }
        }
    }

    private fun createWebView(context: android.content.Context): WebView {
        val wv = WebView(context).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
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
                // Cache
                cacheMode = WebSettings.LOAD_DEFAULT
                setAppCacheEnabled(true)
                setAppCachePath(context.cacheDir.absolutePath)
            }
            webViewClient = webViewClient
            webChromeClient = webChromeClient
            // Scrollbars
            verticalScrollBarEnabled = true
            horizontalScrollBarEnabled = false
            // Hardware acceleration
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
            }
        }
        webView = wv
        return wv
    }

    inner class NexoWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            if (url.startsWith("http://127.0.0.1") || url.startsWith("http://localhost")) {
                return false // Permite navegação interna
            }
            // Links externos abrem no navegador
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).also {
                startActivity(it)
            }
            return true
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest?, error: WebResourceError) {
            Log.w("NexoWebView", "WebView error: ${error.description}")
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            // Em produção, NÃO prosseguir com erro SSL
            if (BuildConfig.DEBUG) {
                handler.proceed()
            } else {
                handler.cancel()
            }
        }
    }

    inner class NexoWebChromeClient : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            // Atualiza barra de progresso se necessário
        }

        override fun onShowFileChooser(
            view: WebView,
            valueCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams
        ): Boolean {
            // Delega para Activity result API
            return false // WebView usa seu próprio seletor
        }
    }

    override fun onDestroy() {
        webView?.destroy()
        super.onDestroy()
    }
}