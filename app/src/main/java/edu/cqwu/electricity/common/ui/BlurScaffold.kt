package edu.cqwu.electricity.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import edu.cqwu.electricity.common.settings.LocalAppSettingsState
import edu.cqwu.electricity.theme.ui.currentTopBarColors

/**
 * 标题栏毛玻璃当前是否生效：系统支持模糊 **且** 用户在「个性化」里开着开关。
 *
 * 页面不要直接判断 `isHazeBlurSupported()`，统一走这里，开关才能一处生效。
 */
@Composable
fun isHeaderBlurOn(): Boolean =
    isHazeBlurSupported() && LocalAppSettingsState.current.headerBlurEnabled

/**
 * 毛玻璃模糊层 modifier：支持时按用户在「高级模糊设置」里的参数
 * （`blurRadius` / `noiseFactor` / `alpha` / `backgroundColor` / `performanceMode`）模糊
 * [hazeState] 里的源，不支持或用户关掉开关时退回不透明底色。
 *
 * 抽出来是为了让「自己搭叠层」的页面（比如课表要同时浮起顶部、左侧几块）也能共用
 * 同一套观感，而不必各复制一份 HazeBlurStyle。
 */
@Composable
fun rememberHazeBlurModifier(hazeState: HazeState): Modifier {
    val settings = LocalAppSettingsState.current
    // 底色跟随「个性化 → 标题栏样式」；模糊开着时作为模糊内容的底、关闭时就是不透明标题栏色。
    // 这样标题栏样式在所有页面、所有开关组合下都生效
    val topBarColor = currentTopBarColors().containerColor
    // 高级参数：名字与 HazeBlurStyle 一一对应，默认值就是全项目统一采用的观感
    val blurRadius = settings.headerBlurRadius.dp
    val noiseFactor = settings.headerBlurNoise
    val alpha = settings.headerBlurAlpha
    val backgroundColorAlpha = settings.headerBlurBackgroundAlpha
    val performanceMode = settings.headerBlurPerformance
    return if (isHeaderBlurOn()) {
        Modifier.hazeBlur(
            input = HazeInput.Sources(hazeState),
            style = remember(topBarColor, blurRadius, noiseFactor, alpha, backgroundColorAlpha) {
                HazeBlurStyle {
                    blurRadius(blurRadius)
                    noiseFactor(noiseFactor)
                    alpha(alpha)
                    backgroundColor(topBarColor.copy(alpha = backgroundColorAlpha))
                }
            },
            performanceMode = performanceMode.toHaze(),
        )
    } else {
        Modifier.background(topBarColor)
    }
}

/**
 * 毛玻璃源层 modifier：开关关闭时返回空 modifier。
 *
 * 必须和 [rememberHazeBlurModifier] 配套使用——关掉模糊后如果还挂着 `hazeSource`，
 * 内容仍会每帧被录进 GraphicsLayer，白白耗性能却没有模糊效果。
 */
@Composable
fun rememberHazeSourceModifier(hazeState: HazeState): Modifier =
    if (isHeaderBlurOn()) Modifier.hazeSource(hazeState) else Modifier

/**
 * 标题栏毛玻璃骨架：内容铺满并作为模糊源，[topBar] 浮在内容之上做毛玻璃。
 *
 * 与普通 Scaffold 的区别：内容与标题栏**重叠**，内容才能滚到标题栏背后；[content] 会拿到
 * 浮层实测高度 [topInset]，页面自行把它作为列表第一个 item 的留白——不能用 contentPadding，
 * 那样内容永远进不到标题栏背后，模糊源就是空的。
 *
 * 模糊层用不透明的 backgroundColor 打底（Haze 的 tint 画在模糊上面，盖不住源内容），
 * 低版本没有 RenderEffect 时退回不透明标题栏。
 *
 * @param topBar 标题栏，内部请用 [transparentTopBarColors] 让底色透明
 * @param content 页面内容，参数为顶部浮层高度
 */
@Composable
fun BlurScaffold(
    topBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (topInset: Dp) -> Unit,
) {
    val hazeState = remember { HazeState() }
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val topInset = with(LocalDensity.current) { headerHeightPx.toDp() }

    val headerModifier = rememberHazeBlurModifier(hazeState)

    Box(modifier = modifier.fillMaxSize()) {
        // 内容层：整页作为模糊源，先绘制（开关关闭时不再录源，避免白耗）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .then(rememberHazeSourceModifier(hazeState))
        ) {
            content(topInset)
        }

        // 标题栏浮层：压在内容之上，模糊其背后的内容
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onSizeChanged { headerHeightPx = it.height }
                .then(headerModifier)
                // 标题栏空白区域的点击不要穿透到底下的列表
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            topBar()
        }
    }
}

/**
 * 毛玻璃标题栏专用配色：沿用用户标题栏样式的内容色，只把底色改成透明，
 * 否则不透明底色会整个盖住 Haze 模糊层。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun transparentTopBarColors(): TopAppBarColors {
    val base = currentTopBarColors()
    // 模糊关闭（用户关掉，或系统不支持）时不需要透明：直接把标题栏样式原样交出去，
    // 否则容器色永远是透明的，这类页面的「标题栏样式」设置就不再生效
    if (!isHeaderBlurOn()) return base
    return TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = Color.Transparent,
        navigationIconContentColor = base.navigationIconContentColor,
        titleContentColor = base.titleContentColor,
        actionIconContentColor = base.actionIconContentColor,
    )
}
