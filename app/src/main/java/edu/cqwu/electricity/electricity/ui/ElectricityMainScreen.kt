package edu.cqwu.electricity.electricity.ui

import edu.cqwu.electricity.theme.ui.currentTopBarColors

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MonetizationOn
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material3.CircularProgressIndicator
import edu.cqwu.electricity.common.ui.AppScaledDropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.app.Routes
import edu.cqwu.electricity.electricity.data.SelectionStep
import edu.cqwu.electricity.common.ui.BottomSheetDialogV2
import edu.cqwu.electricity.common.ui.BottomSheetItem
import edu.cqwu.electricity.theme.ui.LocalNavController
import edu.cqwu.electricity.common.ui.ReLoginContent
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.theme.ui.resolve
import edu.cqwu.electricity.theme.util.ToastUtils
import kotlinx.coroutines.launch

/** 底部导航栏的三个 Tab（标签在 Composable 内通过 stringResource 获取） */
private val electricityTabIcons = listOf(
    Icons.Outlined.Search,
    Icons.Outlined.MonetizationOn,
    Icons.Outlined.Home,
)

private val electricityTabLabelKeys = listOf(
    R.string.electricity_tab_query,
    R.string.electricity_tab_recharge,
    R.string.electricity_tab_myroom,
)

private val electricityTitleKeys = listOf(
    R.string.electricity_query_title,
    R.string.electricity_tab_recharge,
    R.string.electricity_tab_my_dorm,
)

