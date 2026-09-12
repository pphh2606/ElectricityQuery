package edu.cqwu.electricity.campusnetwork

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.app.Routes
import edu.cqwu.electricity.common.ui.FeatureGrid
import edu.cqwu.electricity.common.ui.FeatureGridItem
import edu.cqwu.electricity.theme.ui.LocalNavController
import edu.cqwu.electricity.theme.ui.currentTopBarColors

/**
 * 校园网络首页（入口页）。
 *
 * 布局为 3 列 × 2 行功能网格，骨架与网格项统一由 `common/ui` 的
 * [edu.cqwu.electricity.common.ui.FeatureGrid] / [FeatureGridItem] 提供
 * （与一卡通服务平台首页共用同一份实现，网格自身用 Column + Row 手动分列）。
 *
 * 本期已实现「接入者信息」「网速测试」「网络服务」，其余 3 格置灰「敬请期待」（未来功能位）。
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
                // 网格骨架与网格项统一由 common/ui 提供（与一卡通首页同款）
                FeatureGrid(items = campusFeatureItems) { feature, itemModifier ->
                    FeatureGridItem(
                        icon = feature.icon,
                        label = stringResource(feature.labelRes),
                        enabled = feature.enabled,
                        onClick = {
                            when (feature.action) {
                                CampusFeatureAction.ACCESSOR_INFO ->
                                    nav.navigate(Routes.CAMPUS_NETWORK_ACCESSOR_INFO)
                                CampusFeatureAction.SPEED_TEST ->
                                    nav.navigate(Routes.CAMPUS_NETWORK_SPEED_TEST)
                                CampusFeatureAction.PORTAL_SERVICE ->
                                    nav.navigate(Routes.CAMPUS_NETWORK_PORTAL_SERVICE)
                                CampusFeatureAction.PLACEHOLDER -> Unit
                            }
                        },
                        modifier = itemModifier,
                    )
                }
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

    /** 网络服务（认证网关会话） */
    PORTAL_SERVICE,

    /** 敬请期待占位 */
    PLACEHOLDER,
}

/** 单个网格项数据 */
private data class CampusFeatureItem(
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
    val action: CampusFeatureAction,
) {
    /** 是否可点击 */
    val enabled: Boolean = action != CampusFeatureAction.PLACEHOLDER
}

/** 3×2 网格：接入者信息 + 网速测试 + 网络服务可用，其余为未来功能占位 */
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
        labelRes = R.string.portal_service_title,
        icon = Icons.Outlined.Router,
        action = CampusFeatureAction.PORTAL_SERVICE,
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

// 网格容器（FeatureGrid）与网格项（FeatureGridItem）已上提到 common/ui，
// 与一卡通首页共用同一份实现，本文件不再保留私有副本。

