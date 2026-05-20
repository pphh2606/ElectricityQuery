package edu.cqwu.electricity.ui.electricity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.cqwu.electricity.data.model.BalanceResponse
import edu.cqwu.electricity.data.model.BuildingNode
import edu.cqwu.electricity.data.model.BuyRecord
import edu.cqwu.electricity.data.model.CurrentDataResponse
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
 */
data class ElectricityUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,         // 下拉刷新中（建筑选择页）
    val isBalanceRefreshing: Boolean = false,  // 下拉刷新中（仪表盘页）
    val isDetailRefreshing: Boolean = false,   // 下拉刷新中（详情页）
    val error: String? = null,
    val currentStep: SelectionStep = SelectionStep.AREA,

    // 校区展开式选择中已展开的校区 ID 集合
    val expandedAreaIds: Set<String> = emptySet(),

    // 各级列表数据
    val areas: List<BuildingNode> = emptyList(),
    val floors: List<BuildingNode> = emptyList(),
    val rooms: List<BuildingNode> = emptyList(),
    // ROOM_GRID 展开式分组状态
    val floorRoomsMap: Map<String, FloorRoomLoadState> = emptyMap(),
    // ROOM_GRID 刷新版本号：递增后强制 FloorRoomGroup 重建，折叠已展开的楼层
    val floorRoomRefreshVersion: Int = 0,

    // 已选择项
    val selectedArea: BuildingNode? = null,
    val selectedBuilding: BuildingNode? = null,
    val selectedFloor: BuildingNode? = null,
    val selectedRoom: BuildingNode? = null,

    // 余额查询结果
    val balance: BalanceResponse? = null,

    // ========== 详情页查询状态 ==========
    val isDetailLoading: Boolean = false,
    val detailError: String? = null,
    // 最近6个月用电
    val sixMonthUsage: UsageResponse? = null,
    // 本月每日用电
    val monthDailyUsage: UsageResponse? = null,
    // 电表实时数据（含近24h用电明细和实时状态）
    val currentData: CurrentDataResponse? = null,

    // ========== 充值记录查询状态 ==========
    val rechargeRecordIsQuerying: Boolean = false,
    val rechargeRecordError: String? = null,
    val rechargeRecordList: List<BuyRecord> = emptyList(),
    val rechargeRecordTimeRange: Int = 0,  // 0=一个月, 1=三个月, 2=一年, 3=四年
    val rechargeRecordIsRefreshing: Boolean = false,
    val rechargeRecordHasQueried: Boolean = false,
    val rechargeRecordRoomId: String = "",  // 上次查询使用的 roomId
)

/**
 * 电费查询 ViewModel
 * 管理四级层级选择和余额查询状态
 * （充值相关状态已迁移到 [RechargeViewModel]，我的寝室已迁移到 [MyRoomViewModel]）
 */
