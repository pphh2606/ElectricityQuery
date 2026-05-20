package edu.cqwu.electricity.ui.recharge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.cqwu.electricity.data.model.BalanceResponse
import edu.cqwu.electricity.data.model.OrderStatusResponse
import edu.cqwu.electricity.data.model.PaymentMethod
import edu.cqwu.electricity.data.model.UserRoomInfo
import edu.cqwu.electricity.data.repository.ElectricityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 充值页面独立状态
 */
data class RechargeUiState(
    // 学号/账号查询
    val studentId: String = "",
    val isQuerying: Boolean = false,
    val roomList: List<UserRoomInfo> = emptyList(),
    val selectedRoom: UserRoomInfo? = null,
    val error: String? = null,
    val fullName: String = "",
    val targetUserId: String = "",
    val targetRoomId: String = "",

    // 余额
    val balance: BalanceResponse? = null,
    val balanceLoading: Boolean = false,
    val isRefreshing: Boolean = false,

    // 充值金额
    val selectedAmount: Double? = null,
    val customAmount: String = "",

    // 订单创建
    val isRecharging: Boolean = false,
    val rechargeError: String? = null,
    val payUrl: String? = null,

    // 支付方式
    val selectedPaymentMethod: PaymentMethod? = null,
    val isProcessingPayment: Boolean = false,
    val paymentError: String? = null,
)

/**
 * 充值页面 ViewModel
 *
 * 管理学号查询、金额选择、订单创建、支付方式选择等充值全流程状态。
 * 与 [ElectricityViewModel] 解耦，不共享任何状态。
 */
