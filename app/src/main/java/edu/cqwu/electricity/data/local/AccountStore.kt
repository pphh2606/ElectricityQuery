package edu.cqwu.electricity.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

/**
 * 本地存储的账号信息：学号、密码、最后登录时间、记住密码标志
 */
data class SavedAccountInfo(
    val username: String,
    val password: String? = null,
    val lastLoginTime: Long = 0,
    val rememberPassword: Boolean = true
)

/**
 * 多账号持久化存储（加密版，单一数据源）。
 *
 * 使用 EncryptedSharedPreferences 对账号密码进行 AES-256 加密存储，
 * 密钥由 Android Keystore 自动管理。
 *
 * 设计原则：
 * - 密码始终存储在本地（由 EncryptedSharedPreferences 加密保护）
 * - 每个账号独立存储"记住密码"标志
 * - ViewModel 层根据标志决定是否将密码展示到 UI
 * - 导出凭据时按标志过滤
 */
@Suppress("DEPRECATION")
class AccountStore(context: Context) {

    @Suppress("DEPRECATION")
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
                        lastLoginTime = if (obj.has("t")) obj.getLong("t") else 0,
                        rememberPassword = if (obj.has("r")) obj.getBoolean("r") else true
                    )
                )
            }
            list.sortedByDescending { it.lastLoginTime }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 获取所有勾选了"记住密码"且有密码的账号。
     * 用于凭据导出。
     */
    fun getAllAccountsWithPassword(): List<Pair<String, String>> {
        return getAllAccounts()
            .filter { it.rememberPassword && it.password != null }
            .map { it.username to it.password!! }
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
     * 保存/更新账号信息。
     *
     * 密码始终存储（由 EncryptedSharedPreferences 加密保护）。
     * rememberPassword 标志控制 ViewModel 是否将密码展示到 UI。
     *
     * @param username 学号
     * @param password 密码（始终存储）
     * @param rememberPassword 是否记住密码（存储到每账号的 r 字段）
     */
    fun saveAccount(username: String, password: String?, rememberPassword: Boolean) {
        val json = prefs.getString(KEY_ACCOUNTS, null)
        val arr = if (json != null) try { JSONArray(json) } catch (e: Exception) { JSONArray() } else JSONArray()

        // 查找是否已存在该用户
        var found = false
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.getString("u") == username) {
                obj.put("t", System.currentTimeMillis())
                obj.put("r", rememberPassword)
                if (password != null) {
                    obj.put("p", password)
                }
                found = true
                break
            }
        }

        // 不存在则新增
        if (!found) {
            val newObj = JSONObject()
            newObj.put("u", username)
            if (password != null) {
                newObj.put("p", password)
            }
            newObj.put("t", System.currentTimeMillis())
            newObj.put("r", rememberPassword)
            arr.put(newObj)
        }

        prefs.edit().putString(KEY_ACCOUNTS, arr.toString()).apply()
    }

    /**
     * 更新指定账号的"记住密码"标志（不修改密码）。
     */
    fun setRememberPasswordForAccount(username: String, remember: Boolean) {
        val json = prefs.getString(KEY_ACCOUNTS, null) ?: return
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (obj.getString("u") == username) {
                    obj.put("r", remember)
                    break
                }
            }
            prefs.edit().putString(KEY_ACCOUNTS, arr.toString()).apply()
        } catch (e: Exception) {
            // ignore
        }
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

    companion object {
        private const val PREF_NAME = "account_store_encrypted"
        private const val KEY_ACCOUNTS = "saved_accounts"
    }
}
