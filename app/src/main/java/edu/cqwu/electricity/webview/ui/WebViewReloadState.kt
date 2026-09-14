package edu.cqwu.electricity.webview.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * WebView 专用登录成功后是否需要重新加载页面。
 *
 * 从 `theme/ui/Theme.kt` 拆出：它是 WebView 与登录页之间的协作机制，与主题无关。
 * 由 `app/NavGraph`（`WEBVIEW_LOGIN` 路由）提供，本包 [WebViewBottomSheet] 与
 * [UnifiedWebViewScreen] 消费。
 */
internal val LocalWebViewReloadAfterLogin = staticCompositionLocalOf { false }

/** 消费"登录后需刷新"标记（读取后置回 false） */
internal val LocalWebViewReloadConsumed = staticCompositionLocalOf { {} }
