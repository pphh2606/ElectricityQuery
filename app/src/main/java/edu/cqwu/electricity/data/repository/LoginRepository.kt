package edu.cqwu.electricity.data.repository

import edu.cqwu.electricity.data.network.CasAuthApi
import edu.cqwu.electricity.data.network.LoginResult

/**
 * 登录数据仓库
 *
 * 仅处理网络登录相关逻辑，本地存储（记住密码、多账号）由 AccountStore 直接管理。
 * 所有登录均使用用户隔离的 Cookie 存储，避免多用户串号。
 */
class LoginRepository(
    private val authApi: CasAuthApi = CasAuthApi()
) {
    /**
     * 使用用户隔离的 Cookie 存储执行登录。
     * 多用户场景下，新用户的登录不会影响已有用户的会话。
     *
     * @param username 学号
     * @param password 密码
     * @return Result<LoginResult>
     */
    suspend fun login(username: String, password: String): Result<LoginResult> {
        return authApi.loginForUser(username, password)
    }
}
