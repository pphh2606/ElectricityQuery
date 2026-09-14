package edu.cqwu.electricity.common.ui

import android.os.Build
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import dev.chrisbanes.haze.HazeDefaults

/**
 * 弹窗（底部弹窗 / 加载弹窗）的可见性状态：有多少个弹窗打开、背景模糊进度多少。
 *
 * 从 `theme/ui/Theme.kt` 拆出：它描述的是"弹窗开着没、背景糊到什么程度"，属于通用 UI 基础设施，
 * 与主题配色无关。提供方是 `app/AppShell`，消费方是本包下的弹窗组件
 * （BottomSheetDialog / LoadingDialog / AppScaledWindows）。
 */
@Stable
class SheetVisibilityState {
    private val openCount = mutableIntStateOf(0)
    private val blurProgressState = mutableFloatStateOf(0f)

    val active: Boolean get() = openCount.value > 0

    var blurProgress: Float
        get() = blurProgressState.value
        set(value) {
            blurProgressState.value = value.coerceIn(0f, 1f)
        }

    fun open() {
        openCount.value++
    }

    fun close() {
        openCount.value = (openCount.value - 1).coerceAtLeast(0)
    }
}

val LocalSheetVisibilityState = staticCompositionLocalOf { SheetVisibilityState() }

/** 当前设备是否支持 Haze 背景模糊（Android 12+ 且系统未关闭模糊） */
fun isHazeBlurSupported(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && HazeDefaults.blurEnabled()
