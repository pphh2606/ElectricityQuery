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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowInsetsControllerCompat
import com.materialkolor.material3.dynamicColorScheme
import edu.cqwu.electricity.common.settings.LocalAppSettingsState
import edu.cqwu.electricity.common.settings.NightMode
import edu.cqwu.electricity.common.settings.ReduceMotion
import edu.cqwu.electricity.common.settings.ThemeColorSource
import edu.cqwu.electricity.common.settings.ThemeColorSpec
import edu.cqwu.electricity.common.settings.ThemePaletteStyle
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

/**
 * 当前是否深色主题。
 *
 * 由 [电费查询Theme] 提供，供不方便/不需要重新推导明暗的页面直接读取，
 * 避免再用 `colorScheme.background.luminance() < 0.5f` 之类会在纯黑/高饱和色下误判的写法。
 */
val LocalAppDarkTheme = staticCompositionLocalOf { false }

@Composable
fun 电费查询Theme(
    nightMode: NightMode = NightMode.SYSTEM,
    colorSource: ThemeColorSource = ThemeColorSource.SystemDynamic,
    uiStyle: UiStyle = UiStyle.MATERIAL3,
    paletteStyle: ThemePaletteStyle = ThemePaletteStyle.TONAL_SPOT,
    colorSpec: ThemeColorSpec = ThemeColorSpec.SPEC_2025,
    pureBlack: Boolean = false,
    fontScale: Float = 1f,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = nightMode.isDark(isSystemInDarkTheme())

    // 取色源与引擎解耦：系统动态色只负责提供种子，风格/规范对两种来源一视同仁
    val seedColor = when {
        colorSource is ThemeColorSource.Custom -> colorSource.seedColor
        colorSource is ThemeColorSource.SystemDynamic && dynamicColor
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            colorResource(id = android.R.color.system_accent1_500)
        else -> DefaultSeedColor
    }

    val colorScheme = remember(seedColor, darkTheme, paletteStyle, colorSpec) {
        dynamicColorScheme(
            seedColor = seedColor,
            isDark = darkTheme,
            style = paletteStyle.toMaterialKolor(),
            specVersion = colorSpec.toMaterialKolor(),
        )
    }

    // 界面风格为 MIUI 时，用 miuix 的 ThemeController 按同一颗种子色生成 miuix 配色，
    // 再映射进 Material3 的 ColorScheme，让现有页面也呈现 MIUI 观感；
    // 同时外层套 MiuixTheme，后续 miuix 组件可直接使用。
    val miuixController = remember(uiStyle, darkTheme, seedColor, paletteStyle, colorSpec) {
        if (uiStyle == UiStyle.MIUI) {
            ThemeController(
                colorSchemeMode = if (darkTheme) ColorSchemeMode.Dark else ColorSchemeMode.Light,
                keyColor = seedColor,
                colorSpec = colorSpec.toMiuix(),
                paletteStyle = paletteStyle.toMiuix(),
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
            // miuix 的 Colors 没有 tertiary 档，用容器色近似
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

    // 换色/换风格/换规范时让整套色板平滑过渡；「减少动画」开启时直接瞬切
    val animatedColorScheme = themedColorScheme.animateAsState(
        enabled = LocalAppSettingsState.current.reduceMotion != ReduceMotion.ON,
    )

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
        LocalAppDarkTheme provides darkTheme,
        LocalDensity provides Density(
            density = baseDensity.density,
            fontScale = baseDensity.fontScale * fontScale,
        ),
    ) {
        val themedMaterial: @Composable () -> Unit = {
            // Material 3 Expressive：expressive 动效 + 大圆角形状；
            // 用户开启「减少动画」时退回标准动效，避免界面仍在弹跳
            MaterialExpressiveTheme(
                colorScheme = animatedColorScheme,
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
