package edu.cqwu.electricity.ui.electricity

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.DisposableEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.data.local.AccountStore
import edu.cqwu.electricity.data.model.DetailType
import edu.cqwu.electricity.data.model.SelectionStep
import edu.cqwu.electricity.data.network.AccountManager
import edu.cqwu.electricity.ui.components.LocalSnackbarController
import edu.cqwu.electricity.ui.components.BottomSheetDialog
import edu.cqwu.electricity.ui.components.BottomSheetItem
import edu.cqwu.electricity.ui.myroom.MyRoomViewModel
import edu.cqwu.electricity.ui.recharge.RechargeScreen
import edu.cqwu.electricity.ui.recharge.RechargeViewModel
import edu.cqwu.electricity.ui.theme.LocalTopBarState
import edu.cqwu.electricity.ui.theme.toTopAppBarColors
import edu.cqwu.electricity.util.ToastUtils
import kotlinx.coroutines.launch
import java.io.OutputStream

/**
 * Tab 数据模型
 */
private data class TabItem(
    val label: String,
    val icon: ImageVector
)

/** 底部导航栏的三个 Tab */
private val electricityTabs = listOf(
    TabItem("查询", Icons.Default.Search),
    TabItem("充值", Icons.Default.AccountBalance),
    TabItem("我的", Icons.Default.Home),
)

