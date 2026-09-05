package edu.cqwu.electricity.electricity.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.cqwu.electricity.R
import edu.cqwu.electricity.electricity.data.BalanceResponse
import edu.cqwu.electricity.electricity.data.BuildingNode
import edu.cqwu.electricity.electricity.data.UserRoomInfo
import edu.cqwu.electricity.electricity.data.ElectricityApi
import edu.cqwu.electricity.common.net.SessionExpiredException
import edu.cqwu.electricity.logging.AppLog
import edu.cqwu.electricity.login.domain.SessionCoordinatorV2
import edu.cqwu.electricity.theme.ui.UiMessage
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
    val error: UiMessage? = null,
    val queryError: UiMessage? = null,
    val requiresReLogin: Boolean = false,
)

/**
 * "我的寝室" Tab ViewModel
 *
 * 管理当前登录用户绑定的寝室列表查询和余额加载。
 * 与 [ElectricityViewModel] 完全解耦，不共享任何状态。
 */
class MyRoomViewModel(
    private val api: ElectricityApi = ElectricityApi(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(MyRoomUiState())
    val uiState: StateFlow<MyRoomUiState> = _uiState.asStateFlow()

    // 错误事件 Channel
    private val _errorEvent = Channel<UiMessage>(Channel.BUFFERED)
    val errorEvent = _errorEvent.receiveAsFlow()

    /**
     * 查询当前登录用户绑定的寝室列表，直接更新 selectedRoom 并查询余额。
     *
     * 内部先获取当前登录账号的数字学号（登录用户名可能是登录别名，电费系统只认数字学号），
     * 流程：数字学号 → userId → 房间列表 → 更新 selectedRoom → 查询余额
     * 错误通过 [errorEvent] Channel 通知 UI 层显示。
     */
    fun fastQueryMyRoom() {
        viewModelScope.launch {
            // 立即清空旧房间数据，防止闪现旧房间
            _uiState.update {
                it.copy(
                    isMyRoomQuerying = true,
                    selectedRoom = null,
                    balance = null,
                    error = null,
                    queryError = null,
                    requiresReLogin = false,
                )
            }

            // 获取当前登录账号的数字学号（登录时缓存，本地读取零网络）
            val account = SessionCoordinatorV2.currentAccount()
            if (account == null) {
                AppLog.d("MyRoomVM", "fastQueryMyRoom: 未登录")
                _uiState.update { it.copy(isMyRoomQuerying = false, requiresReLogin = true) }
                return@launch
            }
            val studentId = account.studentId
            if (studentId.isNullOrBlank()) {
                AppLog.w("MyRoomVM", "fastQueryMyRoom: 本地无学号（未回填）")
                _uiState.update {
                    it.copy(
                        isMyRoomQuerying = false,
                        queryError = UiMessage(R.string.common_query_failed, listOf("账号学号未就绪，请稍后重试")),
                    )
                }
                return@launch
            }

            // 学号 → userId
            val userIdResult = api.queryUseridByStudentId(studentId)
            val wechatUser = userIdResult.getOrNull()
            if (wechatUser == null || wechatUser.id.isBlank()) {
                _uiState.update {
                    it.copy(
                        isMyRoomQuerying = false,
                        queryError = UiMessage(R.string.electricity_user_not_registered),
                    )
                }
                return@launch
            }

            // userId → 房间列表
            val roomsResult = api.queryUserRoomList(wechatUser.id)
            roomsResult
                .onSuccess { rooms ->
                    if (rooms.isEmpty()) {
                        _uiState.update {
                            it.copy(
                                isMyRoomQuerying = false,
                                queryError = UiMessage(R.string.electricity_no_room_bound),
                            )
                        }
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
                                queryError = null,
                                requiresReLogin = false,
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
                                _uiState.update { it.copy(isBalanceRefreshing = false, error = UiMessage(R.string.electricity_balance_query_failed, listOf(e.localizedMessage ?: ""))) }
                            }
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isMyRoomQuerying = false,
                            queryError = UiMessage(R.string.common_query_failed, listOf(e.localizedMessage ?: "")),
                            requiresReLogin = e is SessionExpiredException,
                        )
                    }
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
                    queryError = null,
                    requiresReLogin = false,
                    selectedRoom = buildingNode,
                    balance = null
                )
            }
            api.queryBalance(room.roomId)
                .onSuccess { balance ->
                    _uiState.update { it.copy(isBalanceRefreshing = false, balance = balance) }
                }
                .onFailure { e ->
                _uiState.update { it.copy(isBalanceRefreshing = false, error = UiMessage(R.string.electricity_balance_query_failed, listOf(e.localizedMessage ?: ""))) }
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
                        it.copy(isBalanceRefreshing = false, error = UiMessage(R.string.common_refresh_failed, listOf(e.localizedMessage ?: "")))
                    }
                }
        }
    }

}
