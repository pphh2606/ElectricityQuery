package edu.cqwu.electricity.electricity.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Store
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale
import edu.cqwu.electricity.R
import edu.cqwu.electricity.app.Routes
import edu.cqwu.electricity.electricity.data.UserRoomInfo
import edu.cqwu.electricity.login.data.AccountSessionStore
import edu.cqwu.electricity.payment.ui.AmountGrid
import edu.cqwu.electricity.theme.ui.BottomSheetDialog
import edu.cqwu.electricity.theme.ui.BottomSheetItem
import edu.cqwu.electricity.theme.ui.LocalNavController
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.theme.util.ToastUtils

/**
 * 充值页面
 *
 * 将学号输入/查询与充值金额选择合为一页。
 * 顶部显示学号输入框 + 查询按钮，查询成功后在下方显示充值 UI（账户信息 + 金额选择 + 立即充值）。
 *
 * 布局结构与 BuildingSelectionScreen / DashboardScreen 保持一致：
 * Column → Box(weight=1f) → PullToRefreshBox → LazyColumn，
 * 确保内容可滚动、无白边。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RechargeScreen(
    viewModel: RechargeViewModel,
    triggerOtherRecharge: Boolean = false,
    onOtherRechargeTriggered: () -> Unit = {},
) {
    val recharge by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = LocalSnackbarController.current
    val nav = LocalNavController.current

    // 房间切换弹窗状态
    var showRoomSwitchDialog by remember { mutableStateOf(false) }

    // 其他充值方式弹窗
    var showOtherRechargeDialog by remember { mutableStateOf(false) }

    // 外部触发打开其他充值方式弹窗
    LaunchedEffect(triggerOtherRecharge) {
        if (triggerOtherRecharge) {
            showOtherRechargeDialog = true
            onOtherRechargeTriggered()
        }
    }

    // 是否已查询成功（fullName 有值）
    val hasQueriedSuccess = recharge.fullName.isNotBlank()
    // 是否显示充值内容：查询成功后才显示
    val showRechargeContent = hasQueriedSuccess

    // ── 自动填充已登录用户的学号并查询 ──
    // 取当前激活账号（持久化，进程重启后仍有效）
    val loggedInStudentId = remember {
        AccountSessionStore.getActiveUser()
    }
    LaunchedEffect(Unit) {
        viewModel.autoFillFromLogin(loggedInStudentId)
    }

    // ── 查询后自动选择第一个房间 ──
    LaunchedEffect(recharge.roomList) {
        val rooms = recharge.roomList
        if (rooms.isNotEmpty() && recharge.selectedRoom == null) {
            viewModel.selectAccountRoom(rooms[0])
        }
    }

    // ── 房间选择后加载余额 ──
    LaunchedEffect(recharge.selectedRoom) {
        if (recharge.selectedRoom != null) {
            viewModel.loadRechargeBalance()
        }
    }

    // ── 显示充值错误 ──
    LaunchedEffect(recharge.createOrderError) {
        recharge.createOrderError?.let {
            snackbar.show(it, ToastUtils.Type.ERROR)
            viewModel.clearOrderError()
        }
    }

    // 是否有有效金额且未超出单次充值上限
    val effectiveRechargeAmount = recharge.selectedAmount
        ?: recharge.customAmount.trim().toDoubleOrNull()
    val hasValidAmount = effectiveRechargeAmount != null && effectiveRechargeAmount > 0
            && effectiveRechargeAmount <= 1000

    // 账户名称显示
    val accountName: String = recharge.fullName.takeIf { it.isNotBlank() }
        ?: ""

    // 是否可以切换账户（仅账号模式有多房间时允许切换）
    val canSwitchAccount = recharge.roomList.size > 1

    // ── 主体布局：Column + Box(weight=1f) + PullToRefreshBox + LazyColumn ──
    // 与 BuildingSelectionScreen 保持一致的结构模式
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            PullToRefreshBox(
                isRefreshing = recharge.isRefreshing,
                onRefresh = { viewModel.refreshRechargeData() },
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // ============================================================
                    //  学号输入区域（始终可见）
                    // ============================================================

                    // 学号输入框 + 查询按钮
                    item(key = "student_id_input") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextField(
                                value = recharge.studentId,
                                onValueChange = { viewModel.setAccountStudentId(it) },
                                label = { Text(stringResource(R.string.recharge_student_id_label)) },
                                placeholder = { Text(stringResource(R.string.recharge_student_id_placeholder)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                modifier = Modifier.weight(1f),
                                enabled = !recharge.isQuerying,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                )
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = { viewModel.queryAccountRoomList() },
                                modifier = Modifier.height(56.dp),
                                enabled = recharge.studentId.trim().isNotBlank()
                                        && !recharge.isQuerying,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                if (recharge.isQuerying) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        text = stringResource(R.string.recharge_query),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // 错误提示
                    val errorMsg = recharge.queryError
                    if (errorMsg != null) {
                        item(key = "query_error") {
                            Text(
                                text = errorMsg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // ============================================================
                    //  查询成功后：显示充值内容
                    // ============================================================
                    if (showRechargeContent) {
                        // ── 账户信息卡片 ──
                        item(key = "account_card") {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    // 第一行：我的账户
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(
                                                if (canSwitchAccount) {
                                                    Modifier.clickable { showRoomSwitchDialog = true }
                                                } else {
                                                    Modifier
                                                }
                                            ),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.recharge_my_account),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = accountName,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (canSwitchAccount) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                                    contentDescription = stringResource(R.string.recharge_switch_account),
                                                    modifier = Modifier.size(20.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // 第二行：余额 + 刷新按钮
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.recharge_balance),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.height(IntrinsicSize.Min)
                                        ) {
                                            if (recharge.balanceLoading) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(20.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                Text(
                                                    text = recharge.balance?.let {
                                                        String.format(Locale.US, "%.2f", it.userBalance)
                                                    } ?: stringResource(R.string.common_loading),
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Outlined.Refresh,
                                                contentDescription = stringResource(R.string.recharge_refresh_balance),
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clickable(enabled = !recharge.balanceLoading) {
                                                        viewModel.loadRechargeBalance()
                                                    },
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // ── 预设金额网格 ──
                        item(key = "amount_grid") {
                            AmountGrid(
                                selectedAmount = recharge.selectedAmount,
                                onAmountSelected = { viewModel.selectRechargeAmount(it) }
                            )
                        }

                        // ── 自定义金额输入 ──
                        item(key = "custom_amount") {
                            TextField(
                                value = recharge.customAmount,
                                onValueChange = { viewModel.setCustomRechargeAmount(it) },
                                label = { Text(stringResource(R.string.recharge_custom_amount_label)) },
                                placeholder = { Text(stringResource(R.string.recharge_custom_amount_placeholder)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // ── "立即充值"按钮 ──
                        item(key = "pay_button") {
                            Button(
                                onClick = { nav.navigate(Routes.PAYMENT_SELECTION) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                enabled = hasValidAmount,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(
                                    text = stringResource(R.string.recharge_pay_now),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // ── 其他充值方式 ──
                        item(key = "other_methods") {
                            Text(
                                text = stringResource(R.string.recharge_other_methods),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showOtherRechargeDialog = true }
                                    .padding(vertical = 10.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // ── 重要提示 ──
                        item(key = "important_notes") {
                            ImportantNotesCard()
                        }
                    }
                }
            }
        }
    }

    // ================================================================
    //  房间切换弹窗（仅账号模式有多房间时显示）
    // ================================================================
    RoomSelectionDialog(
        visible = showRoomSwitchDialog && recharge.roomList.isNotEmpty(),
        rooms = recharge.roomList,
        onNavigateToWebView = { url, title -> nav.navigate(Routes.unifiedWebViewRoute(url, title)) },
        onRoomSelected = { room ->
            viewModel.switchAccountRoom(room)
            showRoomSwitchDialog = false
        },
        onDismiss = {
            showRoomSwitchDialog = false
        }
    )

    // ================================================================
    //  其他充值方式 - Bottom Sheet
    // ================================================================
    BottomSheetDialog(
        visible = showOtherRechargeDialog,
        onDismissRequest = { showOtherRechargeDialog = false },
            title = stringResource(R.string.recharge_other_method_title)
        ) {
            // 今日校园充值
            BottomSheetItem(
                icon = Icons.Outlined.Store,
                title = stringResource(R.string.recharge_method_campus),
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("campusnextins://"))
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        snackbar.show(resources.getString(R.string.recharge_install_campus_app))
                    }
                    showOtherRechargeDialog = false
                }
            )

            // 应用内 H5 充值
            BottomSheetItem(
                icon = Icons.Outlined.Public,
                title = stringResource(R.string.recharge_method_inapp_h5),
                onClick = {
                    showOtherRechargeDialog = false
                    nav.navigate(Routes.RECHARGE_H5_WEBVIEW)
                }
            )

            // 浏览器 H5 充值
            BottomSheetItem(
                icon = Icons.Outlined.OpenInBrowser,
                title = stringResource(R.string.recharge_method_browser_h5),
                onClick = {
                    try {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(Routes.H5_RECHARGE_URL)
                        )
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        snackbar.show(resources.getString(R.string.common_no_browser))
                    }
                    showOtherRechargeDialog = false
                }
            )
        }
}

// ================================================================
//  重要提示卡片
// ================================================================

/**
 * 重要提示卡片
 */
