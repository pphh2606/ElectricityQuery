package edu.cqwu.electricity.settings.data

import android.app.LocaleManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi

/**
 * 应用设置持久化存储入口。
 * 通用设置通过 [SettingKey] 读写，语言切换保留平台专用逻辑。
 */
class SettingsPreferences(
    private val prefs: SharedPreferences,
    private val appContext: Context? = null,
) {

    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE),
        context.applicationContext,
    )

    fun <T> get(key: SettingKey<T>): T = key.read(prefs)

    fun <T> set(key: SettingKey<T>, value: T) {
        val editor = prefs.edit()
        key.write(editor, value)
        editor.apply()
    }

    // ── 应用语言 ──

    fun getAppLanguage(): AppLanguage {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getPlatformAppLanguage()
        } else {
            val raw = prefs.getString(KEY_APP_LANGUAGE, AppLanguage.SYSTEM.value)
                ?: AppLanguage.SYSTEM.value
            AppLanguage.fromValue(raw)
        }
    }

    fun setAppLanguage(language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setPlatformAppLanguage(language)
        } else {
            prefs.edit().putString(KEY_APP_LANGUAGE, language.value).apply()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun getPlatformAppLanguage(): AppLanguage {
        val context = appContext ?: return AppLanguage.SYSTEM
        val localeManager = context.getSystemService(LocaleManager::class.java)
        val applicationLocales = localeManager.applicationLocales
        return if (applicationLocales.isEmpty) {
            AppLanguage.SYSTEM
        } else {
            AppLanguage.fromLanguageTag(applicationLocales[0].toLanguageTag())
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun setPlatformAppLanguage(language: AppLanguage) {
        val context = appContext ?: return
        val localeManager = context.getSystemService(LocaleManager::class.java)
        val localeList = language.localeTag?.let { LocaleList.forLanguageTags(it) }
            ?: LocaleList.getEmptyLocaleList()
        localeManager.applicationLocales = localeList
    }

    companion object {
        private const val PREF_NAME = "settings_preferences"
        private const val KEY_APP_LANGUAGE = "app_language"
    }
}

