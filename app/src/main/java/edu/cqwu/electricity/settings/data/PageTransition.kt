package edu.cqwu.electricity.settings.data

import androidx.annotation.StringRes
import edu.cqwu.electricity.R

/** 页面过渡动画类型 */
enum class PageTransition(val value: String, @StringRes val labelRes: Int) {
    NONE("none", R.string.settings_page_transition_none),
    SLIDE("slide", R.string.settings_page_transition_slide),
    SLIDE_VERTICAL("slide_vertical", R.string.settings_page_transition_slide_vertical),
    FADE("fade", R.string.settings_page_transition_fade),
    FADE_SCALE("fade_scale", R.string.settings_page_transition_fade_scale),
    CUPERTINO("cupertino", R.string.settings_page_transition_cupertino);

    companion object {
        fun fromValue(value: String): PageTransition {
            return entries.firstOrNull { it.value == value } ?: SLIDE
        }
    }
}
