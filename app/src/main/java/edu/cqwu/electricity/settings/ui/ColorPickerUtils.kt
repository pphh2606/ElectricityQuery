package edu.cqwu.electricity.settings.ui

import androidx.compose.ui.graphics.Color
import java.util.Locale
import kotlin.math.roundToInt

internal data class HsvColor(
    val hue: Float,
    val saturation: Float,
    val value: Float,
)

internal fun Color.toHsv(): HsvColor {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val delta = max - min

    val hue = when {
        delta == 0f -> 0f
        max == red -> 60f * (((green - blue) / delta) % 6f)
        max == green -> 60f * (((blue - red) / delta) + 2f)
        else -> 60f * (((red - green) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }

    return HsvColor(
        hue = hue,
        saturation = if (max == 0f) 0f else delta / max,
        value = max,
    )
}

internal fun Color.toHex(): String {
    val red = (this.red * 255f).roundToInt().coerceIn(0, 255)
    val green = (this.green * 255f).roundToInt().coerceIn(0, 255)
    val blue = (this.blue * 255f).roundToInt().coerceIn(0, 255)
    return String.format(Locale.US, "#%02X%02X%02X", red, green, blue)
}

internal fun parseHexColor(input: String): Color? {
    val normalized = input.trim().removePrefix("#")
    if (normalized.length != 6) return null
    val rgb = normalized.toIntOrNull(16) ?: return null
    return Color(
        red = ((rgb shr 16) and 0xFF) / 255f,
        green = ((rgb shr 8) and 0xFF) / 255f,
        blue = (rgb and 0xFF) / 255f,
    )
}