/**
 * 电费查询主页 — 底部导航栏容器
 *
 * 包含三个 Tab：
 * - 查询：建筑选择（校区→楼栋→房间）
 * - 充值：学号查询→选择金额→充值
 * - 我的：已绑定寝室余额查询
 *
 * 三个 Tab 共享同一个 [ElectricityViewModel]，状态互通。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElectricityMainScreen(
    viewModel: ElectricityViewModel,
    rechargeViewModel: RechargeViewModel,
    myRoomViewModel: MyRoomViewModel,
    onBack: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { electricityTabIcons.size })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current
    val uiState by viewModel.uiState.collectAsState()
    val myRoomState by myRoomViewModel.uiState.collectAsState()
    val snackbar = LocalSnackbarController.current
    val nav = LocalNavController.current

    // ── 我的寝室 Tab 控制状态 ──
    var showRoomSwitchSheet by remember { mutableStateOf(false) }

    // ── 充值 Tab 提示弹窗状态 ──
    var showRechargeInfoDialog by remember { mutableStateOf(false) }

    // ── 外部触发其他充值方式弹窗 ──
    var triggerOtherRecharge by remember { mutableStateOf(false) }

    val topBarColors = currentTopBarColors()

    // 当前 Tab 标题：查询 Tab 进入余额结果页后显示"电费查询结果"
    val currentTitle = when {
        pagerState.currentPage == 0 && uiState.currentStep == SelectionStep.DONE -> stringResource(R.string.dashboard_title)
        else -> stringResource(electricityTitleKeys[pagerState.currentPage])
    }

    // ── 我的寝室三点菜单状态 ──
    var showMyRoomMenu by remember { mutableStateOf(false) }

    // ── 文件导出启动器 ──
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

    // 查询 Tab 的统一返回处理：房间选择 → 校区/楼栋列表 → 退出
    val handleQueryTabBack = {
        when (uiState.currentStep) {
            SelectionStep.AREA -> onBack()
            SelectionStep.ROOM_GRID -> viewModel.goBack()
            SelectionStep.DONE -> viewModel.onReturnedFromDashboard()
        }
    }

    // 系统返回键：查询 Tab 内仅在校区/楼栋列表页放行退出
    BackHandler(
        enabled = pagerState.currentPage == 0 && uiState.currentStep != SelectionStep.AREA
    ) {
        handleQueryTabBack()
    }

    // 查询 Tab 显示余额结果：必须是通过 selectRoom() 标记的 DONE 状态
    val showQueryResult = pagerState.currentPage == 0
        && uiState.selectedRoom != null
        && uiState.currentStep == SelectionStep.DONE
    val room = uiState.selectedRoom
    val balance = uiState.balance

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentTitle, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (pagerState.currentPage == 0) {
                                handleQueryTabBack()
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // ── Tab 0：查询 Tab 显示余额结果时，显示三点菜单 ──
                    if (pagerState.currentPage == 0 && showQueryResult) {
                        DashboardMenuButton(room = room, balance = balance)
                    }
                    // ── Tab 1：充值 Tab 显示提示按钮 ──
                    if (pagerState.currentPage == 1) {
                        IconButton(onClick = { showRechargeInfoDialog = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = stringResource(R.string.recharge_hint),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    // ── Tab 2：我的寝室 Tab 显示切换寝室按钮和三点菜单 ──
                    if (pagerState.currentPage == 2 && myRoomState.selectedRoom != null) {
                        // 切换寝室按钮（仅多房间时显示）
                        if (myRoomState.myRoomList.size > 1) {
                            IconButton(onClick = { showRoomSwitchSheet = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.SyncAlt,
                                    contentDescription = stringResource(R.string.electricity_switch_dorm)
                                )
                            }
                        }
                        Box {
                            IconButton(onClick = { showMyRoomMenu = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreVert,
                                    contentDescription = stringResource(R.string.common_more_options),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            AppScaledDropdownMenu(
                                expanded = showMyRoomMenu,
                                onDismissRequest = { showMyRoomMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.common_copy)) },
                                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                                    onClick = {
                                        showMyRoomMenu = false
                                        val text = getDashboardTextContent(myRoomState.selectedRoom, myRoomState.balance, resources)
                                        copyToClipboard(context, text, resources.getString(R.string.dashboard_title), snackbar)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.common_export)) },
                                    leadingIcon = { Icon(Icons.Outlined.FileDownload, contentDescription = null) },
                                    onClick = {
                                        showMyRoomMenu = false
                                        pendingExportText = getDashboardTextContent(myRoomState.selectedRoom, myRoomState.balance, resources)
                                        pendingExportLabel = resources.getString(R.string.dashboard_title)
                                        saveFileLauncher.launch("electricity_dashboard.txt")
                                    }
                                )
                            }
                        }
                    }
                },
                colors = topBarColors
            )
        },
        bottomBar = {
            NavigationBar {
                electricityTabIcons.forEachIndexed { index, icon ->
                    val label = stringResource(electricityTabLabelKeys[index])
                    NavigationBarItem(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            if (pagerState.currentPage != index) {
                                scope.launch { pagerState.animateScrollToPage(index) }
                            }
                        },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                    )
                }
            }
        }
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.padding(paddingValues),
        ) { page ->
            when (page) {
                0 -> {
                    // ── 查询 Tab：两阶段视图 ──
                    if (showQueryResult) {
                        DashboardScreen(
                            room = room,
                            balance = balance,
                            isRefreshing = uiState.isBalanceRefreshing,
                            isLoading = uiState.isLoading,
                            error = uiState.error,
                            onRefresh = { viewModel.refreshBalance() },
                        )
                    } else {
                        BuildingSelectionScreen(
                            viewModel = viewModel,
                        )
                    }
                }
                1 -> {
                    // ── 充值 Tab ──
                    RechargeScreen(
                        viewModel = rechargeViewModel,
                        triggerOtherRecharge = triggerOtherRecharge,
                        onOtherRechargeTriggered = { triggerOtherRecharge = false },
                    )
                }
                2 -> {
                    // ── 我的 Tab ──
                    MyRoomDashboardTab(
                        viewModel = myRoomViewModel,
                        onReLogin = { nav.navigate(Routes.loginRoute()) },
                    )
                }
            }
        }
    }

    // ── 充值 Tab 提示信息弹窗 - Bottom Sheet ──
    BottomSheetDialogV2(
        visible = showRechargeInfoDialog,
        onDismissRequest = { showRechargeInfoDialog = false },
        title = stringResource(R.string.recharge_hint)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 第1条：纯文本（学号/编号说明）
            Text(
                text = stringResource(R.string.recharge_hint_item1),
                style = MaterialTheme.typography.bodyMedium
            )

            // 第2条：带"点击此处"外部链接（注册）
            val link2Text = buildAnnotatedString {
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                    append(stringResource(R.string.recharge_hint_item2))
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
                            snackbar.show(resources.getString(R.string.common_no_browser))
                        }
                    }
                )
                append(stringResource(R.string.recharge_hint_item2_link))
                pop()
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                    append(stringResource(R.string.recharge_hint_item2_suffix))
                }
            }
            Text(
                text = link2Text,
                style = MaterialTheme.typography.bodyMedium
            )

            // 第3条：带"其他充值方式"内部链接
            val link3Text = buildAnnotatedString {
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
                        showRechargeInfoDialog = false
                        triggerOtherRecharge = true
                    }
                )
                append(stringResource(R.string.recharge_other_methods))
                pop()
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) {
                    append(stringResource(R.string.recharge_hint_item3_suffix))
                }
            }
            Text(
                text = link3Text,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }

    // ── 我的寝室房间切换 BottomSheet ──
    BottomSheetDialogV2(
        visible = showRoomSwitchSheet && myRoomState.myRoomList.isNotEmpty(),
        onDismissRequest = { showRoomSwitchSheet = false },
            title = stringResource(R.string.dashboard_select_dorm),
            leadingButton = {
                TextButton(onClick = {
                    showRoomSwitchSheet = false
                    nav.navigate(Routes.unifiedWebViewRoute("https://electricitypay.cqwu.edu.cn/wxms/pages/user/user-add",
                        resources.getString(R.string.electricity_bind_account)))
                }) {
                    Text(stringResource(R.string.electricity_bind_account))
                }
            },
            trailingButton = {
                TextButton(onClick = {
                    showRoomSwitchSheet = false
                    nav.navigate(Routes.unifiedWebViewRoute("https://electricitypay.cqwu.edu.cn/wxms/pages/user/user-del",
                        resources.getString(R.string.electricity_unbind_account)))
                }) {
                    Text(stringResource(R.string.electricity_unbind_account))
                }
            }
        ) {
            Text(
                text = stringResource(R.string.dashboard_select_room),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            myRoomState.myRoomList.forEach { room ->
                BottomSheetItem(
                    icon = Icons.Outlined.Home,
                    title = room.fullName.ifBlank { room.roomName },
                    onClick = {
                        myRoomViewModel.switchToMyRoom(room)
                        showRoomSwitchSheet = false
                    }
                )
            }
        }
    }

/**
 * "我的" Tab 内容
 *
 * 首次展示时自动查询当前登录用户的寝室，
 * 查询成功后显示 [DashboardScreen] 内容。
 */
