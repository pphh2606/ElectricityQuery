package edu.cqwu.electricity.jwxt.timetable.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import edu.cqwu.electricity.common.net.SessionExpiredException
import edu.cqwu.electricity.jwxt.core.model.JwxtScheduleTerm
import edu.cqwu.electricity.jwxt.core.model.JwxtSection
import edu.cqwu.electricity.jwxt.core.model.JwxtTermWeek
import edu.cqwu.electricity.jwxt.core.model.JwxtTimetableCourse
import edu.cqwu.electricity.jwxt.timetable.data.TimetableApi
import edu.cqwu.electricity.jwxt.timetable.data.TimetableWidgetRepositoryV2
import edu.cqwu.electricity.jwxt.core.JwxtConstants
import edu.cqwu.electricity.jwxt.core.model.JwxtScheduleTarget
import edu.cqwu.electricity.logging.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 在看「某一周」还是「整个学期」 */
enum class TimetableMode { WEEK, TERM }

/**
 * 课表页状态。
 *
 * 页面是「行 = 节次、列 = 星期」的网格：[sections] 是行，[courses] 里的 `dayOfWeek`(1-7) 决定列。
 */
data class TimetableUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val mode: TimetableMode = TimetableMode.WEEK,
    /** 学期选项（`currentFlag` 为当前学期）；周视图与学期视图都用它做「上/下一个」 */
    val terms: List<JwxtScheduleTerm> = emptyList(),
    val selectedTermCode: String = "",
    /** 周次列表（`curWeek` 为当前周） */
    val weeks: List<JwxtTermWeek> = emptyList(),
    val selectedWeek: Int = 0,
    /** 节次列 */
    val sections: List<JwxtSection> = emptyList(),
    /** 当前周的全部课程 */
    val courses: List<JwxtTimetableCourse> = emptyList(),
    /** 会话过期（CAS 票据失效或业务码 401），UI 显示「重新登录」 */
    val requiresReLogin: Boolean = false,
    val errorMessage: String? = null,
) {
    private val weekIndex: Int get() = weeks.indexOfFirst { it.serialNumber == selectedWeek }

    /** 上一周 / 下一周：到学期首末周时为 null，对应的按钮置灰 */
    val prevWeek: Int? get() = weeks.getOrNull(weekIndex - 1)?.serialNumber
    val nextWeek: Int? get() = weeks.getOrNull(weekIndex + 1)?.serialNumber

    /** 当前学期（导航行显示它的名字） */
    val currentTerm: JwxtScheduleTerm? get() = terms.firstOrNull { it.termCode == selectedTermCode }

    private val termIndex: Int get() = terms.indexOfFirst { it.termCode == selectedTermCode }

    /**
     * 学期视图下更早 / 更晚的一个学期。
     *
     * 都按 [terms] 的顺序取——接口按学年**倒序**下发（2030-2031 在前），所以「更早」是下标 +1。
     * 到两端时为 null，对应按钮置灰。
     */
    val olderTermCode: String? get() = terms.getOrNull(termIndex + 1)?.termCode
    val newerTermCode: String? get() = terms.getOrNull(termIndex - 1)?.termCode
}

/**
 * 周课表 ViewModel，同时服务两种场景：
 *
 * - [target] 为 null：**我的课表**（v410 `getMyScheduleDetail`，服务端按会话身份返回自己的课表）；
 * - [target] 不为 null：**查任意对象的课表**（v510 `getScheduleDetail`，带 KBLX + CODE + 校区）。
 *
 * 两套接口不能互换——实测在 v410 的请求体里塞 CODE 会被服务端忽略，永远返回自己的课表。
 *
 * 进页面取学期列表 → 落到当前学期 → 并行取该学期的周次与节次 → 取「当前周」的课表。
 * 之后切周只重取课表（周次与节次不会变），切学期则三份都重取。
 * 失败分别落进 [TimetableUiState.requiresReLogin] 与 [TimetableUiState.errorMessage]，
 * 不做自动重试——重新登录后由页面的 ON_RESUME 触发一次 [load]。
 */
