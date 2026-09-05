package edu.cqwu.electricity.login.data

import android.content.Context
import android.webkit.CookieManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import edu.cqwu.electricity.common.net.CookieParser
import edu.cqwu.electricity.logging.AppLog
import edu.cqwu.electricity.login.model.AuthSessionCommitV2
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 持久化的账号信息：登录用户名（可为学号或登录别名）、密码（可选）、最后登录时间、记住密码标志、登录状态（cookie 集合）。
 *
 * 注意：登录状态（[cookies]）与"登录用户名+密码"相互独立：
 * - 扫码登录：只有登录用户名 + 登录状态，没有密码；
 * - 未勾选"记住密码"：只有登录用户名 + 登录状态，密码不落盘。
 *
 * [id] 为条目唯一标识（UUID）：同一登录用户名可通过不同登录方式产生多个条目，切换/删除按 id 定位。
 */
data class SavedAccount(
    val id: String,
    val username: String,
    val password: String? = null,
    val lastLoginTime: Long = 0,
    val rememberPassword: Boolean = true,
    /** 登录状态：domain(scheme://host) → cookie name→value。为空表示该账号无登录状态 */
    val cookies: Map<String, Map<String, String>> = emptyMap(),
    /** 数字学号（登录时获取并缓存，与登录用户名无关；可能为 null 表示尚未获取/回填） */
    val studentId: String? = null,
) {
    /** 是否有登录状态（存在任一非空 cookie） */
    val hasLoginState: Boolean get() = cookies.any { (_, kv) -> kv.isNotEmpty() }
}

/**
 * 登录会话唯一持久化仓库（替代原 AccountStore + AccountManager）。
 *
 * 设计原则：
 * - 单一数据源：账号列表（登录用户名、密码、登录状态）+ 当前激活账号全部持久化（加密存储），进程重启后可恢复。
 * - 系统 CookieManager 始终只反映当前激活账号的登录态：切换账号 = 清空系统 cookie + WebView DOM 存储，再写入目标账号的 cookie。
 * - 登录/切换成功之前不改动当前任何登录状态：登录过程使用隔离的临时 UserCookieStore，
 *   仅在成功后才通过统一提交入口 [commitSession] 原子激活。
 *
 * 使用方式：在 [edu.cqwu.electricity.app.ElectricityApp.onCreate] 调用 [init]，启动时调用 [restoreActiveSession]。
 */
@Suppress("DEPRECATION")
object AccountSessionStore {

    private const val PREF_NAME = "account_session_store_encrypted"
    private const val KEY_ACCOUNTS = "saved_accounts"
    private const val KEY_ACTIVE_ACCOUNT_ID = "active_account_id"

    @Volatile
    private var storage: KeyValueStoreV2? = null

