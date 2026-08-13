package edu.cqwu.electricity.theme.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import edu.cqwu.electricity.logging.AppLog
import edu.cqwu.electricity.logging.LogLevel
import edu.cqwu.electricity.settings.data.NightMode
import edu.cqwu.electricity.settings.data.PageTransition
import edu.cqwu.electricity.settings.data.QrCodeColorMode
import edu.cqwu.electricity.settings.data.ReduceMotion
import edu.cqwu.electricity.settings.data.SettingsKeys
import edu.cqwu.electricity.settings.data.SettingsPreferences
import edu.cqwu.electricity.settings.data.ThemeColorSource
import edu.cqwu.electricity.settings.data.TopBarStyle

const val MIN_SHEET_BLUR_RADIUS = 0f
const val MAX_SHEET_BLUR_RADIUS = 40f
const val MIN_QR_CORNER_RADIUS = 0f
const val MAX_QR_CORNER_RADIUS = 50f

@Stable
class AppSettingsState(
    private val prefs: SettingsPreferences,
) {
    var nightMode by mutableStateOf(prefs.get(SettingsKeys.NIGHT_MODE))
        private set

    var pureBlack by mutableStateOf(prefs.get(SettingsKeys.PURE_BLACK))
        private set

    var colorSource by mutableStateOf(loadColorSource())
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

    var qrCodeCornerRadius by mutableStateOf(
        prefs.get(SettingsKeys.QR_CODE_CORNER_RADIUS)
            .coerceIn(MIN_QR_CORNER_RADIUS, MAX_QR_CORNER_RADIUS)
    )
        private set

    var qrScreenBrightnessEnabled by mutableStateOf(prefs.get(SettingsKeys.QR_SCREEN_BRIGHTNESS))
        private set

    var sheetBlurEnabled by mutableStateOf(prefs.get(SettingsKeys.SHEET_BLUR_ENABLED))
        private set

    var sheetBlurRadius by mutableStateOf(
        prefs.get(SettingsKeys.SHEET_BLUR_RADIUS).coerceIn(MIN_SHEET_BLUR_RADIUS, MAX_SHEET_BLUR_RADIUS)
    )
        private set

    fun updateNightMode(value: NightMode) {
        nightMode = value
        prefs.set(SettingsKeys.NIGHT_MODE, value)
    }

    fun updatePureBlack(enabled: Boolean) {
        pureBlack = enabled
        prefs.set(SettingsKeys.PURE_BLACK, enabled)
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

    fun updateQrScreenBrightnessEnabled(enabled: Boolean) {
        qrScreenBrightnessEnabled = enabled
        prefs.set(SettingsKeys.QR_SCREEN_BRIGHTNESS, enabled)
    }

    fun updateSheetBlurEnabled(enabled: Boolean) {
        sheetBlurEnabled = enabled
        prefs.set(SettingsKeys.SHEET_BLUR_ENABLED, enabled)
    }

    fun updateSheetBlurRadius(value: Float) {
        val clamped = value.coerceIn(MIN_SHEET_BLUR_RADIUS, MAX_SHEET_BLUR_RADIUS)
        sheetBlurRadius = clamped
        prefs.set(SettingsKeys.SHEET_BLUR_RADIUS, clamped)
    }

    private fun loadColorSource(): ThemeColorSource {
        return if (prefs.get(SettingsKeys.COLOR_SOURCE) == "dynamic") {
            ThemeColorSource.SystemDynamic
        } else {
            ThemeColorSource.Custom(Color(prefs.get(SettingsKeys.SEED_COLOR)))
        }
    }
}

val LocalAppSettingsState = staticCompositionLocalOf<AppSettingsState> {
    error("No AppSettingsState provided")
}
