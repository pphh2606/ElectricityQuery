package edu.cqwu.electricity.person

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import edu.cqwu.electricity.common.navigation.Routes
import edu.cqwu.electricity.app.animatedComposable
import edu.cqwu.electricity.person.ui.PersonSearchScreen
import edu.cqwu.electricity.common.settings.AppSettingsState

/**
 * 本模块的路由注册（由 [edu.cqwu.electricity.app.AppNavGraph] 装配）。
 *
 * 新增本模块页面时只改这个文件，不用动 `app/NavGraph.kt`。
 */
internal fun NavGraphBuilder.personGraph(
    navController: NavHostController,
    settings: AppSettingsState,
) {
    // 查找人员页面
    animatedComposable(settings = settings, route = Routes.PERSON_SEARCH) {
        PersonSearchScreen(
            onBack = { navController.popBackStack() },
            onReLogin = { navController.navigate(Routes.loginRoute()) },
        )
    }
}
