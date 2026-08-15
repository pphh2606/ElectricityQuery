package edu.cqwu.electricity.electricity.ui

import android.content.res.Resources
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import edu.cqwu.electricity.theme.ui.AppScaledDropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.app.Routes
import edu.cqwu.electricity.electricity.data.BalanceResponse
import edu.cqwu.electricity.electricity.data.BuildingNode
import edu.cqwu.electricity.electricity.data.DetailType
import edu.cqwu.electricity.electricity.data.displayName
import edu.cqwu.electricity.theme.ui.LocalNavController
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.theme.ui.ReLoginContent
import edu.cqwu.electricity.theme.ui.UiMessage
import edu.cqwu.electricity.theme.ui.resolve
import edu.cqwu.electricity.theme.util.ToastUtils
import java.util.Locale

/**
 * 仪表盘页面 — 纯 UI 组件，不绑定任何 ViewModel。
 *
 * 显示电费余额信息，并提供详情页导航按钮。
 * 所有数据和事件回调由调用方传入，支持查询 Tab 和我的 Tab 两种使用场景。
 *
 * @param room 当前选中的房间（可能为 null）
 * @param balance 余额数据（可能为 null，表示加载中或查询失败）
 * @param isRefreshing 当前是否正在刷新
 * @param isLoading 当前是否正在加载
 * @param error 错误信息
 * @param onRefresh 下拉刷新回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    room: BuildingNode?,
    balance: BalanceResponse?,
    isRefreshing: Boolean,
    isLoading: Boolean,
    error: UiMessage?,
    onRefresh: () -> Unit,
) {
    val nav = LocalNavController.current
    val resources = LocalResources.current

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // ── 房间概要卡片（始终可见） ──
            item(key = "room_info") {
                RoomInfoCard(room = room)
            }

            // ── 用电信息 / 账户余额（依赖 balance） ──
            if (balance != null) {
                item(key = "electricity_usage") {
                    ElectricityUsageCard(balance = balance)
                }

                item(key = "account_balance") {
                    AccountBalanceCard(balance = balance)
                }
            } else if (isLoading || isRefreshing) {
                // 加载中骨架屏
                item(key = "loading_skeleton") {
                    LoadingSkeleton()
                }
            } else {
                // 查询失败/空状态
                item(key = "error_state") {
                    ErrorStateCard(
                        message = error?.resolve(resources) ?: stringResource(R.string.common_load_failed),
                        onRetry = onRefresh
                    )
                }
            }

            // ── 更多功能（依赖 balance） ──
            if (balance != null) {
                item(key = "more_functions") {
                    MoreFunctionsSection(
                        onNavigateToDetail = { detailType -> nav.navigate(Routes.detailRoute(detailType, room?.id ?: "")) },
                        onNavigateToRechargeRecord = { nav.navigate(Routes.rechargeRecordRoute(room?.id ?: "")) }
                    )
                }
            }

            // 底部留白
            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * 仪表盘三点菜单按钮（复制/导出）— 可嵌入 TopAppBar actions。
 *
 * @param room 当前房间
 * @param balance 当前余额数据
 */
@Composable
fun DashboardMenuButton(
    room: BuildingNode?,
    balance: BalanceResponse?,
) {
    val snackbar = LocalSnackbarController.current
    val context = LocalContext.current
    val resources = LocalResources.current

    var showMenu by remember { mutableStateOf(false) }
    var pendingExportText by remember { mutableStateOf("") }
    var pendingExportLabel by remember { mutableStateOf("") }
    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null && pendingExportText.isNotEmpty()) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(pendingExportText.toByteArray(Charsets.UTF_8))
                }
                snackbar.show(resources.getString(R.string.common_export_success, pendingExportLabel), ToastUtils.Type.SUCCESS)
            } catch (e: Exception) {
                snackbar.show(resources.getString(R.string.common_export_failed, e.message ?: ""), ToastUtils.Type.ERROR)
            }
            pendingExportText = ""
            pendingExportLabel = ""
        }
    }

    Box {
        IconButton(onClick = { showMenu = true }) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = stringResource(R.string.common_more_options),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AppScaledDropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.common_copy)) },
                leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                onClick = {
                    showMenu = false
                    val text = getDashboardTextContent(room, balance, resources)
                    copyToClipboard(context, text, resources.getString(R.string.dashboard_title), snackbar)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.common_export)) },
                leadingIcon = { Icon(Icons.Outlined.FileDownload, contentDescription = null) },
                onClick = {
                    showMenu = false
                    pendingExportText = getDashboardTextContent(room, balance, resources)
                    pendingExportLabel = resources.getString(R.string.dashboard_title)
                    saveFileLauncher.launch("electricity_dashboard.txt")
                }
            )
        }
    }
}

