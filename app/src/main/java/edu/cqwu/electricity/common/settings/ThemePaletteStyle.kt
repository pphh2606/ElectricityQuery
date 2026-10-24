package edu.cqwu.electricity.common.settings

import androidx.annotation.StringRes
import edu.cqwu.electricity.R

/**
 * Material 3 Expressive 的调色板风格（对应 material-kolor 的 PaletteStyle）。
 *
 * 只保留 Material 3 主题会用到的基础风格；Cmf 需要额外的三级种子色，等有需求再加。
 */
enum class ThemePaletteStyle(
    val value: String,
    @param:StringRes val labelRes: Int,
) {
    TONAL_SPOT("TonalSpot", R.string.palette_style_tonal_spot),
    NEUTRAL("Neutral", R.string.palette_style_neutral),
    VIBRANT("Vibrant", R.string.palette_style_vibrant),
    EXPRESSIVE("Expressive", R.string.palette_style_expressive),
    RAINBOW("Rainbow", R.string.palette_style_rainbow),
    FRUIT_SALAD("FruitSalad", R.string.palette_style_fruit_salad),
    MONOCHROME("Monochrome", R.string.palette_style_monochrome),
    FIDELITY("Fidelity", R.string.palette_style_fidelity),
    CONTENT("Content", R.string.palette_style_content),
    ;

    companion object {
        fun fromValue(raw: String?): ThemePaletteStyle =
            entries.firstOrNull { it.value == raw } ?: TONAL_SPOT
    }
}
