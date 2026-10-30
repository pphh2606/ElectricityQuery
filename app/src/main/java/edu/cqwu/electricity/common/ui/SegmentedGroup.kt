package edu.cqwu.electricity.common.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

/** 组首/组尾的外圆角（同 InstallerX 的 CornerRadius）。 */
internal val SegmentedOuterCorner = 16.dp

/** 组内相邻项的连接角（同 InstallerX 的 ConnectionRadius）。 */
internal val SegmentedInnerCorner = 5.dp

/** 项与项之间的缝隙，与 M3 的 SegmentedGap 一致。 */
internal val SegmentedGap = 2.dp

/** 组内某一项的四角圆角。 */
internal data class SegmentedCorners(
    val topStart: Dp,
    val topEnd: Dp,
    val bottomEnd: Dp,
    val bottomStart: Dp,
) {
    fun toShape(): Shape = RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)

    /** 按下时四角一起向外圆角过渡。 */
    fun pressed(progress: Float): SegmentedCorners = SegmentedCorners(
        topStart = lerp(topStart.value, SegmentedOuterCorner.value, progress).dp,
        topEnd = lerp(topEnd.value, SegmentedOuterCorner.value, progress).dp,
        bottomEnd = lerp(bottomEnd.value, SegmentedOuterCorner.value, progress).dp,
        bottomStart = lerp(bottomStart.value, SegmentedOuterCorner.value, progress).dp,
    )
}

/** 未分组（或只有一项）时的四角圆角。 */
private val SingleSegment = SegmentedCorners(
    topStart = SegmentedOuterCorner,
    topEnd = SegmentedOuterCorner,
    bottomEnd = SegmentedOuterCorner,
    bottomStart = SegmentedOuterCorner,
)

/** 当前项在分组中的圆角，由 [SegmentedGroup] 注入。 */
internal val LocalSegmentedCorners = staticCompositionLocalOf { SingleSegment }

/** 当前项在分组中的形状，由 [SegmentedGroup] 注入。 */
internal val LocalSegmentedShape = staticCompositionLocalOf<Shape> { RoundedCornerShape(SegmentedOuterCorner) }

/** 组内第 [index] 项（共 [count] 项）的圆角：首尾大圆角，中间连接角。 */
internal fun segmentedCorners(index: Int, count: Int): SegmentedCorners = when {
    count <= 1 -> SingleSegment
    index == 0 -> SingleSegment.copy(
        bottomEnd = SegmentedInnerCorner,
        bottomStart = SegmentedInnerCorner,
    )

    index == count - 1 -> SingleSegment.copy(
        topStart = SegmentedInnerCorner,
        topEnd = SegmentedInnerCorner,
    )

    else -> SegmentedCorners(
        topStart = SegmentedInnerCorner,
        topEnd = SegmentedInnerCorner,
        bottomEnd = SegmentedInnerCorner,
        bottomStart = SegmentedInnerCorner,
    )
}

/** [SegmentedGroup] 的作用域：一行一个 [item]，下标决定这一行在组内的形状。 */
class SegmentedGroupScope internal constructor() {
    internal val items = mutableListOf<@Composable () -> Unit>()

    fun item(content: @Composable () -> Unit) {
        items += content
    }
}

/**
 * 分段卡片容器：组首/组尾 16dp 外圆角、组内相邻项 5dp 连接角、项间 2dp 缝隙。
 *
 * 设置页（[SettingsGroup]）与底部弹窗（[SheetItemGroup]）共用这一套分段观感，
 * 只是前者传底色形成一张大卡、后者直接铺在弹窗背景上。
 */
@Composable
fun SegmentedGroup(
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    gap: Dp = SegmentedGap,
    content: @Composable SegmentedGroupScope.() -> Unit,
) {
    val scope = remember { SegmentedGroupScope() }
    scope.items.clear()
    scope.content()
    if (containerColor != null) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(SegmentedOuterCorner),
            color = containerColor,
        ) {
            SegmentedItems(scope.items, gap = gap)
        }
    } else {
        SegmentedItems(scope.items, modifier, gap)
    }
}

@Composable
private fun SegmentedItems(
    items: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
    gap: Dp = SegmentedGap,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        items.forEachIndexed { index, item ->
            val corners = segmentedCorners(index, items.size)
            CompositionLocalProvider(
                LocalSegmentedCorners provides corners,
                LocalSegmentedShape provides corners.toShape(),
            ) { item() }
        }
    }
}
