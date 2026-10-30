package edu.cqwu.electricity.settings.ui

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.common.settings.LocalUiStyle
import edu.cqwu.electricity.common.settings.UiStyle
import edu.cqwu.electricity.common.ui.LocalSegmentedShape
import edu.cqwu.electricity.common.ui.SegmentedGroup
import edu.cqwu.electricity.common.ui.SegmentedGroupScope
import edu.cqwu.electricity.common.ui.SegmentedOuterCorner
import top.yukonga.miuix.kmp.basic.BasicComponent as MiuixBasicComponent
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 设置分组：一张 16dp 圆角大卡，行间 2dp，首尾行 16dp 外角、中间行 5dp 连接角。
 *
 * 分段形状由 [SegmentedGroup] 统一注入，底部弹窗的 [SheetItemGroup] 共用同一套计算。
 */
@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable SegmentedGroupScope.() -> Unit,
) {
    if (LocalUiStyle.current == UiStyle.MIUI) {
        // MIUI：一张白色大卡包住所有行，行与行紧贴（没有分段圆角，也没有 2dp 缝隙）
        MiuixCard(
            modifier = modifier.fillMaxWidth(),
            cornerRadius = 16.dp,
            insideMargin = PaddingValues(0.dp),
        ) {
            SegmentedGroup(modifier = Modifier.fillMaxWidth(), gap = 0.dp, content = content)
        }
        return
    }
    SegmentedGroup(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        content = content,
    )
}

/** 组内的非行内容（分段按钮、展开面板等）：给它组内形状与行底色。 */
@Composable
internal fun SettingsGroupBlock(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (LocalUiStyle.current == UiStyle.MIUI) {
        // MIUI 下底色已经由外层大卡提供，这里只要一块不裁角的容器
        Box(modifier = modifier.fillMaxWidth()) { content() }
        return
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(LocalSegmentedShape.current)
            .background(MaterialTheme.colorScheme.surfaceBright),
    ) { content() }
}

/** 可进入下级页面的行尾箭头，颜色比正文淡一档。 */
@Composable
internal fun TrailingChevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
    )
}

/** 可进入下级页面的设置行：图标 + 标题 + 副标题 + 右侧箭头。 */
@Composable
internal fun SettingsNavigationRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    SettingsListItem(
        icon = icon,
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        trailing = { TrailingChevron() },
    )
}

/**
 * M3 1.5 默认用会过冲的 spring 插值圆角，按压中途反转时可能把圆角推成负值；换成 tween 保证收敛。
 * 同 InstallerX 的 `ListItemMotionScheme`。
 */
private class PressMotionScheme(delegate: MotionScheme) : MotionScheme by delegate {
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = tween(durationMillis = 180)
}

@Composable
private fun ListItemMotion(content: @Composable () -> Unit) {
    val motionScheme = MaterialTheme.motionScheme
    val pressMotionScheme = remember(motionScheme) { PressMotionScheme(motionScheme) }
    MaterialTheme(motionScheme = pressMotionScheme, content = content)
}

