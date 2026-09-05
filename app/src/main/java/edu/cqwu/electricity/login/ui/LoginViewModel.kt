package edu.cqwu.electricity.login.ui
import edu.cqwu.electricity.logging.AppLog

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import edu.cqwu.electricity.R
import edu.cqwu.electricity.login.data.CasAuthApi
import edu.cqwu.electricity.login.data.CasLoginException
import edu.cqwu.electricity.login.data.SessionManager
import edu.cqwu.electricity.login.domain.SessionCoordinatorV2
import edu.cqwu.electricity.login.model.AuthSessionCommitV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    /** 预填/登录的目标账号条目 id；为 null 表示手动输入（非预填） */
    val accountId: String? = null,
)

/**
 * 登录页面 ViewModel
 */
class LoginViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        /** 已保存密码的占位符，作为实际值填入 password 字段，类似 QQ 密码形式 */
        private const val SAVED_PASSWORD_PLACEHOLDER = "●●●●●●●●"
    }

    private val authApi = CasAuthApi()

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events: Flow<LoginEvent> = _events.receiveAsFlow()

    init {
        autoFillFromStorage(null)
    }

    /**
     * 从持久化账号仓库自动填充。
     *
     * @param initialAccountId 指定预填的账号条目（切换/失效跳转场景）；null 时填充当前激活条目。
     * 如果该条目勾选了"记住密码"且有已保存密码，设置 hasSavedPassword 标志，
     * UI 层将显示占位圆点而非实际密码，防止密码被眼睛按钮查看。
     */
    private fun autoFillFromStorage(initialAccountId: String?) {
        val target = initialAccountId?.let { SessionCoordinatorV2.accountById(it) }
            ?: SessionCoordinatorV2.currentAccount()
        val rememberPwd = target?.rememberPassword ?: true
        val hasSaved = target?.password != null && rememberPwd
        val state = LoginUiState(
            username = target?.username ?: "",
            password = if (hasSaved) SAVED_PASSWORD_PLACEHOLDER else "",
            rememberPassword = rememberPwd,
            hasSavedPassword = hasSaved,
            accountId = target?.id,
        )
        _uiState.value = state
    }

    /**
     * 重置状态。
     * @param clearForm true 时显示空白表单（"添加账号"场景），false 时自动填充账号。
     * @param initialAccountId 切换账号场景传入目标条目 id，优先预填该条目。
     */
    fun resetState(clearForm: Boolean = false, initialAccountId: String? = null) {
        AppLog.d("LoginVM", "resetState: clearForm=$clearForm, initialAccountId=$initialAccountId")
        if (clearForm) {
            _uiState.value = LoginUiState()
        } else {
            autoFillFromStorage(initialAccountId)
        }
    }

    fun updateUsername(value: String) {
        // 学号被编辑即脱离预填条目：清除条目关联与已保存密码占位状态
        _uiState.update {
            it.copy(username = value, accountId = null, hasSavedPassword = false, password = "")
        }
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
        // 记住密码标志仅在登录成功提交时落盘，登录前不持久化任何账号数据
    }

    fun togglePasswordRevealed() {
        _uiState.update { it.copy(passwordRevealed = !it.passwordRevealed) }
    }

    fun login() {
        val state = _uiState.value
        val username = state.username.trim()
        // 占位状态下从预填条目读取实际密码
        val password = if (state.hasSavedPassword) {
            state.accountId?.let { SessionCoordinatorV2.accountById(it)?.password } ?: ""
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
                        val rememberPwd = _uiState.value.rememberPassword
                        // 登录成功：持久化账号与登录态并原子激活（内部会清除旧登录态）。
                        // 登录过程中当前登录态保持不变（隔离临时 store）。
                        result.cookieStore?.let { tempStore ->
                            val cookies = tempStore.getAllCookies()
                            // 登录后获取数字学号（一次性网络请求；失败传 null，
                            // 由启动验证回填兜底，不影响登录成功），随会话提交直接落盘
                            val studentId = withContext(Dispatchers.IO) {
                                SessionManager.fetchUserInfo(cookies).getOrNull()?.first
                            }
                            withContext(Dispatchers.IO) {
                                SessionCoordinatorV2.commitAndActivate(
                                    AuthSessionCommitV2(
                                        username = username,
                                        password = password,
                                        rememberPassword = rememberPwd,
                                        cookies = cookies,
                                        studentId = studentId,
                                    )
                                )
                            }
                        }

                        _uiState.update { it.copy(isLoading = false) }
                        _events.send(LoginEvent.LoginSuccess(result.cookieString))
                    }
                    .onFailure { e ->
                        // 已知异常/关键词 → 统一本地文案；未命中回退服务端原始 message
                        val errorMsg = e.toLoginErrorResId()?.let { getApplication<Application>().getString(it) }
                            ?: e.message ?: getApplication<Application>().getString(R.string.login_error_unknown)
                        _uiState.update { it.copy(isLoading = false) }
                        _events.send(LoginEvent.Error(errorMsg))
                    }
            } catch (e: Exception) {
                AppLog.e("LoginViewModel", "登录异常", e)
                _uiState.update { it.copy(isLoading = false) }
                _events.send(LoginEvent.Error(getApplication<Application>().getString(R.string.login_error_exception, e.message ?: "")))
            }
        }
    }
}

sealed interface LoginEvent {
    data class Error(val msg: String) : LoginEvent
    data class LoginSuccess(val cookie: String) : LoginEvent
}

/**
 * 账密登录异常 → 用户文案资源 id 的纯映射（不依赖 Android，可 JVM 单测）。
 *
 * 命中已知异常类型或消息关键词时返回对应资源 id；未命中返回 null，
 * 由调用方回退为服务端原始 [Throwable.message]（[R.string.login_error_unknown] 兜底）。
 */
internal fun Throwable.toLoginErrorResId(): Int? = when {
    this is CasLoginException.CaptchaRequired -> R.string.login_error_captcha_required
    this is CasLoginException.LoginRejected -> R.string.login_error_invalid_credentials
    this is CasLoginException.MissingField -> R.string.login_error_fetch_params
    message?.contains("无法获取加密 salt") == true -> R.string.login_error_fetch_params_network
    message?.contains("无法获取 lt") == true -> R.string.login_error_fetch_params
    message?.contains("未能获取到 CASTGC") == true -> R.string.login_error_invalid_credentials
    message?.contains("无法连接到") == true -> R.string.login_error_network
    message?.contains("SocketTimeout") == true || message?.contains("Socket closed") == true ->
        R.string.login_error_timeout
    else -> null
}
