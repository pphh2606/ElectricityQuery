package edu.cqwu.electricity.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

/**
 * 延迟内容渲染组件。
 *
 * 在导航动画期间（默认 350ms）仅显示占位 UI，
 * 动画完成后才渲染实际内容，避免重型组件（如 WebView）
 * 和网络请求与动画帧竞争导致卡顿。
 *
 * 使用方式：
 * ```
 * DeferredContent(delayMs = 350L) {
 *     // 需要延迟渲染的重型内容
 *     HeavyComposable()
 * }
 * ```
 *
 * @param delayMs 延迟毫秒数，应略大于页面切换动画时长（slide 动画默认 ~300ms）
 * @param placeholder 动画期间显示的占位 UI，默认居中旋转进度指示器
 * @param content 动画完成后渲染的实际内容
 */
@Composable
fun DeferredContent(
    delayMs: Long = 350L,
    placeholder: @Composable () -> Unit = { DefaultPlaceholder() },
    content: @Composable () -> Unit
) {
    var ready by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(delayMs)
        ready = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 占位 UI（动画期间显示）
        if (!ready) {
            placeholder()
        }
        // 真实内容（动画完成后淡入显示）
        AnimatedVisibility(
            visible = ready,
            enter = fadeIn()
        ) {
            content()
        }
    }
}

/**
 * 默认占位 UI：居中的 Material3 CircularProgressIndicator
 */
@Composable
fun DefaultPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary
        )
    }
}
