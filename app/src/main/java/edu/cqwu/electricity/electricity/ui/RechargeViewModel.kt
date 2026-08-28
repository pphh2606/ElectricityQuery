package edu.cqwu.electricity.electricity.ui
import edu.cqwu.electricity.logging.AppLog

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import edu.cqwu.electricity.R
import edu.cqwu.electricity.electricity.data.BalanceResponse
import edu.cqwu.electricity.electricity.data.BuyRecord
import edu.cqwu.electricity.payment.data.PaymentMethod
import edu.cqwu.electricity.electricity.data.RechargeTimeRange
import edu.cqwu.electricity.electricity.data.UserRoomInfo
import edu.cqwu.electricity.electricity.data.ElectricityApi
import edu.cqwu.electricity.electricity.data.ElectricityPayApi
import edu.cqwu.electricity.electricity.data.ShowselectPageData
import edu.cqwu.electricity.login.data.AccountSessionStore
import edu.cqwu.electricity.payment.ui.PaymentFlowDelegate
import edu.cqwu.electricity.payment.ui.PaymentState
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
class RechargeViewModel(application: Application) : AndroidViewModel(application) {

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
        getString = { getApplication<Application>().getString(it) },
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
            _uiState.update { it.copy(createOrderError = getApplication<Application>().getString(R.string.error_no_room)) }
            return
        }
        val roomName = uiState.fullName.ifBlank {
            _uiState.update { it.copy(createOrderError = getApplication<Application>().getString(R.string.error_no_room)) }
            return
        }
        val userId = uiState.targetUserId
        val openId = uiState.studentId

        val amount = getEffectiveRechargeAmount()!!

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
                                    createOrderError = getApplication<Application>().getString(R.string.error_parse_order_failed, e.localizedMessage ?: "")
                                )
                            }
                        }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isCreatingOrder = false,
                            createOrderError = getApplication<Application>().getString(R.string.error_create_order_failed, e.localizedMessage ?: "")
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
                AppLog.d("RechargeVM", "轮询订单状态: orderId=$id, status=$status")
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
            AppLog.d("RechargeVM", "学号查询无结果，尝试将输入 [$input] 作为 userId 直接查询房间列表")
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
                            it.copy(isQuerying = false, isRefreshing = false, queryError = getApplication<Application>().getString(R.string.error_no_rooms_bound))
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
                        it.copy(isQuerying = false, isRefreshing = false, queryError = getApplication<Application>().getString(R.string.error_query_failed_detail, e.localizedMessage ?: ""))
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
            _uiState.update { it.copy(queryError = getApplication<Application>().getString(R.string.error_enter_student_id)) }
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
     * 从当前登录账号自动填充数字学号并查询房间列表。
     *
     * 登录用户名可能是登录别名（非学号），而电费系统只认数字学号；
     * 学号在登录时获取并随账号缓存（[AccountSessionStore.getActiveStudentId]，本地读取零网络）。
     *
     * 仅在充值输入框为空时填充（避免覆盖用户已手动输入的内容）；
     * 未登录或本地无学号（未回填）时静默跳过，不打扰用户。
     */
    fun autoFillStudentIdFromLogin() {
        viewModelScope.launch {
            val studentId = AccountSessionStore.getActiveStudentId()
            if (studentId.isNullOrBlank()) {
                AppLog.d("RechargeVM", "autoFillStudentIdFromLogin: 本地无学号（未登录或未回填），跳过自动填充")
                return@launch
            }
            if (_uiState.value.studentId.isBlank()) {
                AppLog.d("RechargeVM", "autoFillStudentIdFromLogin: 自动填充数字学号 [$studentId]")
                setAccountStudentId(studentId)
                queryAccountRoomList()
            }
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
            _recordState.update { it.copy(error = getApplication<Application>().getString(R.string.error_no_room)) }
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
                val errorMsg = buyData?.resultMsg ?: getApplication<Application>().getString(R.string.error_query_record_failed)
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
