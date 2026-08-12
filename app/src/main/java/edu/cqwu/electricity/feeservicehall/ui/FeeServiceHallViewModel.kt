package edu.cqwu.electricity.feeservicehall.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.cqwu.electricity.feeservicehall.data.FeeCategory
import edu.cqwu.electricity.feeservicehall.data.FeeServiceHallApi
import edu.cqwu.electricity.feeservicehall.data.OrderRecord
import edu.cqwu.electricity.feeservicehall.data.UserProfile
import edu.cqwu.electricity.login.data.SessionExpiredException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class FeeServiceHallUiState(
    // 主页 Tab
    val categories: List<FeeCategory> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,

    // 订单 Tab
    val orders: List<OrderRecord> = emptyList(),
    val isOrdersLoading: Boolean = false,
    val isOrdersRefreshing: Boolean = false,
    val isLoadingMoreOrders: Boolean = false,
    val orderPageCurrent: Int = 1,
    val orderTotalPages: Int = 1,
    val orderHasMore: Boolean = false,
    val orderErrorMessage: String? = null,
    val orderRequiresReLogin: Boolean = false,
    val showOrderFilter: Boolean = false,
    val filterProjectName: String = "",
    val filterStartDate: String = "",
    val filterEndDate: String = "",

    // 关闭订单
    val isClosingOrder: Boolean = false,
    val closeOrderResult: CloseOrderResult? = null,

    // 个人资料 Tab
    val profile: UserProfile? = null,
    val isProfileLoading: Boolean = false,
    val profileError: String? = null,
)

/** 关闭订单结果，消费后重置为 null */
sealed class CloseOrderResult {
    data object Success : CloseOrderResult()
    data class Error(val message: String) : CloseOrderResult()
}

class FeeServiceHallViewModel : ViewModel() {

    private val api = FeeServiceHallApi()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private val _uiState = MutableStateFlow(FeeServiceHallUiState())
    val uiState: StateFlow<FeeServiceHallUiState> = _uiState.asStateFlow()

    private var hasLoadedProjects = false
    private var hasLoadedOrders = false
    private var hasLoadedProfile = false

    fun loadIfNeeded() {
        // 首次仅加载主页，订单和个人资料按需加载
        if (!hasLoadedProjects) { hasLoadedProjects = true; loadProjects() }
    }

    /**
     * 按 Tab 切换触发按需加载。
     * 在 Screen 的 HorizontalPager 页面切换时调用。
     */
    fun onTabSelected(index: Int) {
        when (index) {
            0 -> if (!hasLoadedProjects) { hasLoadedProjects = true; loadProjects() }
            1 -> if (!hasLoadedOrders) {
                hasLoadedOrders = true
                val cal = Calendar.getInstance()
                val end = dateFormat.format(cal.time)
                cal.add(Calendar.YEAR, -4)
                val start = dateFormat.format(cal.time)
                _uiState.update { it.copy(filterStartDate = start, filterEndDate = end) }
                loadOrders()
            }
            2 -> if (!hasLoadedProfile) { hasLoadedProfile = true; loadProfile() }
        }
    }

    // ── 主页 ──
    private fun loadProjects() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            api.fetchProjects().onSuccess { c ->
                _uiState.update { it.copy(categories = c, isLoading = false) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    fun refreshProjects() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            api.fetchProjects().onSuccess { c ->
                _uiState.update { it.copy(categories = c, isRefreshing = false) }
            }.onFailure { e ->
                _uiState.update { it.copy(isRefreshing = false, errorMessage = e.message) }
            }
        }
    }

    // ── 订单 ──
    private fun loadOrders() {
        viewModelScope.launch {
            val s = _uiState.value
            _uiState.update {
                it.copy(isOrdersRefreshing = true, orderErrorMessage = null, orderRequiresReLogin = false)
            }
            api.fetchOrders(1, 10, s.filterProjectName, "", s.filterStartDate, s.filterEndDate)
                .onSuccess { page ->
                    _uiState.update {
                        it.copy(
                            orders = page.records, orderPageCurrent = 1,
                            orderTotalPages = page.pages ?: 1,
                            orderHasMore = (page.current ?: 1) < (page.pages ?: 1),
                            isOrdersLoading = false, isOrdersRefreshing = false,
                        )
                    }
                }.onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isOrdersLoading = false,
                            isOrdersRefreshing = false,
                            orderErrorMessage = e.message,
                            orderRequiresReLogin = e is SessionExpiredException,
                        )
                    }
                }
        }
    }

    fun loadMoreOrders() {
        val s = _uiState.value
        if (s.isLoadingMoreOrders || !s.orderHasMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMoreOrders = true) }
            val next = s.orderPageCurrent + 1
            api.fetchOrders(next, 10, s.filterProjectName, "", s.filterStartDate, s.filterEndDate)
                .onSuccess { page ->
                    _uiState.update {
                        it.copy(
                            orders = it.orders + page.records, orderPageCurrent = next,
                            orderTotalPages = page.pages ?: 1,
                            orderHasMore = (page.current ?: next) < (page.pages ?: 1), isLoadingMoreOrders = false,
                        )
                    }
                }.onFailure { _uiState.update { it.copy(isLoadingMoreOrders = false) } }
        }
    }

    fun refreshOrders() { loadOrders() }

    /** 关闭订单 */
    fun closeOrder(orderId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isClosingOrder = true) }
            api.closeOrder(orderId)
                .onSuccess {
                    _uiState.update { it.copy(isClosingOrder = false, closeOrderResult = CloseOrderResult.Success) }
                    refreshOrders()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isClosingOrder = false, closeOrderResult = CloseOrderResult.Error(e.message ?: "未知错误")) }
                }
        }
    }

    /** 消费关闭订单结果 */
    fun consumeCloseOrderResult() {
        _uiState.update { it.copy(closeOrderResult = null) }
    }
    fun toggleOrderFilter() { _uiState.update { it.copy(showOrderFilter = !it.showOrderFilter) } }
    fun setOrderFilterProjectName(n: String) { _uiState.update { it.copy(filterProjectName = n) } }
    fun setOrderFilterStartDate(d: String) { _uiState.update { it.copy(filterStartDate = d) } }
    fun setOrderFilterEndDate(d: String) { _uiState.update { it.copy(filterEndDate = d) } }
    fun applyOrderFilter() { _uiState.update { it.copy(showOrderFilter = false) }; loadOrders() }
    fun resetOrderFilter() {
        val cal = Calendar.getInstance()
        val end = dateFormat.format(cal.time)
        cal.add(Calendar.YEAR, -4)
        val start = dateFormat.format(cal.time)
        _uiState.update { it.copy(filterProjectName = "", filterStartDate = start, filterEndDate = end, showOrderFilter = false) }
        loadOrders()
    }

    // ── 个人资料 ──
    private fun loadProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isProfileLoading = true, profileError = null) }
            api.fetchUserProfile().onSuccess { p ->
                _uiState.update { it.copy(profile = p, isProfileLoading = false) }
            }.onFailure { e ->
                _uiState.update { it.copy(isProfileLoading = false, profileError = e.message) }
            }
        }
    }

    fun refreshAll() {
        refreshProjects()
        refreshOrders()
        loadProfile()
    }
}