class RechargeViewModel(
    private val repository: ElectricityRepository = ElectricityRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(RechargeUiState())
    val uiState: StateFlow<RechargeUiState> = _uiState.asStateFlow()

    // ================================================================
    //  充值金额
    // ================================================================

    /**
     * 选择预设充值金额
     */
    fun selectRechargeAmount(amount: Double) {
        _uiState.update {
            it.copy(selectedAmount = amount, customAmount = "", rechargeError = null)
        }
    }

    /**
     * 设置自定义充值金额
     */
    fun setCustomRechargeAmount(amount: String) {
        _uiState.update {
            it.copy(customAmount = amount, selectedAmount = null, rechargeError = null)
        }
    }

    /**
     * 获取当前有效的充值金额
     */
    private fun getEffectiveRechargeAmount(): Double? {
        val state = _uiState.value
        state.selectedAmount?.let { return it }
        val custom = state.customAmount.trim()
        if (custom.isNotBlank()) {
            return custom.toDoubleOrNull()
        }
        return null
    }

    /**
     * 提交充值请求（仅支持账号充值模式）
     */
    fun submitRecharge() {
        val uiState = _uiState.value
        val roomId = uiState.targetRoomId.ifBlank {
            _uiState.update { it.copy(rechargeError = "未选择房间") }
            return
        }
        val roomName = uiState.fullName.ifBlank {
            _uiState.update { it.copy(rechargeError = "未选择房间") }
            return
        }
        val userId = uiState.targetUserId
        val openId = uiState.studentId

        val amount = getEffectiveRechargeAmount()
        if (amount == null || amount <= 0) {
            _uiState.update { it.copy(rechargeError = "请输入有效金额") }
            return
        }
        if (amount > 1000) {
            _uiState.update { it.copy(rechargeError = "金额超出合理范围（0-1000元）") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(isRecharging = true, rechargeError = null, payUrl = null)
            }

            repository.createRechargeOrder(roomId, roomName, amount, userId, openId)
                .onSuccess { payUrl ->
                    _uiState.update {
                        it.copy(isRecharging = false, payUrl = payUrl)
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isRecharging = false,
                            rechargeError = "创建订单失败: ${e.localizedMessage}"
                        )
                    }
                }
        }
    }

    /**
     * 清除充值状态（保留账号充值状态）
     */
    fun clearRechargeState() {
        _uiState.update {
            it.copy(
                selectedAmount = null,
                customAmount = "",
                isRecharging = false,
                rechargeError = null,
                payUrl = null,
                selectedPaymentMethod = null,
                isProcessingPayment = false,
                paymentError = null,
            )
        }
    }

    /**
     * 仅清除充值错误，保留用户输入（学号、房间、金额等）。
     */
    fun clearRechargeError() {
        _uiState.update { it.copy(rechargeError = null) }
    }

    // ================================================================
    //  支付方式选择
    // ================================================================

    /**
     * 选择支付方式
     */
    fun selectPaymentMethod(method: PaymentMethod) {
        _uiState.update { it.copy(selectedPaymentMethod = method, paymentError = null) }
    }

    /**
     * 清除支付选择状态
     */
    fun clearPaymentState() {
        _uiState.update {
            it.copy(
                selectedPaymentMethod = null,
                paymentError = null,
                isProcessingPayment = false
            )
        }
    }

    /**
     * 查询订单状态（轮询用）
     */
    suspend fun queryPaymentOrderStatus(orderId: String): Result<OrderStatusResponse> {
        return repository.queryOrderStatus(orderId)
    }

    // ================================================================
    //  账号充值方法（学号 → userId → 房间列表 → 选择 → 充值）
    // ================================================================

    /**
     * 设置账号充值的学号
     */
    fun setAccountStudentId(studentId: String) {
        _uiState.update { it.copy(studentId = studentId, error = null) }
    }

    /**
     * 解析输入值：优先按学号查询 userId，查不到则降级直接使用输入值
     */
    private suspend fun resolveUserId(input: String): String {
        val userResult = repository.queryUseridByStudentId(input)
        val userData = userResult.getOrNull()
        return if (userData == null || userData.id.isBlank()) {
            android.util.Log.d("RechargeVM", "学号查询无结果，尝试将输入 [$input] 作为 userId 直接查询房间列表")
            input
        } else {
            userData.id
        }
    }

    /**
     * 通过学号或 userId 查询房间列表
     */
    private suspend fun queryRoomsByStudentId(input: String): Result<List<UserRoomInfo>> {
        val userId = resolveUserId(input)
        return repository.queryUserRoomList(userId)
    }

    /**
     * 查询账号绑定的房间列表
     * 流程：学号 → userId → 房间列表
     */
    fun queryAccountRoomList() {
        val studentId = _uiState.value.studentId.trim()
        if (studentId.isBlank()) {
            _uiState.update { it.copy(error = "请输入学号") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isQuerying = true, isRefreshing = true) }
            queryRoomsByStudentId(studentId)
                .onSuccess { rooms ->
                    if (rooms.isEmpty()) {
                        _uiState.update {
                            it.copy(isQuerying = false, isRefreshing = false, error = "该账号下未绑定任何房间")
                        }
                    } else {
                        _uiState.update {
                            it.copy(isQuerying = false, isRefreshing = false, roomList = rooms, selectedRoom = null, fullName = "")
                        }
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isQuerying = false, isRefreshing = false, error = "查询失败: ${e.localizedMessage}")
                    }
                }
        }
    }

    /**
     * 选择账号绑定的房间
     */
    fun selectAccountRoom(room: UserRoomInfo) {
        _uiState.update {
            it.copy(
                selectedRoom = room,
                fullName = room.fullName,
                targetUserId = room.userId.toString(),
                targetRoomId = room.roomId
            )
        }
    }

    /**
     * 加载充值页面的房间余额
     */
    fun loadRechargeBalance() {
        val state = _uiState.value
        val roomId = state.targetRoomId.ifBlank { return }

        viewModelScope.launch {
            _uiState.update { it.copy(balanceLoading = true) }
            repository.queryBalance(roomId)
                .onSuccess { balance ->
                    _uiState.update { it.copy(balanceLoading = false, balance = balance) }
                }
                .onFailure {
                    _uiState.update { it.copy(balanceLoading = false) }
                }
        }
    }

    /**
     * 充值页面下拉刷新：重新查询学号→房间列表并加载余额
     */
    fun refreshRechargeData() {
        val studentId = _uiState.value.studentId.trim()
        if (studentId.isBlank()) {
            _uiState.update { it.copy(isRefreshing = false) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isQuerying = true, isRefreshing = true) }
            queryRoomsByStudentId(studentId)
                .onSuccess { rooms ->
                    if (rooms.isEmpty()) {
                        _uiState.update {
                            it.copy(isQuerying = false, isRefreshing = false, error = "该账号下未绑定任何房间")
                        }
                    } else {
                        _uiState.update {
                            it.copy(isQuerying = false, isRefreshing = false, roomList = rooms, selectedRoom = null, fullName = "")
                        }
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isQuerying = false, isRefreshing = false, error = "查询失败: ${e.localizedMessage}")
                    }
                }
        }
    }

    /**
     * 切换账号充值房间
     */
    fun switchAccountRoom(room: UserRoomInfo) {
        selectAccountRoom(room)
        loadRechargeBalance()
    }

    /**
     * 清除账号充值状态
     */
    fun clearAccountRechargeState() {
        _uiState.update {
            it.copy(
                studentId = "",
                isQuerying = false,
                roomList = emptyList(),
                selectedRoom = null,
                error = null,
                fullName = "",
                targetUserId = "",
                targetRoomId = "",
                balance = null,
                balanceLoading = false
            )
        }
    }

    /**
     * 从当前登录用户自动填充学号并查询房间列表。
     *
     * 仅在充值输入框为空时填充（避免覆盖用户已手动输入的内容）。
     */
    fun autoFillFromLogin(loggedInStudentId: String?) {
        if (loggedInStudentId.isNullOrBlank()) {
            android.util.Log.d("RechargeVM", "autoFillFromLogin: 未登录，跳过自动填充")
            return
        }
        if (_uiState.value.studentId.isBlank()) {
            android.util.Log.d("RechargeVM", "autoFillFromLogin: 自动填充学号 [$loggedInStudentId]")
            setAccountStudentId(loggedInStudentId)
            queryAccountRoomList()
        }
    }
}
