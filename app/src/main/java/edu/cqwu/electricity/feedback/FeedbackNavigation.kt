package edu.cqwu.electricity.feedback

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import edu.cqwu.electricity.common.navigation.Routes
import edu.cqwu.electricity.app.animatedComposable
import edu.cqwu.electricity.feedback.ui.FeedbackScreen
import edu.cqwu.electricity.common.settings.AppSettingsState

/**
 * 本模块的路由注册（由 [edu.cqwu.electricity.app.AppNavGraph] 装配）。
 *
 * 新增本模块页面时只改这个文件，不用动 `app/NavGraph.kt`。
 */
internal fun NavGraphBuilder.feedbackGraph(
    navController: NavHostController,
    settings: AppSettingsState,
) {
    animatedComposable(settings = settings, route = Routes.FEEDBACK) {
        FeedbackScreen(
            onBack = { navController.popBackStack() },
        )
    }
}
