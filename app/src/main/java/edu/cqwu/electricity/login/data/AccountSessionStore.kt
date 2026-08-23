package edu.cqwu.electricity.login.data

import android.content.Context
import android.content.SharedPreferences
import android.webkit.CookieManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import edu.cqwu.electricity.logging.AppLog
import org.json.JSONArray
import org.json.JSONObject

/**
 * 持久化的账号信息：学号、密码（可选）、最后登录时间、记住密码标志、登录状态（cookie 集合）。
 *
 * 注意：登录状态（[cookies]）与"学号+密码"相互独立：
 * - 扫码登录：只有学号 + 登录状态，没有密码；
 * - 未勾选"记住密码"：只有学号 + 登录状态，密码不落盘。
 */
data class SavedAccount(
    val username: String,
    val password: String? = null,
    val lastLoginTime: Long = 0,
    val rememberPassword: Boolean = true,
    /** 登录状态：domain(scheme://host) → cookie name→value。为空表示该账号无登录状态 */
    val cookies: Map<String, Map<String, String>> = emptyMap(),
) {
    /** 是否有登录状态（存在任一非空 cookie） */
    val hasLoginState: Boolean get() = cookies.any { (_, kv) -> kv.isNotEmpty() }
}

/**
 * 登录会话唯一持久化仓库（替代原 AccountStore + AccountManager）。
 *
 * 设计原则：
 * - 单一数据源：账号列表（学号、密码、登录状态）+ 当前激活账号全部持久化（加密存储），进程重启后可恢复。
 * - 系统 CookieManager 始终只反映当前激活账号的登录态：切换账号 = 清空系统 cookie + WebView DOM 存储，再写入目标账号的 cookie。
 * - 登录/切换成功之前不改动当前任何登录状态：登录过程使用隔离的临时 UserCookieStore，
 *   仅在成功后才通过 [commitLogin] 原子激活。
 *
 * 使用方式：在 [edu.cqwu.electricity.app.ElectricityApp.onCreate] 调用 [init]，启动时调用 [restoreActiveSession]。
 */
@Suppress("DEPRECATION")
object AccountSessionStore {

    private const val PREF_NAME = "account_session_store_encrypted"
    private const val KEY_ACCOUNTS = "saved_accounts"
    private const val KEY_ACTIVE_USER = "active_user"

    @Volatile
    private var prefs: SharedPreferences? = null

