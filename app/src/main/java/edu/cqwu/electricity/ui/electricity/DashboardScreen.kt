package edu.cqwu.electricity.ui.electricity

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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.data.model.BalanceResponse
import edu.cqwu.electricity.data.model.BuildingNode
import edu.cqwu.electricity.data.model.DetailType
import edu.cqwu.electricity.data.model.UserRoomInfo
import edu.cqwu.electricity.data.model.displayName
import edu.cqwu.electricity.ui.components.BottomSheetDialog
import edu.cqwu.electricity.ui.components.BottomSheetItem
import edu.cqwu.electricity.ui.components.LocalSnackbarController
import edu.cqwu.electricity.ui.components.TabScaffold
import edu.cqwu.electricity.util.ToastUtils

/**
 * 仪表盘页面 — 纯 UI 组件，不绑定任何 ViewModel。
 *
 * 显示电费余额信息，并提供详情页导航按钮。
 * 所有数据和事件回调由调用方传入，支持查询 Tab 和我的 Tab 两种使用场景。
 *
 * @param room 当前选中的房间（可能为 null）
 * @param balance 余额数据（可能为 null，表示加载中或查询失败）
 * @param myRoomList 我的寝室房间列表，用于房间切换弹窗（查询 Tab 传入空列表即可）
 * @param isRefreshing 当前是否正在刷新
 * @param isLoading 当前是否正在加载
 * @param error 错误信息
 * @param onRefresh 下拉刷新回调
 * @param onBackToSelection 返回选择页面回调
 * @param onNavigateToDetail 导航到详情页
 * @param onNavigateToAccountSelection 导航到账户选择（充值 Tab）
 * @param onNavigateToH5Recharge 导航到 H5 充值
 * @param onNavigateToRechargeRecord 导航到充值记录
 * @param onSwitchRoom 切换房间回调（我的 Tab 使用）
 * @param showTopBar 是否显示自带的 TopAppBar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    room: BuildingNode?,
    balance: BalanceResponse?,
    myRoomList: List<UserRoomInfo>,
    isRefreshing: Boolean,
    isLoading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onBackToSelection: () -> Unit,
    onNavigateToDetail: (DetailType) -> Unit,
    onNavigateToAccountSelection: () -> Unit = {},
    onNavigateToH5Recharge: () -> Unit = {},
    onNavigateToRechargeRecord: () -> Unit = {},
    onSwitchRoom: (UserRoomInfo) -> Unit = {},
    showTopBar: Boolean = true
) {
    val snackbar = LocalSnackbarController.current
    val context = LocalContext.current

    // 三点菜单
    var showMenu by remember { mutableStateOf(false) }

    // 我的寝室房间切换弹窗
    var showRoomSwitchSheet by remember { mutableStateOf(false) }

    // 文件导出启动器
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
                snackbar.show("已导出到文件: $pendingExportLabel", ToastUtils.Type.SUCCESS)
            } catch (e: Exception) {
                snackbar.show("导出失败: ${e.message}", ToastUtils.Type.ERROR)
            }
            pendingExportText = ""
            pendingExportLabel = ""
        }
    }

    // 使用统一 TabScaffold
    TabScaffold(
        showTopBar = showTopBar,
        title = "电费查询结果",
        onBack = { onBackToSelection() },
        actions = {
            // 我的寝室切换按钮（仅多房间时显示）
            if (myRoomList.size > 1) {
                IconButton(onClick = { showRoomSwitchSheet = true }) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "切换寝室"
                    )
                }
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "更多选项"
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("复制") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            val text = getDashboardTextContent(room, balance)
                            copyToClipboard(context, text, "电费查询结果", snackbar)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("导出") },
                        leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            pendingExportText = getDashboardTextContent(room, balance)
                            pendingExportLabel = "电费查询结果"
                            saveFileLauncher.launch("electricity_dashboard.txt")
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        DashboardContent(
            myRoomList = myRoomList,
            room = room,
            balance = balance,
            isRefreshing = isRefreshing,
            isLoading = isLoading,
            error = error,
            paddingValues = paddingValues,
            showRoomSwitchSheet = showRoomSwitchSheet,
            onShowRoomSwitchSheetChange = { showRoomSwitchSheet = it },
            onRefresh = onRefresh,
            onBackToSelection = onBackToSelection,
            onNavigateToDetail = onNavigateToDetail,
            onNavigateToH5Recharge = onNavigateToH5Recharge,
            onNavigateToRechargeRecord = onNavigateToRechargeRecord,
            onSwitchRoom = onSwitchRoom,
        )
    }
}

/**
 * Dashboard 内容区域（不含 Scaffold/TopAppBar）
 */
