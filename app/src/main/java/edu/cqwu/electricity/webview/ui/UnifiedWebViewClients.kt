package edu.cqwu.electricity.webview.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.State
import edu.cqwu.electricity.R
import edu.cqwu.electricity.common.util.ToastUtils
import edu.cqwu.electricity.logging.AppLog
import edu.cqwu.electricity.theme.ui.SnackbarController
import edu.cqwu.electricity.webview.util.WebViewExternalApp
import edu.cqwu.electricity.webview.util.WebViewUrlUtil
import edu.cqwu.electricity.webview.util.applyWebViewDarkMode
import java.io.ByteArrayInputStream

/** 主内置浏览器的客户端回调依赖（原先由 AndroidView.factory 的闭包直接捕获）。 */
internal class UnifiedWebViewDeps(
    val context: Context,
    val snackbar: SnackbarController,
    val darkModeEnabled: State<Boolean>,
    val launchFilePicker: () -> Unit,
)

/**
 * 构造主内置浏览器的 [WebViewClient]：加载状态与错误浮层、CAS 登录检测、campusphere 提醒、
 * campushoy 资源拦截、自定义 scheme 外跳、历史栈同步。
 */
internal fun createUnifiedWebViewClient(
    state: UnifiedWebViewUiState,
    deps: UnifiedWebViewDeps,
): WebViewClient = object : WebViewClient() {

    private fun updateLoginRequiredOverlay(url: String?) {
        state.loginRequiredOverlayVisible =
            WebViewUrlUtil.shouldShowLoginRequired(url, state.webErrorState != null)
    }

    /**
     * 网页发起的「打开外部应用」：判定 → 查询 → 询问 / 静默，全程同步完成并返回 true（接管），
     * URL 不会被交回内核加载，因此不会出现 net::ERR_UNKNOWN_URL_SCHEME。
     *
     * @return true = 已接管；false = 放行给 WebView 加载
     */
    private fun handleExternalUrl(url: String): Boolean {
        if (!WebViewExternalApp.shouldHandleAsExternal(url)) return false

        val intent = WebViewExternalApp.buildIntent(url) ?: return true

        // 没有应用能处理 → 完全静默：不弹提示、不报错
        val appName = WebViewExternalApp.resolveAppName(deps.context, intent)
        if (appName == null) {
            AppLog.d("WebView_DIAG", "无应用可处理，静默忽略: $url")
            return true
        }

        val message = if (appName.isEmpty()) {
            // 多个应用都能处理时命中的是系统选择器，拿不到具体应用名
            deps.context.getString(R.string.webview_external_app_message_generic)
        } else {
            deps.context.getString(R.string.webview_external_app_message, appName)
        }
        deps.snackbar.show(
            message = message,
            type = ToastUtils.Type.INFO,
            actionLabel = deps.context.getString(R.string.common_confirm),
            onAction = { WebViewExternalApp.start(deps.context, intent) },
        )
        return true
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        AppLog.d("WebView_DIAG", "onPageStarted: $url")
        state.isLoading = true
        // 只有「换了页面」才清错误状态：HTTP 错误页会为自身再走一遍 onPageStarted，
        // 清掉会让 4xx/5xx 的浮层一闪而过
        if (url != state.webErrorUrl) {
            state.webErrorState = null
            state.webErrorUrl = null
        }
        state.loginRequiredOverlayVisible = false
        state.canGoBack = view?.canGoBack() == true

        // campusphere.net 域名检测（仅一次）
        if (url != null && !state.campusphereToastShown) {
            val host = Uri.parse(url).host
            if (host != null && host.endsWith(".campusphere.net")) {
                state.campusphereToastShown = true
                val currentUrl = url
                deps.snackbar.show(
                    message = deps.context.getString(R.string.webview_campusphere_warning),
                    actionLabel = deps.context.getString(R.string.common_open_in_browser),
                    onAction = {
                        try {
                            // 优先尝试打开今日校园 App
                            deps.context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("campusnextins://"))
                            )
                        } catch (_: ActivityNotFoundException) {
                            // 降级：用浏览器打开当前链接
                            try {
                                deps.context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
                                )
                            } catch (_: ActivityNotFoundException) {
                                deps.snackbar.show(deps.context.getString(R.string.common_no_browser))
                            }
                        }
                    }
                )
            }
        }
    }

    // 首帧提交前应用深色模式（API 23+），避免加载期间闪亮色；onPageFinished 兜底
    override fun onPageCommitVisible(view: WebView?, url: String?) {
        super.onPageCommitVisible(view, url)
        view?.applyWebViewDarkMode(deps.darkModeEnabled.value)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        AppLog.d("WebView_DIAG", "onPageFinished: $url")
        state.isLoading = false
        state.canGoBack = view?.canGoBack() == true
        updateLoginRequiredOverlay(url)

        // 注入 JS 强制启用缩放：移除 user-scalable=no，并覆盖 CSS touch-action 的限制
        view?.evaluateJavascript(
            """(function() {
                var meta = document.querySelector('meta[name="viewport"]');
                if (meta) {
                    var content = meta.getAttribute('content') || '';
                    if (content.indexOf('user-scalable=no') !== -1) {
                        meta.setAttribute('content', content.replace(/user-scalable=no/gi, 'user-scalable=yes'));
                    }
                }
                var style = document.createElement('style');
                style.setAttribute('type', 'text/css');
                style.appendChild(document.createTextNode(
                    'html, body, * { touch-action: manipulation !important; }'
                ));
                document.head.appendChild(style);
            })()""", null)
        view?.applyWebViewDarkMode(deps.darkModeEnabled.value)
    }

    /**
     * 主框架加载错误的统一收尾：-10 静默吞掉，其余渲染错误浮层。
     *
     * ERROR_UNSUPPORTED_SCHEME(-10, net::ERR_UNKNOWN_URL_SCHEME)：自定义 scheme 都已被
     * handleExternalUrl 接管，走到这里说明是漏网路径（POST / 无手势 / 重定向 / 主动 loadUrl）。
     * 按产品要求静默吞掉，只收尾加载状态，绝不渲染错误浮层。
     */
    private fun applyMainFrameError(code: Int, description: String?, url: String?) {
        if (code == WebViewClient.ERROR_UNSUPPORTED_SCHEME) {
            AppLog.d("WebView_DIAG", "忽略未知 scheme 错误（不显示错误页）: $url")
            state.isLoading = false
            return
        }
        if (state.webErrorState == null) {
            AppLog.w("WebView_DIAG", ">>> 主框架加载错误: code=$code, desc=$description")
            state.isLoading = false
            state.loginRequiredOverlayVisible = false
            state.webErrorUrl = url
            state.webErrorState = UnifiedWebViewError(
                errorCode = code,
                description = description ?: deps.context.getString(R.string.common_unknown_error),
            )
        }
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame != true) return
        // WebResourceError 与 isForMainFrame 都是 API 23+，Android 5.x 由下面的废弃签名兜底
        val m = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        applyMainFrameError(
            code = if (m) error?.errorCode ?: -1 else -1,
            description = if (m) error?.description?.toString() else null,
            url = request.url.toString(),
        )
    }

    // API 23 以下内核只回调这个已废弃的签名（Android 5.x）。不补它的话，-10 静默与
    // 错误浮层在 Android 5 上都不生效
    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onReceivedError(
        view: WebView?,
        errorCode: Int,
        description: String?,
        failingUrl: String?
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) return
        applyMainFrameError(errorCode, description, failingUrl)
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?
    ) {
        super.onReceivedHttpError(view, request, errorResponse)
        val statusCode = errorResponse?.statusCode ?: 0
        if (request?.isForMainFrame == true && statusCode >= 400 && state.webErrorState == null) {
            AppLog.w("WebView_DIAG", ">>> HTTP 错误: statusCode=$statusCode")
            state.isLoading = false
            state.loginRequiredOverlayVisible = false
            state.webErrorUrl = request.url.toString()
            state.webErrorState = UnifiedWebViewError(
                errorCode = statusCode,
                description = "HTTP $statusCode",
                isHttpError = true
            )
        }
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val url = request?.url?.toString() ?: return null
        if (url.contains("campushoy")) {
            AppLog.d("WebView_DIAG", ">>> 拦截 campushoy.js: $url")
            return WebResourceResponse(
                "application/javascript", "UTF-8",
                ByteArrayInputStream("".toByteArray())
            )
        }
        return null
    }

    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?
    ): Boolean {
        val url = request?.url?.toString() ?: return false
        AppLog.d("WebView_DIAG", "shouldOverrideUrlLoading: $url")
        return handleExternalUrl(url)
    }

    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        state.canGoBack = view?.canGoBack() == true
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        // API 24+ 由新签名负责
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) return false
        return url != null && handleExternalUrl(url)
    }
}

