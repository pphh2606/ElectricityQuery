@file:OptIn(ExperimentalMaterial3Api::class)

package edu.cqwu.electricity.jwxt.moreservice.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.cqwu.electricity.R
import edu.cqwu.electricity.common.navigation.Routes
import edu.cqwu.electricity.common.ui.AppIconBox
import edu.cqwu.electricity.common.ui.ReLoginContent
import edu.cqwu.electricity.jwxt.core.JwxtConstants
import edu.cqwu.electricity.jwxt.core.ui.JwxtChipLabel
import edu.cqwu.electricity.jwxt.core.ui.JwxtLabelChips
import edu.cqwu.electricity.common.navigation.LocalNavController
import edu.cqwu.electricity.theme.ui.currentTopBarColors

/**
 * 「更多服务」二级页（教务首页九宫格里「更多服务」格子的落地页）。
 *
 * 数据来自 `biz/home/listLabelServiceSet`：分组（全部 / 查询 / 申请）→ 分类（学籍 / 选课 / …）→ 服务，
 * 比首页接口多一层分类。列表观感对齐缴费服务大厅「主页」tab：分类标题 + 图标条目 + 细分割线；
 * 顶部 chip 只切换分组，不做滚动联动。
 *
 * 点击服务与「打开网页版」都走内置浏览器——与教务首页一致，本轮不本地化子应用。
 */
@Composable
fun JwxtMoreServiceScreen(
    onBack: () -> Unit,
    viewModel: JwxtMoreServiceViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val nav = LocalNavController.current
    val title = stringResource(R.string.jwxt_more_service)

    val openWeb: (String, String) -> Unit = { url, name ->
        nav.navigate(Routes.unifiedWebViewRoute(url, name))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
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
                actions = {
                    // 打开网页版：保留一条回到教务原网页的退路（与教务首页、缴费大厅顶栏一致）
                    IconButton(onClick = { openWeb(JwxtConstants.MORE_SERVICE_URL, title) }) {
                        Icon(
                            imageVector = Icons.Outlined.OpenInBrowser,
                            contentDescription = stringResource(R.string.common_web_version),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = currentTopBarColors(),
            )
        },
    ) { paddingValues ->
        when {
            uiState.requiresReLogin -> ReLoginContent(
                requiresReLogin = true,
                onReLogin = { nav.navigate(Routes.loginRoute()) },
                modifier = Modifier.padding(paddingValues),
            )

            uiState.errorMessage != null -> ReLoginContent(
                errorMessage = uiState.errorMessage,
                requiresReLogin = false,
                onReLogin = {},
                onRetry = { viewModel.load() },
                modifier = Modifier.padding(paddingValues),
            )

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                // 分组 chip 固定在顶栏下方：它只切换下面的列表内容，不随列表滚动
                JwxtLabelChips(
                    labels = uiState.groups.map { JwxtChipLabel(it.name, it.count) },
                    selectedIndex = uiState.selectedGroupIndex,
                    onSelect = { viewModel.selectGroup(it) },
                )

                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = { viewModel.load(isRefresh = true) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    when {
                        uiState.isLoading -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }

                        uiState.currentCategories.isEmpty() -> Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.jwxt_more_service_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        else -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            uiState.currentCategories.forEach { category ->
                                item(key = "header_${category.name}") {
                                    CategoryHeader(category.name)
                                }
                                items(
                                    items = category.services,
                                    key = { "${category.name}_${it.name}" },
                                ) { service ->
                                    ServiceRow(service) { openWeb(service.pageUrl, service.name) }
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        thickness = 0.5.dp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 分类标题（学籍 / 选课 / …）；规格对齐缴费服务大厅主页 tab */
@Composable
private fun CategoryHeader(name: String) {
    Text(
        text = name,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    )
}

/** 一条服务：图标方底 + 名称 + 右箭头 */
@Composable
private fun ServiceRow(service: JwxtMoreServiceItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconBox(
            iconUrl = service.iconUrl,
            size = SERVICE_ICON_SIZE,
            padding = SERVICE_ICON_PADDING,
            contentDescription = service.name,
            // 接口没给图标时用内置图标兜底，避免只剩一个空方底
            fallbackIcon = Icons.Outlined.Apps,
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = service.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 图标方底规格与缴费服务大厅主页 tab 的条目一致 */
private val SERVICE_ICON_SIZE = 44.dp
private val SERVICE_ICON_PADDING = 8.dp
