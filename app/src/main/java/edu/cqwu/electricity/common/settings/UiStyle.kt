package edu.cqwu.electricity.common.settings

import androidx.annotation.StringRes
import edu.cqwu.electricity.R

/**
 * 界面风格：Material 3 与 MIUI。
 *
 * 目前只影响主题配色（以及 miuix 组件预览页可用），
 * 页面组件与布局仍是同一套 Material 3 实现。
 */
enum class UiStyle(val value: String, @StringRes val labelRes: Int) {
    MATERIAL3("material3", R.string.ui_style_material3),
    MIUI("miui", R.string.ui_style_miui);

    companion object {
        fun fromValue(value: String): UiStyle {
            return entries.firstOrNull { it.value == value } ?: MATERIAL3
        }
    }
}
