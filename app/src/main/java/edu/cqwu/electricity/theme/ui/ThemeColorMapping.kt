package edu.cqwu.electricity.theme.ui

import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import edu.cqwu.electricity.common.settings.ThemeColorSpec
import edu.cqwu.electricity.common.settings.ThemePaletteStyle
import top.yukonga.miuix.kmp.theme.ThemeColorSpec as MiuixColorSpec
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle as MiuixPaletteStyle

/**
 * 本项目设置里的调色板风格 / 配色规范，分别映射到 material-kolor 与 miuix 两套枚举。
 *
 * 为什么要有这层：两套库的枚举常量拼写不同（material-kolor 用 `TonalSpot` 等类对象、
 * miuix 用同名枚举），设置层只认识本项目自己的 [ThemePaletteStyle] / [ThemeColorSpec]。
 */

internal fun ThemePaletteStyle.toMaterialKolor(): PaletteStyle = when (this) {
    ThemePaletteStyle.TONAL_SPOT -> PaletteStyle.TonalSpot
    ThemePaletteStyle.NEUTRAL -> PaletteStyle.Neutral
    ThemePaletteStyle.VIBRANT -> PaletteStyle.Vibrant
    ThemePaletteStyle.EXPRESSIVE -> PaletteStyle.Expressive
    ThemePaletteStyle.RAINBOW -> PaletteStyle.Rainbow
    ThemePaletteStyle.FRUIT_SALAD -> PaletteStyle.FruitSalad
    ThemePaletteStyle.MONOCHROME -> PaletteStyle.Monochrome
    ThemePaletteStyle.FIDELITY -> PaletteStyle.Fidelity
    ThemePaletteStyle.CONTENT -> PaletteStyle.Content
}

internal fun ThemeColorSpec.toMaterialKolor(): ColorSpec.SpecVersion = when (this) {
    ThemeColorSpec.SPEC_2021 -> ColorSpec.SpecVersion.SPEC_2021
    ThemeColorSpec.SPEC_2025 -> ColorSpec.SpecVersion.SPEC_2025
}

internal fun ThemePaletteStyle.toMiuix(): MiuixPaletteStyle = when (this) {
    ThemePaletteStyle.TONAL_SPOT -> MiuixPaletteStyle.TonalSpot
    ThemePaletteStyle.NEUTRAL -> MiuixPaletteStyle.Neutral
    ThemePaletteStyle.VIBRANT -> MiuixPaletteStyle.Vibrant
    ThemePaletteStyle.EXPRESSIVE -> MiuixPaletteStyle.Expressive
    ThemePaletteStyle.RAINBOW -> MiuixPaletteStyle.Rainbow
    ThemePaletteStyle.FRUIT_SALAD -> MiuixPaletteStyle.FruitSalad
    ThemePaletteStyle.MONOCHROME -> MiuixPaletteStyle.Monochrome
    ThemePaletteStyle.FIDELITY -> MiuixPaletteStyle.Fidelity
    ThemePaletteStyle.CONTENT -> MiuixPaletteStyle.Content
}

internal fun ThemeColorSpec.toMiuix(): MiuixColorSpec = when (this) {
    ThemeColorSpec.SPEC_2021 -> MiuixColorSpec.Spec2021
    ThemeColorSpec.SPEC_2025 -> MiuixColorSpec.Spec2025
}
