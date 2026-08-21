package edu.cqwu.electricity.settings.data

import android.content.SharedPreferences
import android.os.Build
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import edu.cqwu.electricity.BuildConfig
import edu.cqwu.electricity.home.data.CustomServiceEntry
import edu.cqwu.electricity.logging.LogLevel

private val gson = Gson()

class SettingKey<T>(
    val name: String,
    val default: T,
    internal val read: SharedPreferences.() -> T,
    internal val write: SharedPreferences.Editor.(T) -> Unit,
)

fun booleanSetting(
    name: String,
    default: Boolean,
): SettingKey<Boolean> = SettingKey(
    name = name,
    default = default,
    read = { getBoolean(name, default) },
    write = { putBoolean(name, it) },
)

fun stringSetting(
    name: String,
    default: String,
): SettingKey<String> = SettingKey(
    name = name,
    default = default,
    read = { getString(name, default) ?: default },
    write = { putString(name, it) },
)

fun intSetting(
    name: String,
    default: Int,
): SettingKey<Int> = SettingKey(
    name = name,
    default = default,
    read = { getInt(name, default) },
    write = { putInt(name, it) },
)

fun longSetting(
    name: String,
    default: Long,
): SettingKey<Long> = SettingKey(
    name = name,
    default = default,
    read = { getLong(name, default) },
    write = { putLong(name, it) },
)

fun floatSetting(
    name: String,
    default: Float,
): SettingKey<Float> = SettingKey(
    name = name,
    default = default,
    read = { getFloat(name, default) },
    write = { putFloat(name, it) },
)

fun stringSetSetting(
    name: String,
    default: Set<String>,
): SettingKey<Set<String>> = SettingKey(
    name = name,
    default = default,
    read = { getStringSet(name, default.toMutableSet())?.toSet() ?: default },
    write = { putStringSet(name, it.toMutableSet()) },
)

fun <T> jsonSetting(
    name: String,
    default: T,
    type: java.lang.reflect.Type,
): SettingKey<T> = SettingKey(
    name = name,
    default = default,
    read = {
        val raw = getString(name, null)
        if (raw.isNullOrBlank()) {
            default
        } else {
            try {
                gson.fromJson<T>(raw, type)
            } catch (_: Exception) {
                default
            }
        }
    },
    write = { putString(name, gson.toJson(it)) },
)

fun <E : Enum<E>> enumSetting(
    name: String,
    default: E,
    fromValue: (String) -> E,
    encode: (E) -> String,
): SettingKey<E> = SettingKey(
    name = name,
    default = default,
    read = { fromValue(getString(name, encode(default)) ?: encode(default)) },
    write = { putString(name, encode(it)) },
)

object SettingsKeys {
    val NIGHT_MODE = enumSetting(
        name = "night_mode",
        default = NightMode.SYSTEM,
        fromValue = NightMode::fromValue,
        encode = { it.value },
    )

    val WEBVIEW_DARK_MODE = booleanSetting(
        name = "webview_dark_mode",
        default = true,
    )

    val PURE_BLACK = booleanSetting(
        name = "pure_black",
        default = false,
    )

    val COLOR_SOURCE = stringSetting(
        name = "color_source",
        default = "dynamic",
    )

    val SEED_COLOR = intSetting(
        name = "seed_color",
        default = 0xFF6750A4.toInt(),
    )

    val PAGE_TRANSITION = enumSetting(
        name = "page_transition",
        default = PageTransition.SLIDE,
        fromValue = PageTransition::fromValue,
        encode = { it.value },
    )

    val REDUCE_MOTION = enumSetting(
        name = "reduce_motion",
        default = ReduceMotion.SYSTEM,
        fromValue = ReduceMotion::fromValue,
        encode = { it.value },
    )

    val TOP_BAR_STYLE = enumSetting(
        name = "top_bar_style",
        default = TopBarStyle.SURFACE,
        fromValue = TopBarStyle::fromValue,
        encode = { it.value },
    )

    val LOG_LEVEL = enumSetting(
        name = "log_level",
        default = defaultLogLevel(),
        fromValue = { raw -> LogLevel.fromValue(raw, defaultLogLevel()) },
        encode = { it.value },
    )

    val QR_CODE_COLOR_MODE = enumSetting(
        name = "qr_code_color_mode",
        default = QrCodeColorMode.MONOCHROME,
        fromValue = QrCodeColorMode::fromValue,
        encode = { it.value },
    )

    val QR_CODE_CORNER_RADIUS = floatSetting(
        name = "qr_code_corner_radius",
        default = 30f,
    )

    val QR_SCREEN_BRIGHTNESS = booleanSetting(
        name = "qr_screen_brightness",
        default = true,
    )

    val SHEET_BLUR_ENABLED = booleanSetting(
        name = "sheet_blur_enabled",
        default = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    )

    val SHEET_BLUR_RADIUS = floatSetting(
        name = "sheet_blur_radius",
        default = 10f,
    )

    val FONT_SCALE = floatSetting(
        name = "font_scale",
        default = 1f,
    )

    val WEBVPN_ENABLED = booleanSetting(
        name = "webvpn_enabled",
        default = false,
    )

    val UA_SELECTED_ID = stringSetting(
        name = "ua_selected_id",
        default = "preset_default",
    )

    val UA_CUSTOM_LIST = stringSetting(
        name = "ua_custom_list",
        default = "[]",
    )

    val MY_SERVICES = stringSetSetting(
        name = "my_services",
        default = emptySet(),
    )

    val CUSTOM_SERVICES = jsonSetting(
        name = "custom_services",
        default = emptyList<CustomServiceEntry>(),
        type = object : TypeToken<List<CustomServiceEntry>>() {}.type,
    )

    val AUTO_UPDATE_ENABLED = booleanSetting(
        name = "auto_update_enabled",
        default = true,
    )

    val CHECK_CI_UPDATES = booleanSetting(
        name = "check_ci_updates",
        default = true,
    )

    val UPDATE_TIMEOUT_MS = intSetting(
        name = "update_timeout_ms",
        default = 3000,
    )

    val SKIPPED_UPDATE_VERSION = longSetting(
        name = "skipped_update_version",
        default = 0L,
    )
}

private fun defaultLogLevel(): LogLevel =
    if (BuildConfig.DEBUG) LogLevel.VERBOSE else LogLevel.WARN