// ====================================================================
//  子组件（保持不变）
// ====================================================================

/**
 * 房间概要卡片 — 始终显示，不依赖 [balance]
 */
@Composable
private fun RoomInfoCard(room: BuildingNode?) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Home,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.dashboard_room_info),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            InfoRow(
                label = stringResource(R.string.dashboard_room_name),
                value = room?.displayName ?: stringResource(R.string.dashboard_unknown)
            )

            Spacer(modifier = Modifier.height(8.dp))

            InfoRow(
                label = stringResource(R.string.dashboard_room_id),
                value = room?.id ?: "-"
            )
        }
    }
}

/**
 * 错误状态卡片 — 查询失败时显示，提供重试按钮
 */
@Composable
private fun ErrorStateCard(
    message: String,
    onRetry: () -> Unit
) {
    ReLoginContent(
        errorMessage = message,
        requiresReLogin = false,
        onReLogin = {},
        onRetry = onRetry,
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .padding(vertical = 16.dp),
    )
}

/**
 * 用电信息卡片 — 剩余电量大号展示
 */
@Composable
private fun ElectricityUsageCard(balance: BalanceResponse) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Home,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.dashboard_power_info),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 剩余电量 — 大号数字
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = String.format(Locale.US, "%.2f", balance.remainEletricCapacity),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.dashboard_unit_degree),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.dashboard_remaining_power),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 账户余额卡片 — 展示各项余额信息
 */
@Composable
private fun AccountBalanceCard(balance: BalanceResponse) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.dashboard_account_balance),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            BalanceRow(label = stringResource(R.string.dashboard_cash_balance), amount = balance.userBalance, unit = stringResource(R.string.common_unit_yuan))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            BalanceRow(label = stringResource(R.string.dashboard_subsidy_balance), amount = balance.subsidyBalance, unit = stringResource(R.string.common_unit_yuan))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            BalanceRow(label = stringResource(R.string.dashboard_base_balance), amount = balance.baseBalance, unit = stringResource(R.string.common_unit_yuan))

            Spacer(modifier = Modifier.height(8.dp))

            // 支付状态
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.dashboard_online_pay),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = if (balance.payEnable == 1)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (balance.payEnable == 1) stringResource(R.string.common_enabled) else stringResource(R.string.common_disabled),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (balance.payEnable == 1)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * 更多功能导航区
 */
@Composable
private fun MoreFunctionsSection(
    onNavigateToDetail: (DetailType) -> Unit,
    onNavigateToRechargeRecord: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.List,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.dashboard_more_features),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            NavItem(
                text = stringResource(R.string.dashboard_recent_6months),
                leadingIcon = Icons.Outlined.DateRange,
                onClick = { onNavigateToDetail(DetailType.SIX_MONTH_USAGE) }
            )
            HorizontalDivider()
            NavItem(
                text = stringResource(R.string.dashboard_daily_this_month),
                leadingIcon = Icons.Outlined.CalendarToday,
                onClick = { onNavigateToDetail(DetailType.MONTH_DAILY_USAGE) }
            )
            HorizontalDivider()
            NavItem(
                text = stringResource(R.string.dashboard_24h_detail),
                leadingIcon = Icons.Outlined.Schedule,
                onClick = { onNavigateToDetail(DetailType.HOURLY_USAGE) }
            )
            HorizontalDivider()
            NavItem(
                text = stringResource(R.string.dashboard_meter_status),
                leadingIcon = Icons.Outlined.Home,
                onClick = { onNavigateToDetail(DetailType.METER_STATUS) }
            )
            HorizontalDivider()
            NavItem(
                text = stringResource(R.string.dashboard_query_recharge_records),
                leadingIcon = Icons.AutoMirrored.Outlined.List,
                onClick = onNavigateToRechargeRecord
            )
        }
    }
}

