package edu.cqwu.electricity.data.repository

import edu.cqwu.electricity.data.local.LoginPreferences
import edu.cqwu.electricity.data.network.CasAuthApi
import edu.cqwu.electricity.data.network.LoginResult

/**
 * 登录数据仓库
 * 协调 CasAuthApi（网络登录）和 LoginPreferences（本地记住密码）
 */
class LoginRepository(
    private val authApi: CasAuthApi = CasAuthApi(),
    private val loginPrefs: LoginPreferences? = null  // 由外部注入，因为需要 Context
) {

    /**
     * 执行登录（使用全局 CookieManager）
     * @param username 学号
     * @param password 密码
     * @return Result<LoginResult>
     */
    suspend fun login(username: String, password: String): Result<LoginResult> {
        return authApi.login(username, password)
    }

    /**
     * 为指定用户执行登录（使用该用户独立的 Cookie 存储）
     * 多用户场景下，新用户的登录不会影响已有用户的会话
     * @param username 学号
     * @param password 密码
     * @return Result<LoginResult>
     */
    suspend fun loginForUser(username: String, password: String): Result<LoginResult> {
        return authApi.loginForUser(username, password)
    }

    /**
     * 保存登录凭据（记住密码）
     */
    fun saveCredentials(username: String, password: String) {
        loginPrefs?.saveCredentials(username, password)
    }

    /**
     * 加载已保存的凭据
     * @return Pair(username, password) 或 null
     */
    fun loadCredentials(): Pair<String, String>? {
        return loginPrefs?.loadCredentials()
    }

    /**
     * 清除已保存的凭据
     */
    fun clearCredentials() {
        loginPrefs?.clearCredentials()
    }

    /**
     * 是否开启记住密码
     */
    fun isRememberEnabled(): Boolean {
        return loginPrefs?.isRememberEnabled() ?: false
    }

    /**
     * 更新记住密码状态
     */
    fun setRememberEnabled(enabled: Boolean) {
        loginPrefs?.setRememberEnabled(enabled)
    }
}
