package edu.cqwu.electricity.theme.ui

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsControllerCompat
import com.materialkolor.dynamicColorScheme
import dev.chrisbanes.haze.HazeDefaults
import edu.cqwu.electricity.settings.data.NightMode
import edu.cqwu.electricity.settings.data.ThemeColorSource
import edu.cqwu.electricity.settings.data.TopBarStyle

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

@Stable
class SheetVisibilityState {
    private val openCount = mutableStateOf(0)
    private val blurProgressState = mutableStateOf(0f)

    val active: Boolean get() = openCount.value > 0

    var blurProgress: Float
        get() = blurProgressState.value
        set(value) {
            blurProgressState.value = value.coerceIn(0f, 1f)
        }

    fun open() {
        openCount.value++
    }

    fun close() {
        openCount.value = (openCount.value - 1).coerceAtLeast(0)
    }
}

val LocalSheetVisibilityState = staticCompositionLocalOf { SheetVisibilityState() }

fun isHazeBlurSupported(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && HazeDefaults.blurEnabled()

val LocalNavController = staticCompositionLocalOf<NavHostController> {
    error("No NavController provided")
}

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
    pureBlack: Boolean = false,
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
            dynamicColorScheme(
                seedColor = colorSource.seedColor,
                isDark = darkTheme
            )
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
