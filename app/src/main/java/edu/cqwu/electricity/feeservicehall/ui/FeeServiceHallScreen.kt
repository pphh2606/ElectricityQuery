@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package edu.cqwu.electricity.feeservicehall.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import edu.cqwu.electricity.R
import edu.cqwu.electricity.common.settings.LocalAppSettingsState
import edu.cqwu.electricity.common.settings.ReduceMotion
import edu.cqwu.electricity.common.ui.AppCircularProgressIndicator
import edu.cqwu.electricity.common.ui.BlurPullRefreshIndicator
import edu.cqwu.electricity.common.ui.AppNavigationBarItem
import edu.cqwu.electricity.common.ui.BlurScaffold
import edu.cqwu.electricity.common.ui.BottomBarOverlay
import edu.cqwu.electricity.common.ui.BottomSheetDialogV2
import edu.cqwu.electricity.common.ui.ListStatsRow
import edu.cqwu.electricity.common.ui.LoadingDialog
import edu.cqwu.electricity.common.ui.ReLoginContent
import edu.cqwu.electricity.common.ui.SectionFilterChip
import edu.cqwu.electricity.common.ui.ShowBottomBarOnChange
import edu.cqwu.electricity.common.ui.transparentTopBarColors
import edu.cqwu.electricity.common.ui.rememberBottomBarScrollState
import edu.cqwu.electricity.common.ui.trackBottomBarScroll
import edu.cqwu.electricity.feeservicehall.data.FeeItem
import edu.cqwu.electricity.feeservicehall.data.FeeServiceHallApi
import edu.cqwu.electricity.feeservicehall.data.OrderRecord
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.common.util.ToastUtils
import kotlinx.coroutines.launch

private const val ORIGINAL_WEB_URL = "https://pay.cqwu.edu.cn/casLogin/"

private data class FeeServiceHallTab(val icon: ImageVector, @androidx.annotation.StringRes val labelRes: Int)

private val tabs = listOf(
    FeeServiceHallTab(Icons.Outlined.Home, R.string.fee_hall_tab_home),
    FeeServiceHallTab(Icons.AutoMirrored.Outlined.Assignment, R.string.fee_hall_tab_orders),
    FeeServiceHallTab(Icons.Outlined.Person, R.string.fee_hall_tab_profile),
)

