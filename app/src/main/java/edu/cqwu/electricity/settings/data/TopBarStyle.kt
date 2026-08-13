package edu.cqwu.electricity.settings.data

import androidx.annotation.StringRes
import edu.cqwu.electricity.R

/** 标题栏颜色样式 */
enum class TopBarStyle(val value: String, @StringRes val labelRes: Int) {
    SURFACE("surface", R.string.settings_topbar_surface),
    SURFACE_CONTAINER_LOWEST("surface_container_lowest", R.string.settings_topbar_surface_container_lowest),
    SURFACE_CONTAINER_LOW("surface_container_low", R.string.settings_topbar_surface_container_low),
    SURFACE_CONTAINER("surface_container", R.string.settings_topbar_surface_container),
    SURFACE_CONTAINER_HIGH("surface_container_high", R.string.settings_topbar_surface_container_high),
    SURFACE_CONTAINER_HIGHEST("surface_container_highest", R.string.settings_topbar_surface_container_highest),
    SURFACE_VARIANT("surface_variant", R.string.settings_topbar_surface_variant);

    companion object {
        fun fromValue(value: String): TopBarStyle {
            return entries.firstOrNull { it.value == value } ?: SURFACE
        }
    }
}
