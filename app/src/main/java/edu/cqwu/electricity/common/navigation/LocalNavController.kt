package edu.cqwu.electricity.common.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController

/**
 * 全局 NavController 的 CompositionLocal。
 *
 * 从 `theme/ui/Theme.kt` 拆出：它属于导航层，与主题无关。由 `app/AppShell` 提供，
 * 各页面用 `LocalNavController.current` 取用。
 */
val LocalNavController = staticCompositionLocalOf<NavHostController> {
    error("No NavController provided")
}
