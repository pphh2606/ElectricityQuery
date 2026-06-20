package edu.cqwu.electricity.data.network.auth

import android.util.Log
import edu.cqwu.electricity.data.network.common.CookieStore
import edu.cqwu.electricity.data.network.common.UserCookieStore
import kotlin.collections.iterator

/**
 * 自动切换结果
 */
sealed interface AutoSwitchResult {
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
        // 同步清除系统 CookieManager（确保旧 Cookie 完全删除后才写入新 Cookie）
        CookieStore.removeAllCookies()

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

        Log.d("AccountManager", "commitLoginCookies: 用户[$username] 已提交 ${allCookies.size} 个域的 Cookie")

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

}
