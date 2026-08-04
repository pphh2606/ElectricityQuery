package edu.cqwu.electricity.theme.ui

import androidx.compose.ui.graphics.Color
import com.materialkolor.dynamicColorScheme

/**
 * 基于种子色生成 Material3 ColorScheme
 * 使用 MaterialKolor 库（基于 material-color-utilities / HCT 色彩空间），
 * 确保色彩方案与 Material You / Dynamic Color 一致。
 */
fun generateColorSchemeFromSeed(seedColor: Color, darkTheme: Boolean): androidx.compose.material3.ColorScheme {
    return dynamicColorScheme(
        seedColor = seedColor,
        isDark = darkTheme,
    )
}
