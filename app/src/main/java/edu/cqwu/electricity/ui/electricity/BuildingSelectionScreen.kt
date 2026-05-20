package edu.cqwu.electricity.ui.electricity

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.data.local.AccountStore
import edu.cqwu.electricity.data.model.BuildingNode
import edu.cqwu.electricity.data.model.SelectionStep
import edu.cqwu.electricity.data.model.displayName
import edu.cqwu.electricity.data.network.AccountManager
import edu.cqwu.electricity.ui.components.LocalSnackbarController
import edu.cqwu.electricity.util.ToastUtils
import edu.cqwu.electricity.ui.electricity.ElectricityViewModel
import edu.cqwu.electricity.ui.electricity.FloorRoomLoadState

/**
 * 建筑选择页面
 * 校区展开式→点击楼栋→ROOM_GRID展开式楼层分组→点击房间→Dashboard
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildingSelectionScreen(
    viewModel: ElectricityViewModel,
    onBack: () -> Unit,
    onRoomSelected: (roomId: String, roomName: String) -> Unit,
    onNavigateToAccountSelection: () -> Unit = {},
    showTopBar: Boolean = true
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbar = LocalSnackbarController.current
    val context = LocalContext.current

    // 获取当前登录学号
    val loggedInStudentId = remember {
        AccountManager.getActiveUser()
            ?: AccountStore(context).getAllAccountNames().firstOrNull()
    }

    // 进入页面时加载校区列表
    LaunchedEffect(Unit) {
        if (uiState.areas.isEmpty()) {
            viewModel.loadAreas()
        }
    }

    // 显示错误（使用 ToastOverlay 替代 Snackbar）
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbar.show(it, ToastUtils.Type.ERROR)
            viewModel.clearError()
        }
    }

    val topBarColors = LocalTopBarState.current.style.toTopAppBarColors(MaterialTheme.colorScheme)

    // 被其他 Composable 嵌套使用时（如底部导航栏 Tab），跳过 TopAppBar/Scaffold
    if (showTopBar) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("电费查询", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        // 我的寝室快捷入口
                        // 充值入口
                        IconButton(onClick = onNavigateToAccountSelection) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = "充值电费"
                            )
                        }
                    },
                    colors = topBarColors
                )
            }
        ) { paddingValues ->
            ContentArea(
                viewModel = viewModel,
                uiState = uiState,
                snackbar = snackbar,
                paddingValues = paddingValues
            )
        }
    } else {
        // 无 TopAppBar/Scaffold 模式（用于底部导航栏 Tab 内嵌）
        ContentArea(
            viewModel = viewModel,
            uiState = uiState,
            snackbar = snackbar,
            paddingValues = PaddingValues(0.dp)
        )
    }
}

/**
 * 建筑选择页面的内容区域（不含 Scaffold/TopAppBar）
 * 通过 [showTopBar] 控制是否被 Scaffold 包裹
 */
