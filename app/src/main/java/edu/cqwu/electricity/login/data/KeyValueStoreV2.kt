package edu.cqwu.electricity.login.data

import android.content.SharedPreferences

/**
 * 账号会话持久化介质（窄接口）。
 *
 * 让 [AccountSessionStore] 的落盘细节可替换、可在 JVM 单测中注入内存实现；
 * 生产环境使用 [SharedPrefsStoreV2]（底层为 EncryptedSharedPreferences）。
 */
internal interface KeyValueStoreV2 {
    fun getString(key: String): String?

    fun putString(key: String, value: String)

    fun remove(key: String)

    fun clear()
}

/** SharedPreferences（含 EncryptedSharedPreferences）适配实现 */
internal class SharedPrefsStoreV2(
    private val prefs: SharedPreferences,
) : KeyValueStoreV2 {
    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }
}
