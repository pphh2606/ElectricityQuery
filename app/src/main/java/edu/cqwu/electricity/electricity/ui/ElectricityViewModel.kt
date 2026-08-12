package edu.cqwu.electricity.electricity.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.cqwu.electricity.R
import edu.cqwu.electricity.electricity.data.BalanceResponse
import edu.cqwu.electricity.electricity.data.BuildingNode
import edu.cqwu.electricity.electricity.data.SelectionStep
import edu.cqwu.electricity.electricity.data.ElectricityApi
import edu.cqwu.electricity.theme.ui.UiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 楼层房间加载状态
 */
sealed class FloorRoomLoadState {
    data object Loading : FloorRoomLoadState()
    data class Success(val rooms: List<BuildingNode>) : FloorRoomLoadState()
    data class Error(val message: UiMessage) : FloorRoomLoadState()
}

/**
 * UI 状态数据类
 *
 * 管理建筑选择流程和余额查询状态。
 * 详情数据已迁移到独立的 [DetailViewModel]。
 * 充值记录数据已迁移到 [RechargeViewModel]。
 */
data class ElectricityUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,         // 下拉刷新中（建筑选择页）
    val isBalanceRefreshing: Boolean = false,  // 下拉刷新中（仪表盘页）
    val error: UiMessage? = null,
    val currentStep: SelectionStep = SelectionStep.AREA,

    // 校区展开式选择中已展开的校区 ID 集合
    val expandedAreaIds: Set<String> = emptySet(),
    // 楼层展开式分组中已展开的楼层 ID 集合
    val expandedFloorIds: Set<String> = emptySet(),

    // 各级列表数据
    val areas: List<BuildingNode> = emptyList(),
    val floors: List<BuildingNode> = emptyList(),
    // ROOM_GRID 展开式分组状态
    val floorRoomsMap: Map<String, FloorRoomLoadState> = emptyMap(),
    // ROOM_GRID 刷新版本号
    val floorRoomRefreshVersion: Int = 0,

    // 已选择项
    val selectedBuilding: BuildingNode? = null,
    val selectedRoom: BuildingNode? = null,

    // 余额查询结果
    val balance: BalanceResponse? = null,
)

/**
 * 电费查询 ViewModel
 *
 * 管理建筑选择流程和余额查询状态。
 * 职责边界单一：仅处理 [ElectricityUiState]。
 *
 * 详情数据 → [DetailViewModel]
 * 充值记录 → [RechargeViewModel]
 */
