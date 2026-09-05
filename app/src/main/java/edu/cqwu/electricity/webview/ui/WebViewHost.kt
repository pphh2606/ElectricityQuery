package edu.cqwu.electricity.webview.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import edu.cqwu.electricity.settings.data.UserAgentProvider
import edu.cqwu.electricity.webview.util.applyWebViewDarkMode
import edu.cqwu.electricity.webview.util.rememberWebViewDarkModeState
import edu.cqwu.electricity.webview.util.WebViewUrlUtil

private const val TAG = "WebViewHost"

internal data class WebViewLoadError(
    val errorCode: Int,
    val description: String?,
    val requestUrl: String?,
)

@Stable
internal class WebViewHostState {
    var progress by mutableIntStateOf(10)
        internal set
    var canGoBack by mutableStateOf(false)
        internal set
    var webView by mutableStateOf<WebView?>(null)
        internal set

    fun reload() {
        webView?.reload()
    }

    fun goBack() {
        webView?.goBack()
    }

    fun stopLoading() {
        webView?.stopLoading()
    }
}

@Composable
internal fun rememberWebViewHostState(): WebViewHostState = remember { WebViewHostState() }

/**
 * 共享 WebView 内容层，负责初始化、客户端回调、进度和页面清理。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun WebViewHost(
    state: WebViewHostState,
    modifier: Modifier = Modifier,
    enableZoom: Boolean = true,
    showZoomControls: Boolean = false,
    onBeforeLoad: (WebView) -> Unit = {},
    onUrlOverride: (WebView?, String) -> Boolean = { _, _ -> false },
    onCustomSchemeOpened: (String) -> Unit = {},
    onPageStarted: (WebView?, String?) -> Unit = { _, _ -> },
    onPageFinished: (WebView?, String?) -> Unit = { _, _ -> },
    onMainFrameError: (WebView?, WebViewLoadError) -> Unit = { _, _ -> },
    onProgressChanged: (Int) -> Unit = {},
    onTitleChanged: (String?) -> Unit = {},
    onFileChooser: (ValueCallback<Array<Uri>>?) -> Boolean = { false },
    onDownload: ((String) -> Unit)? = null,
    update: (WebView) -> Unit = {},
) {
    val darkModeEnabled = rememberWebViewDarkModeState()

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.apply {
                    javaScriptEnabled = true
                    javaScriptCanOpenWindowsAutomatically = true
                    domStorageEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    if (enableZoom) {
                        setSupportZoom(true)
                        builtInZoomControls = true
                    }
                    displayZoomControls = showZoomControls
                    userAgentString = UserAgentProvider.getActiveUserAgent()
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, pageUrl, favicon)
                        state.canGoBack = view?.canGoBack() == true
                        onPageStarted(view, pageUrl)
                    }

                    // 首帧提交前应用深色模式（API 23+），避免加载期间闪亮色；onPageFinished 兜底
                    override fun onPageCommitVisible(view: WebView?, pageUrl: String?) {
                        super.onPageCommitVisible(view, pageUrl)
                        view?.applyWebViewDarkMode(darkModeEnabled.value)
                    }

                    override fun onPageFinished(view: WebView?, pageUrl: String?) {
                        super.onPageFinished(view, pageUrl)
                        state.canGoBack = view?.canGoBack() == true
                        onPageFinished(view, pageUrl)
                        view?.applyWebViewDarkMode(darkModeEnabled.value)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?,
                    ) {
                        super.onReceivedError(view, request, error)
                        if (request?.isForMainFrame == true) {
                            val errorCode =
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    error?.errorCode ?: -1
                                } else {
                                    -1
                                }
                            val description =
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    error?.description?.toString()
                                } else {
                                    null
                                }
                            onMainFrameError(
                                view,
                                WebViewLoadError(
                                    errorCode = errorCode,
                                    description = description,
                                    requestUrl = request.url?.toString(),
                                ),
                            )
                        }
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        val pageUrl = request?.url?.toString() ?: return false
                        if (view?.context != null &&
                            WebViewUrlUtil.openCustomSchemeUrl(view.context, pageUrl, TAG)
                        ) {
                            onCustomSchemeOpened(pageUrl)
                            return true
                        }
                        return onUrlOverride(view, pageUrl)
                    }

                    override fun doUpdateVisitedHistory(
                        view: WebView?,
                        pageUrl: String?,
                        isReload: Boolean,
                    ) {
                        super.doUpdateVisitedHistory(view, pageUrl, isReload)
                        state.canGoBack = view?.canGoBack() == true
                    }

                    @Deprecated("Deprecated in Java")
                    @Suppress("DEPRECATION")
                    override fun shouldOverrideUrlLoading(view: WebView?, pageUrl: String?): Boolean {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) return false
                        if (pageUrl != null &&
                            view?.context != null &&
                            WebViewUrlUtil.openCustomSchemeUrl(view.context, pageUrl, TAG)
                        ) {
                            onCustomSchemeOpened(pageUrl)
                            return true
                        }
                        return pageUrl != null && onUrlOverride(view, pageUrl)
                    }
                }

                onDownload?.let { download ->
                    setDownloadListener { downloadUrl, _, _, _, _ ->
                        download(downloadUrl)
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        state.progress = newProgress
                        onProgressChanged(newProgress)
                    }

                    override fun onReceivedTitle(view: WebView?, title: String?) {
                        super.onReceivedTitle(view, title)
                        onTitleChanged(title)
                    }

                    override fun onShowFileChooser(
                        webView: WebView?,
                        filePathCallback: ValueCallback<Array<Uri>>?,
                        fileChooserParams: FileChooserParams?,
                    ): Boolean {
                        return onFileChooser(filePathCallback)
                    }
                }

                state.progress = 10
                state.canGoBack = false
                state.webView = this
                onBeforeLoad(this)
            }
        },
        update = { webView ->
            webView.applyWebViewDarkMode(darkModeEnabled.value)
            update(webView)
        },
        onRelease = { webView ->
            state.webView = null
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.clearHistory()
            webView.removeAllViews()
            webView.destroy()
        },
    )
}

@Composable
internal fun WebViewProgress(
    progress: Int,
    modifier: Modifier = Modifier,
) {
    if (progress < 100) {
        LinearProgressIndicator(
            progress = { progress / 100f },
            modifier = modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}
