package edu.cqwu.electricity.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
import edu.cqwu.electricity.settings.data.AppLanguage
import edu.cqwu.electricity.settings.data.SettingsPreferences
import edu.cqwu.electricity.shortcut.util.ShortcutHelper
import edu.cqwu.electricity.settings.data.ThemeColorSource
import edu.cqwu.electricity.app.AppShell
import edu.cqwu.electricity.theme.ui.AnimationSettings
import edu.cqwu.electricity.theme.ui.ColorSourceState
import edu.cqwu.electricity.theme.ui.LocalAnimationSettings
import edu.cqwu.electricity.theme.ui.LocalColorSourceState
import edu.cqwu.electricity.theme.ui.LocalNightModeState
import edu.cqwu.electricity.theme.ui.LocalQrCodeSettings
import edu.cqwu.electricity.theme.ui.LocalTopBarState
import edu.cqwu.electricity.theme.ui.NightModeState
import edu.cqwu.electricity.theme.ui.QrCodeSettings
import edu.cqwu.electricity.theme.ui.TopBarState
import edu.cqwu.electricity.theme.ui.电费查询Theme
import edu.cqwu.electricity.settings.util.LocaleContextWrapper

class MainActivity : ComponentActivity() {

    // 快捷方式状态：onNewIntent 时更新，Compose 自动重组
    private val _shortcutAppInfo = mutableStateOf<ShortcutHelper.ShortcutAppInfo?>(null)
    private val _shortcutLaunchId = mutableIntStateOf(0)

    override fun attachBaseContext(newBase: Context) {
        val language = SettingsPreferences(newBase).getAppLanguage()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || language == AppLanguage.SYSTEM) {
            super.attachBaseContext(newBase)
        } else {
            super.attachBaseContext(LocaleContextWrapper.wrap(newBase, language.localeTag!!))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 启用边到边绘制（内容延伸到系统栏后方）
        // 系统栏图标颜色由 Compose 层的 Theme.kt 中的 SideEffect 动态管理
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            // 首次启动时从 intent 提取快捷方式信息
            val initialInfo = remember { ShortcutHelper.extractShortcutAppInfo(intent) }
            LaunchedEffect(initialInfo) {
                if (initialInfo != null && _shortcutAppInfo.value == null) {
                    _shortcutAppInfo.value = initialInfo
                    _shortcutLaunchId.intValue++
                }
            }
            val shortcutAppInfo = _shortcutAppInfo.value
            val shortcutLaunchId = _shortcutLaunchId.intValue
            val settingsPrefs = remember { SettingsPreferences(this@MainActivity) }

            // ── 夜间模式状态 ──
            var nightMode by remember { mutableStateOf(settingsPrefs.getNightMode()) }
            val nightModeState = remember(nightMode) {
                NightModeState(
                    nightMode = nightMode,
                    onNightModeChange = { mode ->
                        nightMode = mode
                        settingsPrefs.setNightMode(mode)
                    }
                )
            }

            // ── 主题颜色源状态 ──
            val initialColorSource = remember {
                val source = settingsPrefs.getColorSource()
                if (source == "dynamic") {
                    ThemeColorSource.SystemDynamic
                } else {
                    val argb = settingsPrefs.getSeedColor()
                    ThemeColorSource.Custom(Color(argb))
                }
            }
            var colorSource by remember { mutableStateOf(initialColorSource) }
            val colorSourceState = remember(colorSource) {
                ColorSourceState(
                    colorSource = colorSource,
                    onColorSourceChange = { source ->
                        colorSource = source
                        when (source) {
                            is ThemeColorSource.SystemDynamic -> settingsPrefs.setColorSource("dynamic")
                            is ThemeColorSource.Custom -> {
                                settingsPrefs.setColorSource("custom")
                                settingsPrefs.setSeedColor(source.seedColor.toArgb())
                            }
                        }
                    }
                )
            }

            // ── 动画设置状态 ──
            var pageTransition by remember { mutableStateOf(settingsPrefs.getPageTransition()) }
            var reduceMotion by remember { mutableStateOf(settingsPrefs.getReduceMotion()) }
            val animationSettings = remember(pageTransition, reduceMotion) {
                AnimationSettings(
                    pageTransition = pageTransition,
                    onPageTransitionChange = { mode ->
                        pageTransition = mode
                        settingsPrefs.setPageTransition(mode)
                    },
                    reduceMotion = reduceMotion,
                    onReduceMotionChange = { mode ->
                        reduceMotion = mode
                        settingsPrefs.setReduceMotion(mode)
                    },
                )
            }

            // ── 标题栏颜色样式状态 ──
            var topBarStyle by remember { mutableStateOf(settingsPrefs.getTopBarStyle()) }
            val topBarState = remember(topBarStyle) {
                TopBarState(
                    style = topBarStyle,
                    onStyleChange = { style ->
                        topBarStyle = style
                        settingsPrefs.setTopBarStyle(style)
                    }
                )
            }

            // ── 二维码设置状态 ──
            var qrCodeColorMode by remember { mutableStateOf(settingsPrefs.getQrCodeColorMode()) }
            var qrCodeCornerRadius by remember { mutableIntStateOf(settingsPrefs.getQrCodeCornerRadius()) }
            var qrScreenBrightnessEnabled by remember { mutableStateOf(settingsPrefs.getQrScreenBrightnessEnabled()) }
            val qrCodeSettings = remember(qrCodeColorMode, qrCodeCornerRadius, qrScreenBrightnessEnabled) {
                QrCodeSettings(
                    colorMode = qrCodeColorMode,
                    onColorModeChange = { mode ->
                        qrCodeColorMode = mode
                        settingsPrefs.setQrCodeColorMode(mode)
                    },
                    cornerRadius = qrCodeCornerRadius,
                    onCornerRadiusChange = { radius ->
                        qrCodeCornerRadius = radius
                        settingsPrefs.setQrCodeCornerRadius(radius)
                    },
                    screenBrightnessEnabled = qrScreenBrightnessEnabled,
                    onScreenBrightnessEnabledChange = { enabled ->
                        qrScreenBrightnessEnabled = enabled
                        settingsPrefs.setQrScreenBrightnessEnabled(enabled)
                    },
                )
            }

            CompositionLocalProvider(
                LocalNightModeState provides nightModeState,
                LocalColorSourceState provides colorSourceState,
                LocalAnimationSettings provides animationSettings,
                LocalTopBarState provides topBarState,
                LocalQrCodeSettings provides qrCodeSettings,
            ) {
                电费查询Theme(
                    nightMode = nightMode,
                    colorSource = colorSource,
                ) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        val navController = rememberNavController()
                        AppShell(
                            navController = navController,
                            shortcutAppInfo = shortcutAppInfo,
                            shortcutLaunchId = shortcutLaunchId,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val info = ShortcutHelper.extractShortcutAppInfo(intent)
        if (info != null) {
            _shortcutAppInfo.value = info
            _shortcutLaunchId.intValue++
        }
    }
}