    /**
     * 初始化（幂等）。首次调用创建 EncryptedSharedPreferences（耗时 ~100ms），应在 Application.onCreate 中调用。
     */
    fun init(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            prefs = EncryptedSharedPreferences.create(
                context,
                PREF_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    private fun prefs(): SharedPreferences =
        prefs ?: throw IllegalStateException("AccountSessionStore 未初始化，请先调用 init(context)")

    // ═══════════════════════════════════════════
    //  读取
    // ═══════════════════════════════════════════

    /** 所有账号，按最后登录时间降序 */
    fun getAllAccounts(): List<SavedAccount> {
        val json = prefs().getString(KEY_ACCOUNTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<SavedAccount>()
            for (i in 0 until arr.length()) {
                parseAccount(arr.getJSONObject(i))?.let(list::add)
            }
            list.sortedByDescending { it.lastLoginTime }
        } catch (e: Exception) {
            AppLog.w("AccountSessionStore", "解析账号列表失败", e)
            emptyList()
        }
    }

    /** 获取指定账号 */
    fun getAccount(username: String): SavedAccount? =
        getAllAccounts().firstOrNull { it.username == username }

    /** 当前激活账号（持久化） */
    fun getActiveUser(): String? =
        prefs().getString(KEY_ACTIVE_USER, null)?.takeIf { it.isNotBlank() }

    // ═══════════════════════════════════════════
    //  登录提交与切换
    // ═══════════════════════════════════════════

    /**
     * 登录成功后提交：持久化账号信息并原子激活该账号。
     *
     * 登录成功之前当前登录态保持不变（登录过程使用隔离的临时 store，调用方仅在成功后调用本方法）。
     *
     * @param username 学号
     * @param password 密码；仅当 [rememberPassword] 为 true 时才落盘（扫码登录传 null + false）
     * @param rememberPassword 是否记住密码
     * @param cookies 登录过程中收集的完整 cookie 集合（domain → name→value）
     */
    fun commitLogin(
        username: String,
        password: String?,
        rememberPassword: Boolean,
        cookies: Map<String, Map<String, String>>,
    ) {
        val accounts = getAllAccounts().toMutableList()
        accounts.removeAll { it.username == username }
        accounts.add(
            SavedAccount(
                username = username,
                password = if (rememberPassword) password else null,
                lastLoginTime = System.currentTimeMillis(),
                rememberPassword = rememberPassword,
                cookies = cookies,
            )
        )
        saveAccounts(accounts)
        AppLog.d("AccountSessionStore", "commitLogin: $username, 记住密码=$rememberPassword, cookie域数=${cookies.size}")
        activate(username)
    }

    /**
     * 原子激活指定账号（QQ 模式切换）：
     * 1. 清空系统 CookieManager（同步等待完成）
     * 2. 清空 WebView DOM 存储（localStorage 等，避免旧账号网页残留）
     * 3. 将该账号持久化的 cookie 写入系统 CookieManager
     * 4. 更新 activeUser
     *
     * 调用方应确保在 IO 线程执行（内部有等待系统 cookie 清除完成的操作）。
     */
    fun activate(username: String) {
        val account = getAccount(username)
            ?: throw IllegalStateException("账号不存在: $username")
        SessionCleaner.clearAll()
        writeCookiesToSystem(account.cookies)
        prefs().edit().putString(KEY_ACTIVE_USER, username).apply()
        AppLog.d("AccountSessionStore", "激活账号: $username, cookie域数=${account.cookies.size}")
    }

    /**
     * 启动时恢复当前账号登录态到系统 CookieManager（幂等，不清除任何数据）。
     * WebKit CookieManager 本身持久化，此处确保 activeUser 的 cookie 一定存在（例如被系统清理后重建）。
     */
    fun restoreActiveSession() {
        val active = getActiveUser() ?: return
        val account = getAccount(active) ?: return
        if (!account.hasLoginState) return
        writeCookiesToSystem(account.cookies)
        AppLog.d("AccountSessionStore", "启动恢复会话: $active, cookie域数=${account.cookies.size}")
    }

    /** 将指定 cookie 集合写入系统 CookieManager */
    private fun writeCookiesToSystem(cookies: Map<String, Map<String, String>>) {
        val cm = CookieManager.getInstance()
        for ((domain, kv) in cookies) {
            for ((name, value) in kv) {
                cm.setCookie(domain, "$name=$value")
            }
        }
        cm.flush()
    }

    /**
     * 将新 cookie 合并进指定账号的持久化登录状态（WebVPN 自动登录、服务 ticket 交换等场景）。
     */
    fun mergeCookies(username: String, cookies: Map<String, Map<String, String>>) {
        if (cookies.isEmpty()) return
        val accounts = getAllAccounts().toMutableList()
        val idx = accounts.indexOfFirst { it.username == username }
        if (idx < 0) return
        val old = accounts[idx]
        val mergedMap = old.cookies.toMutableMap()
        for ((domain, kv) in cookies) {
            if (kv.isEmpty()) continue
            val existing = mergedMap[domain]?.toMutableMap() ?: mutableMapOf()
            existing.putAll(kv)
            mergedMap[domain] = existing
        }
        accounts[idx] = old.copy(cookies = mergedMap)
        saveAccounts(accounts)
        AppLog.d("AccountSessionStore", "合并 cookie 到 $username: ${cookies.keys}")
    }

    /**
     * 将系统 CookieManager 中指定域名的 cookie 合并进当前激活账号的持久化登录状态。
     * 用于服务登录成功后把该服务的登录态随账号一起保存。
     */
    fun mergeSystemCookiesForActiveUser(domain: String) {
        val active = getActiveUser() ?: return
        val cookieString = try {
            CookieManager.getInstance().getCookie(domain)
        } catch (e: Exception) {
            null
        } ?: return
        val parsed = CookieParser.parse(cookieString)
        if (parsed.isEmpty()) return
        mergeCookies(active, mapOf(domain to parsed))
    }

    // ═══════════════════════════════════════════
    //  删除 / 清除
    // ═══════════════════════════════════════════

    /**
     * 删除账号：删除持久化记录（学号 + 密码 + 登录状态）。
     * 若删除的是当前激活账号，同时清空系统登录态（cookie + WebView 存储），回到未登录。
     * 退出登录 API 的调用由调用方在调用本方法前完成（见 [LogoutApi]）。
     */
    fun deleteAccount(username: String) {
        val accounts = getAllAccounts().filterNot { it.username == username }
        saveAccounts(accounts)
        if (getActiveUser() == username) {
            SessionCleaner.clearAll()
            prefs().edit().remove(KEY_ACTIVE_USER).apply()
        }
        AppLog.d("AccountSessionStore", "删除账号: $username")
    }

    /** 清空所有账号数据与登录态（设置页"清除存储空间"用）。 */
    fun clearAllData() {
        SessionCleaner.clearAll()
        prefs().edit().clear().apply()
        AppLog.d("AccountSessionStore", "清空全部账号数据")
    }

    /**
     * 清除所有账号的登录状态（cookie），保留账号（学号）和密码。
     *
     * 用于设置页「登录信息和cookie」清理：清完后账号管理弹窗仍显示账号和密码，
     * 但切换任何账号都需重新登录。不涉及 WebView DOM 存储（由「WebView 数据」项负责）。
     */
    fun clearAllLoginStates() {
        val accounts = getAllAccounts()
        if (accounts.isEmpty()) return
        saveAccounts(accounts.map { it.copy(cookies = emptyMap()) })
        SessionCleaner.clearSystemCookies()
        AppLog.d("AccountSessionStore", "已清除所有账号的登录状态，保留账号与密码")
    }

    // ═══════════════════════════════════════════
    //  序列化
    // ═══════════════════════════════════════════

    private fun saveAccounts(accounts: List<SavedAccount>) {
        val arr = JSONArray()
        for (a in accounts.sortedByDescending { it.lastLoginTime }) {
            val obj = JSONObject()
            obj.put("u", a.username)
            a.password?.let { obj.put("p", it) }
            obj.put("t", a.lastLoginTime)
            obj.put("r", a.rememberPassword)
            val cookiesObj = JSONObject()
            for ((domain, kv) in a.cookies) {
                val kvObj = JSONObject()
                for ((name, value) in kv) kvObj.put(name, value)
                cookiesObj.put(domain, kvObj)
            }
            obj.put("c", cookiesObj)
            arr.put(obj)
        }
        prefs().edit().putString(KEY_ACCOUNTS, arr.toString()).apply()
    }

    private fun parseAccount(obj: JSONObject): SavedAccount? {
        return try {
            val cookies = mutableMapOf<String, Map<String, String>>()
            val cookiesObj = obj.optJSONObject("c")
            if (cookiesObj != null) {
                val keys = cookiesObj.keys()
                while (keys.hasNext()) {
                    val domain = keys.next()
                    val kvObj = cookiesObj.optJSONObject(domain) ?: continue
                    val kv = mutableMapOf<String, String>()
                    val kvKeys = kvObj.keys()
                    while (kvKeys.hasNext()) {
                        val name = kvKeys.next()
                        kv[name] = kvObj.optString(name)
                    }
                    if (kv.isNotEmpty()) cookies[domain] = kv
                }
            }
            SavedAccount(
                username = obj.getString("u"),
                password = if (obj.has("p")) obj.getString("p") else null,
                lastLoginTime = if (obj.has("t")) obj.getLong("t") else 0,
                rememberPassword = if (obj.has("r")) obj.getBoolean("r") else true,
                cookies = cookies,
            )
        } catch (e: Exception) {
            null
        }
    }
}