/** 首页分类区块，itemCount 用于计算 LazyColumn 中分类头的起始索引 */
private data class FeeHallSection(
    val id: String,
    val name: String,
    val itemCount: Int,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FeeServiceHallScreen(
    onBack: () -> Unit,
    onNavigateToWebView: (url: String, title: String) -> Unit,
    onReLogin: () -> Unit = {},
    viewModel: FeeServiceHallViewModel = viewModel(),
    initialTab: Int = 0,
) {
    val resources = LocalResources.current
    val uiState by viewModel.uiState.collectAsState()
    val pagerState = rememberPagerState(initialPage = initialTab, pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    val topBarColors = transparentTopBarColors()
    var selectedOrder by remember { mutableStateOf<OrderRecord?>(null) }
    val snackbar = LocalSnackbarController.current

    LaunchedEffect(Unit) { viewModel.loadIfNeeded() }

    // 监听关闭订单结果
    LaunchedEffect(uiState.closeOrderResult) {
        when (val result = uiState.closeOrderResult) {
            is CloseOrderResult.Success -> {
                snackbar.show(resources.getString(R.string.fee_order_close_success), ToastUtils.Type.SUCCESS)
                selectedOrder = null
                viewModel.consumeCloseOrderResult()
            }
            is CloseOrderResult.Error -> {
                snackbar.show(resources.getString(R.string.fee_order_close_failed, result.message), ToastUtils.Type.ERROR)
                viewModel.consumeCloseOrderResult()
            }
            null -> {}
        }
    }

    // 关闭订单 LoadingDialog
    if (uiState.isClosingOrder) {
        LoadingDialog(message = stringResource(R.string.fee_order_closing))
    }

    // 订单详情底部弹窗
    BottomSheetDialogV2(
        visible = selectedOrder != null,
        onDismissRequest = { selectedOrder = null },
    ) {
        selectedOrder?.let { order ->
            OrderDetailContent(
                order = order,
                onContinuePayment = if (order.isPendingPayment && order.projectId != null) ({
                    val url = FeeServiceHallApi.buildContinuePaymentUrl(order.id, order.projectId!!)
                    onNavigateToWebView(url, resources.getString(R.string.fee_order_continue_pay))
                    selectedOrder = null
                }) else null,
                onCloseOrder = if (order.isPendingPayment) ({
                    viewModel.closeOrder(order.id)
                }) else null,
            )
        }
    }

    // ── 底栏滚动隐藏（与首页三个 tab 共用同一套组件）──
    val appSettings = LocalAppSettingsState.current
    val animateBar = appSettings.reduceMotion != ReduceMotion.ON
    val barScrollState = rememberBottomBarScrollState()

    // 切 tab 后底栏必须回到可见，否则用户找不到导航；从子页面返回时的那一次不算
    ShowBottomBarOnChange(barScrollState, pagerState.currentPage)

    // ── 首页 Tab 的分类索引状态提升到页面级 ──
    // 索引条要挂进标题栏浮层才能和标题栏一起模糊、固定不滚动，状态就必须在这里持有。
    // 列表首项是顶栏留白占位，所以索引下标、当前分类都要减 1。
    val homeListState = rememberLazyListState()

    // 顶栏浮层实测高度（由下方 content lambda 回传）：
    // 分类索引跳转要停在标题栏下沿，否则分组标题会藏进浮层背后
    var headerInset by remember { mutableStateOf(0.dp) }
    val headerInsetPx = with(LocalDensity.current) { headerInset.roundToPx() }
    val sections = remember(uiState.categories) {
        uiState.categories.mapNotNull { category ->
            val items = category.children?.filter { it.type == "2" } ?: emptyList()
            if (items.isEmpty()) null
            else FeeHallSection(id = category.id, name = category.name, itemCount = items.size)
        }
    }
    val sectionStartIndices = remember(sections) {
        var index = 0
        sections.map { section ->
            val start = index
            index += 1 + section.itemCount
            start
        }
    }
    val activeSectionIndex by remember(sections, sectionStartIndices) {
        derivedStateOf {
            // 取「标题栏下沿以下第一个可见项」：胶囊跳转会刻意让上一项尾部留在标题栏背后，
            // 用 firstVisibleItemIndex 会高亮错一格
            val firstVisible = (
                homeListState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.offset + it.size > headerInsetPx }?.index ?: 0
                ).let { if (it > 0) it - 1 else 0 }
            var active = 0
            sectionStartIndices.forEachIndexed { sectionIndex, start ->
                if (start <= firstVisible) active = sectionIndex
            }
            active
        }
    }
    val showIndexDivider by remember {
        derivedStateOf { homeListState.canScrollBackward }
    }

    // 外层 Box 只负责给底栏提供覆盖层与滚动判定：底栏不再占用布局，内容区高度恒定
    Box(
        modifier = Modifier.trackBottomBarScroll(barScrollState),
    ) {
    BlurScaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.fee_hall_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (pagerState.currentPage == 1) {
                        IconButton(shapes = IconButtonDefaults.shapes(), onClick = { viewModel.toggleOrderFilter() }) {
                            Icon(
                                imageVector = Icons.Outlined.FilterList,
                                contentDescription = stringResource(R.string.common_filter),
                                tint = if (uiState.showOrderFilter) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = {
                        onNavigateToWebView(ORIGINAL_WEB_URL, resources.getString(R.string.fee_hall_title))
                    }) {
                        Icon(Icons.Outlined.OpenInBrowser, contentDescription = stringResource(R.string.common_open_original_page))
                    }
                },
                colors = topBarColors,
            )

            // 首页 Tab 的分类索引条：挂进标题栏浮层，和标题栏一起模糊、固定不滚动
            AnimatedVisibility(
                visible = pagerState.currentPage == 0 &&
                    !uiState.isLoading && uiState.errorMessage == null && sections.isNotEmpty(),
                enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(180)),
            ) {
                FeeServiceHallSectionIndex(
                    sections = sections,
                    activeIndex = activeSectionIndex,
                    showDivider = showIndexDivider,
                    onSectionClick = { index ->
                        scope.launch {
                            // +1 跳过列表首项（顶栏留白占位）；负偏移让分组停在标题栏下沿
                            homeListState.animateScrollToItem(
                                sectionStartIndices[index] + 1,
                                -headerInsetPx,
                            )
                        }
                    },
                )
            }

            // 订单 Tab 筛选面板：同样挂进浮层，展开时浮层变高、模糊区域跟着长
            AnimatedVisibility(
                visible = pagerState.currentPage == 1 && uiState.showOrderFilter,
                enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(180)),
            ) {
                OrderFilterPanel(
                    projectName = uiState.filterProjectName,
                    onProjectNameChange = { viewModel.setOrderFilterProjectName(it) },
                    startDate = uiState.filterStartDate,
                    onStartDateChange = { viewModel.setOrderFilterStartDate(it) },
                    endDate = uiState.filterEndDate,
                    onEndDateChange = { viewModel.setOrderFilterEndDate(it) },
                    onApply = { viewModel.applyOrderFilter() },
                    onReset = { viewModel.resetOrderFilter() },
                )
            }

            // 订单 Tab 统计行（有数据时）：出现/收起动画由 ListStatsRow 内部负责，
            // 切到「我的」页时也不会让标题栏和列表跟着跳一下
            ListStatsRow(
                loadedCount = uiState.orders.size,
                currentPage = uiState.orderPageCurrent,
                totalPages = uiState.orderTotalPages,
                visible = pagerState.currentPage == 1 && uiState.orders.isNotEmpty(),
            )
        },
    ) { topInset ->
        // 回传给上方浮层的分类索引跳转：需要知道标题栏（+索引条）实际占了多高
        SideEffect { headerInset = topInset }

        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            LaunchedEffect(page) { viewModel.onTabSelected(page) }
            when (page) {
                0 -> FeeServiceHallHomeTab(
                    uiState = uiState,
                    listState = homeListState,
                    topInset = topInset,
                    onRefresh = { viewModel.refreshAll() },
                    onReLogin = onReLogin,
                    onNavigateToWebView = onNavigateToWebView,
                )
                1 -> FeeServiceHallOrderTab(
                    uiState = uiState,
                    topInset = topInset,
                    onRefresh = { viewModel.refreshOrders() },
                    onLoadMore = { viewModel.loadMoreOrders() },
                    onNavigateToOrderDetail = { order -> selectedOrder = order },
                    onReLogin = onReLogin,
                )
                2 -> FeeServiceHallProfileTab(
                    uiState = uiState,
                    topInset = topInset,
                    onNavigateToWebView = onNavigateToWebView,
                    onReLogin = onReLogin,
                    onRetryProfile = { viewModel.retryProfileLoad() },
                )
            }
        }
    }

        // 底栏：浮在内容之上，隐藏时向下滑出且不拦截触摸
        BottomBarOverlay(
            state = barScrollState,
            animate = animateBar,
        ) {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    val selected = pagerState.currentPage == index
                    AppNavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                scope.launch { pagerState.animateScrollToPage(index) }
                            }
                        },
                        icon = tab.icon,
                        label = stringResource(tab.labelRes),
                    )
                }
            }
        }
    }
}


