package edu.cqwu.electricity.app

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
import edu.cqwu.electricity.settings.data.AppLanguage
import edu.cqwu.electricity.settings.data.NightMode
import edu.cqwu.electricity.settings.data.SettingsKeys
import edu.cqwu.electricity.settings.data.SettingsPreferences
import edu.cqwu.electricity.settings.util.LocaleContextWrapper
import edu.cqwu.electricity.shortcut.util.ShortcutHelper
import edu.cqwu.electricity.theme.ui.AppSettingsState
import edu.cqwu.electricity.theme.ui.LocalAppSettingsState
import edu.cqwu.electricity.theme.ui.电费查询Theme

class MainActivity : ComponentActivity() {

    // 快捷方式状态：onNewIntent 时更新，Compose 自动重组
    private val _shortcutAppInfo = mutableStateOf<ShortcutHelper.ShortcutAppInfo?>(null)
    private val _shortcutLaunchId = mutableIntStateOf(0)

    override fun attachBaseContext(newBase: Context) {
        val language = SettingsPreferences(newBase).getAppLanguage()
        val wrapped = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || language == AppLanguage.SYSTEM) {
            newBase
        } else {
            LocaleContextWrapper.wrap(newBase, language.localeTag!!)
        }

        // 应用内强制夜间模式：在 super.attachBaseContext 之前注入 uiMode 到 override Configuration，
        // 使 values-night 资源按 LIGHT/DARK 设置生效；SYSTEM 模式不干预，跟随系统
        val nightMode = SettingsPreferences(newBase).get(SettingsKeys.NIGHT_MODE)
        if (nightMode != NightMode.SYSTEM) {
            val config = Configuration(wrapped.resources.configuration)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) or
                if (nightMode == NightMode.DARK) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            applyOverrideConfiguration(config)
        }

        super.attachBaseContext(wrapped)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 启用边到边绘制（内容延伸到系统栏后方）
        // 系统栏图标颜色由 Compose 层的 Theme.kt 中的 SideEffect 动态管理
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WindowCompat.enableEdgeToEdge(window)
            window.isNavigationBarContrastEnforced = false
        } else {
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
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

            val appSettingsState = remember {
                AppSettingsState(
                    settingsPrefs,
                    onNightModeApplied = { recreate() },
                )
            }

            CompositionLocalProvider(
                LocalAppSettingsState provides appSettingsState,
            ) {
                电费查询Theme(
                    nightMode = appSettingsState.nightMode,
                    colorSource = appSettingsState.colorSource,
                    pureBlack = appSettingsState.pureBlack,
                    fontScale = appSettingsState.fontScale,
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
