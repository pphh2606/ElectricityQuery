package edu.cqwu.electricity.electricity.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import edu.cqwu.electricity.R
import edu.cqwu.electricity.electricity.data.CurrentDataResponse
import edu.cqwu.electricity.electricity.data.UsageResponse
import edu.cqwu.electricity.electricity.data.ElectricityApi
import edu.cqwu.electricity.theme.ui.UiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 详情页面独立状态。
 * 与 [ElectricityViewModel] 解耦，roomId 通过构造函数注入。
 * 字段定义与原有的 DetailState 保持一致。
 */
data class DetailState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: UiMessage? = null,
    val sixMonthUsage: UsageResponse? = null,
    val monthDailyUsage: UsageResponse? = null,
    val currentData: CurrentDataResponse? = null,
)

/**
 * 详情页面专用 ViewModel。
 *
 * 与 [ElectricityViewModel] 完全解耦，roomId 通过构造函数注入，
 * 生命周期跟随 DETAIL 路由的 NavBackStackEntry，不受 ElectricityMainScreen 生命周期影响。
 */
class DetailViewModel(
    private val roomId: String,
    private val api: ElectricityApi = ElectricityApi()
) : ViewModel() {

    /**
     * 创建 [DetailViewModel] 的工厂。
     * @param roomId 要查询详情的房间 ID
     */
    class Factory(private val roomId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DetailViewModel(roomId) as T
        }
    }

    private val _detailState = MutableStateFlow(DetailState())
    val detailState: StateFlow<DetailState> = _detailState.asStateFlow()

    /**
     * 最近6个月用电记录
     */
    fun loadSixMonthUsage() {
        if (roomId.isBlank()) {
            _detailState.update { it.copy(error = UiMessage(R.string.detail_room_not_selected)) }
            return
        }
        viewModelScope.launch {
            _detailState.update {
                it.copy(isLoading = true, isRefreshing = true, error = null, sixMonthUsage = null)
            }
            api.querySixMonthUsage(roomId)
                .onSuccess { data ->
                    _detailState.update {
                        it.copy(isLoading = false, isRefreshing = false, sixMonthUsage = data)
                    }
                }
                .onFailure { e ->
                    _detailState.update {
                        it.copy(isLoading = false, isRefreshing = false, error = UiMessage(R.string.detail_query_failed, listOf(e.localizedMessage ?: "")))
                    }
                }
        }
    }

    /**
     * 本月每日用电
     */
    fun loadMonthDailyUsage() {
        if (roomId.isBlank()) {
            _detailState.update { it.copy(error = UiMessage(R.string.detail_room_not_selected)) }
            return
        }
        viewModelScope.launch {
            _detailState.update {
                it.copy(isLoading = true, isRefreshing = true, error = null, monthDailyUsage = null)
            }
            api.queryMonthDailyUsage(roomId)
                .onSuccess { data ->
                    _detailState.update {
                        it.copy(isLoading = false, isRefreshing = false, monthDailyUsage = data)
                    }
                }
                .onFailure { e ->
                    _detailState.update {
                        it.copy(isLoading = false, isRefreshing = false, error = UiMessage(R.string.detail_query_failed, listOf(e.localizedMessage ?: "")))
                    }
                }
        }
    }

    /**
     * 近24h用电明细 & 电表实时状态（共享同一个 API）
     */
    fun loadCurrentData() {
        if (roomId.isBlank()) {
            _detailState.update { it.copy(error = UiMessage(R.string.detail_room_not_selected)) }
            return
        }
        viewModelScope.launch {
            _detailState.update {
                it.copy(isLoading = true, isRefreshing = true, error = null, currentData = null)
            }
            api.queryCurrentData(roomId)
                .onSuccess { data ->
                    _detailState.update {
                        it.copy(isLoading = false, isRefreshing = false, currentData = data)
                    }
                }
                .onFailure { e ->
                    _detailState.update {
                        it.copy(isLoading = false, isRefreshing = false, error = UiMessage(R.string.detail_query_failed, listOf(e.localizedMessage ?: "")))
                    }
                }
        }
    }

    /**
     * 清除详情数据，在页面离开时调用。
     */
    fun clearDetailData() {
        _detailState.update { DetailState() }
    }
}