class ElectricityViewModel(
    private val api: ElectricityApi = ElectricityApi()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ElectricityUiState())
    val uiState: StateFlow<ElectricityUiState> = _uiState.asStateFlow()

    /**
     * 通用网络请求模板。
     */
    private inline fun <T> launchRequest(
        crossinline onStart: ElectricityUiState.() -> ElectricityUiState,
        crossinline onSuccess: ElectricityUiState.(T) -> ElectricityUiState,
        crossinline onError: ElectricityUiState.(String) -> ElectricityUiState,
        crossinline request: suspend () -> Result<T>
    ) {
        viewModelScope.launch {
            _uiState.update { it.onStart() }
            request()
                .onSuccess { data ->
                    _uiState.update { it.onSuccess(data) }
                }
                .onFailure { e ->
                    _uiState.update { it.onError(e.localizedMessage ?: "") }
                }
        }
    }

    // ================================================================
    //  建筑选择流程
    // ================================================================

    fun toggleArea(areaId: String) {
        _uiState.update {
            val newSet = if (it.expandedAreaIds.contains(areaId)) {
                it.expandedAreaIds - areaId
            } else {
                it.expandedAreaIds + areaId
            }
            Log.d("DEBUG_expand", "toggleArea: ${it.expandedAreaIds} → $newSet")
            it.copy(expandedAreaIds = newSet)
        }
    }

    fun toggleFloorExpanded(floorId: String) {
        _uiState.update {
            val newSet = if (it.expandedFloorIds.contains(floorId)) {
                it.expandedFloorIds - floorId
            } else {
                it.expandedFloorIds + floorId
            }
            it.copy(expandedFloorIds = newSet)
        }
    }

    fun loadAreas() = launchRequest(
        onStart = { copy(isLoading = true, isRefreshing = true, error = null) },
        onSuccess = { areas ->
            copy(isLoading = false, isRefreshing = false, areas = areas, currentStep = SelectionStep.AREA)
        },
        onError = { msg -> copy(isLoading = false, isRefreshing = false, error = UiMessage(R.string.electricity_fetch_campuses_failed, listOf(msg))) },
        request = { api.getAreas() }
    )

    fun selectBuilding(building: BuildingNode) {
        val floors = building.children ?: emptyList()
        _uiState.update {
            Log.d("DEBUG_expand", "selectBuilding BEFORE: expandedAreaIds=${it.expandedAreaIds}")
            it.copy(
                selectedBuilding = building,
                floors = floors,
                selectedRoom = null,
                balance = null,
                currentStep = SelectionStep.ROOM_GRID,
                floorRoomsMap = emptyMap(),
                error = if (floors.isEmpty()) UiMessage(R.string.building_no_floors) else null
            )
        }
    }

    fun loadRoomsForFloor(floor: BuildingNode) {
        val currentState = _uiState.value.floorRoomsMap[floor.id]
        if (currentState is FloorRoomLoadState.Success) return
        _uiState.update {
            it.copy(floorRoomsMap = it.floorRoomsMap + (floor.id to FloorRoomLoadState.Loading))
        }
        viewModelScope.launch {
            kotlinx.coroutines.withTimeout(15_000L) {
                api.getRooms(floor.id)
            }.onSuccess { rooms ->
                _uiState.update { it.copy(floorRoomsMap = it.floorRoomsMap + (floor.id to FloorRoomLoadState.Success(rooms))) }
            }.onFailure { e ->
                val msg = if (e is kotlinx.coroutines.TimeoutCancellationException) {
                    UiMessage(R.string.electricity_timeout_retry)
                } else {
                    UiMessage(R.string.common_load_failed)
                }
                _uiState.update { it.copy(floorRoomsMap = it.floorRoomsMap + (floor.id to FloorRoomLoadState.Error(msg))) }
            }
        }
    }

    fun selectRoom(room: BuildingNode) {
        _uiState.update {
            Log.d("DEBUG_expand", "selectRoom: expandedAreaIds=${it.expandedAreaIds}")
            it.copy(
                isLoading = true,
                isBalanceRefreshing = true,
                error = null,
                selectedRoom = room,
                balance = null,
                currentStep = SelectionStep.DONE
            )
        }
        viewModelScope.launch {
            api.queryBalance(room.id)
                .onSuccess { balance ->
                    _uiState.update { it.copy(isLoading = false, isBalanceRefreshing = false, balance = balance) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, isBalanceRefreshing = false, error = UiMessage(R.string.electricity_balance_query_failed, listOf(e.localizedMessage ?: ""))) }
                }
        }
    }

    fun goBack() {
        when (val currentStep = _uiState.value.currentStep) {
            SelectionStep.ROOM_GRID -> {
                _uiState.update {
                    it.copy(currentStep = SelectionStep.AREA, selectedBuilding = null, floors = emptyList(), floorRoomsMap = emptyMap(), balance = null)
                }
            }
            SelectionStep.AREA -> { }
            SelectionStep.DONE -> {
                _uiState.update {
                    it.copy(currentStep = SelectionStep.ROOM_GRID, balance = null, selectedRoom = null)
                }
            }
        }
    }

    fun onReturnedFromDashboard() {
        _uiState.update {
            Log.d("DEBUG_expand", "onReturnedFromDashboard: expandedAreaIds=${it.expandedAreaIds}, selectedBuilding=${it.selectedBuilding?.name}")
            it.copy(
                currentStep = if (it.selectedBuilding != null) SelectionStep.ROOM_GRID else SelectionStep.AREA,
                selectedRoom = null,
                balance = null,
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun refreshAreas() = launchRequest(
        onStart = { copy(isRefreshing = true) },
        onSuccess = { areas -> copy(isRefreshing = false, areas = areas) },
        onError = { msg -> copy(isRefreshing = false, error = UiMessage(R.string.common_refresh_failed, listOf(msg))) },
        request = { api.getAreas() }
    )

    fun refreshRoomGrid() {
        _uiState.update { current ->
            current.copy(
                isRefreshing = false,
                floorRoomsMap = emptyMap(),
                expandedFloorIds = emptySet(),
                floorRoomRefreshVersion = current.floorRoomRefreshVersion + 1
            )
        }
    }

    fun refreshBalance() {
        val roomId = _uiState.value.selectedRoom?.id ?: return
        launchRequest(
            onStart = { copy(isBalanceRefreshing = true) },
            onSuccess = { balance -> copy(isBalanceRefreshing = false, balance = balance) },
            onError = { msg -> copy(isBalanceRefreshing = false, error = UiMessage(R.string.common_refresh_failed, listOf(msg))) },
            request = { api.queryBalance(roomId) }
        )
    }

}
