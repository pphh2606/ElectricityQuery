package edu.cqwu.electricity.common.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.common.settings.LocalUiStyle
import edu.cqwu.electricity.common.settings.UiStyle
import top.yukonga.miuix.kmp.basic.PullToRefresh as MiuixPullToRefresh
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** MIUI 模式下由 [CollapsibleScaffold] 提供的标题栏滚动行为：下拉时大标题跟着回弹展开。 */
val LocalMiuixScrollBehavior = staticCompositionLocalOf<ScrollBehavior?> { null }

/** miuix 下拉刷新默认文案是英文，这里固定成中文四段。 */
private val MiuixRefreshTexts = listOf("下拉刷新", "松开刷新", "正在刷新…", "刷新成功")

/**
 * 页面统一的下拉刷新容器，按 [LocalUiStyle] 二选一：MIUI 走 miuix 的圆环与文案并联动大标题，
 * Material 3 走 [PullToRefreshBox] + M3E 的 LoadingIndicator。
 *
 * 两种模式都把内容铺满全屏，顶部留白由页面首项 `Spacer(Modifier.height(topInset))` 承担，
 * 内容才能滚到标题栏背后被 Haze 采样；刷新指示器则按 [topInset] 下移到标题栏下沿。
 *
 * @param topInset 标题栏浮层高度，只用来定位刷新指示器。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    topInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (LocalUiStyle.current == UiStyle.MIUI) {
        // miuix 的 contentPadding 也只影响指示器偏移，不影响内容布局
        MiuixPullToRefresh(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = modifier,
            contentPadding = PaddingValues(top = topInset),
            topAppBarScrollBehavior = LocalMiuixScrollBehavior.current,
            color = MiuixTheme.colorScheme.primary,
            refreshTexts = MiuixRefreshTexts,
        ) {
            content()
        }
        return
    }

    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = state,
        modifier = modifier,
        indicator = {
            // 内容层从屏幕顶端铺满，默认指示器会落在标题栏浮层背后，下移一个浮层高度
            PullToRefreshDefaults.LoadingIndicator(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topInset),
            )
        },
    ) {
        content()
    }
}
