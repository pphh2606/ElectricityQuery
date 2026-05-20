package edu.cqwu.electricity.ui.recharge

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Store
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import edu.cqwu.electricity.ui.theme.LocalTopBarState
import edu.cqwu.electricity.ui.theme.toTopAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.data.local.AccountStore
import edu.cqwu.electricity.data.model.UserRoomInfo
import edu.cqwu.electricity.data.network.AccountManager
import edu.cqwu.electricity.ui.components.BottomSheetDialog
import edu.cqwu.electricity.ui.components.BottomSheetItem
import edu.cqwu.electricity.ui.components.LocalSnackbarController
import edu.cqwu.electricity.ui.navigation.Routes
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
    onNavigateToPayment: () -> Unit,
    onNavigateToH5Recharge: () -> Unit = {},
    showTopBar: Boolean = true
) {
    val recharge by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbar = LocalSnackbarController.current
    val topBarColors = LocalTopBarState.current.style.toTopAppBarColors(MaterialTheme.colorScheme)

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


    // 是否有有效金额
    val hasValidAmount = recharge.selectedAmount != null
            || recharge.customAmount.trim().toDoubleOrNull()?.let { it > 0 } == true

    // 账户名称显示
    val accountName: String = recharge.fullName.takeIf { it.isNotBlank() }
        ?: ""

    // 是否可以切换账户（仅账号模式有多房间时允许切换）
    val canSwitchAccount = recharge.roomList.size > 1

    if (showTopBar) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("充值", fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = {
                                viewModel.clearRechargeState()
                                viewModel.clearAccountRechargeState()
                                onBack()
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "返回",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        actions = {
                            // 提示按钮始终显示
                            IconButton(onClick = { showInfoDialog = true }) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = "提示",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        colors = topBarColors
                    )
                }
            ) { paddingValues ->
                RechargeContent(
                    viewModel = viewModel,
                    recharge = recharge,
                    showInfoDialog = showInfoDialog,
                    onShowInfoDialogChange = { showInfoDialog = it },
                    showOtherRechargeDialog = showOtherRechargeDialog,
                    onShowOtherRechargeDialogChange = { showOtherRechargeDialog = it },
                    showRoomSwitchDialog = showRoomSwitchDialog,
                    onShowRoomSwitchDialogChange = { showRoomSwitchDialog = it },
                    paddingValues = paddingValues,
                    hasValidAmount = hasValidAmount,
                    accountName = accountName,
                    canSwitchAccount = canSwitchAccount,
                    hasQueriedSuccess = hasQueriedSuccess,
                    showRechargeContent = showRechargeContent,
                    onNavigateToPayment = onNavigateToPayment,
                    onNavigateToH5Recharge = onNavigateToH5Recharge,
                )
            }
        }
    } else {
        // 无 TopAppBar/Scaffold 模式（用于底部导航栏 Tab 内嵌）
        RechargeContent(
            viewModel = viewModel,
            recharge = recharge,
            showInfoDialog = showInfoDialog,
            onShowInfoDialogChange = { showInfoDialog = it },
            showOtherRechargeDialog = showOtherRechargeDialog,
            onShowOtherRechargeDialogChange = { showOtherRechargeDialog = it },
            showRoomSwitchDialog = showRoomSwitchDialog,
            onShowRoomSwitchDialogChange = { showRoomSwitchDialog = it },
            paddingValues = PaddingValues(0.dp),
            hasValidAmount = hasValidAmount,
            accountName = accountName,
            canSwitchAccount = canSwitchAccount,
            hasQueriedSuccess = hasQueriedSuccess,
            showRechargeContent = showRechargeContent,
            onNavigateToPayment = onNavigateToPayment,
            onNavigateToH5Recharge = onNavigateToH5Recharge,
        )
    }
}

/**
 * 充值页面内容区域（不含 Scaffold/TopAppBar）
 */