@Composable
private fun MyRoomDashboardTab(
    viewModel: MyRoomViewModel,
    onReLogin: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbar = LocalSnackbarController.current
    val resources = LocalResources.current

    // 收集错误事件
    LaunchedEffect(Unit) {
        viewModel.errorEvent.collect { errorMsg ->
            snackbar.show(errorMsg.resolve(resources), ToastUtils.Type.ERROR)
        }
    }

    // 仅在首次查询（无缓存数据）时自动查询当前登录用户的寝室
    // 后续切换到其他 Tab 再回来时，使用缓存数据，不再重复查询
    LaunchedEffect(Unit) {
        if (uiState.selectedRoom == null) {
            viewModel.fastQueryMyRoom()
        }
    }

    // 正在查询我的寝室
    val isLoadingMyRoom = uiState.isMyRoomQuerying
        || (uiState.myRoomList.isEmpty() && uiState.selectedRoom == null)

    if (isLoadingMyRoom) {
        // 加载中：显示骨架屏
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.electricity_myroom_querying),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else if (uiState.requiresReLogin) {
        ReLoginContent(
            errorMessage = null,
            requiresReLogin = true,
            onReLogin = onReLogin,
            onRetry = { viewModel.fastQueryMyRoom() },
        )
    } else if (uiState.selectedRoom == null) {
        ReLoginContent(
            errorMessage = uiState.queryError?.resolve(resources) ?: stringResource(R.string.common_no_room_data),
            requiresReLogin = false,
            onReLogin = onReLogin,
            onRetry = { viewModel.fastQueryMyRoom() },
        )
    } else {
        // 已有房间数据，显示 Dashboard
        DashboardScreen(
            room = uiState.selectedRoom,
            balance = uiState.balance,
            isRefreshing = uiState.isBalanceRefreshing,
            isLoading = false,
            error = uiState.error,
            onRefresh = { viewModel.refreshBalance() },
        )
    }
}
