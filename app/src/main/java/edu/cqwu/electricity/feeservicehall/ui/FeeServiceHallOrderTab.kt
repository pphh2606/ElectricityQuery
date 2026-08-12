@file:OptIn(ExperimentalMaterial3Api::class)

package edu.cqwu.electricity.feeservicehall.ui

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import edu.cqwu.electricity.R
import edu.cqwu.electricity.theme.ui.DatePickerField
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import coil.compose.AsyncImage
import edu.cqwu.electricity.feeservicehall.data.OrderRecord

/**
 * 订单 Tab 内容
 *
 * 包含筛选面板、统计行、订单列表、自动分页加载。
 */
@Composable
internal fun FeeServiceHallOrderTab(
    uiState: FeeServiceHallUiState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onNavigateToOrderDetail: (OrderRecord) -> Unit,
    filterProjectName: String,
    filterStartDate: String,
    filterEndDate: String,
    showFilter: Boolean,
    onProjectNameChange: (String) -> Unit,
    onStartDateChange: (String) -> Unit,
    onEndDateChange: (String) -> Unit,
    onApplyFilter: () -> Unit,
    onResetFilter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        // ── 筛选面板 ──
        AnimatedVisibility(
            visible = showFilter,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            OrderFilterPanel(
                projectName = filterProjectName,
                onProjectNameChange = onProjectNameChange,
                startDate = filterStartDate,
                onStartDateChange = onStartDateChange,
                endDate = filterEndDate,
                onEndDateChange = onEndDateChange,
                onApply = onApplyFilter,
                onReset = onResetFilter,
            )
        }

        PullToRefreshBox(
            isRefreshing = uiState.isOrdersRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                uiState.isOrdersLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.orderErrorMessage != null && uiState.orders.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.fee_hall_load_failed, uiState.orderErrorMessage ?: ""),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
                uiState.orders.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.fee_hall_no_orders), style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> {
                    val listState = rememberLazyListState()
                    val shouldLoadMore by remember {
                        derivedStateOf {
                            val layoutInfo = listState.layoutInfo
                            val totalItems = layoutInfo.totalItemsCount
                            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            totalItems > 0 && lastVisibleIndex >= totalItems - 3
                                && uiState.orderHasMore && !uiState.isLoadingMoreOrders
                        }
                    }
                    LaunchedEffect(shouldLoadMore) {
                        if (shouldLoadMore) onLoadMore()
                    }

                    Column(Modifier.fillMaxSize()) {
                        OrderStatsRow(
                            loadedCount = uiState.orders.size,
                            currentPage = uiState.orderPageCurrent,
                            totalPages = uiState.orderTotalPages,
                        )
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().weight(1f),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            items(uiState.orders, key = { it.orderNo }) { order ->
                                OrderListItem(
                                    order = order,
                                    onClick = { onNavigateToOrderDetail(order) },
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    thickness = 0.5.dp,
                                )
                            }
                            item(key = "footer") {
                                OrderFooterContent(uiState, onLoadMore)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderFilterPanel(
    projectName: String,
    onProjectNameChange: (String) -> Unit,
    startDate: String,
    onStartDateChange: (String) -> Unit,
    endDate: String,
    onEndDateChange: (String) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        OutlinedTextField(
            value = projectName,
            onValueChange = onProjectNameChange,
            label = { Text(stringResource(R.string.fee_order_project_name)) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DatePickerField(
                label = stringResource(R.string.bill_start_date),
                value = startDate,
                onValueChanged = onStartDateChange,
                modifier = Modifier.weight(1f),
            )
            Text("~", style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            DatePickerField(
                label = stringResource(R.string.bill_end_date),
                value = endDate,
                onValueChanged = onEndDateChange,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.bill_filter_reset)) }
            Button(onClick = onApply, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.bill_filter_apply)) }
        }
    }
}

@Composable
private fun OrderStatsRow(loadedCount: Int, currentPage: Int, totalPages: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(pluralStringResource(R.plurals.fee_order_loaded_count, loadedCount, loadedCount), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.fee_order_page_info, currentPage, totalPages), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OrderListItem(order: OrderRecord, onClick: () -> Unit) {
    val amountText = String.format(Locale.US, "%.2f", order.amountYuan)
    val statusColor = when (order.status) {
        "COMPLETED" -> Color(0xFF4CAF50)
        "PENDING", "PENDING_PAYMENT" -> Color(0xFFFF9800)
        "REFUND" -> Color(0xFF2196F3)
        "CLOSED" -> Color(0xFF9E9E9E)
        else -> Color(0xFF9E9E9E)
    }

    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (order.imgUrl != null) {
                AsyncImage(
                    model = order.imgUrl, contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(8.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Icon(Icons.Outlined.Receipt, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    order.projectName ?: order.productDesc ?: stringResource(R.string.dashboard_unknown),
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(amountText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.fee_order_no_label, order.orderNo), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (order.statusRes != null) stringResource(order.statusRes!!)
                    else order.status.ifBlank { stringResource(R.string.common_unknown) },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = statusColor
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(order.createDate ?: "", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun OrderFooterContent(uiState: FeeServiceHallUiState, onLoadMore: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        if (uiState.isLoadingMoreOrders) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.common_loading), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (uiState.orderHasMore) {
            Text(
                stringResource(R.string.common_swipe_load_more),
                modifier = Modifier.clickable(onClick = onLoadMore),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            )
        } else if (uiState.orders.isNotEmpty()) {
            Text(pluralStringResource(R.plurals.fee_hall_order_all_loaded, uiState.orders.size, uiState.orders.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
