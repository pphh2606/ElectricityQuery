package edu.cqwu.electricity.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import edu.cqwu.electricity.theme.ui.CustomSnackbarVisuals
import edu.cqwu.electricity.common.settings.LocalAppSettingsState
import edu.cqwu.electricity.common.navigation.LocalNavController
import edu.cqwu.electricity.common.ui.LocalSheetVisibilityState
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.common.ui.SheetVisibilityState
import edu.cqwu.electricity.theme.ui.SnackbarController
import edu.cqwu.electricity.common.ui.isHazeBlurSupported
import edu.cqwu.electricity.common.util.ToastUtils

/**
 * 应用外壳
 *
 * 直接透传 [AppNavGraph]，不再包裹 Scaffold 和底栏。
 * 底栏已下放到 HomeScreen 和 ProfileScreen 各自的 Scaffold 内部，
 * 使得页面切换时的过渡动画（滑动／淡入淡出）能将底栏一同带动，动画效果完整统一。
 *
 * 同时提供全局 Snackbar 覆盖层，位于 NavHost 之上，
 * 生命周期跟随 Activity，不受页面导航影响。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(
    navController: NavHostController,
    shortcutAppInfo: edu.cqwu.electricity.shortcut.util.ShortcutHelper.ShortcutAppInfo? = null,
    shortcutLaunchId: Int = 0,
    modifier: Modifier = Modifier,
) {
    val appSettings = LocalAppSettingsState.current
    val blurRadiusDp = appSettings.sheetBlurRadius.dp
    val backdropBlurStyle = remember(blurRadiusDp) {
        HazeStyle(
            blurRadius = blurRadiusDp,
            noiseFactor = 0f,
            tints = emptyList(),
        )
    }
    val snackbarController = remember { SnackbarController() }
    val sheetVisibilityState = remember { SheetVisibilityState() }
    val blurProgress by animateFloatAsState(
        targetValue = if (sheetVisibilityState.active) sheetVisibilityState.blurProgress else 0f,
        animationSpec = tween(durationMillis = 300),
    )
    val useForegroundBlur =
        appSettings.sheetBlurEnabled &&
            isHazeBlurSupported() &&
            (sheetVisibilityState.active || blurProgress > 0.001f)

    CompositionLocalProvider(
        LocalSnackbarController provides snackbarController,
        LocalNavController provides navController,
        LocalSheetVisibilityState provides sheetVisibilityState,
    ) {
        Box(
            modifier = modifier.fillMaxSize()
        ) {
            // 启动横切逻辑：自动更新检查 / Cookie 验证 / 快捷方式分发 / 外部应用弹窗
            AppLaunchEffects(
                navController = navController,
                shortcutAppInfo = shortcutAppInfo,
                shortcutLaunchId = shortcutLaunchId,
            )

            // AppNavGraph 使用独立的 fillMaxSize()，避免外部 modifier 中的 padding 叠加影响布局
            AppNavGraph(
                navController = navController,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (useForegroundBlur) {
                            Modifier.hazeEffect(style = backdropBlurStyle) {
                                blurRadius = blurRadiusDp * blurProgress
                            }
                        } else {
                            Modifier
                        }
                    ),
            )

            // 全局 Snackbar 覆盖层——位于 NavHost 之上
            // 页面切换时不会销毁，Snackbar 动画持续播放
            SnackbarHost(
                hostState = snackbarController.hostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 60.dp),
                snackbar = { data ->
                    // 从 SnackbarVisuals 中提取类型信息，确保多 Snackbar 排队时颜色与实例一一绑定
                    val visuals = data.visuals as? CustomSnackbarVisuals
                    val type = visuals?.type ?: ToastUtils.Type.ERROR

                    @Suppress("DEPRECATION")
                    val swipeDismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value != SwipeToDismissBoxValue.Settled) {
                                data.dismiss()
                                true
                            } else {
                                false
                            }
                        }
                    )

                    val contentColor = when (type) {
                        ToastUtils.Type.ERROR   -> Color(0xFFC62828) // Red 800
                        ToastUtils.Type.SUCCESS -> Color(0xFF2E7D32) // Green 800
                    }

                    SwipeToDismissBox(
                        state = swipeDismissState,
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 16.dp),
                        enableDismissFromStartToEnd = true,
                        enableDismissFromEndToStart = true,
                        backgroundContent = {}
                    ) {
                        ShadowlessSnackbar(
                            data = data,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = contentColor,
                        )
                    }
                }
            )
        }
    }
}

/**
 * 自绘的 Snackbar 卡片：外观与 M3 的 `Snackbar` 一致，但不画阴影。
 *
 * 必须自绘：M3 的 `Snackbar` 内部固定了 6dp `shadowElevation` 且没有暴露参数，关不掉；
 * 那层阴影在 API 28 以下不按形状裁剪，8dp 圆角会退化成发灰的方块。
 * `tonalElevation` 一并置 0——否则 M3 会在 containerColor 上再叠一层色调，颜色就不是调用方给的那个。
 */
@Composable
private fun ShadowlessSnackbar(
    data: SnackbarData,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            // 左右间距照 M3 的规格：正文侧 16dp，action 侧 8dp（按钮自带内边距）
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = data.visuals.message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )

            data.visuals.actionLabel?.let { label ->
                TextButton(onClick = data::performAction) {
                    Text(
                        text = label,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