@Composable
private fun ContentArea(
    viewModel: ElectricityViewModel,
    uiState: ElectricityUiState,
    snackbar: edu.cqwu.electricity.ui.components.SnackbarController,
    paddingValues: androidx.compose.foundation.layout.PaddingValues
) {
    // 仅在校区列表步骤启用下拉刷新（ROOM_GRID 无网络请求，禁用以免用户困惑）
    val pullToRefreshEnabled = uiState.currentStep == SelectionStep.AREA

    // Column + weight(1f) 确保内容区域收到有限约束，
    // 避免 HorizontalPager 传递无限高度导致 LazyColumn 崩溃
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 16.dp)
    ) {
        // 面包屑导航栏（固定不动）
        BreadcrumbBar(
            currentStep = uiState.currentStep,
            selectedArea = uiState.selectedArea,
            selectedBuilding = uiState.selectedBuilding,
            selectedFloor = uiState.selectedFloor,
            onNavigate = { targetStep -> viewModel.navigateToStep(targetStep) }
        )

        Spacer(modifier = Modifier.height(4.dp))

        // 可滚动的列表区域 + 下拉刷新
        Box(modifier = Modifier.weight(1f)) {
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing && pullToRefreshEnabled,
                onRefresh = {
                    when (uiState.currentStep) {
                        SelectionStep.AREA -> viewModel.refreshAreas()
                        SelectionStep.ROOM_GRID -> viewModel.refreshRoomGrid()
                        else -> {}
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
            // ── 列表内容（按步骤分发，标题和面包屑已在外层 Column 固定显示）──
            when (uiState.currentStep) {
                SelectionStep.AREA -> {
                    if (uiState.areas.isEmpty()) {
                        item(key = "empty") {
                            EmptyStateText("暂无校区数据")
                        }
                    } else {
                        items(uiState.areas, key = { it.id }) { area ->
                            AreaBuildingGroup(
                                area = area,
                                isExpanded = uiState.expandedAreaIds.contains(area.id),
                                onToggle = { viewModel.toggleArea(area.id) },
                                onBuildingClick = { building, areaId ->
                                    viewModel.selectBuilding(building, areaId)
                                }
                            )
                        }
                    }
                }
                SelectionStep.ROOM_GRID -> {
                    if (uiState.floors.isEmpty()) {
                        item(key = "empty") {
                            EmptyStateText("该楼栋下无楼层数据")
                        }
                    } else {
                        // 直接在外层 LazyColumn 中 items
                        items(uiState.floors, key = { it.id }) { floor ->
                            FloorRoomGroup(
                                floor = floor,
                                loadState = uiState.floorRoomsMap[floor.id],
                                onRoomClick = { room -> viewModel.selectRoom(room) },
                                onLoadFloor = { floor -> viewModel.loadRoomsForFloor(floor) },
                                refreshVersion = uiState.floorRoomRefreshVersion
                            )
                        }
                    }
                }
                SelectionStep.DONE -> {
                    item(key = "empty") {
                        EmptyStateText("")
                    }
                }
                else -> { /* BUILDING/FLOOR/ROOM 已废弃 */ }
                    }
                }
            }
        }
    }
}

/**
 * 统一的网格卡片组件（MD3 Card 风格），用于楼栋/房间 4 列网格。
 *
 * @param label 显示文字
 * @param onClick 点击回调
 * @param height 卡片高度，楼栋 80.dp / 房间 52.dp
 */
@Composable
private fun GridItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 52.dp
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 空状态文本组件
 */
