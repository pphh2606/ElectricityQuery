package edu.cqwu.electricity.settings.ui

import androidx.compose.ui.graphics.Color

/**
 * 预设种子色。
 *
 * 选中后写入 [edu.cqwu.electricity.common.settings.ThemeColorSource.Custom]，
 * 由 material-kolor 按当前风格/规范生成整套色板。
 */
data class PresetColor(val key: String, val color: Color)

val PresetColors: List<PresetColor> = listOf(
    PresetColor("brand", Color(0xFF66CCFF)),
    PresetColor("pink", Color(0xFFB94073)),
    PresetColor("red", Color(0xFFBA1A1A)),
    PresetColor("orange", Color(0xFF944A00)),
    PresetColor("amber", Color(0xFF8C5300)),
    PresetColor("yellow", Color(0xFF795900)),
    PresetColor("lime", Color(0xFF5E6400)),
    PresetColor("green", Color(0xFF006D39)),
    PresetColor("cyan", Color(0xFF006A64)),
    PresetColor("teal", Color(0xFF006874)),
    PresetColor("light_blue", Color(0xFF00639B)),
    PresetColor("blue", Color(0xFF335BBC)),
    PresetColor("indigo", Color(0xFF5355A9)),
    PresetColor("purple", Color(0xFF6750A4)),
    PresetColor("deep_purple", Color(0xFF7E42A4)),
    PresetColor("blue_grey", Color(0xFF575D7E)),
    PresetColor("brown", Color(0xFF7D524A)),
    PresetColor("grey", Color(0xFF5F6162)),
)
