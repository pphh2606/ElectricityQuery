package edu.cqwu.electricity.data.network

import android.webkit.CookieManager
import edu.cqwu.electricity.data.local.AccountStore

/**
 * 统一的 Cookie 管理层（桥接 android.webkit.CookieManager）。
 *
 * 所有 Cookie 统一保存在 android.webkit.CookieManager（浏览器缓存，磁盘持久化）。
 * 提供增删改查等基础 API。
 *
 * 使用方式：
 *   CookieStore.init()  // Application.onCreate() 中调用
 *   CookieStore.getCookie("https://example.com")
 *   CookieStore.getCookieValue("https://example.com", "JSESSIONID")
 */
object CookieStore {

    private var isInitialized = false

    /**
     * 初始化 CookieStore
     * - 必须在 Application.onCreate() 中调用
     */
    fun init() {
        if (isInitialized) return
        CookieManager.getInstance().setAcceptCookie(true)
        isInitialized = true
    }

    /**
     * 获取指定 URL 的所有 Cookie（name=value; name2=value2 格式）
     */
    fun getCookie(url: String): String? {
        checkInitialized()
        return CookieManager.getInstance().getCookie(url)
    }

    /**
     * 获取指定 URL 中特定名称的 Cookie 值
     */
    fun getCookieValue(url: String, name: String): String? {
        val cookieString = getCookie(url) ?: return null
        // 按分号分割，找到 "name=value" 的条目
        // 不能用正则 look-behind，因为 Java 不支持变长 look-behind
        val prefix = "$name="
        return cookieString.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith(prefix) }
            ?.substringAfter(prefix)
    }

    /**
     * 为指定 URL 设置 Cookie
     * @param url 完整的 URL（含协议），如 "https://authserver.cqwu.edu.cn"
     * @param cookieValue "name=value" 或完整的 Set-Cookie 格式
     */
    fun setCookie(url: String, cookieValue: String) {
        checkInitialized()
        CookieManager.getInstance().setCookie(url, cookieValue)
        CookieManager.getInstance().flush()
    }

    /**
     * 清除所有 Cookie
     */
    fun removeAllCookies() {
        checkInitialized()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }

    // ==================== 内部方法 ====================

    private fun checkInitialized() {
        if (!isInitialized) {
            throw IllegalStateException("CookieStore 未初始化，请先调用 CookieStore.init(context)")
        }
    }

}

/**
 * 单个用户的独立 Cookie 存储（内存 Map）。
 *
 * 用于多用户场景，每个用户拥有自己的 Cookie 存储，
 * OkHttp 请求通过 UserAwareCookieJar 读写此存储，
 * 与系统 CookieManager 完全隔离。
 *
 * 切换用户时，调用 syncToCookieManager() 将该用户的 Cookie
 * 同步到系统 CookieManager，确保 WebView 正常工作。
 */
class UserCookieStore {

    // url -> (cookieName -> cookieValue)
    private val cookieMap = mutableMapOf<String, MutableMap<String, String>>()

    /**
     * 获取指定 URL 的所有 Cookie（name=value; name2=value2 格式）
     */
    fun getCookie(url: String): String? {
        val domainCookies = cookieMap[normalizeUrl(url)] ?: return null
        if (domainCookies.isEmpty()) return null
        return domainCookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    /**
     * 获取指定 URL 中特定名称的 Cookie 值
     */
    fun getCookieValue(url: String, name: String): String? {
        return cookieMap[normalizeUrl(url)]?.get(name)
    }

    /**
     * 为指定 URL 设置 Cookie
     */
    fun setCookie(url: String, cookieValue: String) {
        val normalized = normalizeUrl(url)
        val domainCookies = cookieMap.getOrPut(normalized) { mutableMapOf() }

        // 解析 "name=value" 或 "name=value; path=/; ..."
        val eqIndex = cookieValue.indexOf("=")
        if (eqIndex < 0) return
        val name = cookieValue.substring(0, eqIndex).trim()
        val value = cookieValue.substring(eqIndex + 1).trim()
            .split(";")[0].trim() // 只取第一个分号前的值

        domainCookies[name] = value
    }

    /**
     * 删除指定 URL 的某个 Cookie
     */
    fun removeCookie(url: String, name: String) {
        cookieMap[normalizeUrl(url)]?.remove(name)
    }

    /**
     * 清除该用户的所有 Cookie
     */
    fun removeAllCookies() {
        cookieMap.clear()
    }

    /**
     * 获取该用户的所有 Cookie 总览（用于调试）
     */
    fun getAllCookies(): Map<String, Map<String, String>> {
        return cookieMap.toMap()
    }

    /**
     * 将该用户的 Cookie 同步到系统 CookieManager（供 WebView 使用）
     */
    fun syncToCookieManager() {
        val cm = CookieManager.getInstance()
        for ((url, domainCookies) in cookieMap) {
            for ((name, value) in domainCookies) {
                cm.setCookie(url, "$name=$value")
            }
        }
        cm.flush()
    }

    /**
     * 从系统 CookieManager 导入 Cookie 到本用户存储
     */
    fun syncFromCookieManager() {
        val cm = CookieManager.getInstance()
        val knownDomains = listOf(
            "https://authserver.cqwu.edu.cn",
            "https://electricitypay.cqwu.edu.cn",
            "https://pay.cqwu.edu.cn",
            "http://218.194.176.214:8382"
        )
        for (url in knownDomains) {
            val cookies = cm.getCookie(url) ?: continue
            val parts = cookies.split(";")
            for (part in parts) {
                val trimmed = part.trim()
                if (trimmed.contains("=")) {
                    setCookie(url, trimmed)
                }
            }
        }
    }

    private fun normalizeUrl(url: String): String {
        // 只保留协议 + host，忽略 path 和 query
        return try {
            val uri = android.net.Uri.parse(url)
            "${uri.scheme}://${uri.host}"
        } catch (e: Exception) {
            url
        }
    }
}

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
            when (val result = SessionValidator.validate(userStore, syncFromSystem = false)) {
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
