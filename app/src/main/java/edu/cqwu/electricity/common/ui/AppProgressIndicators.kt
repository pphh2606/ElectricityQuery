package edu.cqwu.electricity.common.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.common.settings.LocalAppSettingsState
import edu.cqwu.electricity.common.settings.LocalUiStyle
import edu.cqwu.electricity.common.settings.ReduceMotion
import edu.cqwu.electricity.common.settings.UiStyle
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 线性进度条（determinate），默认走 M3E 波浪版；开启「减少动画」时退回普通版——
 * 波浪动画是库内部的 rememberInfiniteTransition，不读 MotionScheme，只能在这里自己判断。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppLinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    if (LocalAppSettingsState.current.reduceMotion == ReduceMotion.ON) {
        LinearProgressIndicator(
            progress = progress,
            modifier = modifier,
            color = color,
            trackColor = trackColor,
        )
    } else {
        LinearWavyProgressIndicator(
            progress = progress,
            modifier = modifier,
            color = color,
            trackColor = trackColor,
        )
    }
}

/**
 * 环形加载指示器（indeterminate）。
 *
 * MIUI 模式走 miuix 的 [InfiniteProgressIndicator]（圆弧 + 绕行小圆点），采用其默认 20dp；
 * M3 模式默认走 M3E 波浪版，波浪版内部固定 size(48dp)，标准版这里也锁成 48dp，两者切换时不跳。
 * 行内的小转圈（16dp / 20dp / 24dp）请继续用 [CircularProgressIndicator]。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppCircularProgressIndicator(modifier: Modifier = Modifier) {
    when {
        LocalUiStyle.current == UiStyle.MIUI -> InfiniteProgressIndicator(
            modifier = modifier,
            color = MiuixTheme.colorScheme.primary,
        )
        LocalAppSettingsState.current.reduceMotion == ReduceMotion.ON ->
            CircularProgressIndicator(modifier = modifier.size(48.dp))
        else -> CircularWavyProgressIndicator(modifier = modifier)
    }
}
