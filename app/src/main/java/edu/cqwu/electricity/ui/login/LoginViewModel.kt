package edu.cqwu.electricity.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import edu.cqwu.electricity.data.local.AccountStore
import edu.cqwu.electricity.data.local.CredentialExporter
import edu.cqwu.electricity.data.local.LoginPreferences
import edu.cqwu.electricity.data.network.AccountManager
import edu.cqwu.electricity.data.network.LoginResult
import edu.cqwu.electricity.data.network.SessionValidator
import edu.cqwu.electricity.data.network.UserCookieStore
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
    val password: String = "",
    val isLoading: Boolean = false,
    // 多用户相关
    val savedAccounts: List<String> = emptyList(),   // 所有已保存的学号列表
    // 智能切换相关
    val isAutoSwitching: Boolean = false,             // 是否正在自动验证 Cookie
)

/**
 * 一次性事件（通过 Channel 发送，不会被重复消费）
 */
sealed interface LoginEvent {
    data class Error(val msg: String) : LoginEvent
    data class LoginSuccess(val cookie: String) : LoginEvent
    data class AutoSwitchSuccess(val username: String) : LoginEvent
}

/**
 * 凭据导入/导出结果密封类
 */
sealed class CredentialResult {
    data class ExportSuccess(val encryptedString: String) : CredentialResult()
    data class ImportSuccess(val accounts: List<Pair<String, String>>) : CredentialResult()
    data class Error(val message: String) : CredentialResult()
}

/**
 * 登录页面 ViewModel
 *
 * 管理：
 * - 用户名/密码输入状态
 * - 登录加载/错误/成功状态（一次性事件通过 Channel 发送）
 * - 自动加载已保存的凭据
 * - 多用户账号列表 & 智能切换
 * - 扫码登录后保存用户信息
 */
