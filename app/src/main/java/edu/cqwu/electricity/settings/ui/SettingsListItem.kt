package edu.cqwu.electricity.settings.ui

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** 组首/组尾的外圆角（同 InstallerX 的 CornerRadius），也是按压形变的目标形状。 */
private val OuterCorner = 16.dp

/** 组内相邻行的连接圆角（同 InstallerX 的 ConnectionRadius）。 */
private val InnerCorner = 5.dp

/** 行与行之间的缝隙，M3 的 SegmentedGap 同为 2dp。 */
private val SegmentedGap = 2.dp

/** 行在所属分组里的基础形状，由 [SettingsGroup] 按下标注入。 */
internal val LocalSettingsItemShape = staticCompositionLocalOf<Shape> { RoundedCornerShape(OuterCorner) }

private fun itemShape(index: Int, count: Int): Shape = when {
    count <= 1 -> RoundedCornerShape(OuterCorner)
    index == 0 -> RoundedCornerShape(
        topStart = OuterCorner, topEnd = OuterCorner, bottomEnd = InnerCorner, bottomStart = InnerCorner,
    )
    index == count - 1 -> RoundedCornerShape(
        topStart = InnerCorner, topEnd = InnerCorner, bottomEnd = OuterCorner, bottomStart = OuterCorner,
    )
    else -> RoundedCornerShape(InnerCorner)
}

/** [SettingsGroup] 的作用域：一行一个 [item]，下标决定这一行在组内的形状。 */
class SettingsGroupScope internal constructor() {
    internal val items = mutableListOf<@Composable () -> Unit>()

    fun item(content: @Composable () -> Unit) {
        items += content
    }
}

/**
 * 设置分组：一张 16dp 圆角大卡，行间 2dp，组首/组尾行 16dp 外角、中间行 5dp 连接角，
 * 与 InstallerX 的 `CornerRadius` / `ConnectionRadius` 分段观感一致；按压时行变成 16dp
 * 圆角矩形，形变在同色卡面里清晰可见。
 * 取代原先「Surface + Column 堆行」的写法，形状改由分组按下标统一分配。
 */
@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable SettingsGroupScope.() -> Unit,
) {
    val scope = remember { SettingsGroupScope() }
    scope.items.clear()
    scope.content()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OuterCorner),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SegmentedGap),
        ) {
            scope.items.forEachIndexed { index, item ->
                CompositionLocalProvider(
                    LocalSettingsItemShape provides itemShape(index, scope.items.size),
                ) { item() }
            }
        }
    }
}

/** 组内的非行内容（分段按钮、展开面板等）：给它组内形状与行底色。 */
@Composable
internal fun SettingsGroupBlock(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(LocalSettingsItemShape.current)
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
    val baseShape = LocalSettingsItemShape.current
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
    val twoLine = subtitle != null || supporting != null
    val minHeight = (if (twoLine) 80.dp else 56.dp) * LocalDensity.current.fontScale
    val rowModifier = Modifier
        .fillMaxWidth()
        .heightIn(min = minHeight)
    val shapes = ListItemDefaults.shapes(
        shape = baseShape,
        pressedShape = RoundedCornerShape(OuterCorner),
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
