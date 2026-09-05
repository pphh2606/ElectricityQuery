package edu.cqwu.electricity.campusnetwork.campusnetworkinfo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.cqwu.electricity.R
import edu.cqwu.electricity.campusnetwork.campusnetworkinfo.data.CampusNetworkApi
import edu.cqwu.electricity.campusnetwork.common.CampusNetworkErrorKind
import edu.cqwu.electricity.campusnetwork.common.CampusNetworkException
import edu.cqwu.electricity.campusnetwork.campusnetworkinfo.data.ClientContextData
import edu.cqwu.electricity.logging.AppLog
import edu.cqwu.electricity.theme.ui.UiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 接入者信息页面状态 */
data class ClientContextUiState(
    /** 首次整页加载中（尚无数据可展示） */
    val isLoading: Boolean = false,
    /** 下拉刷新中（已有数据） */
    val isRefreshing: Boolean = false,
    /** 错误信息；非空时优先展示错误态（含重试按钮） */
    val error: UiMessage? = null,
    val data: ClientContextData? = null,
)

/**
 * 接入者信息 ViewModel。
 *
 * 生命周期跟随导航栈条目（由 NavGraph 路由内 viewModel() 创建），
 * 页面重新进入时自动重建并重新拉取，无需手动清空缓存数据。
 *
 * 错误约定：错误经 [CampusNetworkException] 分类后映射为用户可读文案；
 * 技术细节（原始堆栈）统一经 AppLog 记录，不静默吞掉。
 */
class ClientContextViewModel(
    private val api: CampusNetworkApi = CampusNetworkApi(),
) : ViewModel() {

    private val _state = MutableStateFlow(ClientContextUiState())
    val state: StateFlow<ClientContextUiState> = _state.asStateFlow()

    /**
     * 加载接入者信息。
     *
     * @param refresh true 表示下拉刷新（保留旧数据仅转动刷新指示）；
     *                false 表示首次加载或错误重试（整页加载态）
     */
    fun load(refresh: Boolean = false) {
        val current = _state.value
        // 幂等保护：同种请求进行中不重复发起
        if (current.isLoading || (refresh && current.isRefreshing)) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = !refresh && it.data == null,
                    isRefreshing = refresh,
                    error = null,
                )
            }
            api.fetchClientContext()
                .onSuccess { data ->
                    _state.update {
                        it.copy(isLoading = false, isRefreshing = false, data = data)
                    }
                }
                .onFailure { e ->
                    AppLog.e(TAG, "加载接入者信息失败: ${e.message}")
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = e.toUiMessage(),
                        )
                    }
                }
        }
    }

    /** 把异常映射为界面提示（分类决定文案；服务端消息优先原样展示） */
    private fun Throwable.toUiMessage(): UiMessage = when (this) {
        is CampusNetworkException -> when (kind) {
            CampusNetworkErrorKind.CAMPUS_OFFLINE ->
                UiMessage(res = R.string.campus_network_error_need_campus)
            CampusNetworkErrorKind.NO_NETWORK ->
                UiMessage(res = R.string.campus_network_error_no_network)
            CampusNetworkErrorKind.SERVER ->
                UiMessage(res = R.string.campus_network_error_generic, raw = userMessage)
            CampusNetworkErrorKind.PARSE ->
                UiMessage(res = R.string.campus_network_error_parse)
            CampusNetworkErrorKind.UNKNOWN ->
                UiMessage(res = R.string.campus_network_error_generic)
        }
        else -> UiMessage(res = R.string.campus_network_error_generic)
    }

    private companion object {
        const val TAG = "ClientContextViewModel"
    }
}
