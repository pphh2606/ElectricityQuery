package edu.cqwu.electricity.common.settings

import androidx.annotation.StringRes
import dev.chrisbanes.haze.HazePerformanceMode
import edu.cqwu.electricity.R

/**
 * 毛玻璃的采样性能档位，直接映射到 Haze 的 [HazePerformanceMode]。
 *
 * 标题栏模糊与弹窗模糊的高级设置共用；只在高级里出现，普通用户看不到，默认 [DEFAULT]。
 */
enum class BlurPerformanceMode(
    val value: String,
    @param:StringRes val labelRes: Int,
) {
    DEFAULT("default", R.string.advanced_blur_performance_default),
    QUALITY("quality", R.string.advanced_blur_performance_quality),
    BALANCED("balanced", R.string.advanced_blur_performance_balanced),
    PERFORMANCE("performance", R.string.advanced_blur_performance_performance),
    ;

    /** 转成 Haze 的枚举 */
    fun toHaze(): HazePerformanceMode = when (this) {
        DEFAULT -> HazePerformanceMode.Default
        QUALITY -> HazePerformanceMode.Quality
        BALANCED -> HazePerformanceMode.Balanced
        PERFORMANCE -> HazePerformanceMode.Performance
    }

    companion object {
        fun fromValue(value: String): BlurPerformanceMode =
            entries.firstOrNull { it.value == value } ?: DEFAULT
    }
}
