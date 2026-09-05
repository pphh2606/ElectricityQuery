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
 * 修改用户名页面状态。
 */
data class UserNameEditUiState(
    val isRefreshing: Boolean = false,
    val loadError: String? = null,
    val requiresReLogin: Boolean = false,
    val alias: String = "",
    val nickName: String = "",
    /** 别名校验通过且可用（用于显示"登录别名可使用"） */
    val aliasChecked: Boolean = false,
    val aliasError: String? = null,
    val isSaving: Boolean = false,
    val savedMessage: String? = null,
    val savedSuccess: Boolean = true,
)

/**
 * 修改用户名 ViewModel：加载当前别名/昵称 → 别名校验 → 保存。
 *
 * 会话过期（响应为 CAS 登录页）时置 [UserNameEditUiState.requiresReLogin]，UI 引导重新登录。
 */
class UserNameEditViewModel(application: Application) : AndroidViewModel(application) {

    private val api = UserNameEditApi()

    private val _uiState = MutableStateFlow(UserNameEditUiState())
    val uiState: StateFlow<UserNameEditUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** 统一刷新入口（首次进入、下拉刷新、保存成功后）— 刷新期间由 UI 顶部下拉动画指示 */
    fun refresh() {
        val account = SessionCoordinatorV2.currentAccount()
        if (account == null || !account.hasLoginState) {
            _uiState.update { it.copy(isRefreshing = false, loadError = getString(R.string.user_name_edit_no_account)) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, loadError = null) }
            api.loadCurrent(account.cookies)
                .onSuccess { info ->
                    _uiState.update { it.copy(isRefreshing = false, alias = info.alias, nickName = info.nickName) }
                    // 未修改时也对当前别名做一次校验
                    validateAlias(info.alias)
                }
                .onFailure { e -> handleLoadFailure(e) }
        }
    }

    /** 别名每次修改即校验一次：可用 → aliasChecked；不可用 → aliasError（网络失败静默，保存时兜底再校验） */
    fun onAliasChange(value: String) {
        _uiState.update { it.copy(alias = value, aliasError = null, aliasChecked = false) }
        validateAlias(value)
    }

    fun onNickNameChange(value: String) {
        _uiState.update { it.copy(nickName = value) }
    }

    /** 保存：先校验别名再提交，成功/失败消息由 UI 通过 [consumeMessage] 消费 */
    fun save() {
        val state = _uiState.value
        val alias = state.alias.trim()
        val nickName = state.nickName.trim()
        if (alias.isEmpty() || nickName.isEmpty()) {
            _uiState.update {
                it.copy(aliasError = if (alias.isEmpty()) getString(R.string.user_name_edit_alias_unavailable) else it.aliasError)
            }
            return
        }
        val account = SessionCoordinatorV2.currentAccount() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, aliasError = null) }
            api.checkAlias(account.cookies, alias)
                .onSuccess { available ->
                    if (!available) {
                        _uiState.update { it.copy(isSaving = false, aliasError = getString(R.string.user_name_edit_alias_unavailable)) }
                        return@onSuccess
                    }
                    api.submit(account.cookies, alias, nickName)
                        .onSuccess { result ->
                            _uiState.update {
                                it.copy(
                                    isSaving = false,
                                    savedMessage = result.message.ifBlank { getString(R.string.user_name_edit_success) },
                                    savedSuccess = result.isSuccess,
                                )
                            }
                            if (result.isSuccess) refresh()
                        }
                        .onFailure {
                            _uiState.update {
                                it.copy(isSaving = false, savedMessage = getString(R.string.user_name_edit_load_failed), savedSuccess = false)
                            }
                        }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(isSaving = false, savedMessage = getString(R.string.user_name_edit_load_failed), savedSuccess = false)
                    }
                }
        }
    }

    /** 消费保存结果消息（UI 展示后调用，同时触发刷新最新值） */
    fun consumeMessage() {
        _uiState.update { it.copy(savedMessage = null, savedSuccess = true) }
    }

    /** 校验指定别名（空值跳过；网络失败静默，保存时兜底再校验） */
    private fun validateAlias(alias: String) {
        val trimmed = alias.trim()
        if (trimmed.isEmpty()) return
        val account = SessionCoordinatorV2.currentAccount() ?: return
        viewModelScope.launch {
            api.checkAlias(account.cookies, trimmed)
                .onSuccess { available ->
                    _uiState.update {
                        if (available) it.copy(aliasChecked = true)
                        else it.copy(aliasError = getString(R.string.user_name_edit_alias_unavailable))
                    }
                }
        }
    }

    private fun handleLoadFailure(e: Throwable) {
        if (e is SessionExpiredException) {
            _uiState.update { it.copy(isRefreshing = false, requiresReLogin = true) }
            return
        }
        _uiState.update {
            it.copy(isRefreshing = false, loadError = e.message ?: getString(R.string.user_name_edit_load_failed))
        }
    }

    private fun getString(@StringRes resId: Int): String = getApplication<Application>().getString(resId)
}
