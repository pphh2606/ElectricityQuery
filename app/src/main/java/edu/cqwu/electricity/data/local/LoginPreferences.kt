package edu.cqwu.electricity.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * 登录偏好存储
 * 用于"记住密码"功能的持久化
 */
class LoginPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /**
     * 保存账号密码（记住密码）
     */
    fun saveCredentials(username: String, password: String) {
        prefs.edit()
            .putBoolean(KEY_REMEMBER, true)
            .putString(KEY_USERNAME, username)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    /**
     * 加载已保存的账号密码
     * @return Pair(username, password) 或 null
     */
    fun loadCredentials(): Pair<String, String>? {
        if (!isRememberEnabled()) return null
        val username = prefs.getString(KEY_USERNAME, null) ?: return null
        val password = prefs.getString(KEY_PASSWORD, null) ?: return null
        return Pair(username, password)
    }

    /**
     * 清除已保存的账号密码
     */
    fun clearCredentials() {
        prefs.edit()
            .putBoolean(KEY_REMEMBER, false)
            .remove(KEY_USERNAME)
            .remove(KEY_PASSWORD)
            .apply()
    }

    /**
     * 是否开启记住密码
     */
    fun isRememberEnabled(): Boolean {
        return prefs.getBoolean(KEY_REMEMBER, false)
    }

    /**
     * 更新记住密码复选框状态（不修改已有凭据）
     */
    fun setRememberEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REMEMBER, enabled).apply()
        if (!enabled) {
            clearCredentials()
        }
    }

    companion object {
        private const val PREF_NAME = "login_preferences"
        private const val KEY_REMEMBER = "remember_me"
        private const val KEY_USERNAME = "saved_username"
        private const val KEY_PASSWORD = "saved_password"
    }
}
