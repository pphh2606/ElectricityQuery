@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package edu.cqwu.electricity.common.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import edu.cqwu.electricity.R
import edu.cqwu.electricity.common.settings.LocalUiStyle
import edu.cqwu.electricity.common.settings.UiStyle
import edu.cqwu.electricity.theme.ui.currentTopBarColors
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar as MiuixSmallTopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * miuix 的顶栏不提供 [LocalContentColor]（miuix 组件普遍用显式 color 参数），
 * 于是 `actions` 里没写 tint 的 M3 [Icon] 会落到 Compose 的默认值黑色，
 * 夜间模式下就完全看不见了。这里把顶栏前景色补给顶栏内的图标，与标题同色。
 */
@Composable
internal fun MiuixTopBarForeground(
    contentColor: Color? = null,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalContentColor provides (contentColor ?: MiuixTheme.colorScheme.onSurface),
        content = content,
    )
}

/**
 * 标题栏返回按钮：MIUI 与 Material 3 各一套图标按钮，调用方只关心「有没有返回」。
 *
 * @param navigationContent 自定义导航区（比如「关闭」），优先于 [onBack]
 */
@Composable
fun AppNavigationIcon(
    onBack: (() -> Unit)?,
    navigationContent: (@Composable () -> Unit)? = null,
) {
    if (navigationContent != null) {
        navigationContent()
    } else if (onBack != null) {
        if (LocalUiStyle.current == UiStyle.MIUI) {
            MiuixIconButton(onClick = onBack) {
                MiuixIcon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                )
            }
        } else {
            IconButton(shapes = IconButtonDefaults.shapes(), onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                )
            }
        }
    }
}

/**
 * 标题栏配色三分支：指定底色 > 透明（浮在毛玻璃上）> 用户设置的标题栏样式。
 *
 * [AppTopBar]、[AppSmallTopBar] 与首页顶栏共用，避免同样的分支各写一遍。
 */
@Composable
fun appTopBarColors(
    containerColor: Color? = null,
    transparent: Boolean = false,
    contentColor: Color? = null,
): TopAppBarColors {
    if (containerColor != null) {
        val base = currentTopBarColors()
        return TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            scrolledContainerColor = containerColor,
            navigationIconContentColor = contentColor ?: base.navigationIconContentColor,
            titleContentColor = contentColor ?: base.titleContentColor,
            actionIconContentColor = contentColor ?: base.actionIconContentColor,
        )
    }
    return if (transparent) transparentTopBarColors() else currentTopBarColors()
}

/**
 * 单条顶栏：按 [LocalUiStyle] 在 Material 3 与 miuix 之间二选一。
 *
 * 只给「保留自己页面结构、顶栏又不是可折叠大标题」的页面用（课表、扫码），
 * 常规页面请走 [CollapsibleScaffold]。
 *
 * @param transparent 浮在模糊层上时传 true；M3 侧走 [transparentTopBarColors]
 * @param containerColor 指定底色（比如扫码的黑底），优先于 [transparent]
 * @param contentColor 指定前景色，未指定时用各主题的标准色
 */
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    transparent: Boolean = false,
    containerColor: Color? = null,
    contentColor: Color? = null,
) {
    AppSmallTopBar(
        title = title,
        navigationIcon = { AppNavigationIcon(onBack) },
        actions = actions,
        containerColor = containerColor,
        transparent = transparent,
        contentColor = contentColor,
    )
}

/**
 * 固定单行小标题栏：MIUI 用 miuix 的 `SmallTopAppBar`，Material 3 用定高 `TopAppBar`。
 *
 * [AppTopBar] 与 `CollapsibleScaffold(smallTitle = true)` 共用这一份实现，改样式只改这里。
 *
 * @param bottomContent 挂在标题栏下方的内容（tab 行、日期行等）；MIUI 侧会收进一个 Column，
 *   因为 miuix 的 bottomContent 是单子项槽位，多段直接放会叠在同一个位置
 * @param titleContent Material 3 侧自定义标题内容，未指定时用 [title]
 * @param colors Material 3 侧配色；MIUI 侧只有底色和前景色，由 [containerColor]、[transparent]、[contentColor] 决定
 */
@Composable
fun AppSmallTopBar(
    title: String,
    subtitle: String = "",
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: (@Composable () -> Unit)? = null,
    titleContent: (@Composable () -> Unit)? = null,
    containerColor: Color? = null,
    transparent: Boolean = false,
    contentColor: Color? = null,
    colors: TopAppBarColors = appTopBarColors(containerColor, transparent, contentColor),
) {
    if (LocalUiStyle.current == UiStyle.MIUI) {
        // 用 SmallTopAppBar：miuix 的 TopAppBar 默认会把 title 当大标题展开，
        // 这些页面要的是定高单行栏，大标题会平白吃掉首屏高度
        val miuixBottom: @Composable () -> Unit = {
            if (bottomContent != null) {
                Column { bottomContent() }
            }
        }
        MiuixTopBarForeground(contentColor) {
            MiuixSmallTopAppBar(
                title = title,
                subtitle = subtitle,
                navigationIcon = navigationIcon,
                actions = actions,
                bottomContent = miuixBottom,
                color = containerColor
                    ?: if (transparent) Color.Transparent else MiuixTheme.colorScheme.surface,
                titleColor = contentColor ?: MiuixTheme.colorScheme.onSurface,
            )
        }
        return
    }

    Column {
        TopAppBar(
            title = {
                if (titleContent != null) {
                    titleContent()
                } else if (subtitle.isNotEmpty()) {
                    Column {
                        Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            navigationIcon = navigationIcon,
            actions = actions,
            colors = colors,
        )
        if (bottomContent != null) bottomContent()
    }
}
