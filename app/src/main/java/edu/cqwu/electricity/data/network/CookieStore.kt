package edu.cqwu.electricity.data.network

import android.webkit.CookieManager

/**
 * 统一的 Cookie 管理层
 *
 * 所有 Cookie 统一保存在 android.webkit.CookieManager（浏览器缓存，磁盘持久化）。
 * 提供增删改查、导入导出、会话检测等完整 API。
 *
 * 使用方式：
 *   CookieStore.init(context)  // Application.onCreate() 中调用
 *   CookieStore.getCookie("https://example.com", "JSESSIONID")
 *   CookieStore.importFromString("JSESSIONID=xxx; CASTGC=yyy")
 *   CookieStore.exportToString()
 */
object CookieStore {

    // 应用涉及的已知域名列表
    private val KNOWN_DOMAINS = listOf(
        "https://authserver.cqwu.edu.cn",
        "https://electricitypay.cqwu.edu.cn",
        "https://pay.cqwu.edu.cn",
        "http://218.194.176.214:8382"
    )

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
     * 为所有已知域名设置同一个 Cookie
     * 用于导入时，不确定 Cookie 属于哪个域名的场景
     */
    fun setCookieForAllDomains(cookieValue: String) {
        for (url in KNOWN_DOMAINS) {
            CookieManager.getInstance().setCookie(url, cookieValue)
        }
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

    /**
     * 从格式化的 Cookie 字符串导入
     * 支持格式：JSESSIONID=xxx; CASTGC=yyy; ...
     * 会写入所有已知域名
     *
     * @return 成功导入的 Cookie 数量
     */
    fun importFromString(cookieString: String): Int {
        val pairs = parseCookieString(cookieString)
        if (pairs.isEmpty()) return 0

        for ((name, value) in pairs) {
            setCookieForAllDomains("$name=$value")
        }

        return pairs.size
    }

    /**
     * 导出所有已知域名的 Cookie 为格式化的字符串
     * @return "JSESSIONID=xxx; CASTGC=yyy; ..."
     */
    fun exportToString(): String {
        val allPairs = linkedMapOf<String, String>()  // 保持顺序 + 去重

        for (url in KNOWN_DOMAINS) {
            val cookies = getCookie(url) ?: continue
            val parsed = parseCookieString(cookies)
            for ((name, value) in parsed) {
                allPairs[name] = value
            }
        }

        return allPairs.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    // ==================== 内部方法 ====================

    private fun checkInitialized() {
        if (!isInitialized) {
            throw IllegalStateException("CookieStore 未初始化，请先调用 CookieStore.init(context)")
        }
    }

    /**
     * 解析 "name1=val1; name2=val2" 格式的 Cookie 字符串
     */
    private fun parseCookieString(cookieString: String): List<Pair<String, String>> {
        return cookieString
            .split(";")
            .map { it.trim() }
            .filter { it.contains("=") }
            .map { pair ->
                val eqIndex = pair.indexOf("=")
                pair.substring(0, eqIndex).trim() to pair.substring(eqIndex + 1).trim()
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
}