/** 构造主内置浏览器的 [WebChromeClient]：进度、标题与域名、文件选择。 */
internal fun createUnifiedWebChromeClient(
    state: UnifiedWebViewUiState,
    deps: UnifiedWebViewDeps,
): WebChromeClient = object : WebChromeClient() {

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        state.progress = newProgress
        // 不在这里收尾：progress 到 100 通常早于 onPageFinished，提前把 isLoading 置 false
        // 会打断指示器的展开动画；收尾交给 onPageFinished / onReceivedError / onReceivedHttpError
    }

    override fun onReceivedTitle(view: WebView?, title: String?) {
        super.onReceivedTitle(view, title)
        AppLog.d("WebView_DIAG", "onReceivedTitle: $title, url=${view?.url}")
        if (!title.isNullOrBlank()) {
            state.pageTitle = title
        }
        view?.url?.let { url ->
            val host = Uri.parse(url).host
            if (!host.isNullOrBlank()) {
                state.pageDomain = host
            }
        }
    }

    // 文件上传支持（<input type="file">）
    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?
    ): Boolean {
        AppLog.d("WebView_DIAG", "onShowFileChooser")
        state.fileUploadCallback = filePathCallback
        // 始终用 */* 避免 WebView 传 .png 等扩展名导致崩溃
        deps.launchFilePicker()
        return true
    }
}

/**
 * 文件下载支持：交给系统处理下载链接。
 *
 * @param onNoTool 没有任何应用能接住下载链接时的提示
 */
internal fun WebView.applyUnifiedDownloadListener(onNoTool: () -> Unit) {
    setDownloadListener { downloadUrl, _, _, mimeType, _ ->
        AppLog.d("WebView_DIAG", "下载请求: $downloadUrl, mime=$mimeType")
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)))
        } catch (_: ActivityNotFoundException) {
            onNoTool()
        }
    }
}

/** 按域名构建 Referer 请求头，避免已知站点的跨域请求被拒绝。 */
internal fun buildRefererHeaders(url: String): Map<String, String> = when {
    url.contains("pay.cqwu.edu.cn") -> mapOf("Referer" to "https://pay.cqwu.edu.cn/")
    else -> emptyMap()
}
