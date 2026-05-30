package edu.cqwu.electricity.ui.feeservicehall

import androidx.compose.ui.res.stringResource
import edu.cqwu.electricity.R

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import edu.cqwu.electricity.data.network.OrderRecord
import edu.cqwu.electricity.ui.theme.LocalTopBarState
import edu.cqwu.electricity.ui.theme.toTopAppBarColors

/**
 * 订单原生详情页
 *
 * 使用已在订单列表中加载的 [OrderRecord] 数据渲染原生 UI，
 * 无需额外网络请求，页面立即渲染。
 */
/**
 * 订单详情内容（可被 BottomSheetDialog 或全屏页面复用）
 */
@Composable
fun OrderDetailContent(
    order: OrderRecord,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // ── 金额概览卡片 ──
        OrderSummaryCard(order)

        Spacer(Modifier.height(8.dp))

        // ── 订单详细信息 ──
        OrderInfoSection(order)
    }
}

/**
 * 订单详情独立页面（保留以备未来可能的全屏路由使用）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    order: OrderRecord,
    onBack: () -> Unit,
) {
    val topBarColors = LocalTopBarState.current.style.toTopAppBarColors(MaterialTheme.colorScheme)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.fee_order_detail_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = topBarColors,
            )
        },
    ) { innerPadding ->
        OrderDetailContent(
            order = order,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun OrderSummaryCard(order: OrderRecord) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 项目图标
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (order.imgUrl != null) {
                AsyncImage(
                    model = order.imgUrl, contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(8.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Icon(Icons.Default.Receipt, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
            }
        }

        Spacer(Modifier.height(12.dp))

        // 金额
        Text(
            text = "¥%.2f".format(order.amountYuan),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(4.dp))

        // 项目名称
        Text(
            text = order.projectName ?: order.productDesc ?: stringResource(R.string.dashboard_unknown),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(8.dp))

        // 状态标签
        val statusColor = when (order.status) {
            "COMPLETED" -> Color(0xFF4CAF50)
            "PENDING" -> Color(0xFFFF9800)
            "REFUND" -> Color(0xFF2196F3)
            "CLOSED" -> Color(0xFF9E9E9E)
            else -> Color(0xFF9E9E9E)
        }
        Text(
            text = order.statusDisplay,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = statusColor,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(statusColor.copy(alpha = 0.1f))
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun OrderInfoSection(order: OrderRecord) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.fee_order_info),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(8.dp))

        InfoRow(label = stringResource(R.string.fee_order_no), value = order.orderNo)
        InfoRow(label = stringResource(R.string.fee_order_product_desc), value = order.productDesc ?: stringResource(R.string.common_dash))
        InfoRow(label = stringResource(R.string.fee_order_create_time), value = order.createDate ?: stringResource(R.string.common_dash))

        if (order.actualCloseTime != null) {
            InfoRow(label = stringResource(R.string.fee_order_complete_time), value = order.actualCloseTime)
        }
        if (order.updateDate != null) {
            InfoRow(label = stringResource(R.string.fee_order_update_time), value = order.updateDate)
        }
        if (order.schdualCloseTime != null) {
            InfoRow(label = stringResource(R.string.fee_order_close_time), value = order.schdualCloseTime)
        }

        InfoRow(label = stringResource(R.string.fee_order_channel), value = order.tradeChannelDisplay)

        if (order.balanceOrderTradeOrderNo != null) {
            InfoRow(label = stringResource(R.string.fee_order_transaction_no), value = order.balanceOrderTradeOrderNo)
        }
        if (order.engName != null) {
            InfoRow(label = stringResource(R.string.fee_order_project_id), value = order.engName)
        }
        if (order.partnerId != null) {
            InfoRow(label = stringResource(R.string.fee_order_partner), value = order.partnerId)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}
