package edu.cqwu.electricity.ui.hall

import androidx.compose.ui.res.stringResource
import edu.cqwu.electricity.R

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.background
import coil.compose.AsyncImage
import coil.request.ImageRequest
import edu.cqwu.electricity.data.model.HallItem
import edu.cqwu.electricity.ui.components.LocalSnackbarController
import edu.cqwu.electricity.ui.components.ReLoginContent
import edu.cqwu.electricity.ui.theme.LocalTopBarState
import edu.cqwu.electricity.ui.theme.toTopAppBarColors
import kotlinx.coroutines.launch

/**
 * 大厅页面 TopAppBar，由 [MainTabScreen] 在 Scaffold.topBar 中调用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HallTopBar() {
    val topBarColors = LocalTopBarState.current.style.toTopAppBarColors(MaterialTheme.colorScheme)
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.hall_title),
                fontWeight = FontWeight.Bold,
            )
        },
        colors = topBarColors,
    )
}
/**
 * 大厅页面内容（不含 Scaffold / TopAppBar / BottomBar）。
 *
 * 内部使用 2 页 [HorizontalPager] 实现「全部」「收藏」滑动切换。
 * 通过 [snapshotFlow] 监听 [PagerState.currentPage] 稳定页码，
 * 在滑动稳定后同步到 ViewModel 的 [selectTab]，触发收藏数据加载等逻辑。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HallPageContent(
    onNavigateToWebView: (url: String, title: String) -> Unit,
    onNavigateToLogin: () -> Unit = {},
    hallViewModel: HallViewModel = viewModel(),
) {
    val uiState by hallViewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val hallPagerState = rememberPagerState(pageCount = { 2 })
    val snackbar = LocalSnackbarController.current

    // 收藏点击回调：仅触发 ViewModel 网络请求（不再乐观显示 Toast）
    val handleFavoriteClick: (HallItem) -> Unit = { item ->
        hallViewModel.toggleFavorite(item)
    }

    // ═══ 监听 snackbarEvent：服务器响应后显示结果 ═══
    LaunchedEffect(uiState.snackbarEvent) {
        uiState.snackbarEvent?.let { (message, type) ->
            snackbar.show(message, type)
            hallViewModel.clearSnackbarEvent()
        }
    }

    // ═══ 同步 Pager 当前页面到 ViewModel ═══
    // currentPage 在 Pager 滑动稳定后（动画结束）才会变化，
    // 用 LaunchedEffect 的 key 值变化自动触发回调，简洁可靠。
    LaunchedEffect(hallPagerState.currentPage) {
        hallViewModel.selectTab(hallPagerState.currentPage)
    }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = { hallViewModel.refresh() },
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ═══ 顶部 Tab 栏：全部 / 收藏（同步 PagerState）═══
            PrimaryTabRow(
                selectedTabIndex = hallPagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = {},
            ) {
                val tabs = listOf("全部", "收藏")
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = hallPagerState.currentPage == index,
                        onClick = {
                            scope.launch { hallPagerState.animateScrollToPage(index) }
                            hallViewModel.selectTab(index)
                        },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (hallPagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                    )
                }
            }

            // ═══ 内层 HorizontalPager：全部 / 收藏 滑动切换 ═══
            HorizontalPager(
                state = hallPagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                when (page) {
                    0 -> {
                        // 「全部」Tab
                        AllAppsList(
                            items = uiState.allItems,
                            isLoggedIn = uiState.isLoggedIn,
                            togglingAppId = uiState.togglingFavoriteAppId,
                            onItemClick = { item ->
                                val url = "https://ehall.cqwu.edu.cn/appShow?appId=${item.appId}"
                                onNavigateToWebView(url, item.appName)
                            },
                            onFavoriteClick = handleFavoriteClick,
                        )
                    }
                    1 -> {
                        // 「收藏」Tab
                        FavoriteAppsContent(
                            items = uiState.favoriteItems,
                            isLoading = uiState.isFavoriteLoading,
                            requiresReLogin = uiState.requiresReLogin,
                            errorMessage = uiState.errorMessage,
                            onItemClick = { item ->
                                val url = "https://ehall.cqwu.edu.cn/appShow?appId=${item.appId}"
                                onNavigateToWebView(url, item.appName)
                            },
                            onReLogin = onNavigateToLogin,
                            onRetry = { hallViewModel.loadFavorites() },
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════
// 「全部」Tab 内容
// ═══════════════════════════════════════════

@Composable
private fun AllAppsList(
    items: List<HallItem>,
    isLoggedIn: Boolean,
    togglingAppId: String?,
    onItemClick: (HallItem) -> Unit,
    onFavoriteClick: ((HallItem) -> Unit)? = null,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(
            items = items,
            key = { it.appId },
        ) { item ->
            HallListItem(
                item = item,
                isLoggedIn = isLoggedIn,
                isTogglingFavorite = togglingAppId == item.appId,
                onClick = { onItemClick(item) },
                onFavoriteClick = onFavoriteClick,
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 0.5.dp,
            )
        }
    }
}

// ═══════════════════════════════════════════
// 「收藏」Tab 内容
// ═══════════════════════════════════════════

@Composable
private fun FavoriteAppsContent(
    items: List<HallItem>,
    isLoading: Boolean,
    requiresReLogin: Boolean,
    errorMessage: String?,
    onItemClick: (HallItem) -> Unit,
    onReLogin: () -> Unit,
    onRetry: () -> Unit,
) {
    when {
        isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        requiresReLogin -> {
            ReLoginContent(
                errorMessage = errorMessage,
                requiresReLogin = true,
                onReLogin = onReLogin,
                onRetry = onRetry,
            )
        }
        errorMessage != null && items.isEmpty() -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onRetry) {
                    Text("重试")
                }
            }
        }
        items.isEmpty() -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.hall_no_favorites),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        else -> {
            AllAppsList(
                items = items,
                isLoggedIn = false,
                togglingAppId = null,
                onItemClick = onItemClick,
                onFavoriteClick = null,
            )
        }
    }
}

// ═══════════════════════════════════════════
// 列表项组件（「全部」和「收藏」共用）
// ═══════════════════════════════════════════

@Composable
private fun HallListItem(
    item: HallItem,
    isLoggedIn: Boolean,
    isTogglingFavorite: Boolean = false,
    onClick: () -> Unit,
    onFavoriteClick: ((HallItem) -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val context = LocalContext.current
        val imageRequest = remember(item.middleIcon) {
            ImageRequest.Builder(context)
                .data(item.middleIcon)
                .size(128)
                .crossfade(true)
                .build()
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = item.appName,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = item.appName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        // ═══ 右侧：收藏信息（仅登录态显示）═══
        if (isLoggedIn) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable(
                        enabled = onFavoriteClick != null && !isTogglingFavorite,
                        onClick = { onFavoriteClick?.invoke(item) },
                        // Modifier.clickable 默认带原生水波纹 ripple 动画
                    )
                    .padding(horizontal = 4.dp),
            ) {
                if (isTogglingFavorite) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(
                        text = formatFavoriteCount(item.favoriteCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = if (item.favorite) Icons.Filled.Favorite
                        else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (item.favorite) "已收藏" else "未收藏",
                        tint = if (item.favorite) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ═══════════════════════════════════════════
// 工具函数
// ═══════════════════════════════════════════

private fun formatFavoriteCount(count: Int): String {
    return if (count >= 1000) {
        val value = count.toDouble() / 1000.0
        String.format("%.1fk", value)
    } else {
        count.toString()
    }
}
