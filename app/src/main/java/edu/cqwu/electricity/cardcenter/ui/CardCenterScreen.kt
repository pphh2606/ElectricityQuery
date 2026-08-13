package edu.cqwu.electricity.cardcenter.ui

import edu.cqwu.electricity.theme.ui.currentTopBarColors

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CreditCardOff
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.qrcode.data.QrCodeType
import edu.cqwu.electricity.app.Routes
import edu.cqwu.electricity.theme.ui.LocalNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 卡中心 — 本地化 UI 页面
 *
 * 以 3 列网格展示 6 个功能入口，对齐 WeUI 风格：
 * - 账户信息 → 原生 [AccountInfoScreen]（已本地化）
 * - 支付码 / 乘车码 → 原生 [QrCodeDisplayScreen]
 * - 卡挂失 → 原生 [CardLostScreen]（已本地化）
 * - 账单 / 充值 → [UnifiedWebViewScreen]
 *
 * 支持下拉刷新（与首页等其他页面保持一致体验）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardCenterScreen(
    onBack: () -> Unit,
    onNavigateToQrCode: (QrCodeType) -> Unit,
    onNavigateToCardRecharge: () -> Unit = {},
) {
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val nav = LocalNavController.current
    val topBarColors = currentTopBarColors()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.card_center_title),
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
                colors = topBarColors
            )
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    // 模拟刷新延迟，保持与下拉刷新动画一致
                    delay(500)
                    isRefreshing = false
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 功能网格：3 列 × 2 行
                item(key = "grid") {
                    val resources = LocalResources.current
                    CardGrid(
                        onItemClick = { item ->
                            when (item.action) {
                                CardAction.ACCOUNT_INFO -> nav.navigate(Routes.ACCOUNT_INFO)
                                CardAction.QR_CODE_PAY -> onNavigateToQrCode(QrCodeType.PAY)
                                CardAction.QR_CODE_BUS -> onNavigateToQrCode(QrCodeType.BUS)
                                CardAction.CARD_LOST -> nav.navigate(Routes.CARD_LOST)
                                CardAction.BILL -> nav.navigate(Routes.BILL)
                                CardAction.CARD_RECHARGE -> onNavigateToCardRecharge()
                                is CardAction.WEB_VIEW -> nav.navigate(Routes.unifiedWebViewRoute(item.action.url, resources.getString(item.labelRes)))
                            }
                        }
                    )
                }
            }
        }
    }
}

// ====================================================================
//  数据模型
// ====================================================================

/**
 * 卡中心各功能项点击后的导航动作
 */
private sealed class CardAction {
    /** 原生账户信息页面（已本地化） */
    data object ACCOUNT_INFO : CardAction()
    /** 原生二维码（支付码/乘车码） */
    data object QR_CODE_PAY : CardAction()
    data object QR_CODE_BUS : CardAction()
    /** 原生卡挂失页面（已本地化） */
    data object CARD_LOST : CardAction()
    /** 原生账单页面（已本地化） */
    data object BILL : CardAction()
    /** 原生校园卡充值页面（已本地化） */
    data object CARD_RECHARGE : CardAction()
    /** WebView 打开 URL */
    data class WEB_VIEW(val url: String) : CardAction()
}

/**
 * 单个网格项数据
 */
private data class CardGridItem(
    @androidx.annotation.StringRes val labelRes: Int,
    val icon: ImageVector,
    val action: CardAction
)

/** 卡中心 6 个功能项 */
private val cardCenterItems = listOf(
    CardGridItem(
        labelRes = R.string.card_center_account_info,
        icon = Icons.Outlined.AccountBalance,
        action = CardAction.ACCOUNT_INFO
    ),
    CardGridItem(
        labelRes = R.string.card_center_payment_code,
        icon = Icons.Outlined.QrCodeScanner,
        action = CardAction.QR_CODE_PAY
    ),
    CardGridItem(
        labelRes = R.string.card_center_transit_code,
        icon = Icons.Outlined.DirectionsBus,
        action = CardAction.QR_CODE_BUS
    ),
    CardGridItem(
        labelRes = R.string.card_center_bills,
        icon = Icons.Outlined.Receipt,
        action = CardAction.BILL
    ),
    CardGridItem(
        labelRes = R.string.card_center_report_lost,
        icon = Icons.Outlined.CreditCardOff,
        action = CardAction.CARD_LOST
    ),
    CardGridItem(
        labelRes = R.string.card_center_recharge,
        icon = Icons.Outlined.Payments,
        action = CardAction.CARD_RECHARGE
    )
)

// ====================================================================
//  子组件
// ====================================================================

/**
 * 功能网格容器
 * 使用 Column + Row 手动分 3 列，避免嵌套 LazyVerticalGrid 的无限高度约束问题
 *
 * @param onItemClick 点击回调
 */
@Composable
private fun CardGrid(
    onItemClick: (CardGridItem) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        cardCenterItems.chunked(3).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { item ->
                    CardGridItemView(
                        item = item,
                        modifier = Modifier.weight(1f),
                        onClick = { onItemClick(item) }
                    )
                }
                // 补齐空位，使最后一行不足 3 个时保持布局一致
                repeat(3 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * 单个网格项 — 圆形图标 + 文字标签
 */
@Composable
private fun CardGridItemView(
    item: CardGridItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 圆形图标背景（浅色）
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = stringResource(item.labelRes),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 功能名称
            Text(
                text = stringResource(item.labelRes),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
