package edu.cqwu.electricity.feeservicehall.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import edu.cqwu.electricity.R
import edu.cqwu.electricity.feeservicehall.data.FeeItem
import edu.cqwu.electricity.feeservicehall.data.FeeServiceHallApi
import edu.cqwu.electricity.feeservicehall.data.OrderRecord
import edu.cqwu.electricity.theme.ui.BottomSheetDialog
import edu.cqwu.electricity.theme.ui.LoadingDialog
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.theme.util.ToastUtils
import edu.cqwu.electricity.theme.ui.LocalTopBarState
import edu.cqwu.electricity.theme.ui.toTopAppBarColors
import kotlinx.coroutines.launch

private const val ORIGINAL_WEB_URL = "https://pay.cqwu.edu.cn/casLogin/"

private data class FeeServiceHallTab(val icon: ImageVector, @androidx.annotation.StringRes val labelRes: Int)

private val tabs = listOf(
    FeeServiceHallTab(Icons.Outlined.Home, R.string.fee_hall_tab_home),
    FeeServiceHallTab(Icons.AutoMirrored.Outlined.Assignment, R.string.fee_hall_tab_orders),
    FeeServiceHallTab(Icons.Outlined.Person, R.string.fee_hall_tab_profile),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FeeServiceHallScreen(
    onBack: () -> Unit,
    onNavigateToWebView: (url: String, title: String) -> Unit,
    viewModel: FeeServiceHallViewModel = viewModel(),
    initialTab: Int = 0,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val pagerState = rememberPagerState(initialPage = initialTab, pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    val topBarColors = LocalTopBarState.current.style.toTopAppBarColors(MaterialTheme.colorScheme)
    var selectedOrder by remember { mutableStateOf<OrderRecord?>(null) }
    val snackbar = LocalSnackbarController.current

    LaunchedEffect(Unit) { viewModel.loadIfNeeded() }

    // 监听关闭订单结果
    LaunchedEffect(uiState.closeOrderResult) {
        when (val result = uiState.closeOrderResult) {
            is CloseOrderResult.Success -> {
                snackbar.show(context.getString(R.string.fee_order_close_success), ToastUtils.Type.SUCCESS)
                selectedOrder = null
                viewModel.consumeCloseOrderResult()
            }
            is CloseOrderResult.Error -> {
                snackbar.show(context.getString(R.string.fee_order_close_failed, result.message), ToastUtils.Type.ERROR)
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
    BottomSheetDialog(
        visible = selectedOrder != null,
        onDismissRequest = { selectedOrder = null },
        fullscreen = true,
    ) {
        selectedOrder?.let { order ->
            OrderDetailContent(
                order = order,
                onContinuePayment = if (order.isPendingPayment && order.projectId != null) ({
                    val url = FeeServiceHallApi.buildContinuePaymentUrl(order.id, order.projectId!!)
                    onNavigateToWebView(url, context.getString(R.string.fee_order_continue_pay))
                    selectedOrder = null
                }) else null,
                onCloseOrder = if (order.isPendingPayment) ({
                    viewModel.closeOrder(order.id)
                }) else null,
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.fee_hall_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (pagerState.currentPage == 1) {
                        IconButton(onClick = { viewModel.toggleOrderFilter() }) {
                            Icon(
                                imageVector = Icons.Outlined.FilterList,
                                contentDescription = stringResource(R.string.common_filter),
                                tint = if (uiState.showOrderFilter) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = {
                        onNavigateToWebView(ORIGINAL_WEB_URL, context.getString(R.string.fee_hall_title))
                    }) {
                        Icon(Icons.Outlined.OpenInBrowser, contentDescription = stringResource(R.string.common_open_original_page))
                    }
                },
                colors = topBarColors,
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    val tabLabel = stringResource(tab.labelRes)
                    NavigationBarItem(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            if (pagerState.currentPage != index) {
                                scope.launch { pagerState.animateScrollToPage(index) }
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tabLabel) },
                        label = { Text(tabLabel) },
                    )
                }
            }
        },
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier.padding(innerPadding),
        ) { page ->
            LaunchedEffect(page) { viewModel.onTabSelected(page) }
            when (page) {
                0 -> FeeServiceHallHomeTab(
                    uiState = uiState,
                    onRefresh = { viewModel.refreshAll() },
                    onNavigateToWebView = onNavigateToWebView,
                )
                1 -> FeeServiceHallOrderTab(
                    uiState = uiState,
                    onRefresh = { viewModel.refreshOrders() },
                    onLoadMore = { viewModel.loadMoreOrders() },
                    onNavigateToOrderDetail = { order -> selectedOrder = order },
                    filterProjectName = uiState.filterProjectName,
                    filterStartDate = uiState.filterStartDate,
                    filterEndDate = uiState.filterEndDate,
                    showFilter = uiState.showOrderFilter,
                    onProjectNameChange = { viewModel.setOrderFilterProjectName(it) },
                    onStartDateChange = { viewModel.setOrderFilterStartDate(it) },
                    onEndDateChange = { viewModel.setOrderFilterEndDate(it) },
                    onApplyFilter = { viewModel.applyOrderFilter() },
                    onResetFilter = { viewModel.resetOrderFilter() },
                )
                2 -> FeeServiceHallProfileTab(
                    uiState = uiState,
                    onNavigateToWebView = onNavigateToWebView,
                )
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
    onRefresh: () -> Unit,
    onNavigateToWebView: (url: String, title: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.errorMessage != null && uiState.categories.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.fee_hall_load_failed, uiState.errorMessage ?: ""), style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error)
                }
            }
            uiState.categories.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.fee_hall_no_projects), style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
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

@Composable
private fun CategoryHeader(name: String) {
    Text(
        text = name, style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
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
        modifier = modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
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
