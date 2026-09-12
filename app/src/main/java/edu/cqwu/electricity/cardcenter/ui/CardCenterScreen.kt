package edu.cqwu.electricity.cardcenter.ui

import edu.cqwu.electricity.theme.ui.currentTopBarColors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CreditCardOff
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Receipt
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.qrcode.data.QrCodeType
import edu.cqwu.electricity.app.Routes
import edu.cqwu.electricity.common.ui.FeatureGrid
import edu.cqwu.electricity.common.ui.FeatureGridItem
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
                // 功能网格：3 列 × 2 行（骨架与网格项统一由 common/ui 提供）
                item(key = "grid") {
                    val resources = LocalResources.current
                    FeatureGrid(items = cardCenterItems) { card, itemModifier ->
                        FeatureGridItem(
                            icon = card.icon,
                            label = stringResource(card.labelRes),
                            onClick = {
                                when (val action = card.action) {
                                    CardAction.ACCOUNT_INFO -> nav.navigate(Routes.ACCOUNT_INFO)
                                    CardAction.QR_CODE_PAY -> onNavigateToQrCode(QrCodeType.PAY)
                                    CardAction.QR_CODE_BUS -> onNavigateToQrCode(QrCodeType.BUS)
                                    CardAction.CARD_LOST -> nav.navigate(Routes.CARD_LOST)
                                    CardAction.BILL -> nav.navigate(Routes.BILL)
                                    CardAction.CARD_RECHARGE -> onNavigateToCardRecharge()
                                    is CardAction.WEB_VIEW -> nav.navigate(
                                        Routes.unifiedWebViewRoute(action.url, resources.getString(card.labelRes)),
                                    )
                                }
                            },
                            modifier = itemModifier,
                        )
                    }
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

// 网格容器（FeatureGrid）与网格项（FeatureGridItem）已上提到 common/ui，
// 与校园网首页共用同一份实现，本文件不再保留私有副本。

