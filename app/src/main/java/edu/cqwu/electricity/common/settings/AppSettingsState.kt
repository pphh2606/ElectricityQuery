package edu.cqwu.electricity.common.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import edu.cqwu.electricity.logging.AppLog
import edu.cqwu.electricity.logging.LogLevel

const val MIN_SHEET_BLUR_RADIUS = 0f
const val MAX_SHEET_BLUR_RADIUS = 40f
const val MIN_SHEET_BLUR_NOISE = 0f
const val MAX_SHEET_BLUR_NOISE = 1f
const val DEFAULT_SHEET_BLUR_NOISE = 0f
const val MIN_SHEET_BLUR_ALPHA = 0f
const val MAX_SHEET_BLUR_ALPHA = 1f
const val DEFAULT_SHEET_BLUR_ALPHA = 1f
const val DEFAULT_SHEET_BLUR_RADIUS = 10f
const val MIN_QR_CORNER_RADIUS = 0f
const val MAX_QR_CORNER_RADIUS = 50f
const val MIN_FONT_SCALE = 0.8f
const val MAX_FONT_SCALE = 1.5f

// 标题栏毛玻璃高级参数的可调范围与默认值（默认值 = 全项目统一采用的观感）
const val MIN_HEADER_BLUR_RADIUS = 0f
const val MAX_HEADER_BLUR_RADIUS = 40f
const val DEFAULT_HEADER_BLUR_RADIUS = 22f
const val MIN_HEADER_BLUR_NOISE = 0f
const val MAX_HEADER_BLUR_NOISE = 1f
const val DEFAULT_HEADER_BLUR_NOISE = 0.2f
const val MIN_HEADER_BLUR_ALPHA = 0f
const val MAX_HEADER_BLUR_ALPHA = 1f
const val DEFAULT_HEADER_BLUR_ALPHA = 1f
const val MIN_HEADER_BLUR_BACKGROUND_ALPHA = 0f
const val MAX_HEADER_BLUR_BACKGROUND_ALPHA = 1f
const val DEFAULT_HEADER_BLUR_BACKGROUND_ALPHA = 1f