@Composable
private fun EmptyStateText(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * MD3 风格面包屑导航栏
 * 使用 Surface 容器 + 芯片风格的 crumbs，符合 Material Design 3 规范。
 * 当前步骤以 secondaryContainer 色标注，历史步骤可点击跳转。
 */
@Composable
private fun BreadcrumbBar(
    currentStep: SelectionStep,
    selectedArea: BuildingNode?,
    selectedBuilding: BuildingNode?,
    selectedFloor: BuildingNode?,
    onNavigate: (SelectionStep) -> Unit
) {
    data class Crumb(val label: String, val targetStep: SelectionStep, val isActive: Boolean)

    // 面包屑活跃步骤（在 AREA 之后）：ROOM_GRID = 浏览楼层, DONE = 已选房间
    val activeSteps = setOf(SelectionStep.ROOM_GRID, SelectionStep.DONE)

    val crumbs = remember(selectedArea, selectedBuilding, selectedFloor, currentStep) {
        buildList {
            add(Crumb("全部校区", SelectionStep.AREA, currentStep == SelectionStep.AREA))
            if (selectedArea != null && currentStep in activeSteps) {
                add(Crumb(selectedArea.name, SelectionStep.AREA, false))
            }
            // 注意：BUILDING 枚举已被合并到 AREA 展开式选择中，所以 target 设为 AREA。
            // 点击楼栋名称会退回到校区选择页（因为楼栋没有独立页面）。
            if (selectedBuilding != null && currentStep in activeSteps) {
                val isActive = currentStep == SelectionStep.ROOM_GRID
                add(Crumb(selectedBuilding.name, SelectionStep.AREA, isActive))
            }
            // 楼层名点击 → 回到 ROOM_GRID（展开式楼层分组视图）
            if (selectedFloor != null && currentStep in activeSteps) {
                val isActive = currentStep == SelectionStep.DONE
                add(Crumb(selectedFloor.name, SelectionStep.ROOM_GRID, isActive))
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            crumbs.forEachIndexed { index, crumb ->
                if (index > 0) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (crumb.isActive) {
                    // 当前步骤：secondaryContainer 填充色芯片
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = crumb.label,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    // 历史步骤：可点击 chip
                    Surface(
                        onClick = { onNavigate(crumb.targetStep) },
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Text(
                            text = crumb.label,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * 可展开分组的通用头部行
 */
@Composable
private fun ExpandableGroupHeader(
    name: String,
    isExpanded: Boolean,
    onClick: () -> Unit,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
            contentDescription = if (isExpanded) "收起" else "展开",
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(4.dp))
        Text(text = name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        trailingContent?.invoke()
    }
}

/**
 * 单个楼层分组（可点击展开/收起）
 *
 * @param refreshVersion 从 UiState 传入的刷新版本号，变化时自动重置展开状态为收起
 */
@Composable
private fun FloorRoomGroup(
    floor: BuildingNode,
    loadState: FloorRoomLoadState?,
    onRoomClick: (BuildingNode) -> Unit,
    onLoadFloor: (BuildingNode) -> Unit,
    refreshVersion: Int = 0
) {
    var expanded by remember(refreshVersion) { mutableStateOf(false) }
    val displayName = floor.displayName

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        ExpandableGroupHeader(
            name = displayName,
            isExpanded = expanded,
            onClick = {
                expanded = !expanded
                if (expanded && loadState == null) onLoadFloor(floor)
            },
            trailingContent = {
                if (expanded) {
                    when (loadState) {
                        is FloorRoomLoadState.Loading -> { }
                        is FloorRoomLoadState.Success -> {
                            Text("${loadState.rooms.size} 间", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        is FloorRoomLoadState.Error -> {
                            Text("加载失败", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(4.dp))
                            Text("重试", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { onLoadFloor(floor) })
                        }
                        null -> {
                            Text("点击展开", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        )

        AnimatedVisibility(visible = expanded) {
            Column {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                when (loadState) {
                    null -> Spacer(Modifier.height(8.dp))
                    is FloorRoomLoadState.Loading -> Spacer(Modifier.height(8.dp))
                    is FloorRoomLoadState.Success -> {
                        if (loadState.rooms.isEmpty()) {
                            Text("暂无房间", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                        } else {
                            Spacer(Modifier.height(4.dp))
                            RoomGridRow(rooms = loadState.rooms, onRoomClick = onRoomClick)
                        }
                    }
                    is FloorRoomLoadState.Error -> {
                        Text("加载失败: ${loadState.message}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
                        TextButton(onClick = { onLoadFloor(floor) }) { Text("重新加载") }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/**
 * 单个校区分组（可点击展开/收起，显示楼栋列表）
 */
@Composable
private fun AreaBuildingGroup(
    area: BuildingNode,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onBuildingClick: (BuildingNode, String) -> Unit
) {
    val displayName = area.displayName

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        ExpandableGroupHeader(name = displayName, isExpanded = isExpanded, onClick = onToggle)

        AnimatedVisibility(visible = isExpanded) {
            Column {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                val buildings = area.children ?: emptyList()
                if (buildings.isEmpty()) {
                    Text("该校区下无楼栋", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    Spacer(Modifier.height(4.dp))
                    RoomGridRow(rooms = buildings, onRoomClick = { building -> onBuildingClick(building, area.id) })
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/**
 * 房间网格行（手动分组，每行4个）
 */
@Composable
private fun RoomGridRow(
    rooms: List<BuildingNode>,
    onRoomClick: (BuildingNode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rooms.chunked(4).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { room ->
                    GridItem(label = room.displayName, onClick = { onRoomClick(room) }, modifier = Modifier.weight(1f))
                }
                repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

