package edu.cqwu.electricity.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

/**
 * 本地存储的账号信息：学号、密码（可选）、最后登录时间
 * 注意：与 [edu.cqwu.electricity.data.model.AccountInfo]（EPay 账户信息）不同，
 * 此类仅用于本地存储的多账号管理。
 */
data class SavedAccountInfo(
    val username: String,
    val password: String? = null,
    val lastLoginTime: Long = 0
)

/**
 * 多账号持久化存储（加密版，单一数据源）。
 *
 * 使用 EncryptedSharedPreferences 对账号密码进行 AES-256 加密存储，
 * 密钥由 Android Keystore 自动管理。
 *
 * 职责：
 * - 多账号列表（含密码、最后登录时间）
 * - "记住密码" 复选框状态（独立 key）
 *
 * 无论用户是否勾选"记住密码"，都会保存学号到列表。
 * 仅在勾选"记住密码"时保存密码。
 *
 * 使用方式：
 *   val store = AccountStore(context)
 *   store.saveAccount("2024xxxxx", "mypassword", rememberPassword = true)
 *   val allAccounts = store.getAllAccounts()  // 按时间降序
 *   val password = store.getPassword("2024xxxxx")
 */
class AccountStore(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREF_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // ================================================================
    // 记住密码复选框状态
    // ================================================================

    fun getRememberPassword(): Boolean {
        return prefs.getBoolean(KEY_REMEMBER_PASSWORD, true)
    }

    fun setRememberPassword(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REMEMBER_PASSWORD, enabled).apply()
    }

    // ================================================================
    // 多账号管理
    // ================================================================

    /**
     * 获取所有已保存的学号列表
     * @return 学号列表（按最后登录时间降序）
     */
    fun getAllAccountNames(): List<String> {
        return getAllAccounts().map { it.username }
    }

    /**
     * 获取按最后登录时间排序的完整账号列表。
     * 最近登录的排在最前面。
     */
    fun getAllAccounts(): List<SavedAccountInfo> {
        val json = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<SavedAccountInfo>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    SavedAccountInfo(
                        username = obj.getString("u"),
                        password = if (obj.has("p")) obj.getString("p") else null,
                        lastLoginTime = if (obj.has("t")) obj.getLong("t") else 0
                    )
                )
            }
            list.sortedByDescending { it.lastLoginTime }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 批量获取所有有密码的账号（只解析一次 JSON，避免 O(n²) 重复解析）。
     */
    fun getAllAccountsWithPassword(): List<Pair<String, String>> {
        return getAllAccounts()
            .mapNotNull { acc -> acc.password?.let { acc.username to it } }
    }

    /**
     * 获取指定用户的密码
     * @return 密码，如果未保存密码则返回 null
     */
    fun getPassword(username: String): String? {
        val json = prefs.getString(KEY_ACCOUNTS, null) ?: return null
        return try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (obj.getString("u") == username) {
                    return if (obj.has("p")) obj.getString("p") else null
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 保存/更新账号信息
     * - 始终保存学号到列表
     * - 仅在 rememberPassword 为 true 时保存密码
     *
     * @param username 学号
     * @param password 密码（仅在 rememberPassword 时持久化）
     * @param rememberPassword 是否记住密码
     */
    fun saveAccount(username: String, password: String?, rememberPassword: Boolean) {
        val json = prefs.getString(KEY_ACCOUNTS, null)
        val arr = if (json != null) try { JSONArray(json) } catch (e: Exception) { JSONArray() } else JSONArray()

        // 查找是否已存在该用户
        var found = false
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.getString("u") == username) {
                // 更新：始终更新时间，密码按 rememberPassword 决定
                obj.put("t", System.currentTimeMillis())
                if (rememberPassword && password != null) {
                    obj.put("p", password)
                } else if (!rememberPassword) {
                    obj.remove("p")
                }
                found = true
                break
            }
        }

        // 不存在则新增
        if (!found) {
            val newObj = JSONObject()
            newObj.put("u", username)
            if (rememberPassword && password != null) {
                newObj.put("p", password)
            }
            newObj.put("t", System.currentTimeMillis())
            arr.put(newObj)
        }

        prefs.edit().putString(KEY_ACCOUNTS, arr.toString()).apply()
    }

    /**
     * 删除指定账号
     */
    fun removeAccount(username: String) {
        val json = prefs.getString(KEY_ACCOUNTS, null) ?: return
        try {
            val arr = JSONArray(json)
            val newArr = JSONArray()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (obj.getString("u") != username) {
                    newArr.put(obj)
                }
            }
            prefs.edit().putString(KEY_ACCOUNTS, newArr.toString()).apply()
        } catch (e: Exception) {
            // ignore
        }
    }

    /**
     * 清除所有账号
     */
    fun clearAll() {
        prefs.edit().remove(KEY_ACCOUNTS).apply()
    }

    companion object {
        /**
         * 新的加密 SP 文件名。
         * 与旧明文文件 `account_store` 不同，确保隔离。
         */
        private const val PREF_NAME = "account_store_encrypted"
        private const val KEY_ACCOUNTS = "saved_accounts"
        private const val KEY_REMEMBER_PASSWORD = "remember_password"
    }
}