    /**
     * 初始化（幂等）。首次调用创建 EncryptedSharedPreferences（耗时 ~100ms），应在 Application.onCreate 中调用。
     */
    fun init(context: Context) {
        if (storage != null) {
            return
        }
        synchronized(this) {
            if (storage != null) {
                return
            }
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val encPrefs = EncryptedSharedPreferences.create(
                context,
                PREF_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            storage = SharedPrefsStoreV2(encPrefs)
        }
    }

    /**
     * 以内存实现初始化（仅 JVM 单测使用）：跳过 Android 加密存储。
     */
    internal fun initForTesting(fake: KeyValueStoreV2) {
        storage = fake
    }

    private fun store(): KeyValueStoreV2 =
        storage ?: throw IllegalStateException("AccountSessionStore 未初始化，请先调用 init(context)")

    // ═══════════════════════════════════════════
    //  读取
    // ═══════════════════════════════════════════

    /** 所有账号，按最后登录时间降序 */
    fun getAllAccounts(): List<SavedAccount> {
        val json = store().getString(KEY_ACCOUNTS) ?: return emptyList()
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

    /** 按条目 id 获取账号 */
    fun getAccountById(id: String): SavedAccount? =
        getAllAccounts().firstOrNull { it.id == id }

    /** 当前激活的账号条目（按持久化的条目 id 定位） */
    fun getActiveAccount(): SavedAccount? {
        val id = store().getString(KEY_ACTIVE_ACCOUNT_ID)?.takeIf { it.isNotBlank() } ?: return null
        return getAccountById(id)
    }

    // ═══════════════════════════════════════════
    //  登录提交与切换
    // ═══════════════════════════════════════════

    /**
     * 登录提交内部实现：持久化账号信息并原子激活该账号（仅被 [commitSession] 调用）。
     *
     * 登录成功之前当前登录态保持不变（登录过程使用隔离的临时 store，调用方仅在成功后提交）。
     *
     * @param username 登录用户名（学号或登录别名）
     * @param password 密码；仅当 [rememberPassword] 为 true 时才落盘（扫码登录传 null + false）
     * @param rememberPassword 是否记住密码
     * @param cookies 登录过程中收集的完整 cookie 集合（domain → name→value）
     * @param studentId 数字学号（扫码登录时已在手可传；账号密码登录可后补，见 [updateStudentId]）
     */
    private fun commitLogin(
        username: String,
        password: String?,
        rememberPassword: Boolean,
        cookies: Map<String, Map<String, String>>,
        studentId: String? = null,
    ) {
        // 每次登录新增独立条目（允许同一登录用户名多个条目，用于多账号切换测试）
        val account = SavedAccount(
            id = UUID.randomUUID().toString(),
            username = username,
            password = if (rememberPassword) password else null,
            lastLoginTime = System.currentTimeMillis(),
            rememberPassword = rememberPassword,
            cookies = cookies,
            studentId = studentId,
        )
        saveAccounts(getAllAccounts() + account)
        AppLog.d("AccountSessionStore", "commitLogin: $username, 记住密码=$rememberPassword, cookie域数=${cookies.size}, studentId=${studentId ?: "-"}")
        activate(account.id)
    }

    /**
     * 统一提交"登录成功后的账号会话"（账密/扫码登录共用入口）：
     * 登录结果收敛为领域值对象 [AuthSessionCommitV2]，由门面 [edu.cqwu.electricity.login.domain.SessionCoordinatorV2.commitAndActivate] 转调。
     */
    fun commitSession(input: AuthSessionCommitV2) {
        commitLogin(
            username = input.username,
            password = input.password,
            rememberPassword = input.rememberPassword,
            cookies = input.cookies,
            studentId = input.studentId,
        )
    }

    /**
     * 回填/更新指定账号的数字学号（登录成功后获取、启动验证回填等场景）。
     */
    fun updateStudentId(accountId: String, studentId: String?) {
        if (studentId.isNullOrBlank()) return
        val accounts = getAllAccounts().toMutableList()
        val idx = accounts.indexOfFirst { it.id == accountId }
        if (idx < 0) return
        if (accounts[idx].studentId == studentId) return
        accounts[idx] = accounts[idx].copy(studentId = studentId)
        saveAccounts(accounts)
        AppLog.d("AccountSessionStore", "回填学号: ${accounts[idx].username} -> $studentId")
    }

    /** 当前激活账号的数字学号（本地读取，零网络） */
    fun getActiveStudentId(): String? = getActiveAccount()?.studentId

    /**
     * 原子激活指定条目（QQ 模式切换）：
     * 1. 清空系统 CookieManager（同步等待完成）
     * 2. 清空 WebView DOM 存储（localStorage 等，避免旧账号网页残留）
     * 3. 将该条目持久化的 cookie 写入系统 CookieManager
     * 4. 持久化当前激活条目 id
     *
     * 调用方应确保在 IO 线程执行（内部有等待系统 cookie 清除完成的操作）。
     */
    fun activate(accountId: String) {
        val account = getAccountById(accountId)
            ?: throw IllegalStateException("账号不存在: $accountId")
        SessionCleaner.clearAll()
        writeCookiesToSystem(account.cookies)
        store().putString(KEY_ACTIVE_ACCOUNT_ID, accountId)
        AppLog.d("AccountSessionStore", "激活账号: ${account.username}, cookie域数=${account.cookies.size}")
    }

    /**
     * 启动时恢复当前账号登录态到系统 CookieManager（幂等，不清除任何数据）。
     * WebKit CookieManager 本身持久化，此处确保激活条目的 cookie 一定存在（例如被系统清理后重建）。
     */
    fun restoreActiveSession() {
        val account = getActiveAccount() ?: return
        if (!account.hasLoginState) return
        writeCookiesToSystem(account.cookies)
        AppLog.d("AccountSessionStore", "启动恢复会话: ${account.username}, cookie域数=${account.cookies.size}")
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
     * 将新 cookie 合并进指定条目的持久化登录状态（WebVPN 自动登录、服务 ticket 交换等场景）。
     */
    fun mergeCookies(accountId: String, cookies: Map<String, Map<String, String>>) {
        if (cookies.isEmpty()) return
        val accounts = getAllAccounts().toMutableList()
        val idx = accounts.indexOfFirst { it.id == accountId }
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
        AppLog.d("AccountSessionStore", "合并 cookie 到 ${old.username}: ${cookies.keys}")
    }

    /**
     * 将系统 CookieManager 中指定域名的 cookie 合并进当前激活条目的持久化登录状态。
     * 用于服务登录成功后把该服务的登录态随账号一起保存。
     */
    fun mergeSystemCookiesForActiveUser(domain: String) {
        val active = getActiveAccount() ?: return
        val cookieString = try {
            CookieManager.getInstance().getCookie(domain)
        } catch (e: Exception) {
            AppLog.w("AccountSessionStore", "读取系统 Cookie 失败，跳过合并: ${e.message}")
            null
        } ?: return
        val parsed = CookieParser.parse(cookieString)
        if (parsed.isEmpty()) return
        mergeCookies(active.id, mapOf(domain to parsed))
    }

    /**
     * Cookie 备份导入：把草稿作为**新条目**追加（重新生成 UUID）。
     *
     * 侵入最小化：不激活任何条目、不改动系统 CookieManager、
     * 不联网校验；密码字段强制为空（备份不含密码）。用户可在账号/登录设置页手动切换。
     */
    fun importAccounts(drafts: List<SavedAccount>) {
        if (drafts.isEmpty()) return
        val imported = drafts.map { draft ->
            draft.copy(
                id = UUID.randomUUID().toString(),
                password = null,
                rememberPassword = false,
            )
        }
        saveAccounts(getAllAccounts() + imported)
        AppLog.d("AccountSessionStore", "导入 Cookie 备份账号数: ${imported.size}")
    }

    /**
     * 账密凭据导入：把草稿作为**新条目**追加（重新生成 UUID）。
     *
     * 与 [importAccounts]（Cookie 备份，密码恒空）的区别：保留草稿的密码与
     * [SavedAccount.rememberPassword]（账密加密文件含密码）。同样不激活任何条目、
     * 不改动系统 CookieManager——用户之后在账号/登录设置页手动切换登录。
     */
    fun importCredentials(drafts: List<SavedAccount>) {
        if (drafts.isEmpty()) return
        val imported = drafts.map { it.copy(id = UUID.randomUUID().toString()) }
        saveAccounts(getAllAccounts() + imported)
        AppLog.d("AccountSessionStore", "导入账密账号数: ${imported.size}")
    }

    // ═══════════════════════════════════════════
    //  删除 / 清除
    // ═══════════════════════════════════════════

    /**
     * 删除账号条目：删除持久化记录（id + 登录用户名 + 密码 + 登录状态）。
     * 若删除的是当前激活条目，同时清空系统登录态（cookie + WebView 存储），回到未登录。
     * 退出登录 API 的调用由调用方在调用本方法前完成（见 [LogoutApi]）。
     */
    fun deleteAccount(accountId: String) {
        val accounts = getAllAccounts().filterNot { it.id == accountId }
        saveAccounts(accounts)
        if (getActiveAccount()?.id == accountId) {
            SessionCleaner.clearAll()
            store().remove(KEY_ACTIVE_ACCOUNT_ID)
        }
        AppLog.d("AccountSessionStore", "删除账号: $accountId")
    }

    /** 清空所有账号数据与登录态（设置页"清除存储空间"用）。 */
    fun clearAllData() {
        SessionCleaner.clearAll()
        store().clear()
        AppLog.d("AccountSessionStore", "清空全部账号数据")
    }

    /**
     * 清除所有账号的登录状态（cookie），保留账号（登录用户名）和密码。
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
            obj.put("i", a.id)
            obj.put("u", a.username)
            a.password?.let { obj.put("p", it) }
            obj.put("t", a.lastLoginTime)
            obj.put("r", a.rememberPassword)
            a.studentId?.let { obj.put("s", it) }
            val cookiesObj = JSONObject()
            for ((domain, kv) in a.cookies) {
                val kvObj = JSONObject()
                for ((name, value) in kv) kvObj.put(name, value)
                cookiesObj.put(domain, kvObj)
            }
            obj.put("c", cookiesObj)
            arr.put(obj)
        }
        store().putString(KEY_ACCOUNTS, arr.toString())
    }

    private fun parseAccount(obj: JSONObject): SavedAccount? {
        return try {
            // 无 id 的旧格式条目直接丢弃（测试版不迁移旧数据）
            val id = obj.optString("i").takeIf { it.isNotBlank() } ?: return null
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
                id = id,
                username = obj.getString("u"),
                password = if (obj.has("p")) obj.getString("p") else null,
                lastLoginTime = if (obj.has("t")) obj.getLong("t") else 0,
                rememberPassword = if (obj.has("r")) obj.getBoolean("r") else true,
                cookies = cookies,
                studentId = if (obj.has("s")) obj.getString("s") else null,
            )
        } catch (e: Exception) {
            AppLog.w("AccountSessionStore", "解析账号条目失败: ${e.message}")
            null
        }
    }
}
