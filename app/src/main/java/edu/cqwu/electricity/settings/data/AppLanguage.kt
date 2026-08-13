package edu.cqwu.electricity.settings.data

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
