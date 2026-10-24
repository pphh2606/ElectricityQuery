package edu.cqwu.electricity.common.settings

import androidx.annotation.StringRes
import edu.cqwu.electricity.R

/**
 * Material 3 配色规范版本（对应 material-kolor 的 ColorSpec.SpecVersion）。
 *
 * - [SPEC_2021]：Material 3 初版规范，配色更克制、层级对比更弱；
 * - [SPEC_2025]：Material 3 Expressive 规范，主色更饱和、表面层级对比更强。
 */
enum class ThemeColorSpec(
    val value: String,
    @param:StringRes val labelRes: Int,
) {
    SPEC_2021("SPEC_2021", R.string.color_spec_2021),
    SPEC_2025("SPEC_2025", R.string.color_spec_2025),
    ;

    companion object {
        fun fromValue(raw: String?): ThemeColorSpec =
            entries.firstOrNull { it.value == raw } ?: SPEC_2025
    }
}
