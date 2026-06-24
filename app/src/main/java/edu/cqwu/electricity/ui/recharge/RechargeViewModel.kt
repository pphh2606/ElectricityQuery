package edu.cqwu.electricity.ui.recharge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.cqwu.electricity.data.model.BalanceResponse
import edu.cqwu.electricity.data.model.BuyRecord
import edu.cqwu.electricity.data.model.PaymentMethod
import edu.cqwu.electricity.data.model.RechargeTimeRange
import edu.cqwu.electricity.data.model.UserRoomInfo
import edu.cqwu.electricity.data.network.pay.electricityrecharge.ElectricityApi
import edu.cqwu.electricity.data.network.pay.electricityrecharge.ElectricityPayApi
import edu.cqwu.electricity.data.network.pay.electricityrecharge.ShowselectPageData
import edu.cqwu.electricity.ui.paycommom.PaymentFlowDelegate
import edu.cqwu.electricity.ui.paycommom.PaymentState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 充值页面独立状态
 */
data class RechargeUiState(
    // 学号/账号查询
    val studentId: String = "",
    val isQuerying: Boolean = false,
    val roomList: List<UserRoomInfo> = emptyList(),
    val selectedRoom: UserRoomInfo? = null,
    val queryError: String? = null,
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
    val isCreatingOrder: Boolean = false,
    val createOrderError: String? = null,
    val payUrl: String? = null,
    val showselectData: ShowselectPageData? = null,

    // 支付流程（共享 PaymentState）
    val payment: PaymentState = PaymentState(),
)

/**
 * 充值记录独立状态。
 * 生命周期跟随 RECHARGE_RECORD 路由，不受 ElectricityViewModel 影响。
 */
data class RechargeRecordState(
    val isQuerying: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val list: List<BuyRecord> = emptyList(),
    val timeRange: Int = 0,
    val hasQueried: Boolean = false,
    val roomId: String = "",
)

/**
 * 充值页面 ViewModel
 *
 * 管理学号查询、金额选择、订单创建、支付方式选择等充值全流程状态，
 * 以及充值记录查询（独立 [RechargeRecordState]）。
 *
 * 与 [ElectricityViewModel] 解耦，不共享任何状态。
 */
class RechargeViewModel : ViewModel() {

    private val electricityApi = ElectricityApi()
    private val payApi = ElectricityPayApi()

    private val _uiState = MutableStateFlow(RechargeUiState())
    val uiState: StateFlow<RechargeUiState> = _uiState.asStateFlow()

    private val _recordState = MutableStateFlow(RechargeRecordState())
    val recordState: StateFlow<RechargeRecordState> = _recordState.asStateFlow()

    /** 支付流程委托，封装与 CardRechargeViewModel 共有的支付/金额逻辑 */
    private val paymentFlowDelegate = PaymentFlowDelegate(
        scope = viewModelScope,
        getPaymentState = { _uiState.value.payment },
        updatePayment = { transform -> _uiState.update { it.copy(payment = it.payment.transform()) } },
        getSelectedAmount = { _uiState.value.selectedAmount },
        updateAmount = { selectedAmount, customAmount ->
            _uiState.update { it.copy(selectedAmount = selectedAmount, customAmount = customAmount) }
        },
        getCustomAmount = { _uiState.value.customAmount },
        clearOrderError = { _uiState.update { it.copy(createOrderError = null) } },
    )

    // ================================================================
    //  充值金额（委托给 paymentFlowDelegate）
    // ================================================================

    /**
     * 选择预设充值金额
     */
    fun selectRechargeAmount(amount: Double) = paymentFlowDelegate.selectAmount(amount)

    /**
     * 设置自定义充值金额
     */
    fun setCustomRechargeAmount(amount: String) = paymentFlowDelegate.setCustomAmount(amount)

    /**
     * 获取当前有效的充值金额
     */
    fun getEffectiveRechargeAmount(): Double? = paymentFlowDelegate.getEffectiveAmount()

