package edu.cqwu.electricity.common.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

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
            // 先裁圆角再挂点击，水波纹才会跟着圆角走（Card 的 shape 只管背景与边框）
            .clip(RoundedCornerShape(16.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(16.dp),
        // 无阴影：containerColor 与背景同为 surface，轮廓由 1dp 描边提供（低版本阴影会退化成方块）
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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

/**
 * 服务格子：**圆角方底 + 内嵌彩色图标 + 居中小字**，规格与项目首页的图标项一致
 * （`home/ui/HomeScreen.kt` 的 `AppIconItem` / `AppIconBox`：44dp 方底、圆角 8dp、
 * `surfaceVariant` 底色、图标内边距 8dp、文字 `bodySmall`）。
 *
 * 与 [FeatureGridItem] 的区别：那个是"整卡 + 圆形灰底图标 + 单行文字"，用于一卡通/校园网首页；
 * 这个用于教务首页的服务网格（图二是项目的既有观感）。
 *
 * [iconUrl] 由调用方拼好完整地址（服务端返回的是相对路径）；文字保留两行——教务服务名较长
 * （如"个人学业监测报告"），单行会被截断。
 *
 * @param fallbackIcon 没有图标地址时改用这个内置矢量图标（教务的「更多服务」格子即此用法），
 *   传 null 则只显示空的圆角方底
 */
@Composable
fun ServiceIconTile(
    iconUrl: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fallbackIcon: ImageVector? = null,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIconBox(
            iconUrl = iconUrl,
            size = 44.dp,
            padding = 8.dp,
            contentDescription = label,
            fallbackIcon = fallbackIcon,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 通用的应用图标方框：`surfaceVariant` 圆角底 + 内边距包裹的图片。
 *
 * 原先只有首页 `HomeScreen` 内部一份（其分类图标项与「我的服务」图标项共用），
 * 本文件的服务格子需要同一规格，故上移到公共层；[size] / [padding] 由调用方按各自设计给。
 *
 * @param fallbackIcon 没有图标地址时改用这个内置矢量图标（教务的「更多服务」格子即此用法），
 *   传 null 则只显示空的圆角方底
 */
@Composable
fun AppIconBox(
    iconUrl: String,
    size: Dp,
    padding: Dp,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    fallbackIcon: ImageVector? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (fallbackIcon != null && iconUrl.isBlank()) {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val context = LocalContext.current
            val imageRequest = remember(iconUrl) {
                ImageRequest.Builder(context)
                    .data(iconUrl)
                    .size(128)
                    .crossfade(true)
                    .build()
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
