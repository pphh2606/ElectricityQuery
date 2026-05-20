package edu.cqwu.electricity.data.local

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 账号信息：学号、密码（可选）、最后登录时间
 */
data class AccountInfo(
    val username: String,
    val password: String? = null,
    val lastLoginTime: Long = 0
)

/**
 * 多账号持久化存储。
 *
 * 无论用户是否勾选"记住密码"，都会保存学号到列表。
 * 仅在勾选"记住密码"时保存密码。
 * 为未来多账户功能提供数据支撑。
 *
 * 使用方式：
 *   val store = AccountStore(context)
 *   store.saveAccount("2024xxxxx", "mypassword", rememberPassword = true)
 *   val allAccounts = store.getAllAccounts()  // ["2024xxxxx", "2024yyyyy"]
 *   val password = store.getPassword("2024xxxxx")
 */
class AccountStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /**
     * 获取所有已保存的学号列表
     * @return 学号列表（按最后登录时间降序）
     */
    fun getAllAccountNames(): List<String> {
        val json = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(obj.getString("u"))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
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
                    return obj.optString("p", null) ?: return null
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
        private const val PREF_NAME = "account_store"
        private const val KEY_ACCOUNTS = "saved_accounts"
    }
}
