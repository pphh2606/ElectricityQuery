@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package edu.cqwu.electricity.electricity.ui

import androidx.compose.material3.IconButtonDefaults
import edu.cqwu.electricity.common.ui.ScrollLinkedBox
import edu.cqwu.electricity.common.ui.AppPullToRefresh
import edu.cqwu.electricity.common.ui.AppTabRow
import edu.cqwu.electricity.common.ui.CollapsibleScaffold

// 三点菜单
// 剪贴板与文件导出
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.TableChart
import edu.cqwu.electricity.common.ui.AppScaledDropdownMenu
import edu.cqwu.electricity.common.ui.AppScaledMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.common.ui.DateRangeFilterRow
import edu.cqwu.electricity.common.ui.ReLoginContent
import edu.cqwu.electricity.common.ui.rememberTextExportLauncher
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.common.util.copyToClipboard
import kotlinx.coroutines.launch

/**
 * 记录列表页通用模板（记录报表外壳）。
 *
 * 收敛用量报表 / 补助记录等"顶部三点菜单（复制/导出）+ 可选顶部 Tab + 起止日期筛选 +
 * 数据表格 + 底部总计"页面的骨架。**起止日期行与表格结果区在此固定组合成一个整体**，
 * 页面无需再自行拼装筛选区，差异仅通过参数注入：
 * - [beginLabel]/[endLabel]/[beginValue]/[endValue]/[onBeginChange]/[onEndChange]：日期筛选
 * - [tabs]/[initialTabIndex]/[onTabSelected]：可选顶部 Tab（非空时渲染 TabRow + 横向翻页；
 *   传入空则不显示，作为普通记录页，如补助记录）
 * - [columns] + [pages]：表格列定义与**按页**数据（每个 Tab 一页，可各自缓存展示；
 *   补助记录只传单页）
 * - [chartPages]/[chartMode]/[onToggleViewMode]：可选折线图视图——每页的图表内容与表格
 *   [pages] 一一对应；[chartMode] 决定每页渲染表格还是折线（Top Tab 与分页始终在场，
 *   折线视图下仍可点击/滑动切换粒度）
 * - [textContent]：纯文本生成（复制/导出用），各页格式不同
 * - 标题、导出文件名
 *
 * @param title 顶栏标题
 * @param onBack 返回回调
 * @param isRefreshing 刷新中（顶部下拉刷新式指示，同时用于自动查询进度提示）
 * @param columns 表格列定义（各页共用同一列表头）
 * @param pages 每页表格内容（补助记录传 1 个；有 Tab 时数量与 tabs 一致）
 * @param chartPages 每页折线图内容（与 pages 一一对应）；空列表表示不支持图表（不显示切换按钮）
 * @param chartMode 当前是否以折线图渲染每一页（由 ViewModel 的 viewMode 驱动）
 * @param onToggleViewMode 切换表格/折线图回调（null 时不显示切换按钮）
 * @param beginLabel 起始日期占位文本（空值时显示在胶囊内）
 * @param endLabel 结束日期占位文本
 * @param beginValue 起始日期（yyyy-MM-dd）
 * @param endValue 结束日期（yyyy-MM-dd）
 * @param onBeginChange 起始日期变化回调
 * @param onEndChange 结束日期变化回调
 * @param tabs 顶部 Tab 文案列表；null 时不显示 Tab（普通记录页）
 * @param initialTabIndex 初始选中页（需与页面默认筛选条件一致，如"每日"）
 * @param onTabSelected 顶部 Tab/滑动切换回调（页面据此切换数据源与查询）
 * @param onRefresh 下拉刷新回调
 * @param textContent 生成纯文本（复制/导出用）
 * @param exportTitle 导出标题（用于文件名提示与 Snackbar）
 * @param exportFileName 导出文件名（如 "electricity_usage_record.txt"）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordListScreen(
    title: String,
    onBack: () -> Unit,
    isRefreshing: Boolean,
    columns: List<TableColumn>,
    pages: List<RecordTablePageV2>,
    chartPages: List<RecordChartContentV2> = emptyList(),
    chartMode: Boolean = false,
    onToggleViewMode: (() -> Unit)? = null,
    beginLabel: String,
    endLabel: String,
    beginValue: String,
    endValue: String,
    onBeginChange: (String) -> Unit,
    onEndChange: (String) -> Unit,
    tabs: List<String>? = null,
    initialTabIndex: Int = 0,
    onTabSelected: ((Int) -> Unit)? = null,
    onRefresh: () -> Unit,
    textContent: () -> String,
    exportTitle: String,
    exportFileName: String,
) {
    val snackbar = LocalSnackbarController.current
    val context = LocalContext.current

    // 三点菜单状态
    var showMenu by remember { mutableStateOf(false) }

    // 文件导出启动器
    val textExport = rememberTextExportLauncher()

    // 顶部 Tab 分页状态（tabs 为 null 时仅占位 1 页，不渲染）
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = initialTabIndex,
        pageCount = { tabs?.size ?: 1 }
    )

    // 滑动/点击切换后同步页面筛选条件（tabs 非空时才生效）
    LaunchedEffect(pagerState.currentPage) {
        if (tabs != null) onTabSelected?.invoke(pagerState.currentPage)
    }

    CollapsibleScaffold(
        title = title,
        onBack = onBack,
        actions = {
            // 表格 ⇄ 折线图切换按钮（仅在页面提供图表内容时显示）
            if (chartPages.isNotEmpty() && onToggleViewMode != null) {
                IconButton(shapes = IconButtonDefaults.shapes(), onClick = onToggleViewMode) {
                                Icon(
                                    imageVector = if (chartMode) Icons.Outlined.TableChart else Icons.AutoMirrored.Outlined.ShowChart,
                                    contentDescription = stringResource(
                                        if (chartMode) R.string.common_toggle_table else R.string.common_toggle_chart
                                    ),
                                )
                            }
                        }
                        Box {
                            IconButton(shapes = IconButtonDefaults.shapes(), onClick = { showMenu = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreVert,
                                    contentDescription = stringResource(R.string.common_more_options),
                                )
                            }
                            AppScaledDropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                AppScaledMenuItem(
                                    text = { Text(stringResource(R.string.common_copy)) },
                                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        copyToClipboard(context, textContent(), exportTitle, snackbar)
                                    }
                                )
                                AppScaledMenuItem(
                                    text = { Text(stringResource(R.string.common_export)) },
                                    leadingIcon = { Icon(Icons.Outlined.FileDownload, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        textExport.launch(exportFileName, textContent(), exportTitle)
                                    }
                                )
                            }
                        }
                    },
        // 顶部 Tab、日期筛选行、表格表头并入标题栏浮层：和标题栏一起模糊、固定不滚动，
        // 行列表从它们背后滚过；表头跟列表行共享同一份列定义，保证列宽对齐
        expandedTopContent = {
            if (tabs != null) {
                AppTabRow(
                    tabs = tabs,
                    selectedIndex = pagerState.currentPage,
                    onTabSelected = { index ->
                        scope.launch { pagerState.animateScrollToPage(index) }
                    },
                )
            }
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                DateRangeFilterRow(
                    beginLabel = beginLabel,
                    endLabel = endLabel,
                    beginValue = beginValue,
                    endValue = endValue,
                    onBeginChange = onBeginChange,
                    onEndChange = onEndChange,
                )
                Spacer(modifier = Modifier.height(8.dp))
                val headerPage = pages.getOrNull(if (tabs == null) 0 else pagerState.currentPage)
                if (!chartMode && headerPage != null && headerPage.rows.isNotEmpty()) {
                    RecordTableHeader(columns = columns)
                }
            }
        },
    ) { topInset, scrollModifier ->
            AppPullToRefresh(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                topInset = topInset,
                modifier = Modifier.fillMaxSize()
            ) {
                ScrollLinkedBox(scrollModifier) {
                // 有 Tab 时分页器始终在场，每页内部按 chartMode 决定渲染该页的表格还是折线图。
                // 折线图模式下禁左右滑动切 Tab（userScrollEnabled=false 只禁用户手势，
                // 点击顶部 Tab 的程序化滚动仍有效），把横滑手势让给图表的"按压查看详情"。
                if (tabs == null) {
                    // 普通记录页（补助）：单页，表格/折线二选一
                    RecordPageScroll(
                        page = pages.getOrNull(0),
                        chartMode = chartMode,
                        chartContent = chartPages.getOrNull(0),
                        columns = columns,
                        topInset = topInset,
                        onRetry = onRefresh,
                    )
                } else {
                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = !chartMode,
                        modifier = Modifier.fillMaxSize(),
                    ) { pageIndex ->
                        RecordPageScroll(
                            page = pages.getOrNull(pageIndex),
                            chartMode = chartMode,
                            chartContent = chartPages.getOrNull(pageIndex),
                            columns = columns,
                            topInset = topInset,
                            onRetry = onRefresh,
                        )
                    }
                }
                }
            }
    }
}

/**
 * 结果区某页内容：折线图模式优先渲染该页图表，否则渲染该页表格。
 *
 * 表格行放进**单一 LazyColumn** 并为标题栏浮层留出首项空白，这样行能滚到
 * 标题栏/表头背后被模糊；表头由浮层绘制，这里只画行与底部总计。
 */
