package edu.cqwu.electricity.ui.cardcenter

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCardOff
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Receipt
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
import edu.cqwu.electricity.ui.theme.LocalTopBarState
import edu.cqwu.electricity.ui.theme.toTopAppBarColors
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.data.network.QrCodeType
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
    onNavigateToWebView: (url: String, title: String) -> Unit,
    onNavigateToAccountInfo: () -> Unit = {},
    onNavigateToCardLost: () -> Unit = {},
    onNavigateToBill: () -> Unit = {}
) {
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val topBarColors = LocalTopBarState.current.style.toTopAppBarColors(MaterialTheme.colorScheme)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "一卡通服务平台",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
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
                    CardGrid(
                        items = cardCenterItems,
                        onItemClick = { item ->
                            when (item.action) {
                                CardAction.ACCOUNT_INFO -> onNavigateToAccountInfo()
                                CardAction.QR_CODE_PAY -> onNavigateToQrCode(QrCodeType.PAY)
                                CardAction.QR_CODE_BUS -> onNavigateToQrCode(QrCodeType.BUS)
                                CardAction.CARD_LOST -> onNavigateToCardLost()
                                CardAction.BILL -> onNavigateToBill()
                                is CardAction.WEB_VIEW -> onNavigateToWebView(item.action.url, item.label)
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
    /** WebView 打开 URL */
    data class WEB_VIEW(val url: String) : CardAction()
}

/**
 * 单个网格项数据
 */
private data class CardGridItem(
    val label: String,
    val icon: ImageVector,
    val action: CardAction
)

/** 卡中心基础 URL */
private const val EPAY_BASE = "http://218.194.176.214:8382/epay"

/** 卡中心 6 个功能项 */
private val cardCenterItems = listOf(
    CardGridItem(
        label = "账户信息",
        icon = Icons.Filled.AccountBalance,
        action = CardAction.ACCOUNT_INFO
    ),
    CardGridItem(
        label = "支付码",
        icon = Icons.Filled.QrCodeScanner,
        action = CardAction.QR_CODE_PAY
    ),
    CardGridItem(
        label = "乘车码",
        icon = Icons.Filled.DirectionsBus,
        action = CardAction.QR_CODE_BUS
    ),
    CardGridItem(
        label = "账单",
        icon = Icons.Filled.Receipt,
        action = CardAction.BILL
    ),
    CardGridItem(
        label = "卡挂失",
        icon = Icons.Filled.CreditCardOff,
        action = CardAction.CARD_LOST
    ),
    CardGridItem(
        label = "充值",
        icon = Icons.Filled.Payments,
        action = CardAction.WEB_VIEW("https://pay.cqwu.edu.cn/projectDetailEcard/")
    )
)

// ====================================================================
//  子组件
// ====================================================================

/**
 * 功能网格容器
 * 使用 Column + Row 手动分 3 列，避免嵌套 LazyVerticalGrid 的无限高度约束问题
 *
 * @param items 功能项列表
 * @param onItemClick 点击回调
 */
@Composable
private fun CardGrid(
    items: List<CardGridItem>,
    onItemClick: (CardGridItem) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items.chunked(3).forEach { rowItems ->
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
                    contentDescription = item.label,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 功能名称
            Text(
                text = item.label,
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