// ═══════════════════════════════════════════
//  主页 Tab
// ═══════════════════════════════════════════

@Composable
private fun FeeServiceHallHomeTab(
    uiState: FeeServiceHallUiState,
    listState: LazyListState,
    topInset: Dp,
    onRefresh: () -> Unit,
    onReLogin: () -> Unit,
    onNavigateToWebView: (url: String, title: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = onRefresh,
        state = pullState,
        indicator = { BlurPullRefreshIndicator(pullState, uiState.isRefreshing, topInset) },
        modifier = modifier.fillMaxSize(),
    ) {
        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AppCircularProgressIndicator()
                }
            }
            uiState.errorMessage != null && uiState.categories.isEmpty() -> {
                ReLoginContent(
                    errorMessage = stringResource(R.string.fee_hall_load_failed, uiState.errorMessage ?: ""),
                    requiresReLogin = uiState.requiresReLogin,
                    onReLogin = onReLogin,
                    onRetry = onRefresh,
                )
            }
            uiState.categories.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.fee_hall_no_projects), style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    item(key = "top_inset") { Spacer(Modifier.height(topInset)) }
                    uiState.categories.forEach { category ->
                        val items = category.children?.filter { it.type == "2" } ?: emptyList()
                        if (items.isNotEmpty()) {
                            item(key = "header_${category.id}") {
                                CategoryHeader(category.name)
                            }
                            items(items = items, key = { it.id }) { item ->
                                FeeProjectItem(
                                    item = item,
                                    onClick = {
                                        val url = FeeServiceHallApi.buildPaymentUrl(item.proModelUrl, item.id)
                                        onNavigateToWebView(url, item.name)
                                    },
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 缴费服务大厅首页分类索引栏：固定显示在标题栏下方，点击后平滑滚动到对应分类。
 */
@Composable
private fun FeeServiceHallSectionIndex(
    sections: List<FeeHallSection>,
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
            itemsIndexed(sections, key = { _, section -> section.id }) { index, section ->
                SectionFilterChip(
                    text = section.name,
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
private fun CategoryHeader(name: String) {
    Text(
        text = name, style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    )
}

@Composable
private fun FeeProjectItem(item: FeeItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FeeProjectIcon(imgUrl = item.imgUrl, contentDescription = item.name)
        Spacer(Modifier.width(12.dp))
        Text(item.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FeeProjectIcon(imgUrl: String?, contentDescription: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (imgUrl != null) {
            AsyncImage(
                model = imgUrl, contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize().padding(8.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit,
            )
        } else {
            Icon(Icons.Outlined.Receipt, contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
        }
    }
}
