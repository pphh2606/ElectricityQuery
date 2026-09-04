package edu.cqwu.electricity.electricity.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import edu.cqwu.electricity.R
import edu.cqwu.electricity.electricity.data.RecordQueryResultV2
import edu.cqwu.electricity.electricity.data.RecordRepositoryV2
import edu.cqwu.electricity.electricity.data.SubsidyRecord
import edu.cqwu.electricity.theme.ui.UiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 补助记录页面状态。
 */
data class SubsidyRecordUiState(
    /** 是否有请求在途（同时驱动顶部下拉刷新式指示与加载留白） */
    val isRefreshing: Boolean = false,
    val error: UiMessage? = null,
    val records: List<SubsidyRecord> = emptyList(),
    val totalQuantity: Double = 0.0,
    val totalAmount: Double = 0.0,
    val beginTime: String = "",
    val endTime: String = "",
    /** 当前内容视图：表格 / 折线图 */
    val viewMode: RecordViewModeV2 = RecordViewModeV2.TABLE,
)

/**
 * 补助记录页面 ViewModel。
 *
 * 补助接口不区分能源类型与粒度，仅按房间 + 时间区间查询全部补助记录。
 * roomId 通过构造函数注入，生命周期跟随 SUBSIDY_RECORD 路由。
 *
 * 规范分层：通过 [RecordRepositoryV2] 查询，不直接依赖网络实现，便于注入假仓库测试。
 */
class SubsidyRecordViewModel(
    private val roomId: String,
    private val repository: RecordRepositoryV2 = RecordRepositoryV2(),
) : ViewModel() {

    /** 创建 [SubsidyRecordViewModel] 的工厂 */
    class Factory(private val roomId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SubsidyRecordViewModel(roomId) as T
        }
    }

    private val _uiState = MutableStateFlow(SubsidyRecordUiState())
    val uiState: StateFlow<SubsidyRecordUiState> = _uiState.asStateFlow()

    init {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calendar = Calendar.getInstance()
        val endTime = dateFormat.format(calendar.time)
        calendar.set(1970, Calendar.JANUARY, 1)
        val beginTime = dateFormat.format(calendar.time)
        _uiState.update { it.copy(beginTime = beginTime, endTime = endTime) }
        query()
    }

    /** 设置起始日期并重新查询 */
    fun setBeginTime(date: String) {
        if (_uiState.value.isRefreshing) return
        _uiState.update { it.copy(beginTime = date) }
        query()
    }

    /** 设置结束日期并重新查询 */
    fun setEndTime(date: String) {
        if (_uiState.value.isRefreshing) return
        _uiState.update { it.copy(endTime = date) }
        query()
    }

    /** 切换表格/折线图视图（只改展示方式，不重新请求数据） */
    fun toggleViewMode() {
        _uiState.update { st ->
            st.copy(
                viewMode = if (st.viewMode == RecordViewModeV2.TABLE) RecordViewModeV2.CHART else RecordViewModeV2.TABLE,
            )
        }
    }

    /** 下拉刷新（沿用当前筛选条件） */
    fun refresh() {
        if (_uiState.value.isRefreshing) return
        query()
    }

    /** 页面离开时清理状态 */
    fun clearState() {
        _uiState.update { SubsidyRecordUiState() }
    }

    private fun query() {
        if (roomId.isBlank()) {
            _uiState.update { it.copy(isRefreshing = false, error = UiMessage(res = R.string.record_error_no_room)) }
            return
        }
        viewModelScope.launch {
            val state = _uiState.value
            _uiState.update {
                it.copy(isRefreshing = true, error = null, records = emptyList(), totalQuantity = 0.0, totalAmount = 0.0)
            }
            when (val result = repository.querySubsidyRecordsV2(
                roomId = roomId,
                beginTime = state.beginTime,
                endTime = state.endTime,
            )) {
                is RecordQueryResultV2.Success -> {
                    val records = result.records
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            records = records,
                            totalQuantity = records.sumOf { r -> r.quantity },
                            totalAmount = records.sumOf { r -> r.amount },
                        )
                    }
                }

                is RecordQueryResultV2.Failure -> {
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            error = toRecordErrorUiMessage(result.message),
                        )
                    }
                }
            }
        }
    }
}
