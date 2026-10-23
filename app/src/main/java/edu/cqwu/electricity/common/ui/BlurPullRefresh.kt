package edu.cqwu.electricity.common.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * 毛玻璃页面统一的下拉刷新指示器。
 *
 * 这些页面的内容层从屏幕顶端铺满（[BlurScaffold] 结构），默认指示器会落在屏幕最顶部、
 * 被标题栏浮层盖住，所以必须整体下移一个浮层高度。全项目每个毛玻璃页面都要这一句，
 * 抽出来避免 40 处各写 9 行。
 *
 * 用法（`indicator` 参数的接收者就是 [BoxScope]）：
 * ```
 * PullToRefreshBox(
 *     isRefreshing = ...,
 *     onRefresh = ...,
 *     state = pullState,
 *     indicator = { BlurPullRefreshIndicator(pullState, isRefreshing, topInset) },
 * ) { ... }
 * ```
 */
@Composable
fun BoxScope.BlurPullRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    topInset: Dp,
) {
    PullToRefreshDefaults.Indicator(
        state = state,
        isRefreshing = isRefreshing,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = topInset),
    )
}