    /**
     * 提交充值请求（仅支持账号充值模式）
     *
     * 改造后流程：
     * 1. 创建订单 → 获取 payUrl
     * 2. OkHttp 加载 showselect HTML → 解析 orderNo/orderId
     */
    fun submitRecharge() {
        val uiState = _uiState.value
        val roomId = uiState.targetRoomId.ifBlank {
            _uiState.update { it.copy(createOrderError = "未选择房间") }
            return
        }
        val roomName = uiState.fullName.ifBlank {
            _uiState.update { it.copy(createOrderError = "未选择房间") }
            return
        }
        val userId = uiState.targetUserId
        val openId = uiState.studentId

        val amount = getEffectiveRechargeAmount()
        if (amount == null || amount <= 0) {
            _uiState.update { it.copy(createOrderError = "请输入有效金额") }
            return
        }
        if (amount > 1000) {
            _uiState.update { it.copy(createOrderError = "金额超出合理范围（0-1000元）") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(isCreatingOrder = true, createOrderError = null, payUrl = null, showselectData = null)
            }

            electricityApi.createRechargeOrder(roomId, roomName, amount, userId, openId)
                .onSuccess { payUrl ->
                    // OkHttp 加载 showselect 页面并解析隐藏字段
                    payApi.fetchShowselectHtml(payUrl)
                        .onSuccess { showselectData ->
                            _uiState.update {
                                it.copy(
                                    isCreatingOrder = false,
                                    payUrl = payUrl,
                                    showselectData = showselectData,
                                )
                            }
                        }
                        .onFailure { e ->
                            _uiState.update {
                                it.copy(
                                    isCreatingOrder = false,
                                    createOrderError = "解析订单信息失败: ${e.localizedMessage}"
                                )
                            }
                        }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isCreatingOrder = false,
                            createOrderError = "创建订单失败: ${e.localizedMessage}"
                        )
                    }
                }
        }
    }

    // ================================================================
    //  支付提交（委托给 paymentFlowDelegate）
    // ================================================================

    /**
     * 提交支付请求
     *
     * 调用 gotToPay API 获取 sbHtml（支付宝）或 mwebUrl（微信）。
     */
    fun submitPayment() {
        val showselect = _uiState.value.showselectData ?: return
        paymentFlowDelegate.submitPayment(
            getOrderNo = { showselect.orderNo },
            executePayment = { orderNo ->
                val method = _uiState.value.payment.selectedMethod!!
                val result = payApi.gotToPay(orderNo, method.payType, showselect.publictype, showselect.openId)
                    .getOrThrow()
                Pair(result.sbHtml, result.mwebUrl)
            },
        )
    }

    // ================================================================
    //  订单状态轮询（委托给 paymentFlowDelegate）
    // ================================================================

    /**
     * 启动订单状态轮询
     *
     * 在用户从外部支付应用返回后调用。
     */
    fun startPollingOrderStatus(orderId: String) {
        paymentFlowDelegate.startPollingOrderStatus(
            orderId = orderId,
            queryStatus = { id ->
                val result = payApi.queryOrderStatus(id)
                val data = result.getOrNull()
                val status = data?.status
                android.util.Log.d("RechargeVM", "轮询订单状态: orderId=$id, status=$status")
                status
            },
        )
    }

    /**
     * 清除充值状态（保留账号充值状态）
     */
    fun clearRechargeState() {
        _uiState.update {
            it.copy(
                selectedAmount = null,
                customAmount = "",
                isCreatingOrder = false,
                createOrderError = null,
                payUrl = null,
                showselectData = null,
                payment = PaymentState(),
            )
        }
    }

    /**
     * 仅清除充值错误，保留用户输入（学号、房间、金额等）。
     */
    fun clearOrderError() {
        _uiState.update { it.copy(createOrderError = null) }
    }

    // ================================================================
    //  支付方式选择（委托给 paymentFlowDelegate）
    // ================================================================

    /**
     * 选择支付方式
     */
    fun selectPaymentMethod(method: PaymentMethod) = paymentFlowDelegate.selectPaymentMethod(method)

    /**
     * 清除支付选择状态
     */
    fun clearPaymentState() = paymentFlowDelegate.clearPaymentState()

    // ================================================================
    //  账号充值方法（学号 → userId → 房间列表 → 选择 → 充值）
    // ================================================================

    /**
     * 设置账号充值的学号
     */
    fun setAccountStudentId(studentId: String) {
        _uiState.update { it.copy(studentId = studentId, queryError = null) }
    }

    /**
     * 解析输入值：优先按学号查询 userId，查不到则降级直接使用输入值
     */
    private suspend fun resolveUserId(input: String): String {
        val userResult = electricityApi.queryUseridByStudentId(input)
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
        return electricityApi.queryUserRoomList(userId)
    }

    /**
     * 内部方法：查询学号对应的房间列表并自动选择第一个房间。
     * 由 [queryAccountRoomList] 和 [refreshRechargeData] 共用。
     */
    private fun fetchRooms(studentId: String) {
        if (studentId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isQuerying = true, isRefreshing = true) }
            queryRoomsByStudentId(studentId)
                .onSuccess { rooms ->
                    if (rooms.isEmpty()) {
                        _uiState.update {
                            it.copy(isQuerying = false, isRefreshing = false, queryError = "该账号下未绑定任何房间")
                        }
                    } else {
                        // 自动选择第一个房间：即使 roomList 内容与之前相同（LaunchedEffect key 不变），
                        // 也通过直接 selectAccountRoom 确保充值内容显示
                        _uiState.update {
                            it.copy(isQuerying = false, isRefreshing = false, roomList = rooms, selectedRoom = null, fullName = "")
                        }
                        selectAccountRoom(rooms[0])
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isQuerying = false, isRefreshing = false, queryError = "查询失败: ${e.localizedMessage}")
                    }
                }
        }
    }

    /**
     * 查询账号绑定的房间列表
     * 流程：学号 → userId → 房间列表
     */
    fun queryAccountRoomList() {
        val studentId = _uiState.value.studentId.trim()
        if (studentId.isBlank()) {
            _uiState.update { it.copy(queryError = "请输入学号") }
            return
        }
        fetchRooms(studentId)
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
            electricityApi.queryBalance(roomId)
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
        fetchRooms(studentId)
    }

    /**
     * 切换账号充值房间
     */
    fun switchAccountRoom(room: UserRoomInfo) {
        selectAccountRoom(room)
        loadRechargeBalance()
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

    // ================================================================
    //  充值记录查询（独立 _recordState）
    // ================================================================

    /**
     * 设置充值记录查询时间范围
     */
    fun setRechargeRecordTimeRange(index: Int) {
        _recordState.update { it.copy(timeRange = index) }
    }

    /**
     * 查询充值记录
     * @param roomId 要查询的房间 ID
     */
    fun queryRechargeRecords(roomId: String) {
        if (roomId.isBlank()) {
            _recordState.update { it.copy(error = "未选择房间") }
            return
        }
        viewModelScope.launch {
            _recordState.update {
                it.copy(isQuerying = true, isRefreshing = true, error = null, list = emptyList(), roomId = roomId)
            }
            val calendar = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val endTime = dateFormat.format(calendar.time)
            val timeRange = RechargeTimeRange.fromIndex(_recordState.value.timeRange)
            calendar.add(Calendar.DAY_OF_YEAR, -timeRange.days.toInt())
            val beginTime = dateFormat.format(calendar.time)
            val buyResult = electricityApi.queryBuyList(roomId, "0", beginTime, endTime)
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

    /**
     * 清除充值记录查询状态
     */
    fun clearRechargeRecordState() {
        _recordState.update { RechargeRecordState() }
    }
}
