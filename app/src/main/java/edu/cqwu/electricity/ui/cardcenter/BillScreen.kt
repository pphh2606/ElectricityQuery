package edu.cqwu.electricity.ui.cardcenter

import androidx.compose.ui.res.stringResource
import edu.cqwu.electricity.R
import edu.cqwu.electricity.ui.components.DatePickerField

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.data.model.BillPageInfo
import edu.cqwu.electricity.ui.components.LocalSnackbarController
import edu.cqwu.electricity.ui.components.ReLoginContent
import edu.cqwu.electricity.ui.theme.LocalTopBarState
import edu.cqwu.electricity.ui.theme.toTopAppBarColors
import edu.cqwu.electricity.util.ToastUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 账单页面 — 本地化 UI
 *
 * 支持标签页切换（点击 TabRow 或左右滑动 HorizontalPager）、筛选、分页、下拉刷新。
 *
 * TabRow 与 HorizontalPager 双向绑定：
 * - TabRow 点击 → pagerState.animateScrollToPage()
 * - 滑动 → LaunchedEffect(pagerState.currentPage) 同步 switchTab()
 *
 * 状态管理和业务逻辑已迁移至 [BillViewModel]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillScreen(
    viewModel: BillViewModel,
    onBack: () -> Unit,
    onNavigateToWebView: (url: String, title: String) -> Unit,
    onReLogin: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    val topBarColors = LocalTopBarState.current.style.toTopAppBarColors(MaterialTheme.colorScheme)

    // 网页版账单 URL（在原生实现出问题时作为降级方案）
    val webBillUrl = "http://218.194.176.214:8382/epay/thirdapp/bill"

    // 标签页定义（pageIndex 0→tabNo=1, 1→2, 2→4, 3→5）
    val tabLabelKeys = listOf(R.string.bill_tab_all, R.string.bill_tab_unpaid, R.string.bill_tab_success, R.string.bill_tab_failed)
    val tabTabNo = listOf(1, 2, 4, 5)

    // ── HorizontalPager 状态（左右滑动切换 Tab）──
    // 使用 initialPage 确保从 WebView 返回后 Pager 与 ViewModel 的 activeTab 同步
    val scope = rememberCoroutineScope()
    val initialPage = tabTabNo.indexOf(uiState.activeTab).coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { tabLabelKeys.size }
    )
    // 每个 tab 独立维护 LazyListState，保持各自的滚动位置
    val listStates: List<LazyListState> = remember { List(tabLabelKeys.size) { LazyListState() } }

    // ── 稳定化 onClick 回调 ──
    val stableOnNavigateToWebView by rememberUpdatedState(onNavigateToWebView)
    val detailTitleStr = stringResource(R.string.bill_detail_title)
    val onRecordClick = remember<(edu.cqwu.electricity.data.model.BillRecord) -> Unit> {
        { record ->
            if (record.detailUrl.isNotBlank()) {
                stableOnNavigateToWebView(
                    viewModel.getBillDetailUrl(record.detailUrl),
                    detailTitleStr
                )
            }
        }
    }

    // ── 收集单次事件 ──

    // pager 滑动时同步 ViewModel 中的 tab（UI → ViewModel）
    LaunchedEffect(pagerState.currentPage) {
        val pageTabNo = tabTabNo[pagerState.currentPage]
        if (uiState.activeTab != pageTabNo) {
            viewModel.switchTab(pageTabNo)
        }
    }

    // ViewModel 的 activeTab 变化时同步 Pager（ViewModel → UI）
    // 例如从 WebView 返回后恢复状态时，Pager 跟随切换到对应页面
    // 增加 !isScrollInProgress 防止用户滑动时发生竞争
    LaunchedEffect(uiState.activeTab) {
        val targetPage = tabTabNo.indexOf(uiState.activeTab).coerceAtLeast(0)
        if (pagerState.currentPage != targetPage && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    // 显示 Snackbar 消息
    val snackbarController = LocalSnackbarController.current
    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collectLatest { message ->
            snackbarController.show(message, ToastUtils.Type.ERROR)
        }
    }

    // 加载完成后滚动到顶部
    LaunchedEffect(Unit) {
        viewModel.scrollToTop.collectLatest {
            listStates[pagerState.currentPage].animateScrollToItem(0)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.bill_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    val webTitle = stringResource(R.string.bill_web_title)
                    IconButton(onClick = { onNavigateToWebView(webBillUrl, webTitle) }) {
                        Icon(
                            imageVector = Icons.Outlined.OpenInBrowser,
                            contentDescription = stringResource(R.string.common_web_version),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { viewModel.toggleFilterPanel() }) {
                        Icon(
                            imageVector = Icons.Outlined.FilterList,
                            contentDescription = stringResource(R.string.common_filter),
                            tint = if (uiState.showFilterPanel)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = topBarColors
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // ── 标签页切换栏（点击 → animateScrollToPage）──
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier = Modifier.fillMaxWidth(),
                divider = {},
            ) {
                tabLabelKeys.forEachIndexed { index, key ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        text = {
                            Text(
                                text = stringResource(key),
                                fontWeight = if (pagerState.currentPage == index)
                                    FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // ── 筛选面板 ──
            AnimatedVisibility(
                visible = uiState.showFilterPanel,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                FilterPanel(
                    searchQuery = uiState.tempSearchQuery,
                    onSearchQueryChange = { viewModel.onSearchQueryChange(it) },
                    startDate = uiState.tempStartDate,
                    onStartDateChange = { viewModel.onStartDateChange(it) },
                    endDate = uiState.tempEndDate,
                    onEndDateChange = { viewModel.onEndDateChange(it) },
                    incomeChecked = uiState.tempIncome,
                    onIncomeCheckedChange = { viewModel.onIncomeCheckedChange(it) },
                    expenseChecked = uiState.tempExpense,
                    onExpenseCheckedChange = { viewModel.onExpenseCheckedChange(it) },
                    onApply = { viewModel.applyFilter() },
                    onReset = { viewModel.resetFilter() }
                )
            }

            // ── HorizontalPager 内容区域 ──
            // 每个 page 从 tabCache 读取自己的数据，避免预加载时显示其他 tab 的错误数据。
            // 无缓存时回退到 billPageInfo（仅对当前 activeTab 有效）。
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { pageIndex ->
                val pageTabNo = tabTabNo[pageIndex]
                // 从 tabCache 取该 page 独立的数据，无缓存时返回 null（显示空数据/加载态）
                val pageInfo = uiState.tabCache[pageTabNo]
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing && pageTabNo == uiState.activeTab,
                    onRefresh = { viewModel.loadBills(isRefresh = true) },
                    modifier = Modifier.fillMaxSize()
                ) {
                   if (uiState.perTabLoading[pageTabNo] == true) {
                       // ⭐ 全屏居中加载指示器（在 LazyColumn 外部实现真正的垂直居中）
                       Box(
                           modifier = Modifier.fillMaxSize(),
                           contentAlignment = Alignment.Center
                       ) {
                           BillLoadingContent(uiState.perTabElapsed[pageTabNo] ?: 0L)
                       }
                   } else {
                       Column(modifier = Modifier.fillMaxSize()) {
                           // ⭐ 固定统计行（不随 LazyColumn 滚动）
                           if (pageInfo != null && pageInfo.records.isNotEmpty()) {
                               BillStatsRow(pageInfo)
                           }
                           LazyColumn(
                               state = listStates[pageIndex],
                               modifier = Modifier.fillMaxSize().weight(1f),
                               contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                               verticalArrangement = Arrangement.spacedBy(0.dp)
                           ) {
                               when {
                                   // 逐 Tab 错误状态（后台 Tab 加载失败时的轻量提示）
                                   uiState.perTabError[pageTabNo] != null -> {
                                       item(key = "tab_error") {
                                           BillTabErrorContent(uiState.perTabError[pageTabNo] ?: stringResource(R.string.common_load_failed))
                                       }
                                   }
                                   // 错误状态仅当前活跃 Tab 显示
                                   uiState.errorMessage != null && pageTabNo == uiState.activeTab -> {
                                       item(key = "error") {
                                           ReLoginContent(
                                               errorMessage = uiState.errorMessage,
                                               requiresReLogin = uiState.requiresReLogin,
                                               onReLogin = onReLogin,
                                               onRetry = { viewModel.loadBills() },
                                           )
                                       }
                                   }
                                   // 无数据（无缓存且不在加载中）
                                   pageInfo == null -> {
                                       item(key = "no_data") {
                                           BillEmptyDataContent()
                                       }
                                   }
                                   else -> {
                                       if (pageInfo.records.isEmpty()) {
                                           item(key = "empty_list") {
                                               BillEmptyListContent(pageTabNo)
                                           }
                                       } else {
                                           items(pageInfo.records, key = { it.billNo }) { record ->
                                               BillRecordCard(
                                                   record = record,
                                                   onClick = { onRecordClick(record) }
                                               )
                                           }
                                       }
                                       item(key = "footer") {
                                           BillFooterContent(uiState, pageInfo, viewModel)
                                       }
                                   }
                               }
                           }

                           // ── 自动加载下一页（滑到最后 3 项时触发）──
                           // 使用 derivedStateOf 检测滚动位置，所有相关状态都加入 key
                           // 避免因 key 不完整导致切换 Tab 后底部卡在"上滑加载更多"不再触发的问题
                           val autoLoadListState = listStates[pageIndex]
                           val shouldLoadMore by remember {
                               derivedStateOf {
                                   val layoutInfo = autoLoadListState.layoutInfo
                                   val totalItems = layoutInfo.totalItemsCount
                                   val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                   totalItems > 0 && lastVisibleIndex >= totalItems - 8
                               }
                           }
                           LaunchedEffect(
                               pageTabNo, uiState.activeTab, pageInfo?.hasNext,
                               shouldLoadMore, uiState.isLoadingMore
                           ) {
                               if (shouldLoadMore && pageInfo?.hasNext == true && !uiState.isLoadingMore
                                   && pageTabNo == uiState.activeTab) {
                                   viewModel.loadNextPage()
                               }
                           }
                       }
                   }
                }
            }
        }
    }
}

// ====================================================================
//  筛选面板
// ====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterPanel(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    startDate: String,
    onStartDateChange: (String) -> Unit,
    endDate: String,
    onEndDateChange: (String) -> Unit,
    incomeChecked: Boolean,
    onIncomeCheckedChange: (Boolean) -> Unit,
    expenseChecked: Boolean,
    onExpenseCheckedChange: (Boolean) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            label = { Text(stringResource(R.string.bill_merchant_name)) },
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(20.dp))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DatePickerField(label = stringResource(R.string.bill_start_date), value = startDate, onValueChanged = onStartDateChange, modifier = Modifier.weight(1f))
            Text(text = "~", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            DatePickerField(label = stringResource(R.string.bill_end_date), value = endDate, onValueChanged = onEndDateChange, modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = stringResource(R.string.bill_cash_flow), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(role = Role.Checkbox) { onIncomeCheckedChange(!incomeChecked) }) {
                Checkbox(checked = incomeChecked, onCheckedChange = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = stringResource(R.string.bill_income), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(role = Role.Checkbox) { onExpenseCheckedChange(!expenseChecked) }) {
                Checkbox(checked = expenseChecked, onCheckedChange = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = stringResource(R.string.bill_expense), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.bill_filter_reset)) }
            Button(onClick = onApply, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.bill_filter_apply)) }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
    }
}

// ====================================================================
//  交易记录卡片
// ====================================================================

@Composable
private fun BillRecordCard(
    record: edu.cqwu.electricity.data.model.BillRecord,
    onClick: () -> Unit
) {
    val displayTime = remember(record.createTime) {
        record.createTime.padStart(6, '0').let { t ->
            if (t.length == 6) "${t.substring(0, 2)}:${t.substring(2, 4)}:${t.substring(4, 6)}" else t
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            // 第一行：商户（加粗，大号） + 金额（红/绿色，大号，加粗）
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = record.merchant.ifBlank { record.type },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                val amountColor = when {
                    record.type.contains("充值", ignoreCase = true) || record.type.contains("退款", ignoreCase = true) -> Color(0xFF4CAF50)  // 收入：绿
                    else -> Color(0xFFE53935)  // 支出：红
                }
                Text(
                    text = "¥${record.amount}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            // 第二行：付款方式 + 日期时间
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = record.paymentMethod.ifBlank { "-" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = record.createDate, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = displayTime, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            // 第三行：交易类型 + 状态
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = record.type, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                BillStatusBadge(status = record.status, cssClass = record.statusCssClass)
            }
        }
    }
}

@Composable
private fun BillStatusBadge(status: String, cssClass: String) {
    val color = when {
        cssClass.contains("success", ignoreCase = true) -> Color(0xFF4CAF50)
        cssClass.contains("warning", ignoreCase = true) -> Color(0xFFFFA000)
        cssClass.contains("danger", ignoreCase = true) || cssClass.contains("important", ignoreCase = true) -> Color(0xFFE53935)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text = status, style = MaterialTheme.typography.bodySmall, color = color, fontWeight = FontWeight.Medium)
}

// ====================================================================
//  HorizontalPager 各 page 辅助 Composable（提取以减少重复）
// ====================================================================

@Composable
private fun BillLoadingContent(loadElapsed: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = stringResource(R.string.bill_fetching, loadElapsed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BillEmptyDataContent() {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
        Text(text = stringResource(R.string.common_no_data), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BillTabErrorContent(errorMessage: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "⚠ $errorMessage",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.common_please_retry),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun BillEmptyListContent(tabNo: Int) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = Icons.Outlined.Receipt, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when (tabNo) { 2 -> stringResource(R.string.bill_no_unpaid); 4 -> stringResource(R.string.bill_no_success); 5 -> stringResource(R.string.bill_no_failed); else -> stringResource(R.string.bill_no_records) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun BillStatsRow(pageInfo: BillPageInfo) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = stringResource(R.string.bill_loaded_count, pageInfo.records.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = stringResource(R.string.bill_page_info, pageInfo.currentPage, pageInfo.totalPages), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BillFooterContent(uiState: BillUiState, pageInfo: BillPageInfo, viewModel: BillViewModel) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        if (uiState.isLoadingMore && uiState.loadingMoreTab == uiState.activeTab) {
            // 仅当前活跃 Tab 触发的加载更多才显示 loading（防止切换到其他 Tab 后显示错误的加载状态）
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.bill_fetching, uiState.loadMoreElapsed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (pageInfo.hasNext) {
        // 自动加载由 LaunchedEffect（滑到底部最后3项）驱动，点击可手动触发翻页作为兜底
        Text(
            text = stringResource(R.string.common_swipe_load_more),
            modifier = Modifier.clickable {
                if (!uiState.isLoadingMore) viewModel.loadNextPage()
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
        } else if (pageInfo.records.isNotEmpty()) {
            val isCapped = pageInfo.records.size >= 100
            Text(
                text = if (isCapped) stringResource(R.string.bill_capped_hint) else stringResource(R.string.bill_all_loaded, pageInfo.records.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
