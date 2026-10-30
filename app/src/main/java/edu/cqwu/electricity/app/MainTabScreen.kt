package edu.cqwu.electricity.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.cqwu.electricity.R
import edu.cqwu.electricity.common.settings.ReduceMotion
import edu.cqwu.electricity.common.ui.AppNavigationBar
import edu.cqwu.electricity.common.ui.BottomBarOverlay
import edu.cqwu.electricity.common.ui.CollapsibleScaffold
import edu.cqwu.electricity.common.ui.ShowBottomBarOnChange
import edu.cqwu.electricity.common.ui.rememberBottomBarScrollState
import edu.cqwu.electricity.common.ui.trackBottomBarScroll
import edu.cqwu.electricity.hall.ui.HallPageContent
import edu.cqwu.electricity.hall.ui.HallViewModel
import edu.cqwu.electricity.home.ui.HomePage
import edu.cqwu.electricity.home.ui.HomeViewModel
import edu.cqwu.electricity.profile.ui.ProfilePageContent
import edu.cqwu.electricity.profile.ui.ProfileTopBarActions
import edu.cqwu.electricity.common.settings.LocalAppSettingsState
import edu.cqwu.electricity.common.navigation.LocalNavController
import edu.cqwu.electricity.webview.ui.WebViewBottomSheet
import kotlinx.coroutines.launch

/**
 * 主页 Tab 标签页（首页 / 大厅 / 我的）
 *
 * 使用 [HorizontalPager] 实现左右滑动切换页面，底栏与滑动双向同步。
 *
 * 大厅页面内部包含另一个 2 页 HorizontalPager（全部/收藏）。
 * Compose 默认行为：子 Pager 优先消费同方向手势，到边界后才交给父 Pager，
 * 因此无需额外嵌套滑动处理。
 *
 * 底栏是**覆盖层**：内容区恒定占满整屏（只给系统手势条让位），底栏显示时压在内容底部、
 * 隐藏时向下滑出——所以底栏进出不会让内容重排或跳动，滚动时始终享有最大的内容区域。
 * 显隐判定由 [rememberBottomBarScrollState] 通过嵌套滚动收集，三个页面的列表都不用改。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainTabScreen(
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { bottomNavTabs.size })
    val scope = rememberCoroutineScope()
    // 共享 HomeViewModel，让 TopAppBar 和 HomePageContent 访问同一搜索状态
    val homeViewModel: HomeViewModel = viewModel()
    LocalNavController.current

    // ── 半屏 WebView 状态（提升到 MainTabScreen 层级，避免被 HorizontalPager 裁剪）──
    var halfScreenUrl by remember { mutableStateOf<String?>(null) }
    var halfScreenTitle by remember { mutableStateOf("") }

    val appSettings = LocalAppSettingsState.current
    val userScrollEnabled = appSettings.reduceMotion != ReduceMotion.ON
    val animateBar = appSettings.reduceMotion != ReduceMotion.ON
    val barScrollState = rememberBottomBarScrollState()

    // 切 tab、打开半屏浏览器后，底栏都必须回到可见，否则用户会找不到导航；
    // 从子页面返回时的那一次不算（要保留用户离开时的收起状态）
    ShowBottomBarOnChange(barScrollState, pagerState.currentPage, halfScreenUrl)

    Box(
        modifier = modifier.trackBottomBarScroll(barScrollState),
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = userScrollEnabled,
            modifier = Modifier
                .fillMaxSize()
                // 只给系统手势条让位，不给底栏让位：内容区高度不随底栏变化
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) { page ->
            when (page) {
                0 -> {
                    HomePage(homeViewModel = homeViewModel)
                }
                1 -> {
                    val hallViewModel: HallViewModel = viewModel()
                    HallPageContent(
                        hallViewModel = hallViewModel,
                    )
                }
                2 -> {
                    CollapsibleScaffold(
                        title = stringResource(R.string.profile_title),
                        actions = { ProfileTopBarActions() },
                    ) { topInset, scrollModifier ->
                        ProfilePageContent(
                            onOpenHalfScreen = { url, title ->
                                halfScreenUrl = url
                                halfScreenTitle = title
                            },
                            modifier = scrollModifier,
                            topInset = topInset,
                        )
                    }
                }
            }
        }

        // 底栏：浮在内容之上，隐藏时向下滑出且不拦截触摸
        BottomBarOverlay(
            state = barScrollState,
            animate = animateBar,
        ) {
            val selectTab: (Int) -> Unit = { index ->
                scope.launch {
                    if (userScrollEnabled) pagerState.animateScrollToPage(index) else pagerState.scrollToPage(index)
                }
            }
            AppNavigationBar(
                itemCount = bottomNavTabs.size,
                selectedIndex = pagerState.currentPage,
                onSelect = selectTab,
                icon = { bottomNavTabs[it].icon },
                label = { stringResource(bottomNavTabs[it].labelRes) },
            )
        }
    }

    // ── 半屏 WebView 弹窗（在最外层，不受 HorizontalPager 裁剪）──
    WebViewBottomSheet(
        visible = halfScreenUrl != null,
        onDismissRequest = { halfScreenUrl = null },
        url = halfScreenUrl ?: "",
        title = halfScreenTitle,
    )
}
