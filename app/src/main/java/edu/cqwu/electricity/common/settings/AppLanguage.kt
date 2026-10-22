package edu.cqwu.electricity.common.settings

import android.content.Context
import androidx.annotation.StringRes
import edu.cqwu.electricity.R
import java.util.Locale

/**
 * 应用语言枚举
 * SYSTEM: 跟随系统
 * CHINESE: 中文
 * ENGLISH: 英文
 */
enum class AppLanguage(
    val value: String,
    @StringRes val labelRes: Int,
    val nativeName: String,
) {
    SYSTEM("system", R.string.language_system, ""),
    CHINESE("zh", R.string.settings_language_zh, "简体中文"),
    TRADITIONAL_CHINESE("zh-TW", R.string.settings_language_zh_tw, "繁體中文"),
    ENGLISH("en", R.string.settings_language_en, "English"),
    FRENCH("fr", R.string.settings_language_fr, "Français"),
    ARABIC("ar", R.string.settings_language_ar, "العربية"),
    JAPANESE("ja", R.string.settings_language_ja, "日本語");

    val localeTag: String?
        get() = when (this) {
            SYSTEM -> null
            CHINESE -> "zh-CN"
            TRADITIONAL_CHINESE -> "zh-TW"
            ENGLISH -> "en"
            FRENCH -> "fr"
            ARABIC -> "ar"
            JAPANESE -> "ja"
        }

    companion object {
        /** i18n 映射文件里简体中文不需要映射段，直接显示 JSON 原文 */
        private const val SIMPLIFIED_CHINESE_TAG = "zh-CN"

        /**
         * 当前语言在 i18n 映射文件里的键；简体中文返回 null，表示直接显示 JSON 原文。
         *
         * Android 13 起应用语言由平台 per-app language 管理，不再走 attachBaseContext 包装，
         * 因此不能只看 [Locale.getDefault]，要以 [SettingsPreferences] 为准；
         * 只有「跟随系统」时才回退到系统语言。首页与大厅共用这一份实现，避免两处漂移。
         */
        fun currentI18nKey(context: Context): String? {
            val selected = SettingsPreferences(context).getAppLanguage()
            val language = if (selected == SYSTEM) {
                fromLanguageTag(Locale.getDefault().toLanguageTag())
            } else {
                selected
            }
            return language.localeTag?.takeIf { it != SIMPLIFIED_CHINESE_TAG }
        }

        fun fromValue(value: String): AppLanguage {
            return entries.firstOrNull { it.value == value } ?: SYSTEM
        }

        fun fromLanguageTag(tag: String): AppLanguage {
            val locale = Locale.forLanguageTag(tag)
            return when (locale.language) {
                "zh" -> {
                    if (locale.country == "TW" || locale.script == "Hant" || tag.contains("TW")) {
                        TRADITIONAL_CHINESE
                    } else {
                        CHINESE
                    }
                }
                "en" -> ENGLISH
                "fr" -> FRENCH
                "ar" -> ARABIC
                "ja" -> JAPANESE
                else -> SYSTEM
            }
        }
    }
}
