package edu.cqwu.electricity.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 功能入口网格：`columns` 列等宽排布，行/列间距可调，末行不足时补空位保持对齐。
 *
 * 用 `Column + Row` 手动分列，**刻意不用 `LazyVerticalGrid`**：本组件总是嵌在
 * `LazyColumn` / `verticalScroll` 内，嵌套可滚动网格会遇到无限高度约束而崩溃
 * （项目内 `HomeScreen`、`BuildingSelectionScreen` 的注释记录过同一取舍）。
 *
 * 此前一卡通首页（`CardCenterScreen`）与校园网首页（`CampusNetworkScreen`）各有一份
 * 逐字相同的实现（3 列、行距 16dp、列距 12dp、同样的补空位逻辑），本组件即二者的合并。
 * 本组件只承载布局骨架，**不含任何业务数据类**，调用方通过 [itemContent] 决定每一项长什么样。
 *
 * @param columns 列数；默认 3（一卡通 / 校园网首页的既有效果）
 * @param rowSpacing 行间距；默认 16dp
 * @param columnSpacing 列间距；默认 12dp
 * @param itemContent 单项内容；第二个参数是已带 `weight(1f)` 的 Modifier，直接套在根布局上
 */
@Composable
fun <T> FeatureGrid(
    items: List<T>,
    modifier: Modifier = Modifier,
    columns: Int = 3,
    rowSpacing: Dp = 16.dp,
    columnSpacing: Dp = 12.dp,
    itemContent: @Composable (item: T, modifier: Modifier) -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(rowSpacing),
    ) {
        items.chunked(columns).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(columnSpacing),
            ) {
                rowItems.forEach { item ->
                    itemContent(item, Modifier.weight(1f))
                }
                // 补齐空位，使最后一行不足 columns 个时保持布局一致
                repeat(columns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * 网格项：圆形图标底 + 文字标签（一卡通 / 校园网首页的既有样式）。
 *
 * [enabled] 为 false 时整项置灰且不可点击，用于「敬请期待」占位项。
 */
@Composable
fun FeatureGridItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else 0.45f)
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 圆形图标背景（浅色）
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