class ElectricityViewModel(
    private val repository: ElectricityRepository = ElectricityRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ElectricityUiState())
    val uiState: StateFlow<ElectricityUiState> = _uiState.asStateFlow()

    /**
     * 加载校区列表（入口）
     */
    fun loadAreas() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isRefreshing = true, error = null) }
            repository.getAreas()
                .onSuccess { areas ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            areas = areas,
                            currentStep = SelectionStep.AREA
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = "获取校区列表失败: ${e.localizedMessage}"
                        )
                    }
                }
        }
    }

    // ... all other methods remain the same ...
    // 由于文件太长，为了编译通过，这里保留完整代码
    // 从之前的版本完整复制，但删除 myRoom 相关方法和 Channel

    /**
     * 展开/收起校区
     */
    fun toggleArea(areaId: String) {
        _uiState.update {
            val newSet = if (it.expandedAreaIds.contains(areaId)) {
                it.expandedAreaIds - areaId
            } else {
                it.expandedAreaIds + areaId
            }
            it.copy(expandedAreaIds = newSet)
        }
    }

    /**
     * 选择楼栋
     */
    fun selectBuilding(building: BuildingNode, areaId: String) {
        val floors = building.children ?: emptyList()
        _uiState.update {
            val area = it.areas.firstOrNull { a -> a.id == areaId }
            it.copy(
                selectedArea = area,
                selectedBuilding = building,
                floors = floors,
                rooms = emptyList(),
                selectedFloor = null,
                selectedRoom = null,
                balance = null,
                currentStep = SelectionStep.ROOM_GRID,
                floorRoomsMap = emptyMap(),
                expandedAreaIds = emptySet(),
                error = if (floors.isEmpty()) "该楼栋下无楼层数据" else null
            )
        }
    }

    /**
     * 加载指定楼层的房间列表
     */
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

    /**
     * 选择房间并查询电费余额
     */
    fun selectRoom(room: BuildingNode) {
        _uiState.update {
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

    /**
     * 返回上一级
     */
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
            else -> { /* 旧路径（BUILDING/FLOOR/ROOM）已被废弃，不做处理 */ }
        }
    }

    /**
     * 从 Dashboard 返回到 BuildingSelectionScreen
     */
    fun onReturnedFromDashboard() {
        _uiState.update {
            it.copy(currentStep = if (it.selectedBuilding != null) SelectionStep.ROOM_GRID else SelectionStep.AREA)
        }
    }

    /**
     * 面包屑导航跳转
     */
    fun navigateToStep(targetStep: SelectionStep) {
        val currentStep = _uiState.value.currentStep
        if (targetStep == currentStep) return
        when (targetStep) {
            SelectionStep.AREA -> {
                _uiState.update {
                    it.copy(currentStep = SelectionStep.AREA, selectedBuilding = null, floors = emptyList(), floorRoomsMap = emptyMap(), balance = null)
                }
            }
            SelectionStep.ROOM_GRID -> { }
            SelectionStep.DONE -> { }
            else -> { /* 旧路径（BUILDING/FLOOR/ROOM）已被废弃，跳转到 AREA 兜底 */
                _uiState.update {
                    it.copy(currentStep = SelectionStep.AREA)
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun refreshAreas() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            repository.getAreas()
                .onSuccess { areas -> _uiState.update { it.copy(isRefreshing = false, areas = areas) } }
                .onFailure { e -> _uiState.update { it.copy(isRefreshing = false, error = "刷新失败: ${e.localizedMessage}") } }
        }
    }

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
        viewModelScope.launch {
            _uiState.update { it.copy(isBalanceRefreshing = true) }
            repository.queryBalance(roomId)
                .onSuccess { balance -> _uiState.update { it.copy(isBalanceRefreshing = false, balance = balance) } }
                .onFailure { e -> _uiState.update { it.copy(isBalanceRefreshing = false, error = "刷新失败: ${e.localizedMessage}") } }
        }
    }

    private fun getRoomIdOrNull(): String? = _uiState.value.selectedRoom?.id

    fun loadSixMonthUsage() {
        val roomId = getRoomIdOrNull() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isDetailLoading = true, isDetailRefreshing = true, detailError = null, sixMonthUsage = null) }
            repository.querySixMonthUsage(roomId)
                .onSuccess { data -> _uiState.update { it.copy(isDetailLoading = false, isDetailRefreshing = false, sixMonthUsage = data) } }
                .onFailure { e -> _uiState.update { it.copy(isDetailLoading = false, isDetailRefreshing = false, detailError = "查询失败: ${e.localizedMessage}") } }
        }
    }

    fun loadMonthDailyUsage() {
        val roomId = getRoomIdOrNull() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isDetailLoading = true, isDetailRefreshing = true, detailError = null, monthDailyUsage = null) }
            repository.queryMonthDailyUsage(roomId)
                .onSuccess { data -> _uiState.update { it.copy(isDetailLoading = false, isDetailRefreshing = false, monthDailyUsage = data) } }
                .onFailure { e -> _uiState.update { it.copy(isDetailLoading = false, isDetailRefreshing = false, detailError = "查询失败: ${e.localizedMessage}") } }
        }
    }

    fun loadCurrentData() {
        val roomId = getRoomIdOrNull() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isDetailLoading = true, isDetailRefreshing = true, detailError = null, currentData = null) }
            repository.queryCurrentData(roomId)
                .onSuccess { data -> _uiState.update { it.copy(isDetailLoading = false, isDetailRefreshing = false, currentData = data) } }
                .onFailure { e -> _uiState.update { it.copy(isDetailLoading = false, isDetailRefreshing = false, detailError = "查询失败: ${e.localizedMessage}") } }
        }
    }

    fun clearDetailData() {
        _uiState.update {
            it.copy(isDetailLoading = false, isDetailRefreshing = false, detailError = null, sixMonthUsage = null, monthDailyUsage = null, currentData = null)
        }
    }

    // ================================================================
    //  充值记录查询方法
    // ================================================================

    fun setRechargeRecordTimeRange(index: Int) {
        _uiState.update { it.copy(rechargeRecordTimeRange = index) }
        queryRechargeRecords()
    }

    fun queryRechargeRecords() {
        val roomId = getRoomIdOrNull() ?: run {
            _uiState.update { it.copy(rechargeRecordError = "未选择房间") }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(rechargeRecordIsQuerying = true, rechargeRecordIsRefreshing = true, rechargeRecordError = null, rechargeRecordList = emptyList(), rechargeRecordRoomId = roomId)
            }
            val today = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val endTime = dateFormat.format(today.time)
            val days = when (_uiState.value.rechargeRecordTimeRange) {
                1 -> 90L; 2 -> 365L; 3 -> 365L * 4; else -> 30L
            }
            today.add(Calendar.DAY_OF_YEAR, -days.toInt())
            val beginTime = dateFormat.format(today.time)
            val buyResult = repository.queryBuyList(roomId, "0", beginTime, endTime)
            val buyData = buyResult.getOrNull()
            if (buyData == null || buyData.ifSuccess != "Y") {
                val errorMsg = buyData?.resultMsg ?: "查询充值记录失败"
                _uiState.update { it.copy(rechargeRecordIsQuerying = false, rechargeRecordIsRefreshing = false, rechargeRecordHasQueried = true, rechargeRecordError = errorMsg) }
                return@launch
            }
            val records = buyData.buyObj ?: emptyList()
            _uiState.update { it.copy(rechargeRecordIsQuerying = false, rechargeRecordIsRefreshing = false, rechargeRecordHasQueried = true, rechargeRecordList = records) }
        }
    }

    fun clearRechargeRecordState() {
        _uiState.update {
            it.copy(rechargeRecordIsQuerying = false, rechargeRecordIsRefreshing = false, rechargeRecordHasQueried = false, rechargeRecordError = null, rechargeRecordList = emptyList(), rechargeRecordTimeRange = 0, rechargeRecordRoomId = "")
        }
    }
}
