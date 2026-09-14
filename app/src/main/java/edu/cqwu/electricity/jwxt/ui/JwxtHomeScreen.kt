@file:OptIn(ExperimentalMaterial3Api::class)

package edu.cqwu.electricity.jwxt.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.cqwu.electricity.R
import edu.cqwu.electricity.common.navigation.Routes
import edu.cqwu.electricity.common.ui.ReLoginContent
import edu.cqwu.electricity.jwxt.data.JwxtConstants
import edu.cqwu.electricity.common.navigation.LocalNavController
import edu.cqwu.electricity.theme.ui.currentTopBarColors

/**
 * 教务首页（本地化仿写 jwfw `/jwmobile` 首页）。
 *
 * 范围（详见 `plans/jwxt-home-localization-plan.md`）：
 * - 保留：顶部三个统计数字、服务九宫格、「更多服务」、公告行、场景卡片、课表/考试 Tab 卡
 * - 不做：渐变背景（用原生 `surfaceVariant`）、头像与问候语、底部三 tab、Tab 卡内的列表条目
 *
 * 页面自身只负责渲染与导航；数据、登录凭证与错误归类都在 [JwxtHomeViewModel] 与
 * `jwxt.data` 包里。所有子功能点击都交给内置浏览器（本次只本地化首页）。
 *
 * 本文件只放页面骨架与 `LazyColumn` 装配；各区块分别在 `JwxtHomeSections.kt`
 * （服务网格 / 公告行 / 场景卡）与 `JwxtLessonExamCard.kt`（课表·考试卡）里。
 */
@Composable
fun JwxtHomeScreen(
    onBack: () -> Unit,
    viewModel: JwxtHomeViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val nav = LocalNavController.current

    // 字符串资源集中在 composable 作用域取好，避免在非 composable 的点击回调里调用 stringResource
    val moreServiceLabel = stringResource(R.string.jwxt_more_service)
    val jwxtTitle = stringResource(R.string.jwxt_title)
    val noticeTitle = stringResource(R.string.jwxt_notice_title)

    val openWeb: (String, String) -> Unit = { url, title ->
        nav.navigate(Routes.unifiedWebViewRoute(url, title))
    }

    // 从登录页返回时自动重试一次（仅在确实因会话过期失败时），避免用户还要手动下拉
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, uiState.requiresReLogin) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && uiState.requiresReLogin) {
                viewModel.load()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        // 不传 bottomBar：本页是独立路由，因此不会有底部三 tab
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = jwxtTitle,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    // 打开网页版：用内置浏览器打开教务首页；网页端发现没有 token 时
                    // 会自己请求 /jwmobile/auth/index 完成一次 CAS 登录（抓包第 2 步），无需额外处理
                    IconButton(onClick = { openWeb(JwxtConstants.INDEX_URL, jwxtTitle) }) {
                        Icon(
                            imageVector = Icons.Outlined.OpenInBrowser,
                            contentDescription = stringResource(R.string.common_web_version),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = currentTopBarColors(),
            )
        },
    ) { paddingValues ->
        when {
            uiState.requiresReLogin -> ReLoginContent(
                requiresReLogin = true,
                onReLogin = { nav.navigate(Routes.loginRoute()) },
                modifier = Modifier.padding(paddingValues),
            )

            uiState.errorMessage != null -> ReLoginContent(
                errorMessage = uiState.errorMessage,
                requiresReLogin = false,
                onReLogin = {},
                onRetry = { viewModel.load() },
                modifier = Modifier.padding(paddingValues),
            )

            else -> PullToRefreshBox(
                // 首屏也算"正在刷新"：让顶部的下拉指示器一进页面就显示（与项目其它列表页下拉时同一个动画）
                isRefreshing = uiState.isLoading || uiState.isRefreshing,
                onRefresh = { viewModel.load(isRefresh = true) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // ① 服务区：数据未到时用等高的空白撑开——公告行、Tab 卡这些本地内容照常显示，
                    //    这样数据到达时下方内容不会跳动
                    item(key = "service_section") {
                        if (uiState.isLoading) {
                            Spacer(modifier = Modifier.height(SERVICE_SECTION_PLACEHOLDER_HEIGHT))
                        } else {
                            JwxtServiceSection(
                                tabs = uiState.labelTabs,
                                selectedIndex = uiState.selectedLabelIndex,
                                onSelectLabel = { index -> viewModel.selectLabel(index) },
                                items = uiState.currentServices,
                                moreServiceLabel = moreServiceLabel,
                            ) { item ->
                                // 「更多服务」进原生二级页；其余服务仍用内置浏览器打开教务子应用
                                if (item.isMore) {
                                    nav.navigate(Routes.JWXT_MORE_SERVICE)
                                } else {
                                    openWeb(item.pageUrl, item.name)
                                }
                            }
                        }
                    }

                    // ② 公告信息入口（本地固定入口，点击进教务通知中心）
                    item(key = "notice") {
                        JwxtNoticeRow(
                            title = noticeTitle,
                            onClick = { openWeb(JwxtConstants.NOTICE_CENTER_URL, jwxtTitle) },
                        )
                    }

                    // ③ 场景大图卡：加载中用空白占位，无数据时整块不渲染
                    item(key = "scenes") {
                        if (uiState.isLoading) {
                            Spacer(modifier = Modifier.height(SCENE_CARD_HEIGHT))
                        } else if (uiState.scenes.isNotEmpty()) {
                            JwxtSceneRow(
                                scenes = uiState.scenes,
                                detailLabel = stringResource(R.string.jwxt_detail),
                                onDetailClick = { scene -> openWeb(scene.pageUrl, scene.name) },
                            )
                        }
                    }

                    // ④ 课表 / 考试 Tab 卡（本地渲染，加载中显示空状态文案）
                    item(key = "lesson_exam") {
                        JwxtLessonExamCard(
                            lessons = uiState.todayLessons,
                            exams = uiState.exams,
                            lessonError = uiState.lessonError,
                            examError = uiState.examError,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 首屏加载时服务区的空白占位高度 = chip 行 32dp + 两行服务网格 96dp×2 + 行间距 8dp。
 *
 * 与真实内容等高（`JwxtServiceSection` 的 `FeatureGrid(rowSpacing = 8.dp)`，服务名较长时格子为
 * 两行文字 96dp），这样加载完成时下方内容不会跳动。
 */
private val SERVICE_SECTION_PLACEHOLDER_HEIGHT = 232.dp