/**
 * 设置行统一实现：M3 [ListItem] + 前导/标题/副标题/尾随内容，图标与文字整行垂直居中。
 *
 * 形状由所在 [SettingsGroup] 注入。用可交互重载（[onClick] 非空）时，按压会走
 * [ListItemDefaults.shapes] 的形状形变——行临时变成 16dp 圆角矩形，这就是 InstallerX 里
 * 「按住时圆角变化」的来源（不是缩放）。
 * [onClick] 为空（只读或置灰）时用静态重载，避免出现点了没反应的涟漪。
 * [alpha] 用于「系统不支持」的置灰，不必再写一套置灰颜色。
 * [icon] 是 [leading] 的便捷写法；需要色块、单选圈等自定义前导内容时传 [leading]。
 * [supporting] 覆盖默认的 [subtitle] 文本，用于多行或着色的副内容。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsListItem(
    title: String,
    onClick: (() -> Unit)?,
    subtitle: String? = null,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    supporting: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    alpha: Float = 1f,
    titleColor: Color? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val containerColor = MaterialTheme.colorScheme.surfaceBright
    val baseShape = LocalSegmentedShape.current
    val headline: @Composable () -> Unit = {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = (titleColor ?: MaterialTheme.colorScheme.onSurface).copy(alpha = alpha),
        )
    }
    val leadingBlock: (@Composable () -> Unit)? = leading ?: icon?.let {
        {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
    }
    val supportingBlock: (@Composable () -> Unit)? = supporting ?: subtitle?.let {
        {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        }
    }
    // ListItem 的最小高度固定为 56dp/72dp，不随系统字体缩放；按 fontScale 放大，双行基准取 80dp。
    // MIUI 的基准更紧（单行 56、双行 72），miuix 自己也是这个量级，不要拿 M3 的 80dp 套过去。
    val twoLine = subtitle != null || supporting != null
    val fontScale = LocalDensity.current.fontScale
    val minHeight = (if (twoLine) 80.dp else 56.dp) * fontScale
    val miuixMinHeight = (if (twoLine) 72.dp else 56.dp) * fontScale

    if (LocalUiStyle.current == UiStyle.MIUI) {
        // MIUI 的设置项前面不摆装饰性图标（系统设置和 installerx 都是纯文字），
        // 所以只放调用方显式传入的 leading——单选圈、色块、头像这类属于功能或信息，不能省。
        MiuixSettingsRow(
            title = title,
            onClick = onClick,
            subtitle = subtitle,
            leadingBlock = leading,
            supportingBlock = supportingBlock,
            trailing = trailing,
            enabled = enabled,
            alpha = alpha,
            titleColor = titleColor,
            minHeight = miuixMinHeight,
        )
        return
    }

    val rowModifier = Modifier
        .fillMaxWidth()
        .heightIn(min = minHeight)
    val shapes = ListItemDefaults.shapes(
        shape = baseShape,
        pressedShape = RoundedCornerShape(SegmentedOuterCorner),
        selectedShape = baseShape,
        focusedShape = baseShape,
        hoveredShape = baseShape,
    )

    if (onClick != null) {
        ListItemMotion {
            ListItem(
                onClick = onClick,
                modifier = rowModifier,
                enabled = enabled,
                verticalAlignment = Alignment.CenterVertically,
                leadingContent = leadingBlock,
                trailingContent = trailing,
                supportingContent = supportingBlock,
                shapes = shapes,
                colors = ListItemDefaults.colors(containerColor = containerColor),
                elevation = ListItemDefaults.elevation(),
                content = headline,
            )
        }
    } else {
        // 只读行用 disabled 态的可交互重载：仍能拿到 verticalAlignment 的居中排布，
        // 但没有点击涟漪（disabled 的 clickable 不出涟漪），也不被读屏当成可点。
        ListItem(
            onClick = {},
            modifier = rowModifier,
            enabled = false,
            verticalAlignment = Alignment.CenterVertically,
            leadingContent = leadingBlock,
            trailingContent = trailing,
            supportingContent = supportingBlock,
            shapes = shapes,
            colors = ListItemDefaults.colors(
                containerColor = containerColor,
                disabledContainerColor = containerColor,
            ),
            content = headline,
        )
    }
}

/**
 * 设置行的 MIUI 实现。
 *
 * 普通行交给 miuix 的 [MiuixBasicComponent]——行高、字体、按压反馈都是 MIUI 调好的；
 * 带自定义副内容（[supportingBlock]）或自定义标题色的行它表达不出来，退回自绘一行。
 */
@Composable
private fun MiuixSettingsRow(
    title: String,
    onClick: (() -> Unit)?,
    subtitle: String?,
    leadingBlock: (@Composable () -> Unit)?,
    supportingBlock: (@Composable () -> Unit)?,
    trailing: (@Composable () -> Unit)?,
    enabled: Boolean,
    alpha: Float,
    titleColor: Color?,
    minHeight: Dp,
) {
    if (supportingBlock != null || titleColor != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .then(
                    if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .alpha(alpha),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingBlock?.let {
                it()
                Spacer(Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = titleColor ?: MaterialTheme.colorScheme.onSurface,
                )
                supportingBlock?.invoke()
            }
            trailing?.invoke()
        }
        return
    }

    // 单行项不传 summary：miuix 的 BasicComponent 对空串也会渲染出一行，
    // 白白把 56dp 的行撑到 68dp；改用只画标题的内容重载，高度交回组件自己的最小值。
    if (subtitle.isNullOrEmpty()) {
        MiuixBasicComponent(
            modifier = Modifier.alpha(alpha),
            startAction = leadingBlock,
            endActions = { trailing?.invoke() },
            onClick = onClick ?: {},
            enabled = enabled && onClick != null,
        ) {
            MiuixText(
                text = title,
                style = MiuixTheme.textStyles.headline1,
            )
        }
        return
    }

    MiuixBasicComponent(
        modifier = Modifier.alpha(alpha),
        title = title,
        summary = subtitle,
        enabled = enabled && onClick != null,
        onClick = onClick ?: {},
        startAction = leadingBlock,
        endActions = { trailing?.invoke() },
    )
}
