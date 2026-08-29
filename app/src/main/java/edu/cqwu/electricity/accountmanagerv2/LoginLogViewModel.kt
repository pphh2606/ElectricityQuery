package edu.cqwu.electricity.accountmanagerv2

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import edu.cqwu.electricity.R
import edu.cqwu.electricity.login.data.AccountSessionStore
import edu.cqwu.electricity.login.data.SessionExpiredException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 登录日志页面状态。
 */
data class LoginLogUiState(
    val isRefreshing: Boolean = false,
    val loadError: String? = null,
    val requiresReLogin: Boolean = false,

    /** 登录记录列表（分页追加） */
    val records: List<LoginRecord> = emptyList(),

    /** 分页状态 */
    val pageCurrent: Int = 1,
    val pageTotal: Int = 0,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,

    /** 筛选面板展开标记 */
    val showFilterPanel: Boolean = false,

    /** 已应用的筛选条件（请求用） */
    val operType: String = DEFAULT_OPER_TYPE,
    val result: String = DEFAULT_RESULT,
    val startTime: String = DEFAULT_START_TIME,
    val endTime: String = DEFAULT_END_TIME,

    /** 筛选面板草稿（未应用前不触发请求） */
    val tempOperType: String = DEFAULT_OPER_TYPE,
    val tempResult: String = DEFAULT_RESULT,
    val tempStartTime: String = DEFAULT_START_TIME,
    val tempEndTime: String = DEFAULT_END_TIME,

    /** 点击查看详情的记录（非空时弹 BottomSheetDialogV2） */
    val selectedRecord: LoginRecord? = null,

    /** 操作结果消息，UI 通过 Snackbar 消费后重置 */
    val message: String? = null,
    val messageIsError: Boolean = false,
) {
    companion object {
        /** 默认筛选：认证记录 + 全部结果 + 1970-01-01 起、结束时间为空 */
        const val DEFAULT_OPER_TYPE = "0"
        const val DEFAULT_RESULT = ""
        const val DEFAULT_START_TIME = "1970-01-01"
        const val DEFAULT_END_TIME = ""
    }
}

/**
 * 登录日志 ViewModel：加载日志列表 + 筛选面板。
 *
 * 默认筛选按抓包要求：operType=认证记录、result=全部、startTime=1970-01-01、endTime 为空。
 * 会话过期（响应为 CAS 登录页）时置 [LoginLogUiState.requiresReLogin]，UI 引导重新登录。
 */
class LoginLogViewModel(application: Application) : AndroidViewModel(application) {

    private val api = LoginLogApi()

    private val _uiState = MutableStateFlow(LoginLogUiState())
    val uiState: StateFlow<LoginLogUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** 统一刷新入口（首次进入、下拉刷新、应用筛选后）— 重置到第 1 页并替换列表 */
    fun refresh() {
        val account = AccountSessionStore.getActiveAccount()
        if (account == null || !account.hasLoginState) {
            _uiState.update {
                it.copy(isRefreshing = false, loadError = getString(R.string.login_log_no_account))
            }
            return
        }
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, loadError = null, isLoadingMore = false) }
            api.loadLogs(
                cookies = account.cookies,
                operType = state.operType,
                result = state.result,
                startTime = state.startTime,
                endTime = state.endTime,
                pageIndex = 1,
            ).onSuccess { page ->
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        records = page.records,
                        pageCurrent = 1,
                        pageTotal = page.totalPages,
                        hasMore = page.totalPages > 1,
                    )
                }
            }.onFailure { e -> handleLoadFailure(e) }
        }
    }

    /** 加载下一页（滚动到底自动触发，isLoadingMore 防重入） */
    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMore || state.isRefreshing) return
        val account = AccountSessionStore.getActiveAccount() ?: return
        val nextPage = state.pageCurrent + 1
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            api.loadLogs(
                cookies = account.cookies,
                operType = state.operType,
                result = state.result,
                startTime = state.startTime,
                endTime = state.endTime,
                pageIndex = nextPage,
            ).onSuccess { page ->
                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        records = it.records + page.records,
                        pageCurrent = page.currentPage,
                        pageTotal = page.totalPages,
                        hasMore = page.currentPage < page.totalPages,
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoadingMore = false) }
                if (e is SessionExpiredException) {
                    _uiState.update { it.copy(requiresReLogin = true) }
                } else {
                    // 加载更多失败：保留已加载数据，footer 回到"上滑加载更多"可重试
                    _uiState.update {
                        it.copy(message = getString(R.string.login_log_load_failed), messageIsError = true)
                    }
                }
            }
        }
    }

    /** 展开/收起筛选面板 */
    fun toggleFilterPanel() {
        _uiState.update {
            if (it.showFilterPanel) {
                // 收起时丢弃未应用的草稿，恢复为已应用条件
                it.copy(
                    showFilterPanel = false,
                    tempOperType = it.operType,
                    tempResult = it.result,
                    tempStartTime = it.startTime,
                    tempEndTime = it.endTime,
                )
            } else {
                it.copy(showFilterPanel = true)
            }
        }
    }

    fun onTempOperTypeChange(value: String) = _uiState.update { it.copy(tempOperType = value) }
    fun onTempResultChange(value: String) = _uiState.update { it.copy(tempResult = value) }
    fun onTempStartDateChange(value: String) = _uiState.update { it.copy(tempStartTime = value) }
    fun onTempEndDateChange(value: String) = _uiState.update { it.copy(tempEndTime = value) }

    /** 应用草稿筛选并刷新列表 */
    fun applyFilter() {
        _uiState.update {
            it.copy(
                showFilterPanel = false,
                operType = it.tempOperType,
                result = it.tempResult,
                startTime = it.tempStartTime,
                endTime = it.tempEndTime,
            )
        }
        refresh()
    }

    /** 重置筛选为默认值（认证记录 / 全部 / 1970-01-01 / 空）并刷新 */
    fun resetFilter() {
        _uiState.update {
            it.copy(
                showFilterPanel = false,
                operType = LoginLogUiState.DEFAULT_OPER_TYPE,
                result = LoginLogUiState.DEFAULT_RESULT,
                startTime = LoginLogUiState.DEFAULT_START_TIME,
                endTime = LoginLogUiState.DEFAULT_END_TIME,
                tempOperType = LoginLogUiState.DEFAULT_OPER_TYPE,
                tempResult = LoginLogUiState.DEFAULT_RESULT,
                tempStartTime = LoginLogUiState.DEFAULT_START_TIME,
                tempEndTime = LoginLogUiState.DEFAULT_END_TIME,
            )
        }
        refresh()
    }

    /** 点击记录：弹出详情弹窗 */
    fun onRecordClick(record: LoginRecord) {
        _uiState.update { it.copy(selectedRecord = record) }
    }

    /** 关闭详情弹窗 */
    fun dismissRecord() {
        _uiState.update { it.copy(selectedRecord = null) }
    }

    /** 消费操作结果消息（Snackbar 展示后调用） */
    fun consumeMessage() {
        _uiState.update { it.copy(message = null, messageIsError = false) }
    }

    private fun handleLoadFailure(e: Throwable) {
        if (e is SessionExpiredException) {
            _uiState.update { it.copy(isRefreshing = false, requiresReLogin = true) }
            return
        }
        _uiState.update {
            it.copy(
                isRefreshing = false,
                loadError = e.message ?: getString(R.string.login_log_load_failed),
            )
        }
    }

    private fun getString(@StringRes resId: Int): String = getApplication<Application>().getString(resId)
}
