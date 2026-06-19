package edu.cqwu.electricity.data.network

import android.webkit.CookieManager
import edu.cqwu.electricity.data.local.AccountStore

/**
 * 自动切换结果
 */
sealed interface AutoSwitchResult {
    /** Cookie 有效，切换成功 */
    data object Success : AutoSwitchResult
    /** Cookie 无效或无 Cookie，需要手动登录 */
    data class NeedManualLogin(val username: String, val password: String?) : AutoSwitchResult
    /** 验证过程中发生异常 */
    data class Error(val message: String) : AutoSwitchResult
}

/**
 * 多用户 Cookie 管理器。
 *
 * 管理多个用户的 UserCookieStore，提供切换、同步功能。
 */
object AccountManager {

    private val userCookies = mutableMapOf<String, UserCookieStore>()
    private var activeUser: String? = null

    /**
     * 获取或创建指定用户的 Cookie 存储
     */
    fun getCookiesForUser(username: String): UserCookieStore {
        return userCookies.getOrPut(username) { UserCookieStore() }
    }

    /**
     * 切换到指定用户：
     * 1. 清除系统 CookieManager
     * 2. 将该用户的 Cookie 同步到系统 CookieManager
     */
    fun switchToUser(username: String) {
        // 清除系统 CookieManager
        val cm = CookieManager.getInstance()
        cm.removeAllCookies(null)
        cm.flush()

        // 同步该用户的 Cookie 到系统 CookieManager
        val store = getCookiesForUser(username)
        store.syncToCookieManager()

        activeUser = username
    }

    /**
     * 获取当前激活的用户名
     */
    fun getActiveUser(): String? = activeUser

    /**
     * 登录成功后，将临时 Cookie 存储提交到持久存储并切换用户。
     *
     * 这是 CasAuthApi 和 QrLoginApi 统一的登录后提交方法。
     * 登录过程使用隔离的临时 UserCookieStore，仅在成功后才通过此方法
     * 将 Cookie 原子性地迁移到持久存储，避免半成品 Cookie 污染。
     *
     * @param username 登录成功的学号
     * @param tempStore 登录过程中使用的临时 Cookie 存储
     */
    fun commitLoginCookies(username: String, tempStore: UserCookieStore) {
        val persistentStore = getCookiesForUser(username)

        // 先清除持久存储中的旧 Cookie（防止脏数据残留）
        persistentStore.removeAllCookies()

        // 将临时存储中的所有 Cookie 复制到持久存储
        val allCookies = tempStore.getAllCookies()
        for ((domain, domainCookies) in allCookies) {
            for ((cookieName, cookieValue) in domainCookies) {
                persistentStore.setCookie(domain, "$cookieName=$cookieValue")
            }
        }

        android.util.Log.d("AccountManager", "commitLoginCookies: 用户[$username] 已提交 ${allCookies.size} 个域的 Cookie")

        // 切换到该用户（清除系统 CookieManager + 同步持久存储到系统）
        switchToUser(username)
    }

    /**
     * 移除指定用户的所有 Cookie
     */
    fun removeUser(username: String) {
        userCookies.remove(username)
        if (activeUser == username) {
            activeUser = null
        }
    }

    /**
     * 自动切换到指定用户（智能切换逻辑）。
     *
     * 验证该用户的 Cookie 有效性：
     * - Cookie 有效且学号匹配 → 执行切换，返回 [AutoSwitchResult.Success]
     * - Cookie 无效或无 Cookie → 返回 [AutoSwitchResult.NeedManualLogin]
     * - 网络异常 → 返回 [AutoSwitchResult.Error]
     *
     * 此方法可由账号管理弹窗直接调用，无需 LoginViewModel 实例。
     *
     * @param username 目标学号
     * @param accountStore 用于获取密码的 AccountStore 实例
     */
    suspend fun autoSwitchToUser(
        username: String,
        accountStore: AccountStore,
    ): AutoSwitchResult {
        return try {
            val userStore = getCookiesForUser(username)
            when (val result = SessionManager.validateCookie(userStore, syncFromSystem = false)) {
                is SessionValidationResult.Valid -> {
                    if (result.info.username != username) {
                        android.util.Log.w("AccountManager", "学号不匹配: 期望[$username] 实际[${result.info.username}]")
                        AutoSwitchResult.Error("Cookie 返回学号与目标不一致")
                    } else {
                        switchToUser(username)
                        AutoSwitchResult.Success
                    }
                }
                is SessionValidationResult.Invalid -> {
                    AutoSwitchResult.NeedManualLogin(
                        username = username,
                        password = accountStore.getPassword(username)
                    )
                }
                is SessionValidationResult.NetworkError -> {
                    AutoSwitchResult.Error("网络异常，请检查网络")
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 不要吞掉协程取消异常，必须重新抛出
            throw e
        } catch (e: Exception) {
            android.util.Log.e("AccountManager", "autoSwitchToUser 异常: ${e::class.simpleName}: ${e.message}", e)
            AutoSwitchResult.Error(e.message ?: "验证失败")
        }
    }
}
