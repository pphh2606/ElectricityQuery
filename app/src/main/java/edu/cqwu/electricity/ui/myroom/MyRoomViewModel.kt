package edu.cqwu.electricity.ui.myroom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.cqwu.electricity.data.model.BalanceResponse
import edu.cqwu.electricity.data.model.BuildingNode
import edu.cqwu.electricity.data.model.UserRoomInfo
import edu.cqwu.electricity.data.network.pay.electricityrecharge.ElectricityApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * "我的寝室"页面状态
 */
data class MyRoomUiState(
    val myRoomList: List<UserRoomInfo> = emptyList(),
    val isMyRoomQuerying: Boolean = false,
    val selectedRoom: BuildingNode? = null,
    val balance: BalanceResponse? = null,
    val isBalanceRefreshing: Boolean = false,
    val error: String? = null,
)

/**
 * "我的寝室" Tab ViewModel
 *
 * 管理当前登录用户绑定的寝室列表查询和余额加载。
 * 与 [ElectricityViewModel] 完全解耦，不共享任何状态。
 */
class MyRoomViewModel(
    private val api: ElectricityApi = ElectricityApi()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyRoomUiState())
    val uiState: StateFlow<MyRoomUiState> = _uiState.asStateFlow()

    // 错误事件 Channel
    private val _errorEvent = Channel<String>(Channel.BUFFERED)
    val errorEvent = _errorEvent.receiveAsFlow()

    /**
     * 查询当前登录用户绑定的寝室列表，直接更新 selectedRoom 并查询余额。
     *
     * 流程：学号 → userId → 房间列表 → 更新 selectedRoom → 查询余额
     * 错误通过 [errorEvent] Channel 通知 UI 层显示。
     */
    fun fastQueryMyRoom(loggedInStudentId: String) {
        viewModelScope.launch {
            // 立即清空旧房间数据，防止闪现旧房间
            _uiState.update {
                it.copy(
                    isMyRoomQuerying = true,
                    selectedRoom = null,
                    balance = null,
                    error = null
                )
            }

            // 学号 → userId
            val userIdResult = api.queryUseridByStudentId(loggedInStudentId)
            val wechatUser = userIdResult.getOrNull()
            if (wechatUser == null || wechatUser.id.isBlank()) {
                _uiState.update { it.copy(isMyRoomQuerying = false) }
                _errorEvent.trySend("获取用户信息失败，请先在电费平台注册")
                return@launch
            }

            // userId → 房间列表
            val roomsResult = api.queryUserRoomList(wechatUser.id)
            roomsResult
                .onSuccess { rooms ->
                    if (rooms.isEmpty()) {
                        _uiState.update { it.copy(isMyRoomQuerying = false) }
                        _errorEvent.trySend("该账号下未绑定任何房间")
                    } else {
                        val buildingNode = BuildingNode(
                            id = rooms[0].roomId,
                            name = rooms[0].fullName.ifBlank { rooms[0].roomName },
                            num = rooms[0].roomName
                        )
                        _uiState.update {
                            it.copy(
                                isMyRoomQuerying = false,
                                myRoomList = rooms,
                                isBalanceRefreshing = true,
                                error = null,
                                selectedRoom = buildingNode,
                                balance = null,
                            )
                        }
                        // 直接查询余额
                        api.queryBalance(rooms[0].roomId)
                            .onSuccess { balance ->
                                _uiState.update { it.copy(isBalanceRefreshing = false, balance = balance) }
                            }
                            .onFailure { e ->
                                _uiState.update { it.copy(isBalanceRefreshing = false, error = "查询余额失败: ${e.localizedMessage}") }
                            }
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isMyRoomQuerying = false) }
                    _errorEvent.trySend("查询失败: ${e.localizedMessage}")
                }
        }
    }

    /**
     * 切换到指定房间并重新查询余额。
     */
    fun switchToMyRoom(room: UserRoomInfo) {
        val buildingNode = BuildingNode(
            id = room.roomId,
            name = room.fullName.ifBlank { room.roomName },
            num = room.roomName
        )

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBalanceRefreshing = true,
                    error = null,
                    selectedRoom = buildingNode,
                    balance = null
                )
            }
            api.queryBalance(room.roomId)
                .onSuccess { balance ->
                    _uiState.update { it.copy(isBalanceRefreshing = false, balance = balance) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isBalanceRefreshing = false, error = "查询余额失败: ${e.localizedMessage}") }
                }
        }
    }

    /**
     * 下拉刷新余额
     */
    fun refreshBalance() {
        val roomId = _uiState.value.selectedRoom?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBalanceRefreshing = true) }
            api.queryBalance(roomId)
                .onSuccess { balance ->
                    _uiState.update { it.copy(isBalanceRefreshing = false, balance = balance) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isBalanceRefreshing = false, error = "刷新失败: ${e.localizedMessage}")
                    }
                }
        }
    }

}
