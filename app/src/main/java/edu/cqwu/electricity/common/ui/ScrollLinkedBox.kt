package edu.cqwu.electricity.common.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 把 [CollapsibleScaffold] 给的滚动连接挂到下拉刷新容器**内部**。
 *
 * 两套下拉刷新都在 `onPostScroll` 里先吃掉「向下拉的位移」（miuix 是
 * `PullToRefreshModifierNode.consumeAvailableOffset`，Material 3 同理），而
 * nestedScroll 的 post-scroll 是由内向外传递的：
 *
 * - 连接挂在下拉刷新**外层** → 标题栏拿到的是已经被吃成 0 的位移，
 *   表现就是「下拉能刷新，但大标题召不回来」；
 * - 连接挂在**内容层**（本组件）→ 标题栏先收到位移再交给下拉刷新，两者都能工作。
 *
 * 包一层而不是改页面结构，是因为页面里通常是一整个 `when` 分支，没有现成容器可挂。
 */
@Composable
fun ScrollLinkedBox(
    scrollModifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().then(scrollModifier), content = content)
}
