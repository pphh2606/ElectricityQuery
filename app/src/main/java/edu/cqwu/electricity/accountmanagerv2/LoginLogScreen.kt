package edu.cqwu.electricity.accountmanagerv2

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.cqwu.electricity.R
import edu.cqwu.electricity.common.ui.BottomSheetDialogV2
import edu.cqwu.electricity.common.ui.DatePickerField
import edu.cqwu.electricity.common.ui.LabeledFieldRow
import edu.cqwu.electricity.common.ui.ReLoginContent
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.theme.ui.currentTopBarColors
import edu.cqwu.electricity.theme.util.ToastUtils

/**
 * 日志记录页 — 对应 CAS 网页 userLogs.do：
 * 按筛选条件（类型 / 结果 / 起止日期）展示当前账号的登录日志。
 *
 * 交互流程：
 * - 标题栏右侧筛选按钮 → 向下展开筛选面板（仿账单/订单页），查询/重置后刷新列表
 * - 每条记录：第一行客户端类型（强调），第二行登入时间（左）+ 认证类型（右）
 * - 点击记录 → 带拖动手柄的弹窗展示全部字段
 * - 会话过期（响应为 CAS 登录页）→ 重新登录引导
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginLogScreen(
    onBack: () -> Unit,
    onReLogin: () -> Unit,
    viewModel: LoginLogViewModel = viewModel(),
) {
    val snackbar = LocalSnackbarController.current
    // 保留 State 对象引用，供 derivedStateOf 做快照感知的分页判断
    val uiStateState = viewModel.uiState.collectAsState()
    val state = uiStateState.value

    // 操作结果消息（成功/失败），展示后消费
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.show(it, if (state.messageIsError) ToastUtils.Type.ERROR else ToastUtils.Type.SUCCESS)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.login_log_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    // 筛选按钮：展开面板时高亮为 primary 色（仿 BillScreen）
                    IconButton(onClick = viewModel::toggleFilterPanel) {
                        Icon(
                            imageVector = Icons.Outlined.FilterList,
                            contentDescription = stringResource(R.string.common_filter),
                            tint = if (state.showFilterPanel) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                },
                colors = currentTopBarColors(),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // ── 筛选面板（向下展开）──
            AnimatedVisibility(
                visible = state.showFilterPanel,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                // 类型/结果选项（stringResource 为 @Composable 调用，直接内联在组合上下文）
                val operOptions = listOf(
                    "0" to stringResource(R.string.login_log_oper_auth),
                    "1" to stringResource(R.string.login_log_oper_account),
                    "2" to stringResource(R.string.login_log_oper_password),
                    "3" to stringResource(R.string.login_log_oper_app),
                )
                val resultOptions = listOf(
                    "" to stringResource(R.string.login_log_result_all),
                    "1" to stringResource(R.string.login_log_result_success),
                    "0" to stringResource(R.string.login_log_result_failed),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    // 类型单选（大厅搜索筛选按钮同款样式，无标签行）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        operOptions.forEach { (value, label) ->
                            AuthLogFilterChip(
                                text = label,
                                selected = state.tempOperType == value,
                                onClick = { viewModel.onTempOperTypeChange(value) },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 结果单选
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        resultOptions.forEach { (value, label) ->
                            AuthLogFilterChip(
                                text = label,
                                selected = state.tempResult == value,
                                onClick = { viewModel.onTempResultChange(value) },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 起止时间
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DatePickerField(
                            label = stringResource(R.string.login_log_start_time),
                            value = state.tempStartTime,
                            onValueChanged = viewModel::onTempStartDateChange,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "~",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        DatePickerField(
                            label = stringResource(R.string.login_log_end_time),
                            value = state.tempEndTime,
                            onValueChanged = viewModel::onTempEndDateChange,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(onClick = viewModel::resetFilter, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.login_log_reset))
                        }
                        Button(onClick = viewModel::applyFilter, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.login_log_query))
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
                }
            }

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.requiresReLogin -> ReLoginContent(
                        requiresReLogin = true,
                        onReLogin = onReLogin,
                    )
                    state.loadError != null && state.records.isEmpty() -> ReLoginContent(
                        errorMessage = state.loadError,
                        requiresReLogin = false,
                        onReLogin = {},
                        onRetry = viewModel::refresh,
                    )
                    // 首屏加载中：显示加载指示，避免误显"暂无日志记录"
                    state.isRefreshing && state.records.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                    state.records.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.login_log_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(32.dp),
                        )
                    }
                    else -> {
                        // 分页加载：滚动接近底部且还有更多页时自动加载下一页（仿订单页）
                        val listState = rememberLazyListState()
                        val shouldLoadMore by remember {
                            derivedStateOf {
                                val layoutInfo = listState.layoutInfo
                                val totalItems = layoutInfo.totalItemsCount
                                val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                totalItems > 0 && lastVisibleIndex >= totalItems - 3 &&
                                    uiStateState.value.hasMore && !uiStateState.value.isLoadingMore
                            }
                        }
                        LaunchedEffect(shouldLoadMore) {
                            if (shouldLoadMore) viewModel.loadMore()
                        }
                        Column(modifier = Modifier.fillMaxSize()) {
                            // 顶部统计行：已加载条数 + 页码（仿缴费服务大厅订单页）
                            LoginLogStatsRow(
                                loadedCount = state.records.size,
                                currentPage = state.pageCurrent,
                                totalPages = state.pageTotal,
                            )
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                            ) {
                                itemsIndexed(state.records) { index, record ->
                                    LoginRecordCard(
                                        record = record,
                                        onClick = { viewModel.onRecordClick(record) },
                                    )
                                    if (index < state.records.lastIndex) {
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outlineVariant,
                                            thickness = 0.5.dp,
                                        )
                                    }
                                }
                                item(key = "footer") {
                                    LoginLogFooter(
                                        hasMore = state.hasMore,
                                        isLoadingMore = state.isLoadingMore,
                                        onLoadMore = viewModel::loadMore,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 记录详情弹窗（带拖动手柄）
    state.selectedRecord?.let { record ->
        BottomSheetDialogV2(
            visible = true,
            onDismissRequest = viewModel::dismissRecord,
            title = stringResource(R.string.login_log_detail_title),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val ipText = listOfNotNull(record.ipv6, record.ipv4).joinToString("\n")
                if (ipText.isNotEmpty()) {
                    LabeledFieldRow(
                        label = stringResource(R.string.login_log_ip),
                        value = ipText,
                        labelBold = true,
                    )
                }
                LabeledFieldRow(
                    label = stringResource(R.string.login_log_login_time),
                    value = record.loginTimeText,
                    labelBold = true,
                )
                LabeledFieldRow(
                    label = stringResource(R.string.login_log_logout_time),
                    value = record.logoutTimeText.ifBlank { "-" },
                    labelBold = true,
                )
                LabeledFieldRow(
                    label = stringResource(R.string.login_log_auth_type),
                    value = record.authType,
                    labelBold = true,
                )
                LabeledFieldRow(
                    label = stringResource(R.string.login_log_client_type),
                    value = record.clientType,
                    labelBold = true,
                )
                LabeledFieldRow(
                    label = stringResource(R.string.login_log_result),
                    value = record.result,
                    labelBold = true,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════
//  记录卡片
// ═══════════════════════════════════════════

/**
 * 单条登录记录（缴费服务大厅订单界面风格）：无圆角背景，由列表项间分隔线分隔。
 * 第一行客户端类型（强调），第二行登入时间（左）+ 认证类型（右）。
 */
@Composable
private fun LoginRecordCard(
    record: LoginRecord,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    ) {
        Text(
            text = record.clientType.ifBlank { stringResource(R.string.device_session_unknown_device) },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = record.loginTimeText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = record.authType,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 列表顶部统计行：已加载条数（左）+ 页码（右），仿缴费服务大厅订单页 */
@Composable
private fun LoginLogStatsRow(loadedCount: Int, currentPage: Int, totalPages: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = pluralStringResource(R.plurals.login_log_loaded_count, loadedCount, loadedCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.login_log_page_info, currentPage, totalPages),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 列表底部：加载中 / 上滑加载更多 / 已加载全部（仿订单页 footer） */
@Composable
private fun LoginLogFooter(
    hasMore: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isLoadingMore -> Text(
                text = stringResource(R.string.common_loading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            hasMore -> Text(
                text = stringResource(R.string.common_swipe_load_more),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onLoadMore),
            )
            else -> Text(
                text = stringResource(R.string.login_log_all_loaded),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 筛选按钮：大厅搜索筛选按钮同款样式（HallScreen.HallSectionChip）。
 * 圆形 + surfaceContainerHigh 底色 + 无边框。
 */
@Composable
private fun AuthLogFilterChip(
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
