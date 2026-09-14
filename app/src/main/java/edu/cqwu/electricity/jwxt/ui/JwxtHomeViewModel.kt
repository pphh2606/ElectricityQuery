package edu.cqwu.electricity.jwxt.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.cqwu.electricity.common.net.HtmlFormParser
import edu.cqwu.electricity.common.net.SessionExpiredException
import edu.cqwu.electricity.common.util.runCatchingCancellable
import edu.cqwu.electricity.jwxt.data.JwxtApi
import edu.cqwu.electricity.jwxt.data.JwxtConstants
import edu.cqwu.electricity.jwxt.data.JwxtExam
import edu.cqwu.electricity.jwxt.data.JwxtLabelGroup
import edu.cqwu.electricity.jwxt.data.JwxtScene
import edu.cqwu.electricity.jwxt.data.JwxtTodayLesson
import edu.cqwu.electricity.logging.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 服务格子项。
 *
 * [isMore] 为 true 表示本地固定的「更多服务」入口——它的名称由 UI 层用字符串资源渲染，
 * 因此 [name] 与 [iconUrl] 为空（图标用内置矢量图标）。
 */
data class JwxtGridItem(
    val name: String,
    val iconUrl: String,
    val pageUrl: String,
    val isMore: Boolean = false,
)

/**
 * 一个分类标签。
 *
 * 顶部 chip 显示「[name] [count]」（如「全部 11」），点开后显示 [services] 网格。
 * 分组名与数量都来自接口 `biz/home/listLabelService`（抓包实测 3 组：全部 11 / 查询 5 / 申请 6）。
 */
data class JwxtLabelTab(
    val name: String,
    val count: Int,
    val services: List<JwxtGridItem>,
)

/** 场景卡片（名称 + 大图 + 落地页）；图片铺满整张卡片，标题与「详情」悬浮在图上 */
data class JwxtSceneUi(
    val name: String,
    val iconUrl: String,
    val pageUrl: String,
)

/**
 * 今日课程的一条（已清洗）。
 *
 * [sectionRange] 形如 `5-6`、[timeRange] 形如 `14:30-16:10`——含中文的「节（时间）」拼接
 * 交给 UI 层用字符串资源完成，ViewModel 不产出中文。
 */
data class JwxtLessonUi(
    val sectionRange: String,
    val timeRange: String,
    /** 调/补标签的原始码（`01` 调课 / `03` 补课），空串表示正常；文案由 UI 层映射 */
    val transferTag: String,
    val lines: List<JwxtLessonLine>,
)

/** 课程正文的一行（已去掉 HTML 标签）；[highlight] 对应接口 `color` 非空（网页的红字行） */
data class JwxtLessonLine(
    val text: String,
    val highlight: Boolean,
)

/** 本学期考试的一条；[place] / [seat] / [teacher] 为空的段由 UI 层跳过不显示 */
data class JwxtExamUi(
    val timeNote: String,
    val courseName: String,
    val place: String,
    val seat: String,
    val teacher: String,
)

data class JwxtHomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    /** 分类标签（顶部 chip），顺序即接口分组顺序 */
    val labelTabs: List<JwxtLabelTab> = emptyList(),
    val selectedLabelIndex: Int = 0,
    val scenes: List<JwxtSceneUi> = emptyList(),
    /** 今日课程（已清洗并按节次升序）；空列表表示今天没课 */
    val todayLessons: List<JwxtLessonUi> = emptyList(),
    /** 本学期考试（保持接口返回顺序） */
    val exams: List<JwxtExamUi> = emptyList(),
    /** 今日课程接口失败原因（卡片内展示），null 表示成功 */
    val lessonError: String? = null,
    /** 本学期考试接口失败原因（卡片内展示），null 表示成功 */
    val examError: String? = null,
    /** 会话过期（CAS 票据失效或业务码 401），UI 显示「重新登录」 */
    val requiresReLogin: Boolean = false,
    val errorMessage: String? = null,
) {
    /** 当前选中分类的服务网格（分类名由 chip 自己显示，页面上不再重复标题） */
    val currentServices: List<JwxtGridItem>
        get() = labelTabs.getOrNull(selectedLabelIndex)?.services.orEmpty()
}

