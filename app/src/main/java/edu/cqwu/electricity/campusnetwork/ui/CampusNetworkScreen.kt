package edu.cqwu.electricity.campusnetwork.ui

import androidx.annotation.StringRes
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.app.Routes
import edu.cqwu.electricity.theme.ui.LocalNavController
import edu.cqwu.electricity.theme.ui.currentTopBarColors

/**
 * 校园网络首页（入口页）。
 *
 * 布局参考一卡通服务平台（CardCenterScreen）的 3 列 × 2 行功能网格：
 * 使用 Column + Row 手动分 3 列，避免嵌套 LazyVerticalGrid 的无限高度约束问题。
 *
 * 本期已实现「接入者信息」「网速测试」，其余 4 格置灰「敬请期待」（未来功能位）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampusNetworkScreen(
    onBack: () -> Unit,
) {
    val nav = LocalNavController.current
    val topBarColors = currentTopBarColors()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.campus_network_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = topBarColors,
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "grid") {
                CampusNetworkGrid(
                    onItemClick = { item ->
                        when (item.action) {
                            CampusFeatureAction.ACCESSOR_INFO ->
                                nav.navigate(Routes.CAMPUS_NETWORK_ACCESSOR_INFO)
                            CampusFeatureAction.SPEED_TEST ->
                                nav.navigate(Routes.CAMPUS_NETWORK_SPEED_TEST)
                            CampusFeatureAction.PLACEHOLDER -> Unit
                        }
                    },
                )
            }
        }
    }
}

// ====================================================================
//  数据模型
// ====================================================================

/** 功能项点击动作 */
private enum class CampusFeatureAction {
    /** 接入者信息 */
    ACCESSOR_INFO,

    /** 网速测试 */
    SPEED_TEST,

    /** 敬请期待占位 */
    PLACEHOLDER,
}

/** 单个网格项数据 */
private data class CampusFeatureItem(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val action: CampusFeatureAction,
) {
    /** 是否可点击 */
    val enabled: Boolean = action != CampusFeatureAction.PLACEHOLDER
}

/** 3×2 网格：接入者信息 + 网速测试可用，其余为未来功能占位 */
private val campusFeatureItems = listOf(
    CampusFeatureItem(
        labelRes = R.string.campus_network_accessor_title,
        icon = Icons.Outlined.Badge,
        action = CampusFeatureAction.ACCESSOR_INFO,
    ),
    CampusFeatureItem(
        labelRes = R.string.speed_test_title,
        icon = Icons.Outlined.Speed,
        action = CampusFeatureAction.SPEED_TEST,
    ),
    CampusFeatureItem(
        labelRes = R.string.campus_network_coming_soon,
        icon = Icons.Outlined.HourglassEmpty,
        action = CampusFeatureAction.PLACEHOLDER,
    ),
    CampusFeatureItem(
        labelRes = R.string.campus_network_coming_soon,
        icon = Icons.Outlined.HourglassEmpty,
        action = CampusFeatureAction.PLACEHOLDER,
    ),
    CampusFeatureItem(
        labelRes = R.string.campus_network_coming_soon,
        icon = Icons.Outlined.HourglassEmpty,
        action = CampusFeatureAction.PLACEHOLDER,
    ),
    CampusFeatureItem(
        labelRes = R.string.campus_network_coming_soon,
        icon = Icons.Outlined.HourglassEmpty,
        action = CampusFeatureAction.PLACEHOLDER,
    ),
)

// ====================================================================
//  子组件
// ====================================================================

/** 功能网格容器：Column + Row 手动分 3 列（同 CardCenterScreen 的 CardGrid） */
@Composable
private fun CampusNetworkGrid(
    onItemClick: (CampusFeatureItem) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        campusFeatureItems.chunked(3).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowItems.forEach { item ->
                    CampusGridItemView(
                        item = item,
                        modifier = Modifier.weight(1f),
                        onClick = { onItemClick(item) },
                    )
                }
                // 补齐空位，使最后一行不足 3 个时保持布局一致
                repeat(3 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** 单个网格项 — 圆形图标 + 文字标签（占位项置灰不可点） */
@Composable
private fun CampusGridItemView(
    item: CampusFeatureItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (item.enabled) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (item.enabled) 1f else 0.45f)
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
                    imageVector = item.icon,
                    contentDescription = stringResource(item.labelRes),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 功能名称
            Text(
                text = stringResource(item.labelRes),
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
