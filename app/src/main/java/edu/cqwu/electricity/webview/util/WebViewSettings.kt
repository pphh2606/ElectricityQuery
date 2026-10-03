package edu.cqwu.electricity.webview.util

import android.annotation.SuppressLint
import android.webkit.WebView
import edu.cqwu.electricity.common.settings.UserAgentProvider

/**
 * 各 WebView 接入点共用的 WebSettings 初始化。
 *
 * 主内置浏览器与 [edu.cqwu.electricity.webview.ui.WebViewHost] 系（半屏、支付浮层）共用一份，
 * 避免同一组设置两处各写一遍而漂移。
 */
@SuppressLint("SetJavaScriptEnabled")
internal fun WebView.applyCommonWebViewSettings(
    enableZoom: Boolean,
    showZoomControls: Boolean,
) {
    settings.javaScriptEnabled = true
    settings.javaScriptCanOpenWindowsAutomatically = true
    settings.domStorageEnabled = true
    settings.useWideViewPort = true
    settings.loadWithOverviewMode = true
    if (enableZoom) {
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
    }
    settings.displayZoomControls = showZoomControls
    settings.userAgentString = UserAgentProvider.getActiveUserAgent()
}