/**
 * 教务首页 ViewModel。
 *
 * 并行拉取服务分组与场景卡片，把每个分组映射成一个顶部 chip（分类名 + 数量）与它自己的服务网格。
 * 会话过期与其它失败分别落进 [JwxtHomeUiState.requiresReLogin] 与 [JwxtHomeUiState.errorMessage]，
 * 不做自动重试——重新登录后由页面的 ON_RESUME 触发一次 [load]。
 */
class JwxtHomeViewModel(
    private val api: JwxtApi = JwxtApi(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(JwxtHomeUiState())
    val uiState: StateFlow<JwxtHomeUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** 切换分类（点击顶部 chip）。下标收敛到合法范围，避免刷新后分类数量变化导致越界 */
    fun selectLabel(index: Int) {
        _uiState.update { state ->
            if (state.labelTabs.isEmpty()) {
                state
            } else {
                state.copy(selectedLabelIndex = index.coerceIn(0, state.labelTabs.lastIndex))
            }
        }
    }

    /** 加载首页数据；[isRefresh] 为 true 时保留下拉刷新指示器而不是整页加载态 */
    fun load(isRefresh: Boolean = false) {
        _uiState.update {
            it.copy(
                isLoading = !isRefresh,
                isRefreshing = isRefresh,
                requiresReLogin = false,
                errorMessage = null,
                lessonError = null,
                examError = null,
            )
        }

        viewModelScope.launch {
            try {
                // 今日课程 / 本学期考试与首页主数据并行拉取。它们的失败只落在各自卡片上、不连坐首页，
                // 所以异常必须在 async 内部就收敛成 Result（见 runCatchingCancellable）。
                val data = coroutineScope {
                    val groupsDeferred = async { api.fetchLabelServices().getOrThrow() }
                    val scenesDeferred = async { api.fetchMoreScenes().getOrThrow() }
                    val lessonsDeferred = async { runCatchingCancellable { api.fetchTodayLessons().getOrThrow() } }
                    val examsDeferred = async { runCatchingCancellable { api.fetchRecentExams().getOrThrow() } }
                    HomeData(
                        groups = groupsDeferred.await(),
                        scenes = scenesDeferred.await(),
                        lessons = lessonsDeferred.await(),
                        exams = examsDeferred.await(),
                    )
                }
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        isRefreshing = false,
                        labelTabs = buildLabelTabs(data.groups),
                        // 刷新后分类数量可能变化，把选中下标收敛回合法范围
                        selectedLabelIndex = state.selectedLabelIndex
                            .coerceIn(0, (data.groups.size - 1).coerceAtLeast(0)),
                        scenes = buildSceneItems(data.scenes),
                        todayLessons = buildLessons(data.lessons.getOrDefault(emptyList())),
                        exams = buildExams(data.exams.getOrDefault(emptyList())),
                        lessonError = data.lessons.exceptionOrNull()?.message,
                        examError = data.exams.exceptionOrNull()?.message,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(TAG, "教务首页加载失败", e)
                val needLogin = e is SessionExpiredException
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        requiresReLogin = needLogin,
                        errorMessage = if (needLogin) null else e.message,
                    )
                }
            }
        }
    }

    /**
     * 接口分组 → 顶部 chip 与各自的网格。
     *
     * 每个分组对应一个 chip（`labelName` + `serviceNum`）和一块网格（该分组的 `serviceList`，
     * 条数由接口的 `num` 参数控制，这里不再二次截断）。
     * **只有第一个分组（「全部」）**末尾追加本地固定的「更多服务」入口——它本就是
     * "推荐服务 + 更多"的语义，其余分组不加。
     */
    private fun buildLabelTabs(groups: List<JwxtLabelGroup>): List<JwxtLabelTab> =
        groups.mapIndexed { index, group ->
            val services = group.serviceList.map { service ->
                JwxtGridItem(
                    name = service.serviceName,
                    iconUrl = JwxtConstants.iconUrl(service.serviceIcon),
                    pageUrl = JwxtConstants.pageUrl(service.appId, service.url),
                )
            }.toMutableList()

            if (index == 0) {
                // 「更多服务」已改为原生二级页（Routes.JWXT_MORE_SERVICE），这里不再需要落地 URL
                services += JwxtGridItem(
                    name = "",
                    iconUrl = "",
                    pageUrl = "",
                    isMore = true,
                )
            }

            JwxtLabelTab(name = group.labelName, count = group.serviceNum, services = services)
        }

    /**
     * 场景卡片映射。接口提供卡片名、**大图**（`cardIcon`，702×256 的插画）与第一项服务的 hash 路由。
     *
     * 两类卡片直接跳过，与网页端行为一致（网页渲染前会 `filter(e => e.cardIcon)`）：
     * - 没有落地页（`serviceList` 为空）——点了没有去向；
     * - 没有图（`cardIcon` 为空）——大图卡没有图就是一块空白。
     */
    private fun buildSceneItems(scenes: List<JwxtScene>): List<JwxtSceneUi> =
        scenes.mapNotNull { scene ->
            val card = scene.sceneCardList.firstOrNull() ?: return@mapNotNull null
            val service = card.serviceList.firstOrNull() ?: return@mapNotNull null
            if (card.cardIcon.isBlank()) return@mapNotNull null
            JwxtSceneUi(
                name = card.cardName.ifBlank { scene.sceneName },
                iconUrl = JwxtConstants.iconUrl(card.cardIcon),
                pageUrl = JwxtConstants.pageUrl(service.appId, service.url),
            )
        }

    /**
     * 今日课程映射。
     *
     * 接口返回顺序是乱的（实测 5-6 → 1-2 → 3-4 → 7-8 节），按开始节次升序排。
     * 课程内容只能来自 `cellDetail`（顶层 `courseName`/`classroom` 等实测全为 null），
     * 逐行去掉 HTML 标签后展示；清洗后为空的行丢弃。
     */
    private fun buildLessons(lessons: List<JwxtTodayLesson>): List<JwxtLessonUi> =
        lessons.sortedBy { it.startSession }.map { lesson ->
            JwxtLessonUi(
                sectionRange = "${lesson.startSession}-${lesson.endSession}",
                timeRange = "${lesson.startTime}-${lesson.endTime}",
                transferTag = lesson.classTransferTypeCode.take(2)
                    .takeIf { it == JwxtConstants.TRANSFER_TYPE_ADJUST || it == JwxtConstants.TRANSFER_TYPE_MAKEUP }
                    .orEmpty(),
                lines = lesson.cellDetail.mapNotNull { line ->
                    val text = HtmlFormParser.stripHtml(line.text)
                    if (text.isEmpty()) null else JwxtLessonLine(text = text, highlight = line.color != null)
                },
            )
        }

    /**
     * 本学期考试映射。
     *
     * 接口按考试时间**降序**返回，这里重排为升序（早的在上），与「今日课程」按节次升序的
     * 顺序规则一致；同日多场再按开考时间排。排序用接口的 `examStart` / `timeStart`
     * （零填充的 `yyyy-MM-dd` / `HH:mm`，字典序即时间序），不解析含中文的 `timeNote`。
     */
    private fun buildExams(exams: List<JwxtExam>): List<JwxtExamUi> =
        exams.sortedWith(compareBy<JwxtExam>({ it.examStart }, { it.timeStart })).map { exam ->
            JwxtExamUi(
                timeNote = exam.timeNote,
                courseName = exam.courseName,
                place = exam.classroomName.orEmpty(),
                seat = exam.seatNo?.toString().orEmpty(),
                teacher = exam.teacher.orEmpty(),
            )
        }

    private companion object {
        const val TAG = "JwxtHomeViewModel"
    }
}

/**
 * 一次 [JwxtHomeViewModel.load] 的并行结果。
 *
 * [groups] / [scenes] 是首页主数据，失败时直接抛出（走整页错误态）；
 * [lessons] / [exams] 各自带 [Result]，失败只影响对应卡片。
 */
private data class HomeData(
    val groups: List<JwxtLabelGroup>,
    val scenes: List<JwxtScene>,
    val lessons: Result<List<JwxtTodayLesson>>,
    val exams: Result<List<JwxtExam>>,
)