/**
 * 单个功能导航项
 */
@Composable
private fun NavItem(
    text: String,
    leadingIcon: ImageVector?,
    onClick: () -> Unit,
    isAccent: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (isAccent) MaterialTheme.colorScheme.tertiary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isAccent)
                    MaterialTheme.colorScheme.tertiary
                else
                    MaterialTheme.colorScheme.onSurface
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = stringResource(R.string.common_view),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * 加载骨架屏
 */
@Composable
private fun LoadingSkeleton() {
    Column {
        // 用电信息骨架
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.dashboard_querying_balance),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 账户余额骨架
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.common_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ====================================================================
//  辅助 Composable
// ====================================================================

/**
 * 信息行（标签 + 值）
 */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 余额行（标签 + 金额 + 单位）
 */
@Composable
private fun BalanceRow(label: String, amount: Double, unit: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "${String.format(Locale.US, "%.2f", amount)} $unit",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ====================================================================
//  工具函数（保持不变）
// ====================================================================

/**
 * 根据当前房间和余额信息生成格式化的纯文本内容（用于复制和导出）
 */
fun getDashboardTextContent(room: BuildingNode?, balance: BalanceResponse?, resources: Resources): String {
    val sb = StringBuilder()
    sb.appendLine(resources.getString(R.string.electricity_export_title))
    sb.appendLine("=".repeat(40))

    sb.appendLine("\n" + resources.getString(R.string.electricity_export_room_info))
    val roomNum = room?.let {
        if (it.num.isNullOrBlank() || it.num == "0") it.name else it.num
    } ?: resources.getString(R.string.common_unknown)
    sb.appendLine(resources.getString(R.string.electricity_export_room_name, roomNum))
    sb.appendLine(resources.getString(R.string.electricity_export_room_id, room?.id ?: "-"))

    if (balance != null) {
        sb.appendLine("\n" + resources.getString(R.string.electricity_export_usage_info))
        sb.appendLine(resources.getString(R.string.electricity_export_remain_electricity, String.format(Locale.US, "%.2f", balance.remainEletricCapacity)))

        sb.appendLine("\n" + resources.getString(R.string.electricity_export_account_balance))
        sb.appendLine(resources.getString(R.string.electricity_export_cash_balance, String.format(Locale.US, "%.2f", balance.userBalance)))
        sb.appendLine(resources.getString(R.string.electricity_export_subsidy_balance, String.format(Locale.US, "%.2f", balance.subsidyBalance)))
        sb.appendLine(resources.getString(R.string.electricity_export_base_balance, String.format(Locale.US, "%.2f", balance.baseBalance)))

        sb.appendLine("\n" + resources.getString(R.string.electricity_export_payment_status))
        sb.appendLine(resources.getString(R.string.electricity_export_online_payment, if (balance.payEnable == 1) resources.getString(R.string.common_enabled) else resources.getString(R.string.common_disabled)))
    } else {
        sb.appendLine("\n" + resources.getString(R.string.electricity_export_balance_not_loaded))
    }

    return sb.toString()
}

/**
 * 将文本复制到系统剪贴板并显示提示（通过 ToastOverlay）
 */
fun copyToClipboard(
    context: android.content.Context,
    text: String,
    label: String,
    snackbar: edu.cqwu.electricity.theme.ui.SnackbarController
) {
    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
        as android.content.ClipboardManager
    val clip = android.content.ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    snackbar.show(context.getString(R.string.common_copied_to_clipboard), ToastUtils.Type.SUCCESS)
}
