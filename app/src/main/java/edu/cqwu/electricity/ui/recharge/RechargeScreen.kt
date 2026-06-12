package edu.cqwu.electricity.ui.recharge

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.data.local.AccountStore
import edu.cqwu.electricity.data.model.UserRoomInfo
import edu.cqwu.electricity.data.network.AccountManager
import edu.cqwu.electricity.ui.components.BottomSheetDialog
import edu.cqwu.electricity.ui.components.BottomSheetItem
import edu.cqwu.electricity.ui.components.LocalSnackbarController
import edu.cqwu.electricity.ui.navigation.Routes
import edu.cqwu.electricity.ui.theme.LocalNavController
import edu.cqwu.electricity.util.ToastUtils

/**
 * 预设充值金额列表
 */
private val PRESET_AMOUNTS = listOf(5.0, 10.0, 20.0, 50.0, 100.0, 200.0)

/**
 * 充值页面
 *
 * 将学号输入/查询与充值金额选择合为一页。
 * 顶部显示学号输入框 + 查询按钮，查询成功后在下方显示充值 UI（账户信息 + 金额选择 + 立即充值）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RechargeScreen(
    viewModel: RechargeViewModel,
    onBack: () -> Unit,
) {
    val recharge by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbar = LocalSnackbarController.current
    val nav = LocalNavController.current

    // 房间切换弹窗状态
    var showRoomSwitchDialog by remember { mutableStateOf(false) }

    // 提示信息弹窗
    var showInfoDialog by remember { mutableStateOf(false) }

    // 其他充值方式弹窗
    var showOtherRechargeDialog by remember { mutableStateOf(false) }

    // 是否已查询成功（fullName 有值）
    val hasQueriedSuccess = recharge.fullName.isNotBlank()
    // 是否显示充值内容：查询成功后才显示
    val showRechargeContent = hasQueriedSuccess

    // ── 自动填充已登录用户的学号并查询 ──
    // 优先取内存中的活跃用户，其次取本地持久化的最近登录学号
    val loggedInStudentId = remember {
        AccountManager.getActiveUser()
            ?: AccountStore(context).getAllAccountNames().firstOrNull()
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
    LaunchedEffect(recharge.rechargeError) {
        recharge.rechargeError?.let {
            snackbar.show(it, ToastUtils.Type.ERROR)
            viewModel.clearRechargeError()
        }
    }

    // 是否有有效金额
    val hasValidAmount = recharge.selectedAmount != null
            || recharge.customAmount.trim().toDoubleOrNull()?.let { it > 0 } == true

    // 账户名称显示
    val accountName: String = recharge.fullName.takeIf { it.isNotBlank() }
        ?: ""

    // 是否可以切换账户（仅账号模式有多房间时允许切换）
    val canSwitchAccount = recharge.roomList.size > 1

    PullToRefreshBox(
        isRefreshing = recharge.isRefreshing,
        onRefresh = { viewModel.refreshRechargeData() },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        // ============================================================
        //  学号输入区域（始终可见，紧贴标题栏）
        // ============================================================
        Spacer(modifier = Modifier.height(4.dp))

        // 学号输入框 + 查询按钮
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

        // 错误提示
        val errorMsg = recharge.error
        if (errorMsg != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMsg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ============================================================
        //  查询成功后：显示充值内容
        // ============================================================
        if (showRechargeContent) {
            // ── 账户信息卡片 ──
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
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
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
                                        String.format("%.2f", it.userBalance)
                                    } ?: stringResource(R.string.common_loading),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Filled.Refresh,
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

            Spacer(modifier = Modifier.height(24.dp))

            // ── 预设金额网格 ──
            AmountGrid(
                selectedAmount = recharge.selectedAmount,
                onAmountSelected = { viewModel.selectRechargeAmount(it) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── 自定义金额输入 ──
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

            Spacer(modifier = Modifier.height(16.dp))

            // ── "立即充值"按钮 ──
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

            Spacer(modifier = Modifier.height(16.dp))

            // ── 其他充值方式 ──
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

            Spacer(modifier = Modifier.height(12.dp))

            // ── 重要提示 ──
            ImportantNotesCard()
        }
    }
    } // PullToRefreshBox

    // ================================================================
    //  房间切换弹窗（仅账号模式有多房间时显示）
    // ================================================================
    if (showRoomSwitchDialog && recharge.roomList.isNotEmpty()) {
        RoomSelectionDialog(
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
    }

    // ================================================================
    //  提示信息弹窗 - Bottom Sheet
    // ================================================================
    if (showInfoDialog) {
        BottomSheetDialog(
            onDismissRequest = { showInfoDialog = false },
            title = stringResource(R.string.recharge_hint)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 第1条：外部链接
                val link1Text = buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                        append(stringResource(R.string.recharge_hint_item1))
                    }
                    pushLink(
                        LinkAnnotation.Clickable(
                            tag = "url",
                            styles = TextLinkStyles(
                                SpanStyle(
                                    color = MaterialTheme.colorScheme.primary,
                                    textDecoration = TextDecoration.Underline
                                )
                            )
                        ) {
                            try {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://authserver.cqwu.edu.cn/authserver/login?service=https://electricitypay.cqwu.edu.cn/wechat/wx/auth/login")
                                )
                                context.startActivity(intent)
                            } catch (_: ActivityNotFoundException) {
                                snackbar.show(context.getString(R.string.common_no_browser))
                            }
                        }
                    )
                    append(stringResource(R.string.recharge_hint_item1_link))
                    pop()
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                        append(stringResource(R.string.recharge_hint_item1_suffix))
                    }
                }
                Text(
                    text = link1Text,
                    style = MaterialTheme.typography.bodyMedium
                )

                // 第2条：纯文本
                Text(
                    text = stringResource(R.string.recharge_hint_item2),
                    style = MaterialTheme.typography.bodyMedium
                )

                // 第3条：点击触发其他充值弹窗
                val link2Text = buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                        append(stringResource(R.string.recharge_hint_item3_prefix))
                    }
                    pushLink(
                        LinkAnnotation.Clickable(
                            tag = "action",
                            styles = TextLinkStyles(
                                SpanStyle(
                                    color = MaterialTheme.colorScheme.primary,
                                    textDecoration = TextDecoration.Underline
                                )
                            )
                        ) {
                            showInfoDialog = false
                            showOtherRechargeDialog = true
                        }
                    )
                    append(stringResource(R.string.recharge_hint_item1_link))
                    pop()
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                        append(stringResource(R.string.recharge_hint_item3_suffix))
                    }
                }
                Text(
                    text = link2Text,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    // ================================================================
    //  其他充值方式 - Bottom Sheet
    // ================================================================
    if (showOtherRechargeDialog) {
        BottomSheetDialog(
            onDismissRequest = { showOtherRechargeDialog = false },
            title = stringResource(R.string.recharge_other_method_title)
        ) {
            // 今日校园充值
            BottomSheetItem(
                icon = Icons.Default.Store,
                title = stringResource(R.string.recharge_method_campus),
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("campusnextins://"))
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        snackbar.show(context.getString(R.string.recharge_install_campus_app))
                    }
                    showOtherRechargeDialog = false
                }
            )

            // 应用内 H5 充值
            BottomSheetItem(
                icon = Icons.Default.Public,
                title = stringResource(R.string.recharge_method_inapp_h5),
                onClick = {
                    showOtherRechargeDialog = false
                    nav.navigate(Routes.RECHARGE_H5_WEBVIEW)
                }
            )

            // 浏览器 H5 充值
            BottomSheetItem(
                icon = Icons.Default.OpenInBrowser,
                title = stringResource(R.string.recharge_method_browser_h5),
                onClick = {
                    try {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(Routes.H5_RECHARGE_URL)
                        )
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        snackbar.show(context.getString(R.string.common_no_browser))
                    }
                    showOtherRechargeDialog = false
                }
            )
        }
    }
}

// ================================================================
//  预设金额网格
// ================================================================

/**
 * 预设金额网格
 */