@Composable
private fun RechargeContent(
    viewModel: RechargeViewModel,
    recharge: RechargeUiState,
    showInfoDialog: Boolean,
    onShowInfoDialogChange: (Boolean) -> Unit,
    showOtherRechargeDialog: Boolean,
    onShowOtherRechargeDialogChange: (Boolean) -> Unit,
    showRoomSwitchDialog: Boolean,
    onShowRoomSwitchDialogChange: (Boolean) -> Unit,
    paddingValues: androidx.compose.foundation.layout.PaddingValues,
    hasValidAmount: Boolean,
    accountName: String,
    canSwitchAccount: Boolean,
    hasQueriedSuccess: Boolean,
    showRechargeContent: Boolean,
    onNavigateToPayment: () -> Unit,
    onNavigateToH5Recharge: () -> Unit,
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarController.current

    PullToRefreshBox(
        isRefreshing = recharge.isRefreshing,
        onRefresh = { viewModel.refreshRechargeData() },
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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
                label = { Text("请输入学号 或 userId") },
                placeholder = { Text("例如 2022010101 或 12345") },
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
                        text = "查询",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 错误提示
        if (recharge.error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = recharge.error ?: "",
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
                                    Modifier.clickable { onShowRoomSwitchDialogChange(true) }
                                } else {
                                    Modifier
                                }
                            ),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "我的账号",
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
                                    contentDescription = "切换账户",
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
                            text = "余额（元）",
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
                                    } ?: "加载中...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "刷新余额",
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
                label = { Text("自定义金额（元）") },
                placeholder = { Text("例如 0.01") },
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
                onClick = { onNavigateToPayment() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = hasValidAmount,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "立即充值",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── 重要提示 ──
            ImportantNotesCard()
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── 其他充值方式（始终显示，无需查询） ──
        Text(
            text = "其他充值方式",
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onShowOtherRechargeDialogChange(true) }
                .padding(vertical = 12.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
} // PullToRefreshBox

    // ================================================================
    //  房间切换弹窗（仅账号模式有多房间时显示）
    // ================================================================
    if (showRoomSwitchDialog && recharge.roomList.isNotEmpty()) {
        RoomSelectionDialog(
            rooms = recharge.roomList,
            onRoomSelected = { room ->
                viewModel.switchAccountRoom(room)
                onShowRoomSwitchDialogChange(false)
            },
            onDismiss = {
                onShowRoomSwitchDialogChange(false)
            }
        )
    }

    // ================================================================
    //  提示信息弹窗 - Bottom Sheet
    // ================================================================
    if (showInfoDialog) {
        BottomSheetDialog(
            onDismissRequest = { onShowInfoDialogChange(false) },
            title = "提示"
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 第1条：外部链接
                val link1Text = buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                        append("1.如果获取用户失败，请先")
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
                                snackbar.show("未找到可用的浏览器应用")
                            }
                        }
                    )
                    append("点击此处")
                    pop()
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                        append("在平台注册。")
                    }
                }
                Text(
                    text = link1Text,
                    style = MaterialTheme.typography.bodyMedium
                )

                // 第2条：纯文本
                Text(
                    text = "2.若注册后还是提示获取用户信息失败，请尝试填写寝室管理员学号/寝室内第一个注册了平台的学号。",
                    style = MaterialTheme.typography.bodyMedium
                )

                // 第3条：点击触发其他充值弹窗
                val link2Text = buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                        append("3.充值记录会记录在学号所对应的用户头上。如介意请")
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
                            onShowInfoDialogChange(false)
                            onShowOtherRechargeDialogChange(true)
                        }
                    )
                    append("点击此处")
                    pop()
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                        append("选择其他充值方式。")
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
            onDismissRequest = { onShowOtherRechargeDialogChange(false) },
            title = "选择充值方式"
        ) {
            // 今日校园充值
            BottomSheetItem(
                icon = Icons.Default.Store,
                title = "今日校园充值",
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("campusnextins://"))
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        snackbar.show("请先安装今日校园App")
                    }
                    onShowOtherRechargeDialogChange(false)
                }
            )

            // 应用内 H5 充值
            BottomSheetItem(
                icon = Icons.Default.Public,
                title = "应用内H5充值",
                onClick = {
                    onShowOtherRechargeDialogChange(false)
                    onNavigateToH5Recharge()
                }
            )

            // 浏览器 H5 充值
            BottomSheetItem(
                icon = Icons.Default.OpenInBrowser,
                title = "浏览器H5充值",
                onClick = {
                    try {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(Routes.H5_RECHARGE_URL)
                        )
                        context.startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        snackbar.show("未找到可用的浏览器应用")
                    }
                    onShowOtherRechargeDialogChange(false)
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
                            text = "${amount.toInt()}元",
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
                text = "重要提示",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "1. 充值时间为 00:05~23:55，其他时间无法充值",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "2. 如果支付成功但未及时到账，请等待5分钟",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "3. 如果充值完成有余额但未通电，请联系管理员处理",
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
    onDismiss: () -> Unit
) {
    BottomSheetDialog(
        onDismissRequest = onDismiss,
        title = "选择充值房间"
    ) {
        Text(
            text = "该学号下绑定多个房间，请选择要充值的房间：",
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

