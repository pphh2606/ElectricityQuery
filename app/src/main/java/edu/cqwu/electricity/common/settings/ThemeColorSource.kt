package edu.cqwu.electricity.common.settings

import androidx.compose.ui.graphics.Color

/** 主题颜色源 */
sealed interface ThemeColorSource {
    data object SystemDynamic : ThemeColorSource
    data class Custom(val seedColor: Color) : ThemeColorSource
}
