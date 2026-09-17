package edu.cqwu.electricity.common.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 「滚动时隐藏底栏」的状态。
 *
 * 判定放在这里、由 [trackBottomBarScroll] 挂到承载滚动的容器上，所以各个页面都不用改：
 * 只要列表走 Compose 的滚动体系（`LazyColumn` / `verticalScroll`），纵向位移都会冒泡上来。
 *
 * 方向语义（与 AndroidX `TopAppBarScrollBehavior` 一致）：`y < 0` 是手指向上滑、看后面的内容 → 收起；
 * `y > 0` 是向下滑、回到前面的内容 → 恢复。**方向一变就切换**，只在 [deadZonePx] 上留一点点余量：
 * 手指按在屏幕上本身就有 ±1px 级抖动，完全零死区会让底栏反复抽搐。
 */
@Stable
class BottomBarScrollState(
    private val deadZonePx: Float,
    /** 初始是否收起；跨页面恢复时由 [rememberBottomBarScrollState] 的保存器传回来 */
    hidden: Boolean = false,
) {

    /** 底栏是否已收起 */
    var hidden by mutableStateOf(hidden)
        private set

    /** 上次确认过的方向：-1 向上滑（看后面的内容）、1 向下滑、0 还没确认过 */
    private var lastDirection = 0

    /** 跨过死区用的净位移 */
    private var pending = 0f

    /** 让底栏立即回到可见：切 tab、打开弹窗、关掉开关时调用 */
    fun show() {
        hidden = false
        lastDirection = 0
        pending = 0f
    }

    /** 挂到承载滚动的容器上即可，见 [trackBottomBarScroll] */
    internal val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            // 只认手指拖拽：惯性滑动到末端会反向回弹，跟着它动会闪
            if (source != NestedScrollSource.UserInput) return Offset.Zero
            // 优先用「被列表消费掉的位移」；列表消费不动时（内容不足一屏、滑不动）退回用未消费的
            // 位移，否则这类页面永远收不起底栏、被它压住的底部内容也就一直看不到。
            // 两者符号含义一致（都是滚动意图的方向），误判只会落在「本来就已经是这个状态」的方向上。
            onDrag(if (consumed.y != 0f) consumed.y else available.y)
            return Offset.Zero
        }
    }

    /**
     * 同方向重复位移直接忽略（[lastDirection] 挡住重复切换）；方向反转时只要净位移越过
     * [deadZonePx] 就立刻切换，因此手感是「即滑即动」。
     */
    private fun onDrag(delta: Float) {
        if (delta == 0f) return
        val direction = if (delta < 0f) -1 else 1
        if (direction == lastDirection) return

        pending += delta
        if (direction < 0 && pending > -deadZonePx) return
        if (direction > 0 && pending < deadZonePx) return

        lastDirection = direction
        pending = 0f
        hidden = direction < 0
    }
}

@Composable
fun rememberBottomBarScrollState(deadZone: Dp = 2.dp): BottomBarScrollState {
    val deadZonePx = with(LocalDensity.current) { deadZone.toPx() }
    // 用 rememberSaveable：本页被销毁重建（进子页面再返回、进程被系统回收）后，底栏保持离开时的收起状态。
    // 只存「是否收起」这一个开关——死区是固定参数，恢复时按当前值重建即可。
    return rememberSaveable(
        saver = Saver(
            save = { it.hidden },
            restore = { saved -> BottomBarScrollState(deadZonePx, saved) },
        )
    ) {
        BottomBarScrollState(deadZonePx)
    }
}

/**
 * 「切页 / 打开弹窗」时把底栏放回可见（[keys] 是触发信号：当前页下标、弹窗 URL 之类）。
 *
 * 与直接调用 [BottomBarScrollState.show] 的区别：**跳过本页重建后的首次执行**。
 * `LaunchedEffect` 在页面重建时也会跑一次，那一次会把刚从存档恢复的「收起」状态强行展开
 * （现象：返回瞬间是收起的、紧接着又滑出来），所以必须跳过；之后真的切页、开弹窗照常显示。
 */
@Composable
fun ShowBottomBarOnChange(state: BottomBarScrollState, vararg keys: Any?) {
    // 普通 remember：本页每次重建都是 false，于是重建后的首次调用正好被跳过
    var changeSeen by remember { mutableStateOf(false) }
    LaunchedEffect(*keys) {
        if (changeSeen) state.show() else changeSeen = true
    }
}

/**
 * 把底栏显隐判定挂到承载滚动的容器上（挂在内容最外层即可，不必是列表本身）。
 *
 * 只读纵向位移，横向翻页（`HorizontalPager`）不会误触发。
 */
fun Modifier.trackBottomBarScroll(state: BottomBarScrollState): Modifier =
    nestedScroll(state.connection)

/**
 * 覆盖在内容之上的底栏：显隐由 [state] 控制（滚动方向驱动，不再提供关闭开关）。
 *
 * 三个底部 tab（首页/大厅/我的、电费、缴费服务大厅）共用这一份，避免各自的滑入滑出代码重复。
 * 调用处放在 `Box` 里即可，[BoxScope] 扩展会自动把它对齐到底部。
 */
@Composable
fun BoxScope.BottomBarOverlay(
    state: BottomBarScrollState,
    animate: Boolean,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = !state.hidden,
        modifier = Modifier.align(Alignment.BottomCenter),
        // 开启「减少动画」时不做位移动画，直接切换（与页面过渡动画的处理一致）
        enter = if (animate) slideInVertically { it } else EnterTransition.None,
        exit = if (animate) slideOutVertically { it } else ExitTransition.None,
    ) {
        content()
    }
}
