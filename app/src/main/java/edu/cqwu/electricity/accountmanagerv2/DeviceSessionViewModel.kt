package edu.cqwu.electricity.accountmanagerv2

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import edu.cqwu.electricity.R
import edu.cqwu.electricity.common.net.SessionExpiredException
import edu.cqwu.electricity.login.domain.SessionCoordinatorV2
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 登录设备管理页面状态。
 */
data class DeviceSessionUiState(
    val isRefreshing: Boolean = false,
    val loadError: String? = null,
    val requiresReLogin: Boolean = false,

    /** 全部在线会话（含当前会话，UI 层区分展示） */
    val sessions: List<DeviceSession> = emptyList(),

    /** 正在踢出的会话 ID（非空时展示加载状态，阻断重复点击） */
    val kickingSessionId: String? = null,

    /** 操作结果消息，UI 通过 Snackbar 消费后重置 */
    val message: String? = null,
    val messageIsError: Boolean = false,
)

/**
 * 登录设备管理 ViewModel：加载在线会话列表 → 踢出指定会话。
 *
 * 会话过期（响应为 CAS 登录页）时置 [DeviceSessionUiState.requiresReLogin]，UI 引导重新登录。
 * 踢出成功后重新加载列表；踢出失败仅提示，不阻塞其他会话操作。
 */
class DeviceSessionViewModel(application: Application) : AndroidViewModel(application) {

    private val api = DeviceSessionApi()

    private val _uiState = MutableStateFlow(DeviceSessionUiState())
    val uiState: StateFlow<DeviceSessionUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** 统一刷新入口（首次进入、下拉刷新、踢出成功后） */
    fun refresh() {
        val account = SessionCoordinatorV2.currentAccount()
        if (account == null || !account.hasLoginState) {
            _uiState.update {
                it.copy(
                    isRefreshing = false,
                    loadError = getString(R.string.device_session_no_account),
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, loadError = null) }
            api.loadSessions(account.cookies)
                .onSuccess { sessions ->
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            sessions = sessions,
                            kickingSessionId = null,
                        )
                    }
                }
                .onFailure { e -> handleLoadFailure(e) }
        }
    }

    /** 踢出指定会话：确认后由 UI 调用，成功提示并刷新列表，失败提示错误 */
    fun removeSession(sessionId: String) {
        val account = SessionCoordinatorV2.currentAccount() ?: return
        if (_uiState.value.kickingSessionId != null) return // 已有踢出进行中，防重复
        viewModelScope.launch {
            _uiState.update { it.copy(kickingSessionId = sessionId) }
            api.removeSession(account.cookies, sessionId)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            kickingSessionId = null,
                            message = getString(R.string.device_session_kick_success),
                            messageIsError = false,
                        )
                    }
                    refresh()
                }
                .onFailure { e ->
                    if (e is SessionExpiredException) {
                        _uiState.update { it.copy(kickingSessionId = null, requiresReLogin = true) }
                    } else {
                        _uiState.update {
                            it.copy(
                                kickingSessionId = null,
                                message = getString(R.string.device_session_kick_failed),
                                messageIsError = true,
                            )
                        }
                    }
                }
        }
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
                loadError = e.message ?: getString(R.string.device_session_load_failed),
            )
        }
    }

    private fun getString(@StringRes resId: Int): String = getApplication<Application>().getString(resId)
}
