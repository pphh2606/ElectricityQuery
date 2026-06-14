package edu.cqwu.electricity.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import edu.cqwu.electricity.data.local.AccountStore
import edu.cqwu.electricity.data.local.CredentialExporter
import edu.cqwu.electricity.data.network.AccountManager
import edu.cqwu.electricity.data.repository.LoginRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 登录页面 UI 状态（仅持久性状态）
 */
data class LoginUiState(
    val username: String = "",
    val password: String = "",               // UI 显示的密码（可能为假占位符）
    val isLoading: Boolean = false,
    // 记住密码
    val rememberPassword: Boolean = true,             // 是否记住密码（默认记住）
    // 密码显隐控制
    val passwordRevealed: Boolean = false,            // 密码是否明文显示
    val passwordFromStorage: Boolean = false,         // 密码是否来自存储（显示假占位符，不可切换显隐）
)

/** 存储来源密码在 UI 上显示的假占位符 */
private const val PLACEHOLDER_PASSWORD = "12345678"

/**
 * 登录页面 ViewModel
 *
 * 管理：
 * - 用户名/密码输入状态
 * - 登录加载/错误/成功状态（一次性事件通过 Channel 发送）
 * - 自动加载已保存的凭据
 * - 扫码登录后保存用户信息
 *
 * 安全设计：
 * - 从存储加载的密码不会暴露给 UI，仅保存在 [actualPassword] 中
 * - UI 显示假占位符 [PLACEHOLDER_PASSWORD]，用户无法查看真实密码明文
 * - 用户手动输入密码后，眼睛图标才可用
 */
