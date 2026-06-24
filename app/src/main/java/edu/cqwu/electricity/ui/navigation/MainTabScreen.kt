package edu.cqwu.electricity.ui.navigation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.cqwu.electricity.data.local.ReduceMotion
import edu.cqwu.electricity.ui.hall.HallPageContent
import edu.cqwu.electricity.ui.hall.HallTopBar
import edu.cqwu.electricity.ui.hall.HallViewModel
import edu.cqwu.electricity.ui.home.HomePageContent
import edu.cqwu.electricity.ui.home.HomeTopBar
import edu.cqwu.electricity.ui.home.HomeViewModel
import edu.cqwu.electricity.ui.profile.ProfilePageContent
import edu.cqwu.electricity.ui.profile.ProfileTopBar
import edu.cqwu.electricity.ui.theme.AnimationSettings
import edu.cqwu.electricity.ui.theme.LocalNavController
import kotlinx.coroutines.launch

/**
 * 主页 Tab 标签页（首页 / 大厅 / 我的）
 *
 * 使用 [HorizontalPager] 实现左右滑动切换页面，底栏与滑动双向同步。
 *
 * 大厅页面内部包含另一个 2 页 HorizontalPager（全部/收藏）。
 * Compose 默认行为：子 Pager 优先消费同方向手势，到边界后才交给父 Pager，
 * 因此无需额外嵌套滑动处理。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainTabScreen(
    animationSettings: AnimationSettings,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { bottomNavTabs.size })
    val scope = rememberCoroutineScope()
    // 共享 HomeViewModel，让 TopAppBar 和 HomePageContent 访问同一搜索状态
    val homeViewModel: HomeViewModel = viewModel()
    LocalNavController.current

    val userScrollEnabled = animationSettings.reduceMotion != ReduceMotion.ON

    Scaffold(
        topBar = {
            when (pagerState.currentPage) {
                0 -> {
                    val uiState by homeViewModel.uiState.collectAsState()
                    val searchQuery = uiState.searchQuery
                    val isSearching = uiState.isSearching
                    HomeTopBar(
                        searchQuery = searchQuery,
                        isSearching = isSearching,
                        onSearchQueryChange = { homeViewModel.setSearchQuery(it) },
                        onToggleSearch = { homeViewModel.enterSearchMode() },
                        onCloseSearch = { homeViewModel.clearSearch() },
                    )
                }
                1 -> HallTopBar()
                2 -> ProfileTopBar()
            }
        },
        bottomBar = {
            NavigationBar {
                bottomNavTabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            if (pagerState.currentPage != index) {
                                scope.launch {
                                    if (userScrollEnabled) {
                                        pagerState.animateScrollToPage(index)
                                    } else {
                                        pagerState.scrollToPage(index)
                                    }
                                }
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                        label = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
        },
        modifier = modifier,
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = userScrollEnabled,
            modifier = Modifier.padding(innerPadding),
        ) { page ->
            when (page) {
                0 -> HomePageContent(
                    homeViewModel = homeViewModel,
                )
                1 -> {
                    val hallViewModel: HallViewModel = viewModel()
                    HallPageContent(
                        hallViewModel = hallViewModel,
                    )
                }
                2 -> ProfilePageContent()
            }
        }
    }
}
