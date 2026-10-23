@file:OptIn(ExperimentalMaterial3Api::class)

package edu.cqwu.electricity.hall.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import edu.cqwu.electricity.R
import edu.cqwu.electricity.common.navigation.Routes
import edu.cqwu.electricity.common.settings.AppLanguage
import edu.cqwu.electricity.hall.data.HallCategory
import edu.cqwu.electricity.hall.data.HallI18n
import edu.cqwu.electricity.hall.data.HallItem
import edu.cqwu.electricity.hall.data.HallServiceLabel
import edu.cqwu.electricity.logging.AppLog
import edu.cqwu.electricity.common.navigation.LocalNavController
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.common.ui.BlurPullRefreshIndicator
import edu.cqwu.electricity.common.ui.ReLoginContent
import edu.cqwu.electricity.common.ui.SectionFilterChip
import edu.cqwu.electricity.common.ui.rememberHazeBlurModifier
import edu.cqwu.electricity.common.ui.rememberHazeSourceModifier
import kotlinx.coroutines.launch
import java.util.Locale

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
        AppLog.d("TabPerf", "HallPageContent composition done, elapsed=${System.currentTimeMillis() - _hallPerfStart}ms")
    }
    val uiState by hallViewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val hallPagerState = rememberPagerState(pageCount = { 3 })
    val snackbar = LocalSnackbarController.current
    val nav = LocalNavController.current

    // 大厅名称解析器：界面显示的是接口给的 appName / categoryName，
    // 而登录后服务端数据会整体替换本地快照，所以只能在显示处按 ID 查译文
    val context = LocalContext.current
    val languageKey = AppLanguage.currentI18nKey(context)
    val hallI18n = remember(languageKey) { HallI18n.load(context, languageKey) }

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

    // Haze 状态与顶栏高度：标题栏、索引栏共用；hallTopInset 还要用于胶囊跳转的对齐补偿
    val hallBlurState = remember { HazeState() }
    var hallHeaderHeight by remember { mutableIntStateOf(0) }
    val hallTopInset = with(LocalDensity.current) { hallHeaderHeight.toDp() }
    // 分类索引点击后要对齐到标题栏下沿（同首页）：列表从屏幕顶端铺满，直接对齐顶端会被浮层盖住
    val hallTopInsetPx = with(LocalDensity.current) { hallTopInset.roundToPx() }

    // 「全部」Tab 的分类索引状态提升到这里：索引栏要进标题栏浮层，才能和 Tab 行一起模糊
    val allListState = rememberLazyListState()
    val allSectionStartIndices = remember(uiState.categories) {
        var index = 0
        uiState.categories.map { category ->
            val start = index
            index += 1 + category.appList.size
            start
        }
    }
    val allActiveSectionIndex by remember(uiState.categories, allSectionStartIndices) {
        derivedStateOf {
            // 取「标题栏下沿以下第一个可见项」：胶囊跳转会刻意让上一项尾部留在标题栏背后，
            // 用 firstVisibleItemIndex 会高亮错一格
            val firstVisible = (
                allListState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.offset + it.size > hallTopInsetPx }?.index ?: 0
                ).let { if (it > 0) it - 1 else 0 }
            var active = 0
            allSectionStartIndices.forEachIndexed { sectionIndex, start ->
                if (start <= firstVisible) active = sectionIndex
            }
            active
        }
    }
    // 索引条下方细线：只要列表已经滚下去就显示。
    // firstVisibleItemIndex 里含一个顶栏留白占位项，按 index 判断会被推迟整整一个浮层高度
    val allShowIndexDivider by remember {
        derivedStateOf { allListState.canScrollBackward }
    }

    // 标题栏底色跟随「个性化 → 标题栏样式」，模糊开/关都由公共函数处理
    val headerModifier = rememberHazeBlurModifier(hallBlurState)

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(rememberHazeSourceModifier(hallBlurState))
        ) {
            val pullState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { hallViewModel.refresh() },
                state = pullState,
                indicator = { BlurPullRefreshIndicator(pullState, uiState.isRefreshing, hallTopInset) },
                modifier = Modifier.fillMaxSize(),
            ) {
                HorizontalPager(
                    state = hallPagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    when (page) {
                        0 -> {
                            // 「全部」Tab
                            AllAppsList(
                                categories = uiState.categories,
                                isLoggedIn = uiState.isLoggedIn,
                                togglingAppId = uiState.togglingFavoriteAppId,
                                hallI18n = hallI18n,
                                onItemClick = { item ->
                                    val url = "https://ehall.cqwu.edu.cn/appShow?appId=${item.appId}"
                                    nav.navigate(
                                        Routes.unifiedWebViewRoute(
                                            url,
                                            hallI18n.appName(item.appId, item.appName),
                                        )
                                    )
                                },
                                onFavoriteClick = handleFavoriteClick,
                                topInset = hallTopInset,
                                listState = allListState,
                            )
                        }
                        1 -> {
                            // 「收藏」Tab
                            FavoriteAppsContent(
                                items = uiState.favoriteItems,
                                isLoading = uiState.isFavoriteLoading,
                                requiresReLogin = uiState.requiresReLogin,
                                errorMessage = uiState.errorMessage,
                                hallI18n = hallI18n,
                                onItemClick = { item ->
                                    val url = "https://ehall.cqwu.edu.cn/appShow?appId=${item.appId}"
                                    nav.navigate(
                                        Routes.unifiedWebViewRoute(
                                            url,
                                            hallI18n.appName(item.appId, item.appName),
                                        )
                                    )
                                },
                                onReLogin = { nav.navigate(Routes.loginRoute()) },
                                onRetry = { hallViewModel.loadFavorites() },
                                topInset = hallTopInset,
                            )
                        }
                        2 -> {
                            // 「搜索」Tab：搜索框与胶囊已上移到标题栏浮层，这里只留结果列表
                            HallSearchTab(
                                uiState = uiState,
                                hallI18n = hallI18n,
                                onReLogin = { nav.navigate(Routes.loginRoute()) },
                                onRetry = { hallViewModel.loadSearchData() },
                                onItemClick = { item ->
                                    val url = "https://ehall.cqwu.edu.cn/appShow?appId=${item.appId}"
                                    nav.navigate(
                                        Routes.unifiedWebViewRoute(
                                            url,
                                            hallI18n.appName(item.appId, item.appName),
                                        )
                                    )
                                },
                                topInset = hallTopInset,
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                // 模糊/底色要连同状态栏一起铺满：放在 statusBarsPadding 外层，
                // 否则状态栏区域没有浮层覆盖，只能看到底下未模糊的内容
                .then(headerModifier)
                // 浮层实测总高（含状态栏），供列表首项留白
                .onSizeChanged { hallHeaderHeight = it.height }
                // 内容本身让开状态栏，不被状态栏图标压住
                .statusBarsPadding()
        ) {
            // ═══ 顶部 Tab 栏：全部 / 收藏 / 搜索（与标题栏一起模糊）═══
            PrimaryTabRow(
                selectedTabIndex = hallPagerState.currentPage,
                containerColor = Color.Transparent,
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

            // ═══ 分类索引栏（仅「全部」页）═══
            // 用 AnimatedVisibility 淡出 + 收起：左右滑动切换 Tab 时不会「一半就瞬间消失」，
            // 且浮层高度有动画，hallTopInset 平滑变化，列表留白不会跟着跳
            AnimatedVisibility(
                visible = hallPagerState.currentPage == 0 && uiState.categories.isNotEmpty(),
                enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(180)),
            ) {
                HallSectionIndex(
                    categories = uiState.categories,
                    hallI18n = hallI18n,
                    activeIndex = allActiveSectionIndex,
                    showDivider = allShowIndexDivider,
                    onSectionClick = { index ->
                        scope.launch {
                            // 负偏移 = 让目标分组停在标题栏下沿，正好露出分组标题
                            allListState.animateScrollToItem(
                                allSectionStartIndices[index] + 1,
                                -hallTopInsetPx,
                            )
                        }
                    },
                )
            }

            // ═══ 搜索页头部（搜索框 + 角色/分类筛选胶囊）═══
            AnimatedVisibility(
                visible = hallPagerState.currentPage == 2,
                enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(180)),
            ) {
                Column {
                    HallSearchInputRow(
                        query = uiState.searchQuery,
                        onQueryChange = { hallViewModel.setSearchQuery(it) },
                        onSearch = { hallViewModel.performSearch() },
                    )
                    HallSearchLabelRow(
                        labels = uiState.roleLabels,
                        selectedLabelId = uiState.selectedRoleLabelId,
                        onSelect = { hallViewModel.selectRoleLabel(it) },
                    )
                    HallSearchLabelRow(
                        labels = uiState.categoryLabels,
                        selectedLabelId = uiState.selectedCategoryLabelId,
                        onSelect = { hallViewModel.selectCategoryLabel(it) },
                    )
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
    hallI18n: HallI18n,
    onReLogin: () -> Unit,
    onRetry: () -> Unit,
    onItemClick: (HallItem) -> Unit,
    topInset: Dp = 0.dp,
) {
    Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.requiresReLogin -> {
                    ReLoginContent(
                        errorMessage = null,
                        requiresReLogin = true,
                        onReLogin = onReLogin,
                        onRetry = onRetry,
                    )
                }
                uiState.isSearchLoading -> {
                    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.searchError != null -> {
                    ReLoginContent(
                        errorMessage = uiState.searchError,
                        requiresReLogin = false,
                        onReLogin = onReLogin,
                        onRetry = onRetry,
                    )
                }
                else -> {
                    if (uiState.searchResults.isEmpty()) {
                        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.hall_no_search_results),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        FlatAppsList(
                            items = uiState.searchResults,
                            hallI18n = hallI18n,
                            onItemClick = onItemClick,
                            topInset = topInset,
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
            SectionFilterChip(
                text = stringResource(R.string.hall_tab_all),
                selected = selectedLabelId == null,
                onClick = { onSelect(null) },
            )
        }
        items(labels, key = { it.labelId }) { label ->
            SectionFilterChip(
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
    hallI18n: HallI18n,
    onItemClick: (HallItem) -> Unit,
    onFavoriteClick: ((HallItem) -> Unit)? = null,
    topInset: Dp = 0.dp,
    listState: LazyListState,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        item(key = "top_inset") { Spacer(Modifier.height(topInset)) }
        categories.forEach { category ->
            item(key = "header_${category.categoryId}") {
                HallCategoryHeader(hallI18n.categoryName(category.categoryId, category.categoryName))
            }
            items(
                category.appList,
                // 各分类展开在同一个 LazyColumn 里，key 只用 appId 时，
                // 服务端若把同一应用挂到多个分类就会重复 key 并崩溃；带分类前缀可避免
                key = { "${category.categoryId}_${it.appId}" },
            ) { item ->
                HallListItem(
                    item = item,
                    displayName = hallI18n.appName(item.appId, item.appName),
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

/**
 * 「全部」Tab 分类索引栏：固定显示在 Tab 栏下方，点击后平滑滚动到对应分类。
 */
@Composable
private fun HallSectionIndex(
    categories: List<HallCategory>,
    hallI18n: HallI18n,
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
                SectionFilterChip(
                    text = hallI18n.categoryName(category.categoryId, category.categoryName),
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
    hallI18n: HallI18n,
    onItemClick: (HallItem) -> Unit,
    topInset: Dp = 0.dp,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "top_inset") { Spacer(Modifier.height(topInset)) }
        // 收藏/搜索结果由服务端下发，同一 appId 重复出现会让 key 冲突而崩溃，这里按 appId 去重
        items(items = items.distinctBy { it.appId }, key = { it.appId }) { item ->
            HallListItem(
                item = item,
                displayName = hallI18n.appName(item.appId, item.appName),
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
    hallI18n: HallI18n,
    onItemClick: (HallItem) -> Unit,
    onReLogin: () -> Unit,
    onRetry: () -> Unit,
    topInset: Dp = 0.dp,
) {
    when {
        isLoading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
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
            ReLoginContent(
                errorMessage = errorMessage,
                requiresReLogin = false,
                onReLogin = onReLogin,
                onRetry = onRetry,
            )
        }
        items.isEmpty() -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
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
                hallI18n = hallI18n,
                onItemClick = onItemClick,
                topInset = topInset,
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
    displayName: String,
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
                contentDescription = displayName,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = displayName,
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
