@file:OptIn(ExperimentalMaterial3Api::class)

package edu.cqwu.electricity.hall.ui

import androidx.compose.ui.res.stringResource
import edu.cqwu.electricity.R

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import java.util.Locale
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.background
import coil.compose.AsyncImage
import coil.request.ImageRequest
import edu.cqwu.electricity.hall.data.HallCategory
import edu.cqwu.electricity.hall.data.HallItem
import edu.cqwu.electricity.hall.data.HallServiceLabel
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.theme.ui.ReLoginContent
import edu.cqwu.electricity.app.Routes
import edu.cqwu.electricity.theme.ui.LocalNavController
import kotlinx.coroutines.launch

/**
 * 大厅页面内容（不含 Scaffold / TopAppBar / BottomBar）。
 *
 * 内部使用 3 页 [HorizontalPager] 实现「全部」「收藏」「搜索」滑动切换。
 * 通过 [snapshotFlow] 监听 [PagerState.currentPage] 稳定页码，
 * 在滑动稳定后同步到 ViewModel 的 [selectTab]，触发收藏数据加载等逻辑。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HallPageContent(
    hallViewModel: HallViewModel = viewModel(),
) {
    val _hallPerfStart = System.currentTimeMillis()
    androidx.compose.runtime.SideEffect {
        android.util.Log.d("TabPerf", "HallPageContent composition done, elapsed=${System.currentTimeMillis() - _hallPerfStart}ms")
    }
    val uiState by hallViewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val hallPagerState = rememberPagerState(pageCount = { 3 })
    val snackbar = LocalSnackbarController.current
    val nav = LocalNavController.current

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
                val tabs = listOf(
                    stringResource(R.string.hall_tab_all),
                    stringResource(R.string.hall_tab_favorites),
                    stringResource(R.string.common_search),
                )
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
                            categories = uiState.categories,
                            isLoggedIn = uiState.isLoggedIn,
                            togglingAppId = uiState.togglingFavoriteAppId,
                            onItemClick = { item ->
                                val url = "https://ehall.cqwu.edu.cn/appShow?appId=${item.appId}"
                                nav.navigate(Routes.unifiedWebViewRoute(url, item.appName))
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
                                nav.navigate(Routes.unifiedWebViewRoute(url, item.appName))
                            },
                            onReLogin = { nav.navigate(Routes.LOGIN) },
                            onRetry = { hallViewModel.loadFavorites() },
                        )
                    }
                    2 -> {
                        // 「搜索」Tab
                        HallSearchTab(
                            uiState = uiState,
                            onQueryChange = { hallViewModel.setSearchQuery(it) },
                            onSearch = { hallViewModel.performSearch() },
                            onRoleSelect = { hallViewModel.selectRoleLabel(it) },
                            onCategorySelect = { hallViewModel.selectCategoryLabel(it) },
                            onItemClick = { item ->
                                val url = "https://ehall.cqwu.edu.cn/appShow?appId=${item.appId}"
                                nav.navigate(Routes.unifiedWebViewRoute(url, item.appName))
                            },
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════
// 「搜索」Tab 内容
// ═══════════════════════════════════════════

@Composable
private fun HallSearchTab(
    uiState: HallUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onRoleSelect: (String?) -> Unit,
    onCategorySelect: (String?) -> Unit,
    onItemClick: (HallItem) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        HallSearchInputRow(
            query = uiState.searchQuery,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
        )

        HallSearchLabelRow(
            labels = uiState.roleLabels,
            selectedLabelId = uiState.selectedRoleLabelId,
            onSelect = onRoleSelect,
        )

        HallSearchLabelRow(
            labels = uiState.categoryLabels,
            selectedLabelId = uiState.selectedCategoryLabelId,
            onSelect = onCategorySelect,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when {
                uiState.isSearchLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.searchError != null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = uiState.searchError,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                else -> {
                    FlatAppsList(
                        items = uiState.searchResults,
                        onItemClick = onItemClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun HallSearchInputRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.common_search)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            ),
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.common_clear_search),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        )
        IconButton(onClick = onSearch) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = stringResource(R.string.common_search),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun HallSearchLabelRow(
    labels: List<HallServiceLabel>,
    selectedLabelId: String?,
    onSelect: (String?) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") {
            HallSectionChip(
                text = stringResource(R.string.hall_tab_all),
                selected = selectedLabelId == null,
                onClick = { onSelect(null) },
            )
        }
        items(labels, key = { it.labelId }) { label ->
            HallSectionChip(
                text = label.lableName,
                selected = selectedLabelId == label.labelId,
                onClick = { onSelect(label.labelId) },
            )
        }
    }
}

// ═══════════════════════════════════════════
// 「全部」Tab 内容
// ═══════════════════════════════════════════

@Composable
private fun AllAppsList(
    categories: List<HallCategory>,
    isLoggedIn: Boolean,
    togglingAppId: String?,
    onItemClick: (HallItem) -> Unit,
    onFavoriteClick: ((HallItem) -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val sectionStartIndices = remember(categories) {
        var index = 0
        categories.map { category ->
            val start = index
            index += 1 + category.appList.size
            start
        }
    }
    val activeSectionIndex by remember(categories, sectionStartIndices) {
        derivedStateOf {
            val firstVisible = listState.firstVisibleItemIndex
            var active = 0
            sectionStartIndices.forEachIndexed { sectionIndex, start ->
                if (start <= firstVisible) active = sectionIndex
            }
            active
        }
    }
    val showIndexDivider by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (categories.isNotEmpty()) {
            HallSectionIndex(
                categories = categories,
                activeIndex = activeSectionIndex,
                showDivider = showIndexDivider,
                onSectionClick = { index ->
                    scope.launch {
                        listState.animateScrollToItem(sectionStartIndices[index])
                    }
                },
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            categories.forEach { category ->
                item(key = "header_${category.categoryId}") {
                    HallCategoryHeader(category.categoryName)
                }
                items(category.appList, key = { it.appId }) { item ->
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
    }
}

/**
 * 「全部」Tab 分类索引栏：固定显示在 Tab 栏下方，点击后平滑滚动到对应分类。
 */
@Composable
private fun HallSectionIndex(
    categories: List<HallCategory>,
    activeIndex: Int,
    showDivider: Boolean,
    onSectionClick: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(categories, key = { _, category -> category.categoryId }) { index, category ->
                HallSectionChip(
                    text = category.categoryName,
                    selected = activeIndex == index,
                    onClick = { onSectionClick(index) },
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 0.5.dp,
            )
        }
    }
}

@Composable
private fun HallSectionChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        border = null,
        label = {
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
private fun HallCategoryHeader(name: String) {
    Text(
        text = name,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
    )
}

/**
 * 收藏 Tab 的扁平应用列表。
 */
@Composable
private fun FlatAppsList(
    items: List<HallItem>,
    onItemClick: (HallItem) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items = items, key = { it.appId }) { item ->
            HallListItem(
                item = item,
                isLoggedIn = false,
                isTogglingFavorite = false,
                onClick = { onItemClick(item) },
                onFavoriteClick = null,
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
                    Text(stringResource(R.string.common_retry))
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
            FlatAppsList(
                items = items,
                onItemClick = onItemClick,
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
                        imageVector = if (item.favorite) Icons.Default.Favorite
                        else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (item.favorite) stringResource(R.string.hall_favorited) else stringResource(R.string.hall_unfavorited),
                        tint = if (item.favorite) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
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
        String.format(Locale.US, "%.1fk", value)
    } else {
        count.toString()
    }
}
