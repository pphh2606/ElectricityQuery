package edu.cqwu.electricity.theme.ui

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowInsetsControllerCompat
import com.materialkolor.dynamicColorScheme
import edu.cqwu.electricity.common.settings.LocalAppSettingsState
import edu.cqwu.electricity.common.settings.NightMode
import edu.cqwu.electricity.common.settings.ReduceMotion
import edu.cqwu.electricity.common.settings.ThemeColorSource
import edu.cqwu.electricity.common.settings.TopBarStyle
import edu.cqwu.electricity.common.settings.UiStyle
import edu.cqwu.electricity.common.settings.isDark
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/** Android 12 以下没有系统动态取色，用它作为固定种子生成整套默认色板 */
private val DefaultSeedColor = Color(0xFF66CCFF)

@OptIn(ExperimentalMaterial3Api::class)
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
fun currentTopBarColors(): TopAppBarColors =
    LocalAppSettingsState.current.topBarStyle.toTopAppBarColors(MaterialTheme.colorScheme)

@Composable
fun 电费查询Theme(
    nightMode: NightMode = NightMode.SYSTEM,
    colorSource: ThemeColorSource = ThemeColorSource.SystemDynamic,
    uiStyle: UiStyle = UiStyle.MATERIAL3,
    pureBlack: Boolean = false,
    fontScale: Float = 1f,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = nightMode.isDark(isSystemInDarkTheme())

    val seedColor = when {
        colorSource is ThemeColorSource.Custom -> colorSource.seedColor
        colorSource is ThemeColorSource.SystemDynamic && dynamicColor
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            colorResource(id = android.R.color.system_accent1_500)
        else -> DefaultSeedColor
    }

    val colorScheme = when {
        colorSource is ThemeColorSource.SystemDynamic && dynamicColor
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        colorSource is ThemeColorSource.Custom -> {
            dynamicColorScheme(
                seedColor = colorSource.seedColor,
                isDark = darkTheme
            )
        }
        // Android 12 以下取不到系统动态色，用固定种子生成整套色板
        else -> dynamicColorScheme(seedColor = DefaultSeedColor, isDark = darkTheme)
    }

    // 界面风格为 MIUI 时，用 miuix 的 ThemeController 按同一颗种子色生成 miuix 配色，
    // 再映射进 Material3 的 ColorScheme，让现有页面也呈现 MIUI 观感；
    // 同时外层套 MiuixTheme，后续 miuix 组件可直接使用。
    val miuixController = remember(uiStyle, darkTheme, seedColor) {
        if (uiStyle == UiStyle.MIUI) {
            ThemeController(
                colorSchemeMode = if (darkTheme) ColorSchemeMode.Dark else ColorSchemeMode.Light,
                keyColor = seedColor,
                isDark = darkTheme,
            )
        } else {
            null
        }
    }
    val miuixColors = if (miuixController != null) miuixController.currentColors() else null

    val themedColorScheme = if (miuixColors != null) {
        colorScheme.copy(
            primary = miuixColors.primary,
            onPrimary = miuixColors.onPrimary,
            primaryContainer = miuixColors.primaryContainer,
            onPrimaryContainer = miuixColors.onPrimaryContainer,
            secondary = miuixColors.secondary,
            onSecondary = miuixColors.onSecondary,
            secondaryContainer = miuixColors.secondaryContainer,
            onSecondaryContainer = miuixColors.onSecondaryContainer,
            tertiary = miuixColors.tertiaryContainer,
            onTertiary = miuixColors.onTertiaryContainer,
            tertiaryContainer = miuixColors.tertiaryContainer,
            onTertiaryContainer = miuixColors.onTertiaryContainer,
            background = miuixColors.background,
            onBackground = miuixColors.onBackground,
            surface = miuixColors.surface,
            onSurface = miuixColors.onSurface,
            surfaceVariant = miuixColors.surfaceVariant,
            onSurfaceVariant = miuixColors.onSurfaceVariantSummary,
            error = miuixColors.error,
            onError = miuixColors.onError,
            errorContainer = miuixColors.errorContainer,
            onErrorContainer = miuixColors.onErrorContainer,
            outline = miuixColors.outline,
            outlineVariant = miuixColors.dividerLine,
            surfaceTint = miuixColors.primary,
            surfaceContainer = miuixColors.surfaceContainer,
            surfaceContainerHigh = miuixColors.surfaceContainerHigh,
            surfaceContainerHighest = miuixColors.surfaceContainerHighest,
            // miuix 没有 Low/Lowest 两档，用最接近的层级近似
            surfaceContainerLow = miuixColors.surfaceContainer,
            surfaceContainerLowest = miuixColors.background,
            // 卡片/行取「永远比页面亮」档：miuix 里最接近的是最高容器档
            surfaceBright = miuixColors.surfaceContainerHighest,
            surfaceDim = miuixColors.background,
        )
    } else {
        colorScheme
    }.let { base ->
        if (darkTheme && pureBlack) {
            base.copy(
                background = Color.Black,
                surface = Color.Black,
                surfaceContainerLowest = Color.Black,
                surfaceContainerLow = Color(0xFF0A0A0A),
                surfaceContainer = Color(0xFF121212),
                surfaceContainerHigh = Color(0xFF1A1A1A),
                surfaceContainerHighest = Color(0xFF242424),
                // 卡片/行用的「永远比 surface 亮」档：纯黑下 surface 是 0，
                // 这里给一档比 Highest 更亮的灰，保证卡片仍浮在页面之上
                surfaceBright = Color(0xFF2C2C2C),
                surfaceDim = Color.Black,
                surfaceVariant = Color(0xFF1C1C1C),
            )
        } else {
            base
        }
    }

    // 根据 darkTheme 动态更新系统状态栏和导航栏图标颜色
    val view = LocalView.current
    val window = LocalActivity.current?.window
    SideEffect {
        window?.let {
            val controller = WindowInsetsControllerCompat(it, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    // 切换界面风格时不重建内容，保住当前页面与滚动位置
    val preservedContent = remember {
        movableContentOf<@Composable () -> Unit> { targetContent -> targetContent() }
    }

    val baseDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = baseDensity.density,
            fontScale = baseDensity.fontScale * fontScale,
        ),
    ) {
        val themedMaterial: @Composable () -> Unit = {
            // Material 3 Expressive：expressive 动效 + 大圆角形状；
            // 用户开启「减少动画」时退回标准动效，避免界面仍在弹跳
            MaterialExpressiveTheme(
                colorScheme = themedColorScheme,
                motionScheme = if (LocalAppSettingsState.current.reduceMotion == ReduceMotion.ON) {
                    MotionScheme.standard()
                } else {
                    MotionScheme.expressive()
                },
                typography = Typography,
            ) {
                preservedContent(content)
            }
        }
        if (miuixColors != null) {
            MiuixTheme(colors = miuixColors) {
                themedMaterial()
            }
        } else {
            themedMaterial()
        }
    }
}