class JwxtTimetableViewModel(
    private val api: TimetableApi = TimetableApi(),
    private val target: JwxtScheduleTarget? = null,
) : ViewModel() {

    /** 创建本 ViewModel 的工厂；[target] 为 null 即「我的课表」 */
    class Factory(private val target: JwxtScheduleTarget?) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return JwxtTimetableViewModel(target = target) as T
        }
    }

    private val _uiState = MutableStateFlow(TimetableUiState())
    val uiState: StateFlow<TimetableUiState> = _uiState.asStateFlow()

    /** 正在进行的课表请求：切周/切学期时取消上一笔，避免旧结果后到、盖住新的 */
    private var tableJob: Job? = null

    init {
        load()
    }

    /** 首次进入 / 重新登录后：取学期列表并载入当前学期的课表 */
    fun load() {
        launchLoad {
            val terms = api.fetchScheduleTerms().getOrThrow()
            val term = terms.firstOrNull { it.currentFlag } ?: terms.firstOrNull()
            // 接口至少会给一项；真给空列表时收尾，避免页面一直停在加载态
            if (term == null) {
                _uiState.update { it.copy(isLoading = false, terms = emptyList()) }
                return@launchLoad
            }
            _uiState.update { it.copy(terms = terms, selectedTermCode = term.termCode) }
            loadTermInto(term.termCode)
        }
    }

    /** 切换学期：周次、节次、课表都重取（不同学期的周次与节次可能不同），并落到该学期的当前周 */
    fun selectTerm(termCode: String) {
        if (termCode == _uiState.value.selectedTermCode) return

        _uiState.update { it.copy(selectedTermCode = termCode, courses = emptyList()) }
        launchLoad { loadTermInto(termCode) }
    }

    /** 切换周次（仅周视图用）：只重取课表——网页每切一次周也是重发这一支请求 */
    fun selectWeek(week: Int) {
        if (week == _uiState.value.selectedWeek) return

        _uiState.update { it.copy(selectedWeek = week, courses = emptyList()) }
        launchLoad { loadCourses() }
    }

    /** 切换「周 / 学期」视图：按新模式重取课表（周视图取该周，学期视图取整学期） */
    fun selectMode(mode: TimetableMode) {
        if (mode == _uiState.value.mode) return

        _uiState.update { it.copy(mode = mode, courses = emptyList()) }
        launchLoad { loadCourses() }
    }

    /** 下拉刷新：重取当前学期的课表数据（学期列表不会变，不必再取） */
    fun refresh() {
        val termCode = _uiState.value.selectedTermCode
        if (termCode.isBlank()) {
            load()
            return
        }
        launchLoad(isRefreshing = true) { loadTermInto(termCode) }
    }

    /**
     * 统一的加载外壳：置加载态 → 执行 [block] → 失败归类。
     *
     * [block] 自己把成功的结果写进状态（各入口要更新的字段不同：切周只换课程，切学期要连周次与节次一起换）。
     */
    private fun launchLoad(isRefreshing: Boolean = false, block: suspend () -> Unit) {
        tableJob?.cancel()
        tableJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = !isRefreshing,
                    isRefreshing = isRefreshing,
                    requiresReLogin = false,
                    errorMessage = null,
                )
            }

            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e, "课表加载失败")
            }
        }
    }

    /** 取某学期的周次与节次、落到该学期的当前周，再按当前模式取课表（[load] 与切学期共用） */
    private suspend fun loadTermInto(termCode: String) {
        // 节次接口的 KBLX 与校区要跟着查询目标走：写死「03 + 空校区」在查教室时拿不到节次
        val kblx = target?.type?.kblx ?: JwxtConstants.KBLX_MY_SCHEDULE
        val campusCode = target?.campusCode.orEmpty()

        // 周次与节次互不依赖，并行取
        val (weeks, sections) = coroutineScope {
            val weeksDeferred = async { api.fetchTermWeeks(termCode).getOrThrow() }
            val sectionsDeferred = async { api.fetchSections(termCode, campusCode, kblx).getOrThrow() }
            weeksDeferred.await() to sectionsDeferred.await()
        }

        val week = weeks.firstOrNull { it.curWeek } ?: weeks.firstOrNull()
        _uiState.update {
            it.copy(weeks = weeks, selectedWeek = week?.serialNumber ?: 0, sections = sections)
        }
        loadCourses()
    }

    /**
     * 按当前模式取课表并写进状态。
     *
     * 周视图传该周的 `serialNumber`；学期视图传 null（请求体里就是空串）拿到整学期的排布——
     * 这时同一个格子里会叠着不同周的课，页面按轨道并排显示，而正文里自带周次（服务端加的）。
     */
    private suspend fun loadCourses() {
        val state = _uiState.value
        val week = if (state.mode == TimetableMode.WEEK) state.selectedWeek else null
        // 我的课表走 v410（服务端按会话身份取数）；查课表走 v510（带 KBLX + CODE，见类注释）
        val fetched = if (target == null) {
            api.fetchScheduleDetail(state.selectedTermCode, week).getOrThrow()
        } else {
            api.fetchTargetScheduleDetail(
                termCode = state.selectedTermCode,
                week = week,
                kblx = target.type.kblx,
                code = target.code,
                campusCode = target.campusCode,
            ).getOrThrow()
        }
        // `dayOfWeek` 只应是 1..7；越界的课定位不到任何一列，丢掉并记一行日志（否则会静默消失）
        val courses = fetched.filter { it.dayOfWeek in 1..JwxtConstants.DAYS_IN_WEEK }
        if (courses.size != fetched.size) {
            AppLog.w(TAG, "丢弃 ${fetched.size - courses.size} 门 dayOfWeek 越界的课程")
        }
        _uiState.update { it.copy(isLoading = false, isRefreshing = false, courses = courses) }
        cacheForWidgetIfCurrent(state, courses)
    }

    /**
     * 「我的课表 + 当前教学周」时，把刚取到的数据顺手写进桌面小组件缓存。
     *
     * 以下情况**不写**：查别人的课表（[target] 不为 null）、学期视图、翻到了别的周——
     * 小组件要的是"今天"，把这些数据写进去会让桌面显示错的课。
     *
     * 顺带也是「课表页看到的 = 桌面看到的」这条保证的实现处：数据刚取到就直接喂过去，
     * 不让小组件再打一次网络。
     */
    private suspend fun cacheForWidgetIfCurrent(
        state: TimetableUiState,
        courses: List<JwxtTimetableCourse>,
    ) {
        if (target != null || state.mode != TimetableMode.WEEK) return

        val currentWeek = state.weeks.firstOrNull { it.curWeek }?.serialNumber ?: return
        if (state.selectedWeek != currentWeek) return

        TimetableWidgetRepositoryV2.cacheFrom(
            termCode = state.selectedTermCode,
            weekSerial = currentWeek,
            courses = courses,
        )
    }

    /** 失败归类：会话过期交给界面显示「重新登录」，其余原样显示错误信息 */
    private fun onError(e: Exception, message: String) {
        AppLog.e(TAG, message, e)
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

    private companion object {
        const val TAG = "JwxtTimetableViewModel"
    }
}
