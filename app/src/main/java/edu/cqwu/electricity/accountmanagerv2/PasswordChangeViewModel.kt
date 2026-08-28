package edu.cqwu.electricity.accountmanagerv2

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import edu.cqwu.electricity.R
import edu.cqwu.electricity.login.data.AccountSessionStore
import edu.cqwu.electricity.login.data.LogoutApi
import edu.cqwu.electricity.login.data.SessionExpiredException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 修改密码页面状态。
 */
data class PasswordChangeUiState(
    val isRefreshing: Boolean = false,
    val loadError: String? = null,
    val requiresReLogin: Boolean = false,

    /** 页面下发的加密盐（加载成功后才可提交） */
    val salt: String = "",

    val oldPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val captcha: String = "",

    /** 验证码图片 URL（带时间戳，每次刷新变化以绕过缓存） */
    val captchaUrl: String = "",
    /** 新密码本地强度校验失败提示（长度/字符种类） */
    val passwordError: String? = null,
    /** 两次新密码不一致提示 */
    val confirmError: String? = null,
    /** 验证码为空提示 */
    val captchaError: String? = null,

    val isSaving: Boolean = false,
    /** 服务端返回/本地失败消息，UI 通过 Snackbar 消费后重置 */
    val message: String? = null,
    val messageIsError: Boolean = false,
    /** 修改成功（服务端已注销登录态）— UI 弹出引导重新登录 */
    val changeSucceeded: Boolean = false,
)

/**
 * 修改密码 ViewModel：加载页面取盐 → 本地强度校验 → 提交修改。
 *
 * 修改成功后服务端会注销当前账号的登录信息，本阶段仅置 [PasswordChangeUiState.changeSucceeded]
 * 提示用户重新登录，不主动清理本地登录态（不清 cookie、不删账号）。
 */
class PasswordChangeViewModel(application: Application) : AndroidViewModel(application) {

    private val api = PasswordChangeApi()

    private val _uiState = MutableStateFlow(PasswordChangeUiState())
    val uiState: StateFlow<PasswordChangeUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** 统一刷新入口（首次进入、下拉刷新、修改成功后）— 重新加载页面取得新盐值 */
    fun refresh() {
        val account = AccountSessionStore.getActiveAccount()
        if (account == null || !account.hasLoginState) {
            _uiState.update { it.copy(isRefreshing = false, loadError = getString(R.string.password_change_no_account)) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, loadError = null) }
            api.loadPage(account.cookies)
                .onSuccess { info ->
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            salt = info.salt,
                            oldPassword = "",
                            newPassword = "",
                            confirmPassword = "",
                            captcha = "",
                            passwordError = null,
                            confirmError = null,
                            captchaError = null,
                            changeSucceeded = false,
                            captchaUrl = api.captchaUrl(),
                        )
                    }
                }
                .onFailure { e -> handleLoadFailure(e) }
        }
    }

    fun onOldPasswordChange(value: String) {
        _uiState.update { it.copy(oldPassword = value) }
    }

    fun onNewPasswordChange(value: String) {
        _uiState.update { it.copy(newPassword = value, passwordError = null) }
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.update { it.copy(confirmPassword = value, confirmError = null) }
    }

    fun onCaptchaChange(value: String) {
        _uiState.update { it.copy(captcha = value, captchaError = null) }
    }

    /** 刷新验证码图片（点击验证码图调用） */
    fun refreshCaptcha() {
        _uiState.update {
            it.copy(
                captcha = "",
                captchaError = null,
                captchaUrl = api.captchaUrl(),
            )
        }
    }

    /** 提交修改：本地校验通过后提交，失败消息由 UI 消费展示 */
    fun save() {
        val state = _uiState.value

        // 本地校验 1：新密码强度（长度至少8位；字符种类至少3种）
        val pwdError = validatePasswordStrength(state.newPassword)
        if (pwdError != null) {
            _uiState.update { it.copy(passwordError = pwdError) }
            return
        }

        // 本地校验 2：两次新密码一致
        if (state.newPassword != state.confirmPassword) {
            _uiState.update { it.copy(confirmError = getString(R.string.password_change_confirm_mismatch)) }
            return
        }

        // 本地校验 3：旧密码与验证码非空
        if (state.oldPassword.isEmpty()) {
            _uiState.update { it.copy(message = getString(R.string.password_change_old_empty), messageIsError = true) }
            return
        }
        if (state.captcha.isBlank()) {
            _uiState.update { it.copy(captchaError = getString(R.string.password_change_captcha_empty)) }
            return
        }

        val account = AccountSessionStore.getActiveAccount() ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, passwordError = null, confirmError = null, captchaError = null) }
            api.submit(
                cookies = account.cookies,
                salt = state.salt,
                oldPassword = state.oldPassword,
                newPassword = state.newPassword,
                confirmPassword = state.confirmPassword,
                captcha = state.captcha.trim(),
            ).onSuccess { result ->
                if (result.isSuccess) {
                    // 修改成功：服务端已注销当前账号登录态，再主动调用 logout 彻底退出登录
                    // （尽力而为：失败仅记日志，不影响「下线通知」弹窗与后续重启流程）
                    LogoutApi.logout(account.username, account.cookies)
                    _uiState.update { it.copy(isSaving = false, changeSucceeded = true) }
                } else {
                    // 失败（多为验证码错误）：提示错误消息并自动刷新验证码
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            message = result.message.ifBlank { getString(R.string.password_change_failed) },
                            messageIsError = true,
                            captcha = "",
                            captchaUrl = api.captchaUrl(),
                        )
                    }
                }
            }.onFailure { e ->
                if (e is SessionExpiredException) {
                    _uiState.update { it.copy(isSaving = false, requiresReLogin = true) }
                } else {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            message = e.message ?: getString(R.string.password_change_failed),
                            messageIsError = true,
                        )
                    }
                }
            }
        }
    }

    /** 消费失败消息（Snackbar 展示后调用） */
    fun consumeMessage() {
        _uiState.update { it.copy(message = null, messageIsError = false) }
    }

    private fun validatePasswordStrength(pwd: String): String? {
        if (pwd.length < 8) return getString(R.string.password_change_strength)
        var kinds = 0
        if (pwd.any { it.isDigit() }) kinds++
        if (pwd.any { it.isUpperCase() }) kinds++
        if (pwd.any { it.isLowerCase() }) kinds++
        if (pwd.any { !it.isLetterOrDigit() }) kinds++
        return if (kinds < 3) getString(R.string.password_change_strength) else null
    }

    private fun handleLoadFailure(e: Throwable) {
        if (e is SessionExpiredException) {
            _uiState.update { it.copy(isRefreshing = false, requiresReLogin = true) }
            return
        }
        _uiState.update {
            it.copy(isRefreshing = false, loadError = e.message ?: getString(R.string.password_change_load_failed))
        }
    }

    private fun getString(@StringRes resId: Int): String = getApplication<Application>().getString(resId)
}