class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val loginPrefs = LoginPreferences(application)
    private val accountStore = AccountStore(application)
    private val repository = LoginRepository(loginPrefs = loginPrefs)

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    // ═══ 一次性事件 Channel（替代 State + LaunchedEffect）═══
    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events: Flow<LoginEvent> = _events.receiveAsFlow()

    init {
        // 加载已保存的凭据（默认记住密码）
        val saved = repository.loadCredentials()
        if (saved != null) {
            _uiState.update {
                it.copy(
                    username = saved.first,
                    password = saved.second
                )
            }
        }
        // 加载所有已保存的学号列表
        _uiState.update {
            it.copy(savedAccounts = accountStore.getAllAccountNames())
        }
    }

    /**
     * 更新用户名
     */
    fun updateUsername(value: String) {
        _uiState.update { it.copy(username = value) }
    }

    /**
     * 更新密码
     */
    fun updatePassword(value: String) {
        _uiState.update { it.copy(password = value) }
    }

    /**
     * 删除指定账号：
     * 1. 从本地 AccountStore 中移除学号
     * 2. 清除记住密码
     * 3. 清除内存中该用户的 Cookie（UserCookieStore）
     * 4. 清除系统 CookieManager（WebView 缓存）
     * 5. 如果删除的是当前登录用户，切换到未登录状态
     */
    fun removeAccount(username: String) {
        accountStore.removeAccount(username)
        repository.clearCredentials()
        // 清除该用户的 Cookie 存储和系统 WebView Cookie
        AccountManager.removeUser(username)
        android.webkit.CookieManager.getInstance().removeAllCookies(null)
        android.webkit.CookieManager.getInstance().flush()
        _uiState.update {
            it.copy(
                savedAccounts = accountStore.getAllAccountNames(),
                // 如果删除的是当前显示的账号，清空输入框
                username = if (it.username == username) "" else it.username,
                password = if (it.username == username) "" else it.password
            )
        }
    }

    /**
     * 切换到指定用户（智能切换逻辑）。
     *
     * 行为：
     * 1. 总是先填充学号+密码到输入框（保持 UI 响应）
     * 2. 异步检查 AccountManager 是否有该用户的 Cookie
     * 3. 有 Cookie → 调用 SessionValidator 验证有效性
     * 4. Cookie 有效 → 自动切换，通过 LoginEvent.AutoSwitchSuccess 通知
     * 5. Cookie 无效 / 无 Cookie → 保持在输入状态，等待用户操作
     */
    fun switchToUser(username: String) {
        val password = accountStore.getPassword(username) ?: ""
        _uiState.update {
            it.copy(
                username = username,
                password = password,
                isAutoSwitching = true
            )
        }

        // 异步检查 Cookie
        viewModelScope.launch {
            val userStore = AccountManager.getCookiesForUser(username)

            // 直接调用 SessionValidator 验证（内部会从 UserCookieStore 和系统 CookieManager 兜底）
            val userInfo = SessionValidator.validate(userStore)

            if (userInfo != null) {
                // Cookie 有效，自动切换
                AccountManager.switchToUser(username)
                android.util.Log.d("LoginViewModel",
                    "智能切换: 用户[$username] Cookie 有效，自动切换成功")
                _uiState.update { it.copy(isAutoSwitching = false) }
                _events.send(LoginEvent.AutoSwitchSuccess(username))
                return@launch
            } else {
                android.util.Log.d("LoginViewModel",
                    "智能切换: 用户[$username] Cookie 无效或无 Cookie")
            }

            // Cookie 无效或无 Cookie → 保持在输入状态
            _uiState.update { it.copy(isAutoSwitching = false) }
        }
    }

    /**
     * 执行登录
     * 使用该用户独立的 Cookie 存储，避免影响已有用户的会话
     */
    fun login() {
        val state = _uiState.value
        val username = state.username.trim()
        val password = state.password.trim()

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

            // === 诊断日志：登录前的用户 Cookie 状态 ===
            val userStore = AccountManager.getCookiesForUser(username)
            val preLoginCookies = userStore.getCookie("https://authserver.cqwu.edu.cn") ?: "(空)"
            android.util.Log.d("LoginViewModel", "=== 诊断 === 用户[$username] 登录前Cookie: $preLoginCookies")

            val loginStartTime = System.currentTimeMillis()

            repository.loginForUser(username, password)
                .onSuccess { result ->
                    val loginEndTime = System.currentTimeMillis()
                    android.util.Log.d("LoginViewModel", "=== 诊断 === 用户[$username] 登录成功, 总耗时=${loginEndTime - loginStartTime}ms")

                    _uiState.update { it.copy(isLoading = false) }
                    // 发送登录成功事件
                    _events.send(LoginEvent.LoginSuccess(result.cookieString))

                    // 默认记住密码
                    repository.saveCredentials(username, password)

                    // 保存到多账号列表（保存密码）
                    accountStore.saveAccount(
                        username = username,
                        password = password,
                        rememberPassword = true
                    )

                    // 切换为该用户的 Cookie 环境
                    AccountManager.switchToUser(username)

                    // 刷新已保存的学号列表
                    _uiState.update {
                        it.copy(savedAccounts = accountStore.getAllAccountNames())
                    }
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
        }
    }

    /**
     * 刷新已保存的学号列表（用于页面重新可见时更新下拉列表）
     */
    fun refreshSavedAccounts() {
        _uiState.update {
            it.copy(savedAccounts = accountStore.getAllAccountNames())
        }
    }

    // ==================== 扫码登录回调 ====================

    /**
     * 扫码登录成功后保存用户信息。
     * 由 QrLoginScreen 在扫码成功时调用。
     *
     * @param username 从 /authserver/index.do 提取的学号
     * @param cookieStore 扫码登录使用的独立 UserCookieStore（含 CASTGC）
     */
    fun onQrLoginSuccess(username: String, cookieStore: UserCookieStore) {
        // 1. 保存到 AccountStore（无密码，不记住密码）
        accountStore.saveAccount(
            username = username,
            password = null,
            rememberPassword = false
        )

        // 2. 将独立 Cookie 存储的 CASTGC 导入 AccountManager
        val userStore = AccountManager.getCookiesForUser(username)
        val castgc = cookieStore.getCookie("https://authserver.cqwu.edu.cn")
        if (castgc != null) {
            userStore.setCookie("https://authserver.cqwu.edu.cn", castgc)
            userStore.setCookie("https://authserver.cqwu.edu.cn/authserver/login", castgc)
        }

        // 3. 切换为该用户的 Cookie 环境
        AccountManager.switchToUser(username)

        // 4. 刷新已保存的学号列表
        _uiState.update {
            it.copy(savedAccounts = accountStore.getAllAccountNames())
        }

        android.util.Log.d("LoginViewModel", "扫码登录用户[$username] 已保存到 AccountManager")
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
     * @return CredentialResult.ExportSuccess 或 CredentialResult.Error
     */
    fun exportCredentials(exportPassword: String): CredentialResult {
        val state = _uiState.value
        val currentUsername = state.username.trim()
        val currentPassword = state.password.trim()

        // 1. 从本地存储收集所有已保存的账号（仅限有密码的）
        val accounts = mutableListOf<Pair<String, String>>()
        val savedNames = accountStore.getAllAccountNames()
        for (name in savedNames) {
            val pwd = accountStore.getPassword(name)
            if (pwd != null) {
                accounts.add(name to pwd)
            }
        }

        // 2. 加上当前输入框中的账号（如果不在列表中）
        if (currentUsername.isNotBlank() && currentPassword.isNotBlank()) {
            val exists = accounts.any { it.first == currentUsername }
            if (!exists) {
                accounts.add(currentUsername to currentPassword)
            }
        }

        if (accounts.isEmpty()) {
            return CredentialResult.Error("没有可导出的账号，请先登录一次")
        }

        return try {
            val encrypted = CredentialExporter.export(accounts, exportPassword)
            CredentialResult.ExportSuccess(encrypted)
        } catch (e: Exception) {
            CredentialResult.Error("导出失败: ${e.message}")
        }
    }

    // ==================== 凭据导入 ====================

    /**
     * 导入加密凭据并自动登录第一个账号
     * @param encryptedData 加密凭据字符串
     * @param exportPassword 导出密码
     */
    fun importAndLogin(encryptedData: String, exportPassword: String) {
        val accounts = CredentialExporter.import(encryptedData, exportPassword)
        if (accounts.isNullOrEmpty()) {
            viewModelScope.launch { _events.send(LoginEvent.Error("凭据导入失败：密码错误或数据已损坏")) }
            return
        }

        val (username, password) = accounts.first()
        _uiState.update {
            it.copy(username = username, password = password)
        }

        // 自动执行登录
        login()
    }
}