@Composable
private fun AmountGrid(
    selectedAmount: Double?,
    onAmountSelected: (Double) -> Unit
) {
    // 每行3个按钮
    val rows = PRESET_AMOUNTS.chunked(3)

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { amount ->
                    val isSelected = selectedAmount == amount
                    OutlinedButton(
                        onClick = { onAmountSelected(amount) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.recharge_preset_amount, amount.toInt()),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
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
    rooms: List<UserRoomInfo>,
    onRoomSelected: (UserRoomInfo) -> Unit,
    onDismiss: () -> Unit,
    onNavigateToWebView: (String, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    BottomSheetDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.recharge_select_room_title),
        leadingButton = {
            TextButton(onClick = {
                onDismiss()
                onNavigateToWebView("https://electricitypay.cqwu.edu.cn/wxms/pages/user/user-add",
                    context.getString(R.string.electricity_bind_account))
            }) {
                Text(stringResource(R.string.electricity_bind_account))
            }
        },
        trailingButton = {
            TextButton(onClick = {
                onDismiss()
                onNavigateToWebView("https://electricitypay.cqwu.edu.cn/wxms/pages/user/user-del",
                    context.getString(R.string.electricity_unbind_account))
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
                icon = Icons.Default.Home,
                title = room.fullName.ifBlank { room.roomName },
                onClick = {
                    onRoomSelected(room)
                    onDismiss()
                }
            )
        }
    }
}

