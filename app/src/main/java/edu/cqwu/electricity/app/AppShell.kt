package edu.cqwu.electricity.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import edu.cqwu.electricity.theme.ui.CustomSnackbarVisuals
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.theme.ui.SnackbarController
import edu.cqwu.electricity.theme.ui.LocalNavController
import edu.cqwu.electricity.theme.util.ToastUtils

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
    val snackbarController = remember { SnackbarController() }

    CompositionLocalProvider(
        LocalSnackbarController provides snackbarController,
        LocalNavController provides navController,
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            // AppNavGraph 使用独立的 fillMaxSize()，避免外部 modifier 中的 padding 叠加影响布局
            AppNavGraph(
                navController = navController,
                shortcutAppInfo = shortcutAppInfo,
                shortcutLaunchId = shortcutLaunchId,
                modifier = Modifier.fillMaxSize(),
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
                        Snackbar(
                            snackbarData = data,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = contentColor,
                            actionColor = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(8.dp),
                        )
                    }
                }
            )
        }
    }
}