@Composable
private fun RecordPageScroll(
    page: RecordTablePageV2?,
    chartMode: Boolean,
    chartContent: RecordChartContentV2?,
    columns: List<TableColumn>,
    topInset: Dp,
    onRetry: () -> Unit,
) {
    val error = page?.error
    when {
        error != null -> {
            ReLoginContent(
                errorMessage = error,
                requiresReLogin = false,
                onRetry = onRetry,
                modifier = Modifier.padding(top = topInset),
            )
        }

        chartMode -> {
            Box(modifier = Modifier.fillMaxSize().padding(top = topInset)) {
                RecordChartArea(
                    content = chartContent ?: RecordChartContentV2(),
                    modifier = Modifier.fillMaxSize(),
                    onRetry = onRetry,
                )
            }
        }

        page == null || (page.isLoading && page.rows.isEmpty()) -> {
            // 无数据时留白，避免闪"暂无数据"；进度由顶部下拉刷新指示提示
            Box(modifier = Modifier.fillMaxSize())
        }

        page.rows.isEmpty() -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = topInset),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = page.emptyText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // 左右留白给内容而不是给容器：滚动区仍铺满整屏，标题栏背后不会露出空白
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                item(key = "top_inset") { Spacer(Modifier.height(topInset)) }

                items(page.rows) { row ->
                    RecordTableRow(columns = columns, row = row)
                }

                val footer = page.footer
                if (footer != null) {
                    item(key = "footer") {
                        RecordTableFooter(columns = columns, footer = footer)
                    }
                }
            }
        }
    }
}
