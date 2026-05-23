package edu.cqwu.electricity.ui.feeservicehall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.cqwu.electricity.data.network.FeeCategory
import edu.cqwu.electricity.data.network.FeeServiceHallApi
import edu.cqwu.electricity.data.network.OrderRecord
import edu.cqwu.electricity.data.network.UserProfile
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
    val showOrderFilter: Boolean = false,
    val filterProjectName: String = "",
    val filterStartDate: String = "",
    val filterEndDate: String = "",

    // 个人资料 Tab
    val profile: UserProfile? = null,
    val isProfileLoading: Boolean = false,
    val profileError: String? = null,
)

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
                loadOrders(isRefresh = true)
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
    private fun loadOrders(isRefresh: Boolean) {
        viewModelScope.launch {
            val s = _uiState.value
            _uiState.update {
                if (isRefresh) it.copy(isOrdersRefreshing = true, orderErrorMessage = null)
                else it.copy(isOrdersLoading = true, orderErrorMessage = null)
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
                    _uiState.update { it.copy(isOrdersLoading = false, isOrdersRefreshing = false, orderErrorMessage = e.message) }
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

    fun refreshOrders() { loadOrders(isRefresh = true) }
    fun toggleOrderFilter() { _uiState.update { it.copy(showOrderFilter = !it.showOrderFilter) } }
    fun setOrderFilterProjectName(n: String) { _uiState.update { it.copy(filterProjectName = n) } }
    fun setOrderFilterStartDate(d: String) { _uiState.update { it.copy(filterStartDate = d) } }
    fun setOrderFilterEndDate(d: String) { _uiState.update { it.copy(filterEndDate = d) } }
    fun applyOrderFilter() { _uiState.update { it.copy(showOrderFilter = false) }; loadOrders(isRefresh = true) }
    fun resetOrderFilter() {
        val cal = Calendar.getInstance()
        val end = dateFormat.format(cal.time)
        cal.add(Calendar.YEAR, -4)
        val start = dateFormat.format(cal.time)
        _uiState.update { it.copy(filterProjectName = "", filterStartDate = start, filterEndDate = end, showOrderFilter = false) }
        loadOrders(isRefresh = true)
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
