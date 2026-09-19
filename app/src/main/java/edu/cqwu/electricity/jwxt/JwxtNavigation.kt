package edu.cqwu.electricity.jwxt

import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import edu.cqwu.electricity.common.navigation.Routes
import edu.cqwu.electricity.app.animatedComposable
import edu.cqwu.electricity.jwxt.core.model.JwxtScheduleTarget
import edu.cqwu.electricity.jwxt.core.model.ScheduleTargetType
import edu.cqwu.electricity.jwxt.moreservice.ui.JwxtMoreServiceScreen
import edu.cqwu.electricity.jwxt.score.ui.JwxtScoreScreen
import edu.cqwu.electricity.jwxt.schedulequery.ui.JwxtScheduleQueryScreen
import edu.cqwu.electricity.jwxt.schedulequery.ui.JwxtScheduleTargetScreen
import edu.cqwu.electricity.jwxt.timetable.ui.JwxtTimetableScreen
import edu.cqwu.electricity.jwxt.home.ui.JwxtHomeScreen
import edu.cqwu.electricity.common.settings.AppSettingsState

/**
 * 本模块的路由注册（由 [edu.cqwu.electricity.app.AppNavGraph] 装配）。
 *
 * 新增本模块页面时只改这个文件，不用动 `app/NavGraph.kt`。
 */
internal fun NavGraphBuilder.jwxtGraph(
    navController: NavHostController,
    settings: AppSettingsState,
) {
    // 教务首页（jwfw /jwmobile 本地化页；入口：首页「本科教务系统」「移动教务」）
    animatedComposable(settings = settings, route = Routes.JWXT_HOME) {
        JwxtHomeScreen(
            onBack = { navController.popBackStack() },
        )
    }

    // 教务「更多服务」（二级页；入口：教务首页九宫格的「更多服务」格子）
    animatedComposable(settings = settings, route = Routes.JWXT_MORE_SERVICE) {
        JwxtMoreServiceScreen(
            onBack = { navController.popBackStack() },
        )
    }

    // 教务「成绩查询」（二级页；入口：教务首页九宫格的「成绩查询」格子）
    animatedComposable(settings = settings, route = Routes.JWXT_SCORE) {
        JwxtScoreScreen(
            onBack = { navController.popBackStack() },
        )
    }

    // 教务「周课表」（二级页；入口：App 首页的「我的课表」格子）
    animatedComposable(settings = settings, route = Routes.JWXT_TIMETABLE) {
        JwxtTimetableScreen(
            onBack = { navController.popBackStack() },
        )
    }

    // 教务「全校课表查询」入口页（二级页；入口：教务首页九宫格的「全校课表查询」格子）
    animatedComposable(settings = settings, route = Routes.JWXT_SCHEDULE_QUERY) {
        JwxtScheduleQueryScreen(
            onBack = { navController.popBackStack() },
            onSelectType = { type -> navController.navigate(Routes.jwxtScheduleTargetRoute(type.kblx)) },
        )
    }

    // 选择页：教室 / 教师 / 班级三种界面共用同一个页面，靠路由参数区分
    animatedComposable(settings = settings, route = Routes.JWXT_SCHEDULE_TARGET) { entry ->
        val type = ScheduleTargetType.fromKblx(entry.arguments?.getString("scheduleType").orEmpty())
        if (type == null) {
            // 参数不合法（正常进不到这里）：退回上一页，避免一直停在白屏
            LaunchedEffect(Unit) { navController.popBackStack() }
        } else {
            JwxtScheduleTargetScreen(
                type = type,
                onBack = { navController.popBackStack() },
                onOpenTable = { target ->
                    navController.navigate(
                        Routes.jwxtScheduleTableRoute(
                            kblx = target.type.kblx,
                            code = target.code,
                            campus = target.campusCode,
                            name = target.name,
                        )
                    )
                },
            )
        }
    }

    // 通用课表页：「查任意对象的课表」；「我的课表」走上面的 JWXT_TIMETABLE（target = null）
    animatedComposable(settings = settings, route = Routes.JWXT_SCHEDULE_TABLE) { entry ->
        val args = entry.arguments
        val type = ScheduleTargetType.fromKblx(args?.getString("kblx").orEmpty())
        if (type == null) {
            LaunchedEffect(Unit) { navController.popBackStack() }
        } else {
            JwxtTimetableScreen(
                onBack = { navController.popBackStack() },
                target = JwxtScheduleTarget(
                    type = type,
                    code = args?.getString("code").orEmpty(),
                    name = args?.getString("name").orEmpty(),
                    campusCode = args?.getString("campus").orEmpty(),
                ),
            )
        }
    }
}
