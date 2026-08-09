package edu.cqwu.electricity.settings.data

import android.app.LocaleManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.LocaleList
import androidx.compose.ui.graphics.Color
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import edu.cqwu.electricity.home.data.CustomServiceEntry
import java.util.Locale

/**
 * 应用语言枚举
 * SYSTEM: 跟随系统
 * CHINESE: 中文
 * ENGLISH: 英文
 */
enum class AppLanguage(val value: String, val displayName: String) {
    SYSTEM("system", "跟随系统"),
    CHINESE("zh", "简体中文"),
    TRADITIONAL_CHINESE("zh-TW", "繁體中文"),
    ENGLISH("en", "English"),
    FRENCH("fr", "Français"),
    ARABIC("ar", "العربية"),
    JAPANESE("ja", "日本語");

    val localeTag: String?
        get() = when (this) {
            SYSTEM -> null
            CHINESE -> "zh"
            TRADITIONAL_CHINESE -> "zh-TW"
            ENGLISH -> "en"
            FRENCH -> "fr"
            ARABIC -> "ar"
            JAPANESE -> "ja"
        }

    companion object {
        fun fromValue(value: String): AppLanguage {
            return entries.firstOrNull { it.value == value } ?: SYSTEM
        }

        fun fromLanguageTag(tag: String): AppLanguage {
            val locale = Locale.forLanguageTag(tag)
            return when (locale.language) {
                "zh" -> {
                    if (locale.country == "TW" || locale.script == "Hant" || tag.contains("TW")) {
                        TRADITIONAL_CHINESE
                    } else {
                        CHINESE
                    }
                }
                "en" -> ENGLISH
                "fr" -> FRENCH
                "ar" -> ARABIC
                "ja" -> JAPANESE
                else -> SYSTEM
            }
        }
    }
}

/**
 * 夜间模式枚举
 * SYSTEM: 跟随系统设置
 * LIGHT:  强制浅色模式
 * DARK:   强制深色模式
 */
enum class NightMode(val value: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromValue(value: String): NightMode {
            return entries.firstOrNull { it.value == value } ?: SYSTEM
        }
    }
}

/**
 * 主题颜色源
 * SystemDynamic: 系统 Material You 动态取色
 * Custom:        用户自选种子色
 */
sealed interface ThemeColorSource {
    data object SystemDynamic : ThemeColorSource
    data class Custom(val seedColor: Color) : ThemeColorSource
}

/**
 * 应用设置持久化存储
 *
 * 保存用户自定义设置项，包括：
 * - 夜间模式（SYSTEM / LIGHT / DARK）
 * - 主题颜色源（dynamic / custom + seedColor）
 * - 页面过渡动画开关（预留，后续完善）
 */
class SettingsPreferences(context: Context) {

    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // ── 夜间模式 ──

    fun getNightMode(): NightMode {
        val raw = prefs.getString(KEY_NIGHT_MODE, NightMode.SYSTEM.value) ?: NightMode.SYSTEM.value
        return NightMode.fromValue(raw)
    }

    fun setNightMode(mode: NightMode) {
        prefs.edit().putString(KEY_NIGHT_MODE, mode.value).apply()
    }

    // ── 主题颜色源 ──

    /** "dynamic" 或 "custom" */
    fun getColorSource(): String {
        return prefs.getString(KEY_COLOR_SOURCE, "dynamic") ?: "dynamic"
    }

    fun setColorSource(source: String) {
        prefs.edit().putString(KEY_COLOR_SOURCE, source).apply()
    }

    /** 种子色 ARGB 值，默认 Material3 基准紫 */
    fun getSeedColor(): Int {
        return prefs.getInt(KEY_SEED_COLOR, 0xFF6750A4.toInt())
    }

    fun setSeedColor(argb: Int) {
        prefs.edit().putInt(KEY_SEED_COLOR, argb).apply()
    }

    // ── 页面过渡动画类型 ──

    fun getPageTransition(): PageTransition {
        val raw = prefs.getString(KEY_PAGE_TRANSITION, PageTransition.SLIDE.value) ?: PageTransition.SLIDE.value
        return PageTransition.fromValue(raw)
    }

    fun setPageTransition(type: PageTransition) {
        prefs.edit().putString(KEY_PAGE_TRANSITION, type.value).apply()
    }

    // ── 标题栏颜色样式 ──

    // ── 应用语言 ──

