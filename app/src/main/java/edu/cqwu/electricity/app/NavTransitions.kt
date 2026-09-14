package edu.cqwu.electricity.app

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import edu.cqwu.electricity.common.settings.AppSettingsState
import edu.cqwu.electricity.common.settings.PageTransition
import edu.cqwu.electricity.common.settings.ReduceMotion

/**
 * 页面转场动画与「带动画的路由」注册扩展。
 *
 * 从 `NavGraph.kt` 拆出：装配层只负责"哪条路由对应哪个页面"，转场规则单独成文件更清楚。
 */

/**
 * 从动画设置生成过渡 EnterTransition。
 * 注意：减少动画的判断放在过渡 lambda 内部（每次转场计算时读取当前设置），
 * 切换"减少动画"后下一次页面切换即生效，无需重启应用。
 */
internal fun enterAnim(settings: AppSettingsState): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition? = {
    if (settings.reduceMotion == ReduceMotion.ON) {
        EnterTransition.None
    } else {
        val d = 300
        when (settings.pageTransition) {
            PageTransition.NONE -> EnterTransition.None
            PageTransition.SLIDE -> slideInHorizontally(tween(d)) { it }
            PageTransition.SLIDE_VERTICAL -> slideInVertically(tween(d)) { it }
            PageTransition.FADE -> fadeIn(tween(d))
            PageTransition.FADE_SCALE -> scaleIn(tween(d), 0.9f) + fadeIn(tween(d))
            PageTransition.CUPERTINO -> slideInHorizontally(tween(350)) { it } + fadeIn(tween(350))
        }
    }
}

internal fun exitAnim(settings: AppSettingsState): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition? = {
    if (settings.reduceMotion == ReduceMotion.ON) {
        ExitTransition.None
    } else {
        val d = 300
        when (settings.pageTransition) {
            PageTransition.NONE -> ExitTransition.None
            PageTransition.SLIDE -> slideOutHorizontally(tween(d)) { -it / 5 } + fadeOut(tween(d))
            PageTransition.SLIDE_VERTICAL -> slideOutVertically(tween(d)) { -it / 5 } + fadeOut(tween(d))
            PageTransition.FADE -> fadeOut(tween(d))
            PageTransition.FADE_SCALE -> scaleOut(tween(d), 0.9f) + fadeOut(tween(d))
            PageTransition.CUPERTINO -> slideOutHorizontally(tween(350)) { -it / 4 }
        }
    }
}

internal fun popEnterAnim(settings: AppSettingsState): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition? = {
    // 减少动画开启时，返回不做任何过渡（直接切换）
    if (settings.reduceMotion == ReduceMotion.ON) {
        EnterTransition.None
    } else {
        val d = 300
        when (settings.pageTransition) {
            PageTransition.NONE -> EnterTransition.None
            PageTransition.SLIDE -> slideInHorizontally(tween(d)) { -it / 5 } + fadeIn(tween(d))
            PageTransition.SLIDE_VERTICAL -> slideInVertically(tween(d)) { -it / 5 } + fadeIn(tween(d))
            PageTransition.FADE -> fadeIn(tween(d))
            PageTransition.FADE_SCALE -> scaleIn(tween(d), 0.9f) + fadeIn(tween(d))
            PageTransition.CUPERTINO -> slideInHorizontally(tween(350)) { -it / 4 }
        }
    }
}

internal fun popExitAnim(settings: AppSettingsState): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition? = {
    // 减少动画开启时，返回不做任何过渡（直接切换）
    if (settings.reduceMotion == ReduceMotion.ON) {
        ExitTransition.None
    } else {
        val d = 300
        when (settings.pageTransition) {
            PageTransition.NONE -> ExitTransition.None
            PageTransition.SLIDE -> slideOutHorizontally(tween(d)) { it }
            PageTransition.SLIDE_VERTICAL -> slideOutVertically(tween(d)) { it }
            PageTransition.FADE -> fadeOut(tween(d))
            PageTransition.FADE_SCALE -> scaleOut(tween(d), 0.9f) + fadeOut(tween(d))
            PageTransition.CUPERTINO -> slideOutHorizontally(tween(350)) { it } + fadeOut(tween(350))
        }
    }
}

/**
 * 动画路由——自动附加页面过渡动画。
 * 调用方在 [AppNavGraph] 或各模块的 graph 函数中通过 [LocalAppSettingsState] 获取设置后传入。
 */
internal fun NavGraphBuilder.animatedComposable(
    settings: AppSettingsState,
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable(
        route = route,
        arguments = arguments,
        deepLinks = deepLinks,
        enterTransition = enterAnim(settings),
        exitTransition = exitAnim(settings),
        popEnterTransition = popEnterAnim(settings),
        popExitTransition = popExitAnim(settings),
        content = content,
    )
}
