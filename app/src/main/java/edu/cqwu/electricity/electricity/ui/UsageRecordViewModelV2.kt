package edu.cqwu.electricity.electricity.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import edu.cqwu.electricity.R
import edu.cqwu.electricity.electricity.data.RecordQueryResultV2
import edu.cqwu.electricity.electricity.data.RecordRepositoryV2
import edu.cqwu.electricity.electricity.data.UsageGranularityV2
import edu.cqwu.electricity.electricity.data.UsageRecordV2
import edu.cqwu.electricity.theme.ui.UiMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** 单个数据粒度（Tab）的缓存内容 */
data class UsageTabContentV2(
    val records: List<UsageRecordV2> = emptyList(),
    val totalConsume: Double = 0.0,
    val totalCost: Double = 0.0,
    val error: UiMessage? = null,
)

/** 三个粒度各一份空缓存 */
private fun emptyUsageTabs(): Map<UsageGranularityV2, UsageTabContentV2> =
    UsageGranularityV2.entries.associateWith { UsageTabContentV2() }

/**
 * 用量报表（用电明细）页面状态。
 *
 * 每个粒度（小时/每日/每月）独立缓存 [tabs]；切回已加载过的粒度时直接显示其缓存，
 * 并在后台静默刷新（请求期间不遮住旧数据）。
 */
data class UsageRecordV2UiState(
    /** 是否有请求在途（顶部下拉刷新式指示） */
    val isRefreshing: Boolean = false,
    /** 当前选中的数据粒度（默认每日） */
    val granularity: UsageGranularityV2 = UsageGranularityV2.DAILY,
    /** 当前内容视图：表格 / 折线图 */
    val viewMode: RecordViewModeV2 = RecordViewModeV2.TABLE,
    val beginTime: String = "",
    val endTime: String = "",
    /** 各粒度各自缓存的记录与汇总 */
    val tabs: Map<UsageGranularityV2, UsageTabContentV2> = emptyUsageTabs(),
)

/**
 * 用量报表（用电明细）页面 ViewModel。
 *
 * 固定查询电费（costType=0），支持小时/每日/每月粒度与自定义起止日期。
 * roomId 通过构造函数注入，生命周期跟随 USAGE_RECORD 路由。
 *
 * 规范分层：通过 [RecordRepositoryV2] 查询，不直接依赖网络实现，便于注入假仓库测试。
 */
class UsageRecordViewModelV2(
    private val roomId: String,
    private val repository: RecordRepositoryV2 = RecordRepositoryV2(),
) : ViewModel() {

    /** 创建 [UsageRecordViewModelV2] 的工厂 */
    class Factory(private val roomId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return UsageRecordViewModelV2(roomId) as T
        }
    }

    private val _uiState = MutableStateFlow(UsageRecordV2UiState())
    val uiState: StateFlow<UsageRecordV2UiState> = _uiState.asStateFlow()

    /** 当前在途查询任务；快速切换/静默刷新只保留最后一个请求 */
    private var queryJob: Job? = null

    init {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calendar = Calendar.getInstance()
        val endTime = dateFormat.format(calendar.time)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        val beginTime = dateFormat.format(calendar.time)
        _uiState.update { it.copy(beginTime = beginTime, endTime = endTime) }
        queryCurrent()
    }

    /**
     * 切换数据粒度：更新选中项并查询该粒度。
     * 该粒度已有缓存时界面立即显示缓存（外壳按页渲染），本次请求作为后台静默刷新；
     * 无缓存时则等待首次数据返回。
     */
    fun selectGranularity(granularity: UsageGranularityV2) {
        if (granularity == _uiState.value.granularity) return
        _uiState.update { it.copy(granularity = granularity) }
        queryCurrent()
    }

    /** 设置起始日期：日期变化会使全部粒度缓存失效，清空后查询当前粒度 */
    fun setBeginTime(date: String) {
        if (_uiState.value.isRefreshing) return
        _uiState.update {
            it.copy(beginTime = date, tabs = emptyUsageTabs())
        }
        queryCurrent()
    }

    /** 设置结束日期：同 [setBeginTime]，清空全部缓存后查询当前粒度 */
    fun setEndTime(date: String) {
        if (_uiState.value.isRefreshing) return
        _uiState.update {
            it.copy(endTime = date, tabs = emptyUsageTabs())
        }
        queryCurrent()
    }

    /** 切换表格/折线图视图（只改展示方式，不重新请求数据） */
    fun toggleViewMode() {
        _uiState.update { st ->
            st.copy(
                viewMode = if (st.viewMode == RecordViewModeV2.TABLE) RecordViewModeV2.CHART else RecordViewModeV2.TABLE,
            )
        }
    }

    /** 下拉刷新：仅刷新当前粒度（保留其缓存用于刷新期间展示） */
    fun refresh() {
        if (_uiState.value.isRefreshing) return
        queryCurrent()
    }

    /** 页面离开时清理状态 */
    fun clearState() {
        _uiState.update { UsageRecordV2UiState() }
    }

    private fun queryCurrent() {
        if (roomId.isBlank()) {
            _uiState.update { st ->
                st.copy(
                    isRefreshing = false,
                    tabs = st.tabs + (st.granularity to st.tabs.getValue(st.granularity).copy(error = UiMessage(res = R.string.record_error_no_room))),
                )
            }
            return
        }
        // 取消上一轮查询，快速切换粒度时只保留最后一次；缓存数据不清空，静默刷新期间界面仍显示旧数据
        queryJob?.cancel()
        queryJob = viewModelScope.launch {
            val state = _uiState.value
            val granularity = state.granularity
            _uiState.update { it.copy(isRefreshing = true) }
            when (val result = repository.queryUsageRecordsV2(
                roomId = roomId,
                granularity = granularity,
                beginTime = state.beginTime,
                endTime = state.endTime,
            )) {
                is RecordQueryResultV2.Success -> {
                    val records = result.records
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            tabs = it.tabs + (
                                granularity to UsageTabContentV2(
                                    records = records,
                                    totalConsume = records.sumOf { r -> r.consumeTotal },
                                    totalCost = records.sumOf { r -> r.costTotal },
                                )
                            ),
                        )
                    }
                }

                is RecordQueryResultV2.Failure -> {
                    // 保留旧缓存（若有），错误只记录到该粒度（界面仅在无数据时展示错误）
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            tabs = it.tabs + (
                                granularity to it.tabs.getValue(granularity)
                                    .copy(error = toRecordErrorUiMessage(result.message))
                            ),
                        )
                    }
                }
            }
        }
    }
}