    fun getAppLanguage(): AppLanguage {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getPlatformAppLanguage()
        } else {
            val raw = prefs.getString(KEY_APP_LANGUAGE, AppLanguage.SYSTEM.value)
                ?: AppLanguage.SYSTEM.value
            AppLanguage.fromValue(raw)
        }
    }

    fun setAppLanguage(language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setPlatformAppLanguage(language)
        } else {
            prefs.edit().putString(KEY_APP_LANGUAGE, language.value).apply()
        }
    }

    private fun getPlatformAppLanguage(): AppLanguage {
        val localeManager = appContext.getSystemService(LocaleManager::class.java)
        val applicationLocales = localeManager.applicationLocales
        return if (applicationLocales.isEmpty) {
            AppLanguage.SYSTEM
        } else {
            AppLanguage.fromLanguageTag(applicationLocales[0].toLanguageTag())
        }
    }

    private fun setPlatformAppLanguage(language: AppLanguage) {
        val localeManager = appContext.getSystemService(LocaleManager::class.java)
        val localeList = language.localeTag?.let { LocaleList.forLanguageTags(it) }
            ?: LocaleList.getEmptyLocaleList()
        localeManager.applicationLocales = localeList
    }

    // ── WebVPN 代理 ──

    fun isWebVpnEnabled(): Boolean {
        return prefs.getBoolean(KEY_WEBVPN_ENABLED, false)
    }

    fun setWebVpnEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WEBVPN_ENABLED, enabled).apply()
    }

    // ── 标题栏颜色样式 ──

    fun getTopBarStyle(): TopBarStyle {
        val raw = prefs.getString(KEY_TOP_BAR_STYLE, TopBarStyle.SURFACE.value) ?: TopBarStyle.SURFACE.value
        return TopBarStyle.fromValue(raw)
    }

    fun setTopBarStyle(style: TopBarStyle) {
        prefs.edit().putString(KEY_TOP_BAR_STYLE, style.value).apply()
    }

    // ── 减少动画（无障碍） ──

    fun getReduceMotion(): ReduceMotion {
        val raw = prefs.getString(KEY_REDUCE_MOTION, ReduceMotion.SYSTEM.value) ?: ReduceMotion.SYSTEM.value
        return ReduceMotion.fromValue(raw)
    }

    fun setReduceMotion(mode: ReduceMotion) {
        prefs.edit().putString(KEY_REDUCE_MOTION, mode.value).apply()
    }

    // ── 二维码颜色模式 ──

    fun getQrCodeColorMode(): QrCodeColorMode {
        val raw = prefs.getString(KEY_QR_CODE_COLOR_MODE, QrCodeColorMode.THEME_SNAKE.value)
            ?: QrCodeColorMode.THEME_SNAKE.value
        return QrCodeColorMode.fromValue(raw)
    }

    fun setQrCodeColorMode(mode: QrCodeColorMode) {
        prefs.edit().putString(KEY_QR_CODE_COLOR_MODE, mode.value).apply()
    }

    // ── 二维码圆角度（百分比 0~50） ──

    fun getQrCodeCornerRadius(): Int {
        return prefs.getInt(KEY_QR_CODE_CORNER_RADIUS, 30)
    }

    fun setQrCodeCornerRadius(radius: Int) {
        prefs.edit().putInt(KEY_QR_CODE_CORNER_RADIUS, radius).apply()
    }

    // ── 二维码屏幕高亮开关 ──

    fun getQrScreenBrightnessEnabled(): Boolean {
        return prefs.getBoolean(KEY_QR_SCREEN_BRIGHTNESS, true)
    }

    fun setQrScreenBrightnessEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_QR_SCREEN_BRIGHTNESS, enabled).apply()
    }

    // ── 浏览器标识（User-Agent） ──

    /**
     * 获取当前选中的浏览器标识条目 ID。
     * 默认返回 "preset_default"（与升级前行为一致）。
     */
    fun getSelectedUaId(): String {
        return prefs.getString(KEY_UA_SELECTED_ID, "preset_default") ?: "preset_default"
    }

    fun setSelectedUaId(id: String) {
        prefs.edit().putString(KEY_UA_SELECTED_ID, id).apply()
    }

    /**
     * 获取用户自定义浏览器标识列表（JSON 数组）。
     */
    fun getCustomUaList(): String {
        return prefs.getString(KEY_UA_CUSTOM_LIST, "[]") ?: "[]"
    }

    fun setCustomUaList(json: String) {
        prefs.edit().putString(KEY_UA_CUSTOM_LIST, json).apply()
    }

    // ── 我的服务（已收藏 appId 集合） ──

    /**
     * 获取已收藏到「我的服务」的应用 ID 集合。
     */
    fun getMyServiceIds(): Set<String> {
        return prefs.getStringSet(KEY_MY_SERVICES, emptySet()) ?: emptySet()
    }

    /**
     * 保存已收藏到「我的服务」的应用 ID 集合。
     */
    fun setMyServiceIds(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_MY_SERVICES, ids).apply()
    }

    // ── 我的服务：自定义网站列表 ──

    private val gson by lazy { Gson() }

    /**
     * 获取用户自定义网站快捷方式列表。
     */
    fun getCustomServices(): List<CustomServiceEntry> {
        val json = prefs.getString(KEY_CUSTOM_SERVICES, "[]") ?: "[]"
        return try {
            val type = object : TypeToken<List<CustomServiceEntry>>() {}.type
            gson.fromJson(json, type)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * 保存用户自定义网站快捷方式列表。
     */
    fun setCustomServices(services: List<CustomServiceEntry>) {
        prefs.edit().putString(KEY_CUSTOM_SERVICES, gson.toJson(services)).apply()
    }

    companion object {
        private const val PREF_NAME = "settings_preferences"
        private const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_WEBVPN_ENABLED = "webvpn_enabled"
        private const val KEY_NIGHT_MODE = "night_mode"
        private const val KEY_COLOR_SOURCE = "color_source"
        private const val KEY_SEED_COLOR = "seed_color"
        private const val KEY_PAGE_TRANSITION = "page_transition"
        private const val KEY_REDUCE_MOTION = "reduce_motion"
        private const val KEY_TOP_BAR_STYLE = "top_bar_style"
        private const val KEY_QR_CODE_COLOR_MODE = "qr_code_color_mode"
        private const val KEY_QR_CODE_CORNER_RADIUS = "qr_code_corner_radius"
        private const val KEY_QR_SCREEN_BRIGHTNESS = "qr_screen_brightness"
        private const val KEY_UA_SELECTED_ID = "ua_selected_id"
        private const val KEY_UA_CUSTOM_LIST = "ua_custom_list"
        private const val KEY_MY_SERVICES = "my_services"
        private const val KEY_CUSTOM_SERVICES = "custom_services"
    }
}

/** 页面过渡动画类型 */
enum class PageTransition(val value: String, val displayName: String) {
    NONE("none", "无动画"),
    SLIDE("slide", "水平滑动"),
    SLIDE_VERTICAL("slide_vertical", "垂直滑动"),
    FADE("fade", "淡入淡出"),
    FADE_SCALE("fade_scale", "缩放淡入"),
    CUPERTINO("cupertino", "Cupertino");

    companion object {
        fun fromValue(value: String): PageTransition {
            return entries.firstOrNull { it.value == value } ?: SLIDE
        }
    }
}

/** 减少动画模式 */
enum class ReduceMotion(val value: String, val displayName: String) {
    SYSTEM("system", "跟随系统"),
    ON("on", "始终减少"),
    OFF("off", "始终开启");

    companion object {
        fun fromValue(value: String): ReduceMotion {
            return entries.firstOrNull { it.value == value } ?: SYSTEM
        }
    }
}

/** 标题栏颜色样式 */
enum class TopBarStyle(val value: String, val displayName: String) {
    SURFACE("surface", "背景色"),
    SURFACE_CONTAINER_LOWEST("surface_container_lowest", "极浅强调"),
    SURFACE_CONTAINER_LOW("surface_container_low", "浅强调"),
    SURFACE_CONTAINER("surface_container", "中等强调"),
    SURFACE_CONTAINER_HIGH("surface_container_high", "深强调"),
    SURFACE_CONTAINER_HIGHEST("surface_container_highest", "最深强调"),
    SURFACE_VARIANT("surface_variant", "变体色");

    companion object {
        fun fromValue(value: String): TopBarStyle {
            return entries.firstOrNull { it.value == value } ?: SURFACE
        }
    }
}

/** 二维码颜色模式 */
enum class QrCodeColorMode(val value: String) {
    THEME_SNAKE("theme_snake"),
    MONOCHROME("monochrome");

    companion object {
        fun fromValue(value: String): QrCodeColorMode {
            return entries.firstOrNull { it.value == value } ?: THEME_SNAKE
        }
    }
}

/** 夜间模式的显示名称 */
val NightMode.displayName: String
    get() = when (this) {
        NightMode.SYSTEM -> "跟随系统"
        NightMode.LIGHT -> "浅色模式"
        NightMode.DARK -> "深色模式"
    }