@Stable
class AppSettingsState(
    private val prefs: SettingsPreferences,
) {
    var nightMode by mutableStateOf(prefs.get(SettingsKeys.NIGHT_MODE))
        private set

    var pureBlack by mutableStateOf(prefs.get(SettingsKeys.PURE_BLACK))
        private set

    var tabLabelSelectedOnly by mutableStateOf(prefs.get(SettingsKeys.TAB_LABEL_SELECTED_ONLY))
        private set

    var colorSource by mutableStateOf(loadColorSource())
        private set

    var uiStyle by mutableStateOf(prefs.get(SettingsKeys.UI_STYLE))
        private set

    var pageTransition by mutableStateOf(prefs.get(SettingsKeys.PAGE_TRANSITION))
        private set

    var reduceMotion by mutableStateOf(prefs.get(SettingsKeys.REDUCE_MOTION))
        private set

    var topBarStyle by mutableStateOf(prefs.get(SettingsKeys.TOP_BAR_STYLE))
        private set

    var logLevel by mutableStateOf(prefs.get(SettingsKeys.LOG_LEVEL))
        private set

    var qrCodeColorMode by mutableStateOf(prefs.get(SettingsKeys.QR_CODE_COLOR_MODE))
        private set

    var qrCodeCornerRadius by mutableFloatStateOf(
        prefs.get(SettingsKeys.QR_CODE_CORNER_RADIUS)
            .coerceIn(MIN_QR_CORNER_RADIUS, MAX_QR_CORNER_RADIUS)
    )
        private set

    var qrCodeCornerRadiusEnabled by mutableStateOf(prefs.get(SettingsKeys.QR_CODE_CORNER_RADIUS_ENABLED))
        private set

    /** 生效的二维码圆角百分比：开关关闭时强制为 0 */
    val effectiveQrCodeCornerRadius: Float
        get() = if (qrCodeCornerRadiusEnabled) qrCodeCornerRadius else 0f

    var qrScreenBrightnessEnabled by mutableStateOf(prefs.get(SettingsKeys.QR_SCREEN_BRIGHTNESS))
        private set

    var sheetBlurEnabled by mutableStateOf(prefs.get(SettingsKeys.SHEET_BLUR_ENABLED))
        private set

    /** 标题栏毛玻璃开关（与弹窗模糊互相独立） */
    var headerBlurEnabled by mutableStateOf(prefs.get(SettingsKeys.HEADER_BLUR_ENABLED))
        private set

    // ── 标题栏毛玻璃高级参数：名称与 Haze 的 HazeBlurStyle / hazeBlur 参数对应 ──

    /** `HazeBlurStyleScope.blurRadius`：模糊半径 */
    var headerBlurRadius by mutableFloatStateOf(
        prefs.get(SettingsKeys.HEADER_BLUR_RADIUS)
            .coerceIn(MIN_HEADER_BLUR_RADIUS, MAX_HEADER_BLUR_RADIUS)
    )
        private set

    /** `HazeBlurStyleScope.noiseFactor`：磨砂颗粒强度 */
    var headerBlurNoise by mutableFloatStateOf(
        prefs.get(SettingsKeys.HEADER_BLUR_NOISE)
            .coerceIn(MIN_HEADER_BLUR_NOISE, MAX_HEADER_BLUR_NOISE)
    )
        private set

    /** `HazeBlurStyleScope.alpha`：整层不透明度（含底色） */
    var headerBlurAlpha by mutableFloatStateOf(
        prefs.get(SettingsKeys.HEADER_BLUR_ALPHA)
            .coerceIn(MIN_HEADER_BLUR_ALPHA, MAX_HEADER_BLUR_ALPHA)
    )
        private set

    /** `HazeBlurStyleScope.backgroundColor` 的透明度：越低越能透出内容 */
    var headerBlurBackgroundAlpha by mutableFloatStateOf(
        prefs.get(SettingsKeys.HEADER_BLUR_BACKGROUND_ALPHA)
            .coerceIn(MIN_HEADER_BLUR_BACKGROUND_ALPHA, MAX_HEADER_BLUR_BACKGROUND_ALPHA)
    )
        private set

    /** `hazeBlur(performanceMode = ...)`：采样性能档位 */
    var headerBlurPerformance by mutableStateOf(
        BlurPerformanceMode.fromValue(prefs.get(SettingsKeys.HEADER_BLUR_PERFORMANCE))
    )
        private set

    // ── 弹窗背景模糊的高级参数（名称同样与 Haze 的 API 对应）──

    /** `HazeBlurStyleScope.noiseFactor`：磨砂颗粒强度 */
    var sheetBlurNoise by mutableFloatStateOf(
        prefs.get(SettingsKeys.SHEET_BLUR_NOISE)
            .coerceIn(MIN_SHEET_BLUR_NOISE, MAX_SHEET_BLUR_NOISE)
    )
        private set

    /** `HazeBlurStyleScope.alpha`：模糊层整体不透明度 */
    var sheetBlurAlpha by mutableFloatStateOf(
        prefs.get(SettingsKeys.SHEET_BLUR_ALPHA)
            .coerceIn(MIN_SHEET_BLUR_ALPHA, MAX_SHEET_BLUR_ALPHA)
    )
        private set

    /** `hazeBlur(performanceMode = ...)`：采样性能档位 */
    var sheetBlurPerformance by mutableStateOf(
        BlurPerformanceMode.fromValue(prefs.get(SettingsKeys.SHEET_BLUR_PERFORMANCE))
    )
        private set

    var sheetBlurRadius by mutableFloatStateOf(
        prefs.get(SettingsKeys.SHEET_BLUR_RADIUS).coerceIn(MIN_SHEET_BLUR_RADIUS, MAX_SHEET_BLUR_RADIUS)
    )
        private set

    var fontScale by mutableFloatStateOf(
        prefs.get(SettingsKeys.FONT_SCALE).coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
    )
        private set

    fun updateNightMode(value: NightMode) {
        nightMode = value
        prefs.set(SettingsKeys.NIGHT_MODE, value)
        AppCompatDelegate.setDefaultNightMode(value.toAppCompatMode())
    }

    fun updatePureBlack(enabled: Boolean) {
        pureBlack = enabled
        prefs.set(SettingsKeys.PURE_BLACK, enabled)
    }

    fun updateTabLabelSelectedOnly(enabled: Boolean) {
        tabLabelSelectedOnly = enabled
        prefs.set(SettingsKeys.TAB_LABEL_SELECTED_ONLY, enabled)
    }

    fun updateColorSource(source: ThemeColorSource) {
        colorSource = source
        when (source) {
            ThemeColorSource.SystemDynamic -> prefs.set(SettingsKeys.COLOR_SOURCE, "dynamic")
            is ThemeColorSource.Custom -> {
                prefs.set(SettingsKeys.COLOR_SOURCE, "custom")
                prefs.set(SettingsKeys.SEED_COLOR, source.seedColor.toArgb())
            }
        }
    }

    fun updateUiStyle(value: UiStyle) {
        uiStyle = value
        prefs.set(SettingsKeys.UI_STYLE, value)
    }

    fun updatePageTransition(value: PageTransition) {
        pageTransition = value
        prefs.set(SettingsKeys.PAGE_TRANSITION, value)
    }

    fun updateReduceMotion(value: ReduceMotion) {
        reduceMotion = value
        prefs.set(SettingsKeys.REDUCE_MOTION, value)
    }

    fun updateTopBarStyle(value: TopBarStyle) {
        topBarStyle = value
        prefs.set(SettingsKeys.TOP_BAR_STYLE, value)
    }

    fun updateLogLevel(value: LogLevel) {
        logLevel = value
        prefs.set(SettingsKeys.LOG_LEVEL, value)
        AppLog.setMinLevel(value)
    }

    fun updateQrCodeColorMode(value: QrCodeColorMode) {
        qrCodeColorMode = value
        prefs.set(SettingsKeys.QR_CODE_COLOR_MODE, value)
    }

    fun updateQrCodeCornerRadius(value: Float) {
        val clamped = value.coerceIn(MIN_QR_CORNER_RADIUS, MAX_QR_CORNER_RADIUS)
        qrCodeCornerRadius = clamped
        prefs.set(SettingsKeys.QR_CODE_CORNER_RADIUS, clamped)
    }

    fun updateQrCodeCornerRadiusEnabled(enabled: Boolean) {
        qrCodeCornerRadiusEnabled = enabled
        prefs.set(SettingsKeys.QR_CODE_CORNER_RADIUS_ENABLED, enabled)
    }

    fun updateQrScreenBrightnessEnabled(enabled: Boolean) {
        qrScreenBrightnessEnabled = enabled
        prefs.set(SettingsKeys.QR_SCREEN_BRIGHTNESS, enabled)
    }

    fun updateSheetBlurEnabled(enabled: Boolean) {
        sheetBlurEnabled = enabled
        prefs.set(SettingsKeys.SHEET_BLUR_ENABLED, enabled)
    }

    fun updateHeaderBlurEnabled(enabled: Boolean) {
        headerBlurEnabled = enabled
        prefs.set(SettingsKeys.HEADER_BLUR_ENABLED, enabled)
    }

    fun updateHeaderBlurRadius(value: Float) {
        val clamped = value.coerceIn(MIN_HEADER_BLUR_RADIUS, MAX_HEADER_BLUR_RADIUS)
        headerBlurRadius = clamped
        prefs.set(SettingsKeys.HEADER_BLUR_RADIUS, clamped)
    }

    fun updateHeaderBlurNoise(value: Float) {
        val clamped = value.coerceIn(MIN_HEADER_BLUR_NOISE, MAX_HEADER_BLUR_NOISE)
        headerBlurNoise = clamped
        prefs.set(SettingsKeys.HEADER_BLUR_NOISE, clamped)
    }

    fun updateHeaderBlurAlpha(value: Float) {
        val clamped = value.coerceIn(MIN_HEADER_BLUR_ALPHA, MAX_HEADER_BLUR_ALPHA)
        headerBlurAlpha = clamped
        prefs.set(SettingsKeys.HEADER_BLUR_ALPHA, clamped)
    }

    fun updateHeaderBlurBackgroundAlpha(value: Float) {
        val clamped = value.coerceIn(MIN_HEADER_BLUR_BACKGROUND_ALPHA, MAX_HEADER_BLUR_BACKGROUND_ALPHA)
        headerBlurBackgroundAlpha = clamped
        prefs.set(SettingsKeys.HEADER_BLUR_BACKGROUND_ALPHA, clamped)
    }

    fun updateHeaderBlurPerformance(value: BlurPerformanceMode) {
        headerBlurPerformance = value
        prefs.set(SettingsKeys.HEADER_BLUR_PERFORMANCE, value.value)
    }

    /** 把所有高级参数恢复成默认值（不改开关） */
    fun resetHeaderBlurAdvanced() {
        updateHeaderBlurRadius(DEFAULT_HEADER_BLUR_RADIUS)
        updateHeaderBlurNoise(DEFAULT_HEADER_BLUR_NOISE)
        updateHeaderBlurAlpha(DEFAULT_HEADER_BLUR_ALPHA)
        updateHeaderBlurBackgroundAlpha(DEFAULT_HEADER_BLUR_BACKGROUND_ALPHA)
        updateHeaderBlurPerformance(BlurPerformanceMode.DEFAULT)
    }

    fun updateSheetBlurNoise(value: Float) {
        val clamped = value.coerceIn(MIN_SHEET_BLUR_NOISE, MAX_SHEET_BLUR_NOISE)
        sheetBlurNoise = clamped
        prefs.set(SettingsKeys.SHEET_BLUR_NOISE, clamped)
    }

    fun updateSheetBlurAlpha(value: Float) {
        val clamped = value.coerceIn(MIN_SHEET_BLUR_ALPHA, MAX_SHEET_BLUR_ALPHA)
        sheetBlurAlpha = clamped
        prefs.set(SettingsKeys.SHEET_BLUR_ALPHA, clamped)
    }

    fun updateSheetBlurPerformance(value: BlurPerformanceMode) {
        sheetBlurPerformance = value
        prefs.set(SettingsKeys.SHEET_BLUR_PERFORMANCE, value.value)
    }

    /** 弹窗模糊的高级参数恢复默认值（不改开关） */
    fun resetSheetBlurAdvanced() {
        updateSheetBlurRadius(DEFAULT_SHEET_BLUR_RADIUS)
        updateSheetBlurNoise(DEFAULT_SHEET_BLUR_NOISE)
        updateSheetBlurAlpha(DEFAULT_SHEET_BLUR_ALPHA)
        updateSheetBlurPerformance(BlurPerformanceMode.DEFAULT)
    }

    fun updateSheetBlurRadius(value: Float) {
        val clamped = value.coerceIn(MIN_SHEET_BLUR_RADIUS, MAX_SHEET_BLUR_RADIUS)
        sheetBlurRadius = clamped
        prefs.set(SettingsKeys.SHEET_BLUR_RADIUS, clamped)
    }

    fun updateFontScale(value: Float) {
        val clamped = value.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
        fontScale = clamped
        prefs.set(SettingsKeys.FONT_SCALE, clamped)
    }

    private fun loadColorSource(): ThemeColorSource {
        return if (prefs.get(SettingsKeys.COLOR_SOURCE) == "dynamic") {
            ThemeColorSource.SystemDynamic
        } else {
            ThemeColorSource.Custom(Color(prefs.get(SettingsKeys.SEED_COLOR)))
        }
    }
}

internal fun NightMode.toAppCompatMode(): Int = when (this) {
    NightMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    NightMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
    NightMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
}

val LocalAppSettingsState = staticCompositionLocalOf<AppSettingsState> {
    error("No AppSettingsState provided")
}
