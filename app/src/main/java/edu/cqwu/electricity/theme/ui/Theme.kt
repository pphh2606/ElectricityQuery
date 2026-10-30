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
import edu.cqwu.electricity.common.settings.LocalUiStyle
import edu.cqwu.electricity.common.settings.NightMode
import edu.cqwu.electricity.common.settings.ReduceMotion
import edu.cqwu.electricity.common.settings.ThemeColorSource
import edu.cqwu.electricity.common.settings.ThemeColorSpec
import edu.cqwu.electricity.common.settings.ThemePaletteStyle
import edu.cqwu.electricity.common.settings.TopBarStyle
import edu.cqwu.electricity.common.settings.UiStyle
import edu.cqwu.electricity.common.settings.isDark
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.LocalContentColor as LocalMiuixContentColor
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
    miuixMonet: Boolean = false,
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

    // 界面风格为 MIUI 时，用 miuix 的 ThemeController 取色板，再映射进 Material3 的
    // ColorScheme，让现有页面也呈现 MIUI 观感；同时外层套 MiuixTheme 供 miuix 组件使用。
    //
    // 这里固定用 Light/Dark 模式：miuix 会直接返回内置 fixed 色板（标志性 #3482FF 蓝），
    // 此时 keyColor/paletteStyle/colorSpec 不参与计算。
    //
    // 不能用 MonetLight/MonetDark：miuix 0.9.x 的 colorsFromSeed 是冲着
    // material-color-utilities 5.x 编译的，引用 com.materialkolor.scheme.DynamicScheme；
    // 本项目用 material-kolor 6.0.0，该包已改名为 com.materialkolor.dynamiccolor，
    // 一调 currentColors() 就 NoClassDefFoundError 闪退。
    // 「MIUIx 取色」因此改成在固定色板上替换主色系，见下面的 miuixColors。
    val miuixController = remember(uiStyle, darkTheme) {
        if (uiStyle != UiStyle.MIUI) {
            null
        } else {
            ThemeController(
                colorSchemeMode = if (darkTheme) ColorSchemeMode.Dark else ColorSchemeMode.Light,
                isDark = darkTheme,
            )
        }
    }

    // 开启「MIUIx 取色」时，把 MIUI 固定色板的主色系换成 MD3 引擎（material-kolor 6.0.0）
    // 算出的颜色：primary/secondary/三级容器与 error 跟着主题色走；
    // 页面灰底、白卡、分割线这些层级仍保留 MIUI 的值，避免 MIUI 观感被冲淡。
    val miuixColors = miuixController?.currentColors()?.let { base ->
        if (!miuixMonet) {
            base
        } else {
            base.copy(
                primary = colorScheme.primary,
                onPrimary = colorScheme.onPrimary,
                primaryContainer = colorScheme.primaryContainer,
                onPrimaryContainer = colorScheme.onPrimaryContainer,
                secondary = colorScheme.secondary,
                onSecondary = colorScheme.onSecondary,
                secondaryContainer = colorScheme.secondaryContainer,
                onSecondaryContainer = colorScheme.onSecondaryContainer,
                tertiaryContainer = colorScheme.tertiaryContainer,
                onTertiaryContainer = colorScheme.onTertiaryContainer,
                error = colorScheme.error,
                onError = colorScheme.onError,
                errorContainer = colorScheme.errorContainer,
                onErrorContainer = colorScheme.onErrorContainer,
                outline = colorScheme.outline,
                dividerLine = colorScheme.outlineVariant,
            )
        }
    }

    // miuix 的明暗层级是「background 灰底 < surface 亮面 < surfaceContainer* 沉台」，
    // 而 Material 3 是「surface 亮、surfaceContainer* 逐级变暗」。如果按字段同名直接拷，
    // 卡片（surfaceContainer 系）会比页面（surface）更暗，出现「灰卡沉在白底上」的反向层次，
    // 这正是 MIUI 风格发灰、没有 MIUI 味的主因。
    //
    // 这里按语义重排：页面底吃 background（MIUI 的 #F7F7F7），卡片/浮层一律吃 surface
    // （MIUI 的 #FCFCFC 白卡），只有需要「比卡片更沉一档」的控件（chip、输入框、图标圆底）
    // 才吃 surfaceVariant / surfaceContainer。surfaceTint 置透明，关掉 Material 的 tonal
    // 染色，否则白卡会被 primary 刷上一层蓝，看着发脏。
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
            // 页面：灰底 + 白面
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
            // 关掉 Material 的 tonal 叠色，保持 MIUI 的纯白卡
            surfaceTint = Color.Transparent,
            // 卡片 / 列表 / 弹窗：一律用 miuix 的亮面，保证「白卡浮在灰底上」
            surfaceContainerLowest = miuixColors.surface,
            surfaceContainerLow = miuixColors.surface,
            surfaceContainer = miuixColors.surface,
            // chip、日期胶囊这类「卡片里的控件」需要比白卡沉一点才看得出边界
            surfaceContainerHigh = miuixColors.surfaceVariant,
            // 图标圆底、输入框这类再沉一档
            surfaceContainerHighest = miuixColors.surfaceContainer,
            // 卡片底色：必须比页面亮，MIUI 里就是 surface 那层白
            surfaceBright = miuixColors.surface,
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
        LocalUiStyle provides uiStyle,
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
                // MiuixTheme 要待在 M3 主题的里层：M3 会把 ripple 提供给 LocalIndication，
                // 而 miuix 的按压反馈靠的是 MiuixTheme 提供的 MiuixIndication（整块叠一层
                // 6% 的遮罩），放在外层时这个 local 会被 M3 覆盖，MIUI 风格就变成水波涟漪了。
                if (miuixColors != null) {
                    MiuixTheme(colors = miuixColors) {
                        // miuix 的 Colors 重载不提供 LocalContentColor，图标默认 tint 会退回
                        // Color.Black；这里补一层，让没写 tint 的 MiuixIcon 跟主题前景色。
                        CompositionLocalProvider(
                            LocalMiuixContentColor provides miuixColors.onBackground,
                        ) {
                            preservedContent(content)
                        }
                    }
                } else {
                    preservedContent(content)
                }
            }
        }
        themedMaterial()
    }
}
