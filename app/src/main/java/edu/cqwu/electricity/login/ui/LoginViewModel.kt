package edu.cqwu.electricity.login.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import edu.cqwu.electricity.R
import edu.cqwu.electricity.login.data.AccountStore
import edu.cqwu.electricity.login.data.CredentialExporter
import edu.cqwu.electricity.login.data.AccountManager
import edu.cqwu.electricity.login.data.CasAuthApi
import edu.cqwu.electricity.login.data.CasLoginException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 登录页面 UI 状态
 */
data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val rememberPassword: Boolean = true,
    val passwordRevealed: Boolean = false,
    /** 是否有已保存的密码（占位状态）。为 true 时 password 为占位符，用户只能全选删除或开始新输入 */
    val hasSavedPassword: Boolean = false,
)

/**
 * 登录页面 ViewModel
 */
class LoginViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        /** 已保存密码的占位符，作为实际值填入 password 字段，类似 QQ 密码形式 */
        private const val SAVED_PASSWORD_PLACEHOLDER = "●●●●●●●●"
    }

    private val accountStore = AccountStore.getInstance(application)
    private val authApi = CasAuthApi()

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events: Flow<LoginEvent> = _events.receiveAsFlow()

    init {
        autoFillFromStorage()
    }

    /**
     * 从 AccountStore 自动填充最近登录的账号。
     * 如果该账号勾选了"记住密码"且有已保存密码，设置 hasSavedPassword 标志，
     * UI 层将显示占位圆点而非实际密码，防止密码被眼睛按钮查看。
     */
    private fun autoFillFromStorage() {
        val allAccounts = accountStore.getAllAccounts()
        val lastAccount = allAccounts.firstOrNull()
        val rememberPwd = lastAccount?.rememberPassword ?: true
        val hasSaved = lastAccount?.password != null && rememberPwd
        val state = LoginUiState(
            username = lastAccount?.username ?: "",
            password = if (hasSaved) SAVED_PASSWORD_PLACEHOLDER else "",
            rememberPassword = rememberPwd,
            hasSavedPassword = hasSaved,
        )
        _uiState.value = state
    }

    /**
     * 重置状态。
     * @param clearForm true 时显示空白表单（"添加账号"场景），false 时自动填充最近账号。
     */
    fun resetState(clearForm: Boolean = false) {
        android.util.Log.d("LoginVM", "resetState: clearForm=$clearForm")
        if (clearForm) {
            _uiState.value = LoginUiState()
        } else {
            autoFillFromStorage()
        }
    }

    fun updateUsername(value: String) {
        _uiState.update { it.copy(username = value) }
    }

    fun updatePassword(value: String) {
        val state = _uiState.value
        if (state.hasSavedPassword && value != SAVED_PASSWORD_PLACEHOLDER) {
            // 用户修改了占位符 — 提取新输入内容并清除占位状态
            val newInput = if (value.startsWith(SAVED_PASSWORD_PLACEHOLDER)) {
                value.removePrefix(SAVED_PASSWORD_PLACEHOLDER)
            } else {
                ""  // 用户删除了部分/全部占位符
            }
            _uiState.update { it.copy(password = newInput, passwordRevealed = false, hasSavedPassword = false) }
        } else if (!state.hasSavedPassword) {
            _uiState.update { it.copy(password = value, passwordRevealed = false) }
        }
    }

    fun setRememberPassword(remember: Boolean) {
        _uiState.update { it.copy(rememberPassword = remember, hasSavedPassword = if (!remember) false else it.hasSavedPassword) }
        // 更新当前账号的记住密码标志
        val username = _uiState.value.username.trim()
        if (username.isNotBlank()) {
            accountStore.setRememberPasswordForAccount(username, remember)
        }
    }

    fun togglePasswordRevealed() {
        _uiState.update { it.copy(passwordRevealed = !it.passwordRevealed) }
    }

    fun login() {
        val state = _uiState.value
        val username = state.username.trim()
        // 占位状态下从 AccountStore 读取实际密码
        val password = if (state.hasSavedPassword) {
            accountStore.getPassword(username) ?: ""
        } else {
            state.password
        }

        if (username.isBlank()) {
            viewModelScope.launch { _events.send(LoginEvent.Error(getApplication<Application>().getString(R.string.login_error_student_id_required))) }
            return
        }
        if (password.isBlank()) {
            viewModelScope.launch { _events.send(LoginEvent.Error(getApplication<Application>().getString(R.string.login_error_password_required))) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                authApi.loginForUser(username, password)
                    .onSuccess { result ->
                        result.cookieStore?.let { tempStore ->
                            AccountManager.commitLoginCookies(username, tempStore)
                        }

                        _uiState.update { it.copy(isLoading = false) }
                        _events.send(LoginEvent.LoginSuccess(result.cookieString))

                        val rememberPwd = _uiState.value.rememberPassword
                        accountStore.saveAccount(
                            username = username,
                            password = password,
                            rememberPassword = rememberPwd
                        )
                    }
                    .onFailure { e ->
                        val errorMsg = when {
                            e is CasLoginException.CaptchaRequired -> getApplication<Application>().getString(R.string.login_error_captcha_required)
                            e is CasLoginException.LoginRejected -> getApplication<Application>().getString(R.string.login_error_invalid_credentials)
                            e is CasLoginException.MissingField -> getApplication<Application>().getString(R.string.login_error_fetch_params)
                            e.message?.contains("无法获取加密 salt") == true -> getApplication<Application>().getString(R.string.login_error_fetch_params_network)
                            e.message?.contains("无法获取 lt") == true -> getApplication<Application>().getString(R.string.login_error_fetch_params)
                            e.message?.contains("未能获取到 CASTGC") == true -> getApplication<Application>().getString(R.string.login_error_invalid_credentials)
                            e.message?.contains("无法连接到") == true -> getApplication<Application>().getString(R.string.login_error_network)
                            e.message?.contains("SocketTimeout") == true || e.message?.contains("Socket closed") == true -> {
                                getApplication<Application>().getString(R.string.login_error_timeout)
                            }
                            else -> e.message ?: getApplication<Application>().getString(R.string.login_error_unknown)
                        }
                        _uiState.update { it.copy(isLoading = false) }
                        _events.send(LoginEvent.Error(errorMsg))
                    }
            } catch (e: Exception) {
                android.util.Log.e("LoginViewModel", "登录异常", e)
                _uiState.update { it.copy(isLoading = false) }
                _events.send(LoginEvent.Error(getApplication<Application>().getString(R.string.login_error_exception, e.message ?: "")))
            }
        }
    }

    // ==================== 凭据导出 ====================

    fun exportCredentials(exportPassword: String) {
        viewModelScope.launch {
            if (exportPassword.length < 4) {
                _events.send(LoginEvent.Error(getApplication<Application>().getString(R.string.login_error_password_too_short)))
                return@launch
            }
            val state = _uiState.value
            val currentUsername = state.username.trim()
            // 占位状态下从 AccountStore 读取实际密码用于导出
            val currentPassword = if (state.hasSavedPassword) {
                accountStore.getPassword(currentUsername) ?: ""
            } else {
                state.password
            }

            val accounts = accountStore.getAllAccountsWithPassword().toMutableList()

            if (currentUsername.isNotBlank() && currentPassword.isNotBlank()) {
                val exists = accounts.any { it.first == currentUsername }
                if (!exists) {
                    accounts.add(currentUsername to currentPassword)
                }
            }

            if (accounts.isEmpty()) {
                _events.send(LoginEvent.Error(getApplication<Application>().getString(R.string.login_error_no_account_to_export)))
                return@launch
            }

            try {
                val encrypted = CredentialExporter.export(accounts, exportPassword)
                _events.send(LoginEvent.ExportSuccess(encrypted))
            } catch (e: Exception) {
                _events.send(LoginEvent.Error(getApplication<Application>().getString(R.string.login_error_export_failed, e.message ?: "")))
            }
        }
    }

    // ==================== 凭据导入 ====================

    fun importAndLogin(encryptedData: String, exportPassword: String) {
        val accounts = try {
            CredentialExporter.import(encryptedData, exportPassword)
        } catch (e: Exception) {
            viewModelScope.launch { _events.send(LoginEvent.Error(getApplication<Application>().getString(R.string.login_error_parse_credential, e.message ?: ""))) }
            return
        }
        if (accounts.isNullOrEmpty()) {
            viewModelScope.launch { _events.send(LoginEvent.Error(getApplication<Application>().getString(R.string.login_error_import_credential))) }
            return
        }

        val (username, password) = accounts.first()
        _uiState.update {
            it.copy(
                username = username,
                password = password,
            )
        }

        login()
    }
}

sealed interface LoginEvent {
    data class Error(val msg: String) : LoginEvent
    data class LoginSuccess(val cookie: String) : LoginEvent
    data class ExportSuccess(val encryptedData: String) : LoginEvent
}
