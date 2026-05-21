package edu.cqwu.electricity.ui.electricity

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.cqwu.electricity.data.model.BalanceResponse
import edu.cqwu.electricity.data.model.BuildingNode
import edu.cqwu.electricity.data.model.BuyRecord
import edu.cqwu.electricity.data.model.CurrentDataResponse
import edu.cqwu.electricity.data.model.RechargeTimeRange
import edu.cqwu.electricity.data.model.SelectionStep
import edu.cqwu.electricity.data.model.UsageResponse
import edu.cqwu.electricity.data.repository.ElectricityRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
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
    data class Error(val message: String) : FloorRoomLoadState()
}

/**
 * UI 状态数据类
 *
 * 注意：详情数据（DetailState）和充值记录（RecordState）已迁移到独立的 StateFlow，
 * 它们的变更不会触发本状态的更新。
 */
data class ElectricityUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,         // 下拉刷新中（建筑选择页）
    val isBalanceRefreshing: Boolean = false,  // 下拉刷新中（仪表盘页）
    val error: String? = null,
    val currentStep: SelectionStep = SelectionStep.AREA,

    // 校区展开式选择中已展开的校区 ID 集合（全局保留，组件移出组合树后仍可恢复）
    val expandedAreaIds: Set<String> = emptySet(),
    // 楼层展开式分组中已展开的楼层 ID 集合（全局保留，组件移出组合树后仍可恢复）
    val expandedFloorIds: Set<String> = emptySet(),

    // 各级列表数据
    val areas: List<BuildingNode> = emptyList(),
    val floors: List<BuildingNode> = emptyList(),
    // ROOM_GRID 展开式分组状态
    val floorRoomsMap: Map<String, FloorRoomLoadState> = emptyMap(),
    // ROOM_GRID 刷新版本号：递增后强制 FloorRoomGroup 重建，折叠已展开的楼层
    val floorRoomRefreshVersion: Int = 0,

    // 已选择项
    val selectedArea: BuildingNode? = null,
    val selectedBuilding: BuildingNode? = null,
    val selectedRoom: BuildingNode? = null,

    // 余额查询结果
    val balance: BalanceResponse? = null,
)

/**
 * 详情页面独立状态。
 * 与主 [ElectricityUiState] 分离，避免详情页加载影响查询/充值 Tab 的重组。
 */
data class DetailState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val sixMonthUsage: UsageResponse? = null,
    val monthDailyUsage: UsageResponse? = null,
    val currentData: CurrentDataResponse? = null,
)

/**
 * 充值记录独立状态。
 * 与主 [ElectricityUiState] 分离，避免充值记录查询影响其他页面的重组。
 */
data class RecordState(
    val isQuerying: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val list: List<BuyRecord> = emptyList(),
    val timeRange: Int = 0,
    val hasQueried: Boolean = false,
    val roomId: String = "",
)

/**
 * 电费查询 ViewModel
 * 管理四级层级选择和余额查询状态
 * （充值相关状态已迁移到 [RechargeViewModel]，我的寝室已迁移到 [MyRoomViewModel]）
 *
 * 包含三个独立的 StateFlow：
 * - [uiState]：建筑选择 + 余额查询（核心流程）
 * - [detailState]：详情页数据
 * - [recordState]：充值记录数据
 */