@Composable
private fun ImportantNotesCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.recharge_important_notes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = stringResource(R.string.recharge_note_time),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.recharge_note_delay),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.recharge_note_power),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ================================================================
//  房间选择对话框 - Bottom Sheet
// ================================================================

/**
 * 房间选择对话框
 * 当学号绑定多个房间时，弹出让用户选择一个
 */
@Composable
private fun RoomSelectionDialog(
    visible: Boolean = true,
    rooms: List<UserRoomInfo>,
    onRoomSelected: (UserRoomInfo) -> Unit,
    onDismiss: () -> Unit,
    onNavigateToWebView: (String, String) -> Unit = { _, _ -> }
) {
    val resources = LocalResources.current
    BottomSheetDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.recharge_select_room_title),
        leadingButton = {
            TextButton(onClick = {
                onDismiss()
                onNavigateToWebView("https://electricitypay.cqwu.edu.cn/wxms/pages/user/user-add",
                    resources.getString(R.string.electricity_bind_account))
            }) {
                Text(stringResource(R.string.electricity_bind_account))
            }
        },
        trailingButton = {
            TextButton(onClick = {
                onDismiss()
                onNavigateToWebView("https://electricitypay.cqwu.edu.cn/wxms/pages/user/user-del",
                    resources.getString(R.string.electricity_unbind_account))
            }) {
                Text(stringResource(R.string.electricity_unbind_account))
            }
        }
    ) {
        Text(
            text = stringResource(R.string.recharge_select_room_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        rooms.forEach { room ->
            BottomSheetItem(
                icon = Icons.Outlined.Home,
                title = room.fullName.ifBlank { room.roomName },
                onClick = {
                    onRoomSelected(room)
                    onDismiss()
                }
            )
        }
    }
}
