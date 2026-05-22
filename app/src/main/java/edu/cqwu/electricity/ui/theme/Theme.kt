package edu.cqwu.electricity.ui.theme

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import edu.cqwu.electricity.data.local.NightMode
import edu.cqwu.electricity.data.local.ThemeColorSource
import edu.cqwu.electricity.data.local.TopBarStyle

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

data class NightModeState(
    val nightMode: NightMode,
    val onNightModeChange: (NightMode) -> Unit,
)

val LocalNightModeState = staticCompositionLocalOf<NightModeState> {
    error("No NightModeState provided")
}

data class ColorSourceState(
    val colorSource: ThemeColorSource,
    val onColorSourceChange: (ThemeColorSource) -> Unit,
)

val LocalColorSourceState = staticCompositionLocalOf<ColorSourceState> {
    error("No ColorSourceState provided")
}

/** 动画设置 */
data class AnimationSettings(
    val pageTransition: edu.cqwu.electricity.data.local.PageTransition,
    val onPageTransitionChange: (edu.cqwu.electricity.data.local.PageTransition) -> Unit,
    val reduceMotion: edu.cqwu.electricity.data.local.ReduceMotion,
    val onReduceMotionChange: (edu.cqwu.electricity.data.local.ReduceMotion) -> Unit,
)

val LocalAnimationSettings = staticCompositionLocalOf<AnimationSettings> {
    error("No AnimationSettings provided")
}

data class TopBarState(
    val style: TopBarStyle,
    val onStyleChange: (TopBarStyle) -> Unit,
)

val LocalTopBarState = staticCompositionLocalOf<TopBarState> {
    error("No TopBarState provided")
}

/** 二维码设置 */
data class QrCodeSettings(
    val colorMode: edu.cqwu.electricity.data.local.QrCodeColorMode,
    val onColorModeChange: (edu.cqwu.electricity.data.local.QrCodeColorMode) -> Unit,
    val cornerRadius: Int,
    val onCornerRadiusChange: (Int) -> Unit,
    val screenBrightnessEnabled: Boolean,
    val onScreenBrightnessEnabledChange: (Boolean) -> Unit,
)

val LocalQrCodeSettings = staticCompositionLocalOf<QrCodeSettings> {
    error("No QrCodeSettings provided")
}

@Composable
fun TopBarStyle.toTopAppBarColors(colorScheme: ColorScheme): TopAppBarColors {
    return when (this) {
        TopBarStyle.SURFACE -> TopAppBarDefaults.topAppBarColors(
            containerColor = colorScheme.surface,
            titleContentColor = colorScheme.onSurface,
        )
        TopBarStyle.SURFACE_CONTAINER_LOWEST -> TopAppBarDefaults.topAppBarColors(
            containerColor = colorScheme.surfaceContainerLowest,
            titleContentColor = colorScheme.onSurface,
        )
        TopBarStyle.SURFACE_CONTAINER_LOW -> TopAppBarDefaults.topAppBarColors(
            containerColor = colorScheme.surfaceContainerLow,
            titleContentColor = colorScheme.onSurface,
        )
        TopBarStyle.SURFACE_CONTAINER -> TopAppBarDefaults.topAppBarColors(
            containerColor = colorScheme.surfaceContainer,
            titleContentColor = colorScheme.onSurface,
        )
        TopBarStyle.SURFACE_CONTAINER_HIGH -> TopAppBarDefaults.topAppBarColors(
            containerColor = colorScheme.surfaceContainerHigh,
            titleContentColor = colorScheme.onSurface,
        )
        TopBarStyle.SURFACE_CONTAINER_HIGHEST -> TopAppBarDefaults.topAppBarColors(
            containerColor = colorScheme.surfaceContainerHighest,
            titleContentColor = colorScheme.onSurface,
        )
        TopBarStyle.SURFACE_VARIANT -> TopAppBarDefaults.topAppBarColors(
            containerColor = colorScheme.surfaceVariant,
            titleContentColor = colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun 电费查询Theme(
    nightMode: NightMode = NightMode.SYSTEM,
    colorSource: ThemeColorSource = ThemeColorSource.SystemDynamic,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (nightMode) {
        NightMode.SYSTEM -> isSystemInDarkTheme()
        NightMode.LIGHT -> false
        NightMode.DARK -> true
    }

    val colorScheme = when {
        colorSource is ThemeColorSource.SystemDynamic && dynamicColor
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        colorSource is ThemeColorSource.Custom -> {
            generateColorSchemeFromSeed(
                seedColor = colorSource.seedColor,
                darkTheme = darkTheme
            )
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // 根据 darkTheme 动态更新系统状态栏和导航栏图标颜色
    val view = LocalView.current
    val window = (LocalContext.current as? ComponentActivity)?.window
    SideEffect {
        window?.let {
            val controller = WindowInsetsControllerCompat(it, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
