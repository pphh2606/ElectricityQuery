@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package edu.cqwu.electricity.common.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.common.settings.LocalUiStyle
import edu.cqwu.electricity.common.settings.UiStyle
import edu.cqwu.electricity.common.ui.BlurScaffold
import edu.cqwu.electricity.common.ui.transparentTopBarColors
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 页面统一外壳：默认是大标题随滚动折叠成小标题。
 *
 * 两套实现按 [LocalUiStyle] 二选一，页面代码保持不变：
 * - Material 3：标题栏浮在内容之上做模糊，[content] 的首参是浮层实测高度；
 * - MIUI：走 miuix 的 `Scaffold + TopAppBar`，内容排在标题栏下方，首参固定传 0。
 *
 * @param onBack 为 null 时不画返回键（底部导航的根页面）。
 * @param subtitle 副标题，只有 MIUI 的顶栏有这一行（比如内置浏览器显示域名）。
 * @param navigationContent 自定义左侧区域，用于「返回 + 关闭」这类双按钮；传了就忽略 [onBack]。
 * @param blurEnabled 是否给标题栏做毛玻璃。正文是 WebView 的页面必须传 false。
 * @param smallTitle 为 true 时不展开大标题：MD3 用定高 `TopAppBar`，MIUI 用 `SmallTopAppBar`。
 *   网页浏览、课表这类信息密集页传 true，避免大标题挤占首屏。
 * @param content 参数一为标题栏需要给内容首项留的空白；参数二必须挂在滚动容器之前
 * （`Modifier.fillMaxSize().then(scrollModifier).verticalScroll(...)`）。
 */
@Composable
fun CollapsibleScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    expandedTopContent: @Composable () -> Unit = {},
    topBarColors: TopAppBarColors = transparentTopBarColors(),
    titleContent: (@Composable () -> Unit)? = null,
    subtitle: String = "",
    navigationContent: (@Composable () -> Unit)? = null,
    blurEnabled: Boolean = true,
    smallTitle: Boolean = false,
    content: @Composable (topInset: Dp, scrollModifier: Modifier) -> Unit,
) {
    if (LocalUiStyle.current == UiStyle.MIUI) {
        MiuixCollapsibleScaffold(
            title = title,
            modifier = modifier,
            onBack = onBack,
            actions = actions,
            expandedTopContent = expandedTopContent,
            subtitle = subtitle,
            navigationContent = navigationContent,
            blurEnabled = blurEnabled,
            smallTitle = smallTitle,
            content = content,
        )
    } else {
        MaterialCollapsibleScaffold(
            title = title,
            modifier = modifier,
            onBack = onBack,
            actions = actions,
            expandedTopContent = expandedTopContent,
            topBarColors = topBarColors,
            titleContent = titleContent,
            navigationContent = navigationContent,
            blurEnabled = blurEnabled,
            smallTitle = smallTitle,
            content = content,
        )
    }
}

@Composable
private fun MiuixCollapsibleScaffold(
    title: String,
    modifier: Modifier,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
    expandedTopContent: @Composable () -> Unit,
    subtitle: String,
    navigationContent: (@Composable () -> Unit)?,
    blurEnabled: Boolean,
    smallTitle: Boolean,
    content: @Composable (topInset: Dp, scrollModifier: Modifier) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val navigation: @Composable () -> Unit = { AppNavigationIcon(onBack, navigationContent) }
    // 模糊关闭（用户关掉开关或系统不支持）时顶栏要回到底色，否则透明顶栏会直接露出内容
    val blurOn = blurEnabled && isHeaderBlurOn()

    // 与 M3 分支共用 BlurScaffold：标题栏与内容必须重叠，内容才能滚到标题栏背后被 Haze 采样。
    // miuix 的 Scaffold 是「标题栏 + 内容」上下排列的结构，永远不会有可模糊的源
    BlurScaffold(
        modifier = modifier,
        blur = blurEnabled,
        // miuix 的页面底是 surface；用 M3 的 surfaceContainer 会和深色卡片撞成同一个颜色
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            if (smallTitle) {
                // 小标题：只有一行，不折叠；页面自带的展开区内容仍然挂在它底下
                AppSmallTopBar(
                    title = title,
                    subtitle = subtitle,
                    navigationIcon = navigation,
                    actions = actions,
                    bottomContent = { expandedTopContent() },
                    transparent = blurOn,
                )
            } else {
                MiuixTopBarForeground {
                    MiuixTopAppBar(
                        title = title,
                        // 展开时的大标题，滚动后收成上面那条
                        largeTitle = title,
                        subtitle = subtitle,
                        // 浮在模糊层上时底色必须让出来，否则整层 Haze 会被这块不透明底盖住
                        color = if (blurOn) Color.Transparent else MiuixTheme.colorScheme.surface,
                        navigationIcon = navigation,
                        actions = actions,
                        scrollBehavior = scrollBehavior,
                        // 同上：多段内容必须先收进一个 Column，否则会重叠
                        bottomContent = { Column { expandedTopContent() } },
                    )
                }
            }
        },
    ) { topInset ->
        // 内容铺满并作为模糊源；首项留白由页面自己按 topInset 承担
        // 顺带把 behavior 提供给下拉刷新，让下拉时大标题跟着回弹展开
        CompositionLocalProvider(LocalMiuixScrollBehavior provides scrollBehavior) {
            content(topInset, Modifier.nestedScroll(scrollBehavior.nestedScrollConnection))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MaterialCollapsibleScaffold(
    title: String,
    modifier: Modifier,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
    expandedTopContent: @Composable () -> Unit,
    topBarColors: TopAppBarColors,
    titleContent: (@Composable () -> Unit)?,
    navigationContent: (@Composable () -> Unit)?,
    blurEnabled: Boolean,
    smallTitle: Boolean,
    content: @Composable (topInset: Dp, scrollModifier: Modifier) -> Unit,
) {
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    val navigation: @Composable () -> Unit = { AppNavigationIcon(onBack, navigationContent) }

    BlurScaffold(
        modifier = modifier,
        blur = blurEnabled,
        topBar = {
            if (smallTitle) {
                // 小标题：定高一行，不折叠
                AppSmallTopBar(
                    title = title,
                    navigationIcon = navigation,
                    actions = actions,
                    bottomContent = { expandedTopContent() },
                    titleContent = titleContent,
                    colors = topBarColors,
                )
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LargeFlexibleTopAppBar(
                        title = {
                            if (titleContent != null) titleContent()
                            else Text(text = title)
                        },
                        navigationIcon = navigation,
                        actions = actions,
                        colors = topBarColors,
                        scrollBehavior = scrollBehavior,
                    )
                    expandedTopContent()
                }
            }
        },
    ) { topInset ->
        content(topInset, Modifier.nestedScroll(scrollBehavior.nestedScrollConnection))
    }
}
