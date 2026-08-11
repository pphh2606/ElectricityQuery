@file:OptIn(ExperimentalMaterial3Api::class)

package edu.cqwu.electricity.electricity.ui

import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import edu.cqwu.electricity.R

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
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.login.data.AccountStore
import edu.cqwu.electricity.electricity.data.BuildingNode
import edu.cqwu.electricity.electricity.data.SelectionStep
import edu.cqwu.electricity.electricity.data.displayName
import edu.cqwu.electricity.login.data.AccountManager
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.theme.util.ToastUtils

/**
 * 建筑选择页面
 * 校区展开式→点击楼栋→ROOM_GRID展开式楼层分组→点击房间→Dashboard
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildingSelectionScreen(
    viewModel: ElectricityViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbar = LocalSnackbarController.current
    val context = LocalContext.current

    // 获取当前登录学号
    remember {
        AccountManager.getActiveUser()
            ?: AccountStore.getInstance(context).getAllAccountNames().firstOrNull()
    }

    // 进入页面时加载校区列表
    LaunchedEffect(Unit) {
        android.util.Log.d("DEBUG_expand", "BuildingSelectionScreen LaunchedEffect: areas.isEmpty=${uiState.areas.isEmpty()}, expandedAreaIds=${uiState.expandedAreaIds}, currentStep=${uiState.currentStep}")
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

    ContentArea(
        viewModel = viewModel,
        uiState = uiState,
    )
}

/**
 * 建筑选择页面的内容区域
 */
@Composable
private fun ContentArea(
    viewModel: ElectricityViewModel,
    uiState: ElectricityUiState,
) {
    // 仅在校区列表步骤启用下拉刷新（ROOM_GRID 无网络请求，禁用以免用户困惑）
    val pullToRefreshEnabled = uiState.currentStep == SelectionStep.AREA

    // Column + weight(1f) 确保内容区域收到有限约束，
    // 避免 HorizontalPager 传递无限高度导致 LazyColumn 崩溃
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
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
                    // ── 列表内容（按步骤分发）──
                    when (uiState.currentStep) {
                        SelectionStep.AREA -> {
                            if (uiState.areas.isEmpty()) {
                                item(key = "empty") {
                                    EmptyStateText(stringResource(R.string.building_no_campus))
                                }
                            } else {
                                android.util.Log.d("DEBUG_expand", "Rendering AREA step: areas=${uiState.areas.size}, expandedAreaIds=${uiState.expandedAreaIds}")
                                items(uiState.areas, key = { it.id }) { area ->
                                    AreaBuildingGroup(
                                        area = area,
                                        isExpanded = uiState.expandedAreaIds.contains(area.id),
                                        onToggle = { viewModel.toggleArea(area.id) },
                                        onBuildingClick = { building, areaId ->
                                            viewModel.selectBuilding(building)
                                        }
                                    )
                                }
                            }
                        }
                        SelectionStep.ROOM_GRID -> {
                            if (uiState.floors.isEmpty()) {
                                item(key = "empty") {
                                    EmptyStateText(stringResource(R.string.building_no_floors))
                                }
                            } else {
                                // 直接在外层 LazyColumn 中 items
                                items(uiState.floors, key = { it.id }) { floor ->
                                    FloorRoomGroup(
                                        floor = floor,
                                        loadState = uiState.floorRoomsMap[floor.id],
                                        isExpanded = uiState.expandedFloorIds.contains(floor.id),
                                        onToggle = { viewModel.toggleFloorExpanded(floor.id) },
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
            imageVector = if (isExpanded) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.KeyboardArrowRight,
            contentDescription = if (isExpanded) stringResource(R.string.common_collapse) else stringResource(R.string.common_expand),
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
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onRoomClick: (BuildingNode) -> Unit,
    onLoadFloor: (BuildingNode) -> Unit,
    refreshVersion: Int = 0
) {
    val displayName = floor.displayName

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        ExpandableGroupHeader(
            name = displayName,
            isExpanded = isExpanded,
            onClick = {
                onToggle()
                if (!isExpanded && loadState == null) onLoadFloor(floor)
            },
            trailingContent = {
                if (isExpanded) {
                    when (loadState) {
                        is FloorRoomLoadState.Loading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is FloorRoomLoadState.Success -> {
 Text(pluralStringResource(R.plurals.building_rooms_count, loadState.rooms.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        is FloorRoomLoadState.Error -> {
                            Text(stringResource(R.string.common_load_failed), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.common_retry), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { onLoadFloor(floor) })
                        }
                        null -> {
                            Text(stringResource(R.string.building_click_to_expand), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        )

        AnimatedVisibility(visible = isExpanded) {
            Column {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                when (loadState) {
                    null -> Spacer(Modifier.height(8.dp))
                    is FloorRoomLoadState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    is FloorRoomLoadState.Success -> {
                        if (loadState.rooms.isEmpty()) {
                            Text(stringResource(R.string.building_no_rooms), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                        } else {
                            Spacer(Modifier.height(4.dp))
                            RoomGridRow(rooms = loadState.rooms, onRoomClick = onRoomClick)
                        }
                    }
                    is FloorRoomLoadState.Error -> {
                        Text(stringResource(R.string.common_load_failed) + ": ${loadState.message}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
                        TextButton(onClick = { onLoadFloor(floor) }) { Text(stringResource(R.string.building_reload)) }
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
                    Text(stringResource(R.string.building_no_buildings), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
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