/** 各 Tab 的 TopAppBar 标题 */
private val tabTitles = listOf("电费查询", "充值", "我的寝室")

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
    onNavigateToWebView: (url: String, title: String) -> Unit,
    onNavigateToDetail: (DetailType) -> Unit,
    onNavigateToPayment: () -> Unit,
    onNavigateToH5Recharge: () -> Unit,
    onNavigateToRechargeRecord: () -> Unit,
    onReLogin: () -> Unit = {},
) {
    // ── 退出 3 Tab 页面时重置所有 ViewModel 状态 ──
    DisposableEffect(Unit) {
        onDispose {
            viewModel.resetToInitial()
        }
    }

    val pagerState = rememberPagerState(pageCount = { electricityTabs.size })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val myRoomState by myRoomViewModel.uiState.collectAsState()
    val snackbar = LocalSnackbarController.current

    // ── 我的寝室 Tab 控制状态 ──
    var showRoomSwitchSheet by remember { mutableStateOf(false) }

    val topBarColors = LocalTopBarState.current.style.toTopAppBarColors(MaterialTheme.colorScheme)

    // 获取当前登录学号
    val loggedInStudentId = remember {
        AccountManager.getActiveUser()
            ?: AccountStore(context).getAllAccountNames().firstOrNull()
    }

    // 当前 Tab 标题：查询 Tab 选中房间后显示"电费查询结果"
    val currentTitle = when {
        pagerState.currentPage == 0 && uiState.selectedRoom != null -> "电费查询结果"
        else -> tabTitles[pagerState.currentPage]
    }

    // ── 三点菜单状态（查询 Tab 和我的 Tab 各自独立）──
    var showMenu by remember { mutableStateOf(false) }
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
                snackbar.show("已导出到文件: $pendingExportLabel", ToastUtils.Type.SUCCESS)
            } catch (e: Exception) {
                snackbar.show("导出失败: ${e.message}", ToastUtils.Type.ERROR)
            }
            pendingExportText = ""
            pendingExportLabel = ""
        }
    }

    // 系统返回键：查询 Tab 内从余额结果回到建筑选择
    BackHandler(
        enabled = pagerState.currentPage == 0 && uiState.currentStep == SelectionStep.DONE
    ) {
        viewModel.onReturnedFromDashboard()
    }

    // 查询 Tab 显示余额结果：必须是通过 selectRoom() 标记的 DONE 状态
    val showQueryResult = pagerState.currentPage == 0
        && uiState.selectedRoom != null
        && uiState.currentStep == SelectionStep.DONE
    android.util.Log.d("EMS_showQR",
        "page=${pagerState.currentPage}, " +
        "selectedRoom=${uiState.selectedRoom?.name}, " +
        "currentStep=${uiState.currentStep}, " +
        "showQueryResult=$showQueryResult")
    val room = uiState.selectedRoom
    val balance = uiState.balance

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentTitle, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (showQueryResult) {
                                viewModel.onReturnedFromDashboard()
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // ── Tab 0：查询 Tab 显示余额结果时，显示三点菜单 ──
                    if (pagerState.currentPage == 0 && showQueryResult) {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "更多选项",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                    // ── Tab 2：我的寝室 Tab 显示切换寝室按钮和三点菜单 ──
                    if (pagerState.currentPage == 2 && myRoomState.selectedRoom != null) {
                        // 切换寝室按钮（仅多房间时显示）
                        if (myRoomState.myRoomList.size > 1) {
                            IconButton(onClick = { showRoomSwitchSheet = true }) {
                                Icon(
                                    imageVector = Icons.Default.SwapHoriz,
                                    contentDescription = "切换寝室"
                                )
                            }
                        }
                        Box {
                            IconButton(onClick = { showMyRoomMenu = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "更多选项",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = showMyRoomMenu,
                                onDismissRequest = { showMyRoomMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("复制") },
                                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                    onClick = {
                                        showMyRoomMenu = false
                                        val text = getDashboardTextContent(myRoomState.selectedRoom, myRoomState.balance)
                                        copyToClipboard(context, text, "电费查询结果", snackbar)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("导出") },
                                    leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                                    onClick = {
                                        showMyRoomMenu = false
                                        pendingExportText = getDashboardTextContent(myRoomState.selectedRoom, myRoomState.balance)
                                        pendingExportLabel = "电费查询结果"
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
                electricityTabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            if (pagerState.currentPage != index) {
                                scope.launch { pagerState.animateScrollToPage(index) }
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
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
                            myRoomList = emptyList(),
                            isRefreshing = uiState.isBalanceRefreshing,
                            isLoading = uiState.isLoading,
                            error = uiState.error,
                            onRefresh = { viewModel.refreshBalance() },
                            onBackToSelection = {
                                viewModel.onReturnedFromDashboard()
                            },
                            onNavigateToDetail = onNavigateToDetail,
                            onNavigateToAccountSelection = {
                                scope.launch { pagerState.animateScrollToPage(1) }
                            },
                            onNavigateToH5Recharge = onNavigateToH5Recharge,
                            onNavigateToRechargeRecord = onNavigateToRechargeRecord,
                            showTopBar = false,
                        )
                    } else {
                        BuildingSelectionScreen(
                            viewModel = viewModel,
                            onBack = {},
                            onNavigateToAccountSelection = {
                                scope.launch { pagerState.animateScrollToPage(1) }
                            },
                            showTopBar = false,
                        )
                    }
                }
                1 -> {
                    // ── 充值 Tab ──
                    RechargeScreen(
                        viewModel = rechargeViewModel,
                        onBack = {},
                        onNavigateToPayment = onNavigateToPayment,
                        onNavigateToH5Recharge = onNavigateToH5Recharge,
                        showTopBar = false,
                    )
                }
                2 -> {
                    // ── 我的 Tab ──
                    MyRoomDashboardTab(
                        viewModel = myRoomViewModel,
                        loggedInStudentId = loggedInStudentId,
                        onSwitchToQuery = {
                            scope.launch { pagerState.animateScrollToPage(0) }
                        },
                        onSwitchToRecharge = {
                            scope.launch { pagerState.animateScrollToPage(1) }
                        },
                        onNavigateToDetail = onNavigateToDetail,
                        onNavigateToH5Recharge = onNavigateToH5Recharge,
                        onNavigateToRechargeRecord = onNavigateToRechargeRecord,
                        showRoomSwitchSheet = showRoomSwitchSheet,
                        onShowRoomSwitchSheetChange = { showRoomSwitchSheet = it },
                    )
                }
            }
        }
    }

    // ── 我的寝室房间切换 BottomSheet ──
    if (showRoomSwitchSheet && myRoomState.myRoomList.isNotEmpty()) {
        BottomSheetDialog(
            onDismissRequest = { showRoomSwitchSheet = false },
            title = "选择寝室"
        ) {
            Text(
                text = "请选择要查看的寝室：",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            myRoomState.myRoomList.forEach { room ->
                BottomSheetItem(
                    icon = Icons.Default.Home,
                    title = room.fullName.ifBlank { room.roomName },
                    onClick = {
                        myRoomViewModel.switchToMyRoom(room)
                        showRoomSwitchSheet = false
                    }
                )
            }
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
    loggedInStudentId: String?,
    onSwitchToQuery: () -> Unit,
    onSwitchToRecharge: () -> Unit,
    onNavigateToDetail: (DetailType) -> Unit,
    onNavigateToH5Recharge: () -> Unit,
    onNavigateToRechargeRecord: () -> Unit,
    showRoomSwitchSheet: Boolean = false,
    onShowRoomSwitchSheetChange: (Boolean) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbar = LocalSnackbarController.current

    // 收集错误事件
    LaunchedEffect(Unit) {
        viewModel.errorEvent.collect { errorMsg ->
            snackbar.show(errorMsg, ToastUtils.Type.ERROR)
        }
    }

    // 仅在首次查询（无缓存数据）时自动查询当前登录用户的寝室
    // 后续切换到其他 Tab 再回来时，使用缓存数据，不再重复查询
    LaunchedEffect(Unit) {
        if (loggedInStudentId != null && uiState.selectedRoom == null) {
            viewModel.fastQueryMyRoom(loggedInStudentId)
        }
    }

    // 正在查询我的寝室
    val isLoadingMyRoom = uiState.isMyRoomQuerying
        || (loggedInStudentId != null && uiState.myRoomList.isEmpty() && uiState.selectedRoom == null)

    if (isLoadingMyRoom) {
        // 加载中：显示骨架屏
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text(
                    "正在获取我的寝室信息...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else if (uiState.selectedRoom == null) {
        // 查询失败或未登录
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "暂无房间数据",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        // 已有房间数据，显示 Dashboard
        DashboardScreen(
            room = uiState.selectedRoom,
            balance = uiState.balance,
            myRoomList = uiState.myRoomList,
            isRefreshing = uiState.isBalanceRefreshing,
            isLoading = false,
            error = uiState.error,
            onRefresh = { viewModel.refreshBalance() },
            onBackToSelection = onSwitchToQuery,
            onNavigateToDetail = onNavigateToDetail,
            onNavigateToAccountSelection = onSwitchToRecharge,
            onNavigateToH5Recharge = onNavigateToH5Recharge,
            onNavigateToRechargeRecord = onNavigateToRechargeRecord,
            onSwitchRoom = { viewModel.switchToMyRoom(it) },
            showTopBar = false,
        )
    }
}