@Composable
private fun DashboardContent(
    myRoomList: List<UserRoomInfo>,
    room: BuildingNode?,
    balance: BalanceResponse?,
    isRefreshing: Boolean,
    isLoading: Boolean,
    error: String?,
    paddingValues: PaddingValues,
    showRoomSwitchSheet: Boolean,
    onShowRoomSwitchSheetChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onBackToSelection: () -> Unit,
    onNavigateToDetail: (DetailType) -> Unit,
    onNavigateToH5Recharge: () -> Unit,
    onNavigateToRechargeRecord: () -> Unit,
    onSwitchRoom: (UserRoomInfo) -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
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
                        message = error ?: "加载失败",
                        onRetry = onRefresh
                    )
                }
            }

            // ── 更多功能（依赖 balance） ──
            if (balance != null) {
                item(key = "more_functions") {
                    MoreFunctionsSection(
                        onNavigateToDetail = onNavigateToDetail,
                        onNavigateToRechargeRecord = onNavigateToRechargeRecord
                    )
                }
            }

            // ── 返回按钮 ──
            item(key = "back_button") {
                BackButton(onClick = onBackToSelection)
            }

            // 底部留白
            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // ── 我的寝室房间切换 BottomSheet ──
    if (showRoomSwitchSheet && myRoomList.isNotEmpty()) {
        BottomSheetDialog(
            onDismissRequest = { onShowRoomSwitchSheetChange(false) },
            title = "选择寝室"
        ) {
            Text(
                text = "请选择要查看的寝室：",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            myRoomList.forEach { room ->
                BottomSheetItem(
                    icon = Icons.Default.Home,
                    title = room.fullName.ifBlank { room.roomName },
                    onClick = {
                        onSwitchRoom(room)
                        onShowRoomSwitchSheetChange(false)
                    }
                )
            }
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
                    imageVector = Icons.Default.Home,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "房间信息",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            InfoRow(
                label = "房间名称",
                value = room?.displayName ?: "未知"
            )

            Spacer(modifier = Modifier.height(8.dp))

            InfoRow(
                label = "房间 ID",
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
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text("重新查询")
            }
        }
    }
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
                    imageVector = Icons.Default.Home,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "用电信息",
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
                        text = String.format("%.2f", balance.remainEletricCapacity),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "度",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "剩余电量",
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
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "账户余额",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            BalanceRow(label = "现金余额", amount = balance.userBalance, unit = "元")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            BalanceRow(label = "补贴余额", amount = balance.subsidyBalance, unit = "元")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            BalanceRow(label = "基础余额", amount = balance.baseBalance, unit = "元")

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
                    text = "在线支付",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (balance.payEnable == 1)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (balance.payEnable == 1) "已启用" else "已禁用",
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
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "更多功能",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            NavItem(
                text = "最近6个月用电记录",
                leadingIcon = Icons.Default.DateRange,
                onClick = { onNavigateToDetail(DetailType.SIX_MONTH_USAGE) }
            )
            HorizontalDivider()
            NavItem(
                text = "本月每日用电",
                leadingIcon = Icons.Default.CalendarToday,
                onClick = { onNavigateToDetail(DetailType.MONTH_DAILY_USAGE) }
            )
            HorizontalDivider()
            NavItem(
                text = "近24h用电明细",
                leadingIcon = Icons.Default.Schedule,
                onClick = { onNavigateToDetail(DetailType.HOURLY_USAGE) }
            )
            HorizontalDivider()
            NavItem(
                text = "电表实时状态",
                leadingIcon = Icons.Default.Home,
                onClick = { onNavigateToDetail(DetailType.METER_STATUS) }
            )
            HorizontalDivider()
            NavItem(
                text = "查询充值记录",
                leadingIcon = Icons.AutoMirrored.Filled.List,
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
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "查看",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * 重新选择房间按钮
 */
@Composable
private fun BackButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = "重新选择房间",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
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
                        text = "正在查询余额...",
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
                    text = "加载中...",
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
            text = "${String.format("%.2f", amount)} $unit",
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
fun getDashboardTextContent(room: BuildingNode?, balance: BalanceResponse?): String {
    val sb = StringBuilder()
    sb.appendLine("电费查询结果")
    sb.appendLine("=".repeat(40))

    sb.appendLine("\n【房间信息】")
    val roomNum = room?.let {
        if (it.num.isNullOrBlank() || it.num == "0") it.name else it.num
    } ?: "未知"
    sb.appendLine("  房间名称/编号: $roomNum")
    sb.appendLine("  房间 ID: ${room?.id ?: "-"}")

    if (balance != null) {
        sb.appendLine("\n【用电信息】")
        sb.appendLine("  剩余电量: ${String.format("%.2f", balance.remainEletricCapacity)} 度")

        sb.appendLine("\n【账户余额】")
        sb.appendLine("  现金余额: ${String.format("%.2f", balance.userBalance)} 元")
        sb.appendLine("  补贴余额: ${String.format("%.2f", balance.subsidyBalance)} 元")
        sb.appendLine("  基础余额: ${String.format("%.2f", balance.baseBalance)} 元")

        sb.appendLine("\n【支付状态】")
        sb.appendLine("  在线支付: ${if (balance.payEnable == 1) "已启用" else "已禁用"}")
    } else {
        sb.appendLine("\n余额数据暂未加载")
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
    snackbar: edu.cqwu.electricity.ui.components.SnackbarController
) {
    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
        as android.content.ClipboardManager
    val clip = android.content.ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    snackbar.show("已复制到剪贴板", ToastUtils.Type.SUCCESS)
}
