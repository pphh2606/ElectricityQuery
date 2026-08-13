package edu.cqwu.electricity.settings.data

/** 二维码颜色模式 */
enum class QrCodeColorMode(val value: String) {
    THEME_SNAKE("theme_snake"),
    MONOCHROME("monochrome");

    companion object {
        fun fromValue(value: String): QrCodeColorMode {
            return entries.firstOrNull { it.value == value } ?: MONOCHROME
        }
    }
}