class ElectricityViewModel(
    private val repository: ElectricityRepository = ElectricityRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ElectricityUiState())
    val uiState: StateFlow<ElectricityUiState> = _uiState.asStateFlow()

    private val _detailState = MutableStateFlow(DetailState())
    val detailState: StateFlow<DetailState> = _detailState.asStateFlow()

    private val _recordState = MutableStateFlow(RecordState())
    val recordState: StateFlow<RecordState> = _recordState.asStateFlow()

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
                    _uiState.update { it.onError(e.localizedMessage ?: "未知错误") }
                }
        }
    }

    /**
     * 详情请求模板（更新 _detailState 而非 _uiState）。
     */
    private inline fun <T> launchDetailRequest(
        crossinline onStart: DetailState.() -> DetailState,
        crossinline onSuccess: DetailState.(T) -> DetailState,
        crossinline onError: DetailState.(String) -> DetailState,
        crossinline request: suspend () -> Result<T>
    ) {
        viewModelScope.launch {
            _detailState.update { it.onStart() }
            request()
                .onSuccess { data ->
                    _detailState.update { it.onSuccess(data) }
                }
                .onFailure { e ->
                    _detailState.update { it.onError(e.localizedMessage ?: "未知错误") }
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
        onError = { msg -> copy(isLoading = false, isRefreshing = false, error = "获取校区列表失败: $msg") },
        request = { repository.getAreas() }
    )

    fun selectBuilding(building: BuildingNode, areaId: String) {
        val floors = building.children ?: emptyList()
        _uiState.update {
            Log.d("DEBUG_expand", "selectBuilding BEFORE: expandedAreaIds=${it.expandedAreaIds}")
            Log.d("DEBUG_expand", "selectBuilding BEFORE: expandedAreaIds=${it.expandedAreaIds}")
            val area = it.areas.firstOrNull { a -> a.id == areaId }
            it.copy(
                selectedArea = area,
                selectedBuilding = building,
                floors = floors,
                selectedRoom = null,
                balance = null,
                currentStep = SelectionStep.ROOM_GRID,
                floorRoomsMap = emptyMap(),
                error = if (floors.isEmpty()) "该楼栋下无楼层数据" else null
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
            repository.getRooms(floor.id)
                .onSuccess { rooms ->
                    _uiState.update { it.copy(floorRoomsMap = it.floorRoomsMap + (floor.id to FloorRoomLoadState.Success(rooms))) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(floorRoomsMap = it.floorRoomsMap + (floor.id to FloorRoomLoadState.Error(e.message ?: "加载失败"))) }
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
            repository.queryBalance(room.id)
                .onSuccess { balance ->
                    _uiState.update { it.copy(isLoading = false, isBalanceRefreshing = false, balance = balance) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, isBalanceRefreshing = false, error = "查询余额失败: ${e.localizedMessage}") }
                }
        }
    }

    fun goBack() {
        val currentStep = _uiState.value.currentStep
        when (currentStep) {
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
            it.copy(currentStep = if (it.selectedBuilding != null) SelectionStep.ROOM_GRID else SelectionStep.AREA)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun refreshAreas() = launchRequest(
        onStart = { copy(isRefreshing = true) },
        onSuccess = { areas -> copy(isRefreshing = false, areas = areas) },
        onError = { msg -> copy(isRefreshing = false, error = "刷新失败: $msg") },
        request = { repository.getAreas() }
    )

    fun refreshRoomGrid() {
        _uiState.update { current ->
            val clearedMap = current.floorRoomsMap.mapValues { (_, state) ->
                if (state is FloorRoomLoadState.Success) null else state
            }.filterValues { it != null }.mapValues { (_, v) -> v!! }
            current.copy(isRefreshing = false, floorRoomsMap = clearedMap, floorRoomRefreshVersion = current.floorRoomRefreshVersion + 1)
        }
    }

    fun refreshBalance() {
        val roomId = _uiState.value.selectedRoom?.id ?: return
        launchRequest(
            onStart = { copy(isBalanceRefreshing = true) },
            onSuccess = { balance -> copy(isBalanceRefreshing = false, balance = balance) },
            onError = { msg -> copy(isBalanceRefreshing = false, error = "刷新失败: $msg") },
            request = { repository.queryBalance(roomId) }
        )
    }

    private fun getRoomIdOrNull(): String? = _uiState.value.selectedRoom?.id

    // ================================================================
    //  详情页数据（独立 _detailState）
    // ================================================================

    fun loadSixMonthUsage() {
        val roomId = getRoomIdOrNull() ?: return
        launchDetailRequest(
            onStart = { copy(isLoading = true, isRefreshing = true, error = null, sixMonthUsage = null) },
            onSuccess = { data -> copy(isLoading = false, isRefreshing = false, sixMonthUsage = data) },
            onError = { msg -> copy(isLoading = false, isRefreshing = false, error = "查询失败: $msg") },
            request = { repository.querySixMonthUsage(roomId) }
        )
    }

    fun loadMonthDailyUsage() {
        val roomId = getRoomIdOrNull() ?: return
        launchDetailRequest(
            onStart = { copy(isLoading = true, isRefreshing = true, error = null, monthDailyUsage = null) },
            onSuccess = { data -> copy(isLoading = false, isRefreshing = false, monthDailyUsage = data) },
            onError = { msg -> copy(isLoading = false, isRefreshing = false, error = "查询失败: $msg") },
            request = { repository.queryMonthDailyUsage(roomId) }
        )
    }

    fun loadCurrentData() {
        val roomId = getRoomIdOrNull() ?: return
        launchDetailRequest(
            onStart = { copy(isLoading = true, isRefreshing = true, error = null, currentData = null) },
            onSuccess = { data -> copy(isLoading = false, isRefreshing = false, currentData = data) },
            onError = { msg -> copy(isLoading = false, isRefreshing = false, error = "查询失败: $msg") },
            request = { repository.queryCurrentData(roomId) }
        )
    }

    fun clearDetailData() {
        _detailState.update {
            DetailState()
        }
    }

    // ================================================================
    //  充值记录（独立 _recordState）
    // ================================================================

    fun setRechargeRecordTimeRange(index: Int) {
        _recordState.update { it.copy(timeRange = index) }
        queryRechargeRecords()
    }

    fun queryRechargeRecords() {
        val roomId = getRoomIdOrNull() ?: run {
            _recordState.update { it.copy(error = "未选择房间") }
            return
        }
        viewModelScope.launch {
            _recordState.update {
                it.copy(isQuerying = true, isRefreshing = true, error = null, list = emptyList(), roomId = roomId)
            }
            val today = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val endTime = dateFormat.format(today.time)
            val timeRange = RechargeTimeRange.fromIndex(_recordState.value.timeRange)
            today.add(Calendar.DAY_OF_YEAR, -timeRange.days.toInt())
            val beginTime = dateFormat.format(today.time)
            val buyResult = repository.queryBuyList(roomId, "0", beginTime, endTime)
            val buyData = buyResult.getOrNull()
            if (buyData == null || buyData.ifSuccess != "Y") {
                val errorMsg = buyData?.resultMsg ?: "查询充值记录失败"
                _recordState.update { it.copy(isQuerying = false, isRefreshing = false, hasQueried = true, error = errorMsg) }
                return@launch
            }
            val records = buyData.buyObj ?: emptyList()
            _recordState.update { it.copy(isQuerying = false, isRefreshing = false, hasQueried = true, list = records) }
        }
    }

    fun clearRechargeRecordState() {
        _recordState.update { RecordState() }
    }

    /**
     * 重置所有状态到初始值。
     * 在 [ElectricityMainScreen] 退出时调用，确保下次进入时重新加载。
     */
    fun resetToInitial() {
        _uiState.update { ElectricityUiState() }
        _detailState.update { DetailState() }
        _recordState.update { RecordState() }
    }
}