class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val accountStore = AccountStore(application)
    private val repository = LoginRepository()

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // ═══ 一次性事件 Channel（替代 State + LaunchedEffect）═══
    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events: Flow<LoginEvent> = _events.receiveAsFlow()

    /**
     * 真实密码，由存储加载，永不暴露给 UI。
     * 当 [LoginUiState.passwordFromStorage] 为 true 时，
     * [LoginUiState.password] 显示的是假占位符 [PLACEHOLDER_PASSWORD]，
     * 实际登录时使用此变量中的真实密码。
     */
    private var actualPassword: String = ""

    init {
        // 加载记住密码复选框的上次状态（默认 true，与历史行为一致）
        val rememberPwd = accountStore.getRememberPassword()

        // 从 AccountStore 获取最近登录的账号
        val allAccounts = accountStore.getAllAccounts()
        val lastAccount = allAccounts.firstOrNull()

        if (lastAccount != null && lastAccount.password != null && rememberPwd) {
            // 有最近账号且记住密码，自动填充（显示假占位符）
            actualPassword = lastAccount.password
            _uiState.update {
                it.copy(
                    username = lastAccount.username,
                    password = PLACEHOLDER_PASSWORD,
                    rememberPassword = true,
                    passwordFromStorage = true
                )
            }
        } else {
            // 无账号、无密码、或未勾选记住密码 → 只填充学号
            _uiState.update {
                it.copy(
                    username = lastAccount?.username ?: "",
                    password = "",
                    rememberPassword = rememberPwd,
                    passwordFromStorage = false
                )
            }
        }
    }

    /**
     * 重置所有输入状态为初始值。
     *
     * 每次进入 LoginScreen 时调用，确保不残留上一次登录的数据。
     */
    fun resetState() {
        actualPassword = ""
        val rememberPwd = accountStore.getRememberPassword()
        val lastAccount = accountStore.getAllAccounts().firstOrNull()
        if (lastAccount != null && lastAccount.password != null && rememberPwd) {
            actualPassword = lastAccount.password
            _uiState.value = LoginUiState(
                username = lastAccount.username,
                password = PLACEHOLDER_PASSWORD,
                rememberPassword = true,
                passwordFromStorage = true
            )
        } else {
            _uiState.value = LoginUiState(
                username = lastAccount?.username ?: "",
                password = "",
                rememberPassword = rememberPwd,
                passwordFromStorage = false
            )
        }
    }

    /**
     * 更新用户名
     */
    fun updateUsername(value: String) {
        _uiState.update { it.copy(username = value) }
    }

    /**
     * 更新密码（用户手动输入）。
     * 用户输入密码时标记为非存储来源，允许切换明文/密文显示。
     */
    fun updatePassword(value: String) {
        actualPassword = ""  // 用户手动输入时，清除之前存储的真实密码
        _uiState.update {
            it.copy(
                password = value,
                passwordFromStorage = false,
                passwordRevealed = false
            )
        }
    }

    /**
     * 更新记住密码复选框状态
     */
    fun setRememberPassword(remember: Boolean) {
        _uiState.update { it.copy(rememberPassword = remember) }
        accountStore.setRememberPassword(remember)
    }

    /**
     * 切换密码明文/密文显示（仅在用户手动输入密码时可用）
     */
    fun togglePasswordRevealed() {
        _uiState.update {
            if (it.passwordFromStorage) it
            else it.copy(passwordRevealed = !it.passwordRevealed)
        }
    }

    /**
     * 获取实际用于登录的密码。
     * - 如果密码来自存储（显示占位符），返回内部保存的真实密码
     * - 如果密码是用户手动输入的，返回 UI 上的密码
     */
    private fun resolveLoginPassword(): String {
        val state = _uiState.value
        return if (state.passwordFromStorage) actualPassword else state.password
    }

    /**
     * 执行登录
     * 使用该用户独立的 Cookie 存储，避免影响已有用户的会话
     */
    fun login() {
        val state = _uiState.value
        val username = state.username.trim()
        val password = resolveLoginPassword()  // 使用解析后的真实密码

        // 输入校验
        if (username.isBlank()) {
            viewModelScope.launch { _events.send(LoginEvent.Error("请输入学号")) }
            return
        }
        if (password.isBlank()) {
            viewModelScope.launch { _events.send(LoginEvent.Error("请输入密码")) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // === 诊断日志：登录前的用户 Cookie 状态 ===
                val userStore = AccountManager.getCookiesForUser(username)
                val preLoginCookies = userStore.getCookie("https://authserver.cqwu.edu.cn") ?: "(空)"
                android.util.Log.d("LoginViewModel", "=== 诊断 === 用户[$username] 登录前Cookie: $preLoginCookies")

                val loginStartTime = System.currentTimeMillis()

                repository.login(username, password)
                    .onSuccess { result ->
                        val loginEndTime = System.currentTimeMillis()
                        android.util.Log.d("LoginViewModel", "=== 诊断 === 用户[$username] 登录成功, 总耗时=${loginEndTime - loginStartTime}ms")

                        _uiState.update { it.copy(isLoading = false) }
                        // 发送登录成功事件
                        _events.send(LoginEvent.LoginSuccess(result.cookieString))

                        // 保存到 AccountStore（学号始终保存，密码按复选框决定）
                        val rememberPwd = _uiState.value.rememberPassword
                        accountStore.saveAccount(
                            username = username,
                            password = password,
                            rememberPassword = rememberPwd
                        )

                        // 切换为该用户的 Cookie 环境
                        AccountManager.switchToUser(username)
                    }
                    .onFailure { e ->
                        val loginEndTime = System.currentTimeMillis()
                        val elapsed = loginEndTime - loginStartTime

                        // === 诊断日志：登录失败后的 Cookie 状态 ===
                        val failCookies = userStore.getCookie("https://authserver.cqwu.edu.cn") ?: "(空)"
                        val allCookies = userStore.getAllCookies()
                        android.util.Log.e("LoginViewModel", "=== 诊断 === 用户[$username] 登录失败, 耗时=${elapsed}ms, 异常=${e::class.simpleName}: ${e.message}")
                        android.util.Log.e("LoginViewModel", "=== 诊断 === 失败后Cookie: $failCookies")
                        android.util.Log.e("LoginViewModel", "=== 诊断 === UserCookieStore完整内容: $allCookies")

                        val errorMsg = when {
                            e.message?.contains("无法获取加密 salt") == true -> "获取登录参数失败，请检查网络"
                            e.message?.contains("无法获取 lt") == true -> "获取登录参数失败"
                            e.message?.contains("未能获取到 CASTGC") == true -> "登录失败：账号或密码错误"
                            e.message?.contains("无法连接到") == true -> "网络连接失败，请检查网络"
                            e.message?.contains("SocketTimeout") == true || e.message?.contains("Socket closed") == true -> {
                                "网络请求超时，服务器连接不稳定，请稍后重试"
                            }
                            else -> e.message ?: "登录失败，未知错误"
                        }
                        _uiState.update { it.copy(isLoading = false) }
                        _events.send(LoginEvent.Error(errorMsg))
                    }
            } catch (e: Exception) {
                // 防御性捕获：防止非 Result 异常导致 isLoading 卡住
                android.util.Log.e("LoginViewModel", "登录异常", e)
                _uiState.update { it.copy(isLoading = false) }
                _events.send(LoginEvent.Error("登录异常: ${e.message}"))
            }
        }
    }
    // ==================== 凭据导出 ====================

    /**
     * 导出所有已保存的账号凭据（加密）。
     *
     * 从本地 AccountStore 收集所有已保存（记住密码）的账号，
     * 同时包含当前输入框中的账号（如果不在已保存列表中），
     * 合并去重后一起加密导出。
     *
     * @param exportPassword 用户设定的导出密码
     */
    fun exportCredentials(exportPassword: String) {
        viewModelScope.launch {
            if (exportPassword.length < 4) {
                _events.send(LoginEvent.Error("密码长度不能少于4位"))
                return@launch
            }
            val state = _uiState.value
            val currentUsername = state.username.trim()
            val currentPassword = resolveLoginPassword()

            // 1. 一次性读取所有带密码的账号
            val accounts = accountStore.getAllAccountsWithPassword().toMutableList()

            // 2. 加上当前输入框中的账号（如果不在列表中）
            if (currentUsername.isNotBlank() && currentPassword.isNotBlank()) {
                val exists = accounts.any { it.first == currentUsername }
                if (!exists) {
                    accounts.add(currentUsername to currentPassword)
                }
            }

            if (accounts.isEmpty()) {
                _events.send(LoginEvent.Error("没有可导出的账号，请先登录一次"))
                return@launch
            }

            try {
                val encrypted = CredentialExporter.export(accounts, exportPassword)
                _events.send(LoginEvent.ExportSuccess(encrypted))
            } catch (e: Exception) {
                _events.send(LoginEvent.Error("导出失败: ${e.message}"))
            }
        }
    }

    // ==================== 凭据导入 ====================

    /**
     * 导入加密凭据并自动登录第一个账号
     * @param encryptedData 加密凭据字符串
     * @param exportPassword 导出密码
     */
    fun importAndLogin(encryptedData: String, exportPassword: String) {
        val accounts = try {
            CredentialExporter.import(encryptedData, exportPassword)
        } catch (e: Exception) {
            viewModelScope.launch { _events.send(LoginEvent.Error("凭据解析失败: ${e.message}")) }
            return
        }
        if (accounts.isNullOrEmpty()) {
            viewModelScope.launch { _events.send(LoginEvent.Error("凭据导入失败：密码错误或数据已损坏")) }
            return
        }

        val (username, password) = accounts.first()
        actualPassword = password  // 保存真实密码
        _uiState.update {
            it.copy(
                username = username,
                password = if (password.isNotEmpty()) PLACEHOLDER_PASSWORD else "",
                passwordFromStorage = password.isNotEmpty()
            )
        }

        // 自动执行登录
        login()
    }
}

/**
 * 一次性事件（通过 Channel 发送，不会被重复消费）
 */
sealed interface LoginEvent {
    data class Error(val msg: String) : LoginEvent
    data class LoginSuccess(val cookie: String) : LoginEvent
    data class ExportSuccess(val encryptedData: String) : LoginEvent
}

