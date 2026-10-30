package edu.cqwu.electricity.webview.ui

import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** WebView 加载错误：null 表示无错误，非 null 表示显示自定义错误叠加层。 */
internal data class UnifiedWebViewError(
    val errorCode: Int,
    val description: String,
    val isHttpError: Boolean = false,
)

/** 主内置浏览器的全部页面状态（原先平铺在 Composable 顶部的十余个 remember）。 */
@Stable
internal class UnifiedWebViewUiState(
    initialTitle: String,
    /**
     * 初始域名：从入口 URL 解析出来。
     *
     * 页面第一帧就要有副标题，否则 miuix 的小标题栏会先只有一行、等 WebView 回调
     * `onPageStarted` 拿到 host 后再长出一行，标题栏高度会跳一下。
     */
    initialDomain: String = "",
) {

    var isLoading by mutableStateOf(true)
    var webErrorState by mutableStateOf<UnifiedWebViewError?>(null)

    /** 出错时的页面 URL：用来区分「换了页面」与同一页面的二次导航，避免 4xx/5xx 浮层一闪而过 */
    var webErrorUrl by mutableStateOf<String?>(null)

    var loginRequiredOverlayVisible by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var progress by mutableIntStateOf(10)
    var pageTitle by mutableStateOf(initialTitle)
    var pageDomain by mutableStateOf(initialDomain)
    var showMenu by mutableStateOf(false)
    var fileUploadCallback by mutableStateOf<ValueCallback<Array<Uri>>?>(null)
    var webViewRef by mutableStateOf<WebView?>(null)
    var needsReloadAfterReturn by mutableStateOf(false)
    var campusphereToastShown by mutableStateOf(false)

    /**
     * 清错误浮层 + 立刻进入加载态 + reload（刷新按钮与错误浮层「重试」共用）。
     *
     * reload 是异步的，慢站要数秒才回调 onPageStarted，不先置位就没有加载指示；
     * 同一个 URL 的 reload 又不会再触发 onPageStarted 里的清空逻辑，不清浮层会一直盖着。
     */
    fun reloadWithLoading() {
        webErrorState = null
        webErrorUrl = null
        isLoading = true
        webViewRef?.reload()
    }
}
