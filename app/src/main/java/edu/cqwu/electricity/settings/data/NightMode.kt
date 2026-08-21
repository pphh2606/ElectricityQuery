package edu.cqwu.electricity.settings.data

import androidx.annotation.StringRes
import edu.cqwu.electricity.R

/** 夜间模式枚举 */
enum class NightMode(val value: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromValue(value: String): NightMode {
            return entries.firstOrNull { it.value == value } ?: SYSTEM
        }
    }
}

/** 夜间模式的显示资源 */
@get:StringRes
val NightMode.labelRes: Int
    get() = when (this) {
        NightMode.SYSTEM -> R.string.settings_night_mode_system
        NightMode.LIGHT -> R.string.settings_night_mode_light
        NightMode.DARK -> R.string.settings_night_mode_dark
    }

/** 根据系统深浅解析该模式最终是否为深色。 */
fun NightMode.isDark(systemDark: Boolean): Boolean = when (this) {
    NightMode.SYSTEM -> systemDark
    NightMode.LIGHT -> false
    NightMode.DARK -> true
}
