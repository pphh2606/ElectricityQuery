package edu.cqwu.electricity.settings.data

import androidx.annotation.StringRes
import edu.cqwu.electricity.R

/** 减少动画模式 */
enum class ReduceMotion(val value: String, @StringRes val labelRes: Int) {
    SYSTEM("system", R.string.settings_reduce_motion_system),
    ON("on", R.string.settings_reduce_motion_on),
    OFF("off", R.string.settings_reduce_motion_off);

    companion object {
        fun fromValue(value: String): ReduceMotion {
            return entries.firstOrNull { it.value == value } ?: SYSTEM
        }
    }
}
