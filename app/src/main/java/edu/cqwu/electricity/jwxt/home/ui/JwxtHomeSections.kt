package edu.cqwu.electricity.jwxt.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import edu.cqwu.electricity.common.ui.FeatureGrid
import edu.cqwu.electricity.common.ui.ServiceIconTile
import edu.cqwu.electricity.jwxt.core.ui.JwxtChipLabel
import edu.cqwu.electricity.jwxt.core.ui.JwxtLabelChips

/**
 * 教务首页的服务区、公告行与场景卡。
 *
 * 从 `JwxtHomeScreen.kt` 拆出：页面骨架留在那边，这里只放各"区块"的实现。
 * 分类 chip 行由首页与更多服务页共用，已抽到 `jwxt/core/ui/JwxtLabelChips.kt`。
 */

// ═══════════════════════════════════════════
//  服务区（分类 chip + 网格）
// ═══════════════════════════════════════════

/**
 * 服务区：分类 chip 行 + 当前分类的 4 列服务网格。
 *
 * chip 与网格是**同一个模块**，一起滚动；当前分类由 chip 高亮表示，
 * 所以不再单独显示分类标题（那会和 chip 上的文字重复）。
 *
 * 布局复用 [FeatureGrid]（项目里明确不用 `LazyVerticalGrid`，嵌在 `LazyColumn` 里会因无限高度
 * 约束崩溃）；单项用 [ServiceIconTile]，「更多服务」靠它的 `fallbackIcon` 用内置矢量图标。
 */
@Composable
internal fun JwxtServiceSection(
    tabs: List<JwxtLabelTab>,
    selectedIndex: Int,
    onSelectLabel: (Int) -> Unit,
    items: List<JwxtGridItem>,
    moreServiceLabel: String,
    onItemClick: (JwxtGridItem) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        JwxtLabelChips(
            labels = tabs.map { JwxtChipLabel(it.name, it.count) },
            selectedIndex = selectedIndex,
            onSelect = onSelectLabel,
        )

        FeatureGrid(
            items = items,
            columns = GRID_COLUMNS,
            rowSpacing = 8.dp,
            columnSpacing = 4.dp,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) { item, itemModifier ->
            ServiceIconTile(
                iconUrl = item.iconUrl,
                label = if (item.isMore) moreServiceLabel else item.name,
                onClick = { onItemClick(item) },
                modifier = itemModifier,
                // 「更多服务」没有服务端图标，用内置矢量图标
                fallbackIcon = if (item.isMore) Icons.Outlined.Apps else null,
            )
        }
    }
}

// ═══════════════════════════════════════════
//  公告行
// ═══════════════════════════════════════════

/**
 * 公告入口行：设置页选项的样式（图标 + 标题 + 箭头，无副标题）。点击进教务通知中心。
 *
 * 只用「公告信息」这样的入口名，不对"有没有公告"表态——本轮不调公告接口，写死状态文案会误导。
 * 样式对齐设置模块的 `PersonalizationScreen.SettingRow`（去掉副标题），卡片底色同为 `surfaceContainerLow`。
 */
@Composable
internal fun JwxtNoticeRow(title: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(16.dp),
        // 显式关掉阴影：MD3 Card 默认带 1dp，本页是扁平风格（与 Tab 卡一致）
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            // clickable 必须放在 Card 内部：涟漪算作 Card 的内容，会被它的圆角裁成圆角反馈；
            // 挂到 Card 的 modifier 上（外层）则画在容器之外，是直角矩形、与圆角边缘对不上
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

// ═══════════════════════════════════════════
//  场景卡片（图片大卡）
// ═══════════════════════════════════════════

/**
 * 场景卡片：一张大图铺满卡片，标题与「详情」按钮悬浮在图片左侧，**整张卡片都是点击热区**。
 *
 * 尺寸与层级照搬网页 CSS（抓包里 `widget-StudentRecommendSceneBulletin` 的 chunk.css）：
 * `.recommendWrapper` 高 128px；`.background` 左 11px、宽 `100vw - 22px`、圆角 8px；
 * `.contentWrapper` 垂直居中且左内边距 17px；`.title` 宽 7em；`.button` 圆角 14px。
 *
 * 与网页的两处差异（均为需求决定）：网页用 Swiper 自动轮播，这里只保留手动左右滑；
 * 网页只有「详情」按钮可点，这里整卡可点。
 */
@Composable
internal fun JwxtSceneRow(
    scenes: List<JwxtSceneUi>,
    detailLabel: String,
    onDetailClick: (JwxtSceneUi) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { scenes.size })

    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxWidth()
            .height(SCENE_CARD_HEIGHT),
        // 左右各留 11dp，使每页宽度等于「屏宽 - 22dp」，与网页 .background 的宽度一致
        contentPadding = PaddingValues(horizontal = SCENE_CARD_SIDE_MARGIN),
        // 相邻两卡之间的间隔：网页里每张图左右各留 11px，滑动到两张之间时就是 22px
        pageSpacing = SCENE_CARD_SPACING,
    ) { page ->
        val scene = scenes[page]

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(SCENE_CARD_CORNER))
                .clickable { onDetailClick(scene) },
        ) {
            // 拉伸填满，与网页 `<img>` 行为一致（原图 702×256，卡片比例更宽，构图与网页相同）
            AsyncImage(
                model = scene.iconUrl,
                contentDescription = scene.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )

            // 悬浮层：垂直居中 + 左侧 17dp
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = SCENE_CONTENT_START),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = scene.name,
                    modifier = Modifier.width(SCENE_TITLE_WIDTH),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    // 固定深色：卡片图是固定的浅色插画，跟随主题会在深色模式下变成白字而看不清
                    color = COLOR_SCENE_TITLE,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(SCENE_BUTTON_TOP_GAP))

                // 视觉上是按钮，但点击统一由整卡处理：不再嵌套 clickable，避免按钮吞掉卡片点击
                Text(
                    text = detailLabel,
                    modifier = Modifier
                        .clip(RoundedCornerShape(SCENE_BUTTON_CORNER))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

private const val GRID_COLUMNS = 4

// 场景卡片尺寸，全部取自网页 CSS（`widget-StudentRecommendSceneBulletin` 的 chunk.css）
// 首页首屏加载时也用 SCENE_CARD_HEIGHT 做空白占位，故为 internal
internal val SCENE_CARD_HEIGHT = 128.dp        // .recommendWrapper height:128px
private val SCENE_CARD_SIDE_MARGIN = 11.dp    // .background left:11px → 宽度 100vw - 22px
private val SCENE_CARD_SPACING = 22.dp        // 相邻两卡的间距 = 左右各 11px 相加（网页滑动到两张之间时）
/** 场景卡标题固定色：卡片图是固定的浅色插画，跟随主题会在深色模式下变白 → 压在图上读不清 */
private val COLOR_SCENE_TITLE = Color(0xFF121212)
// 圆角不跟随网页（那边是 8px）：与页面其它卡片（公告行 / Tab 卡）统一为 16dp
private val SCENE_CARD_CORNER = 16.dp
private val SCENE_CONTENT_START = 17.dp       // .contentWrapper padding-left:17px
private val SCENE_TITLE_WIDTH = 140.dp        // .title width:7em（21px 字号下约 147px）
private val SCENE_BUTTON_TOP_GAP = 5.dp       // .buttonWrapper margin-top:5px
private val SCENE_BUTTON_CORNER = 14.dp       // .button border-radius:14px
