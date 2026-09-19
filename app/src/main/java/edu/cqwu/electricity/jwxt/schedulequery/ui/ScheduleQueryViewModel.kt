package edu.cqwu.electricity.jwxt.schedulequery.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import edu.cqwu.electricity.common.net.SessionExpiredException
import edu.cqwu.electricity.jwxt.schedulequery.data.ScheduleQueryApi
import edu.cqwu.electricity.jwxt.timetable.data.TimetableApi
import edu.cqwu.electricity.jwxt.schedulequery.data.JwxtClassRow
import edu.cqwu.electricity.jwxt.schedulequery.data.JwxtClassroomRow
import edu.cqwu.electricity.jwxt.schedulequery.data.JwxtDictItem
import edu.cqwu.electricity.jwxt.schedulequery.data.JwxtPagedRows
import edu.cqwu.electricity.jwxt.core.model.JwxtScheduleTarget
import edu.cqwu.electricity.jwxt.core.model.JwxtScheduleTerm
import edu.cqwu.electricity.jwxt.schedulequery.data.JwxtTeacherRow
import edu.cqwu.electricity.jwxt.core.model.ScheduleTargetType
import edu.cqwu.electricity.logging.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 选择页的一行；三种课表类型统一成同一种展示行，UI 只认这一种 */
data class ScheduleRow(
    /** 查课表用的对象代码（教室号 / 工号 / 班级代码） */
    val code: String,
    /** 主名称 */
    val name: String,
    /** 次信息（楼栋 / 工号，可为空） */
    val subtitle: String = "",
    /** 标签（校区、院系、性别、年级、专业…） */
    val tags: List<String> = emptyList(),
)

/** 「全校课表查询」选择页的状态 */
data class ScheduleQueryUiState(
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val rows: List<ScheduleRow> = emptyList(),
    /** 已加载到第几页（页面顶部统计行用） */
    val pageNumber: Int = 0,
    /** 总页数；不分页的查询（班级页选到专业后走 `link/class`）固定算 1 页 */
    val pageTotal: Int = 0,
    val requiresReLogin: Boolean = false,
    val loadError: String? = null,
    /** 学期 */
    val terms: List<JwxtScheduleTerm> = emptyList(),
    val termCode: String = "",
    /** 搜索关键词（教室名 / 教师姓名） */
    val keyword: String = "",
    /**
     * 只看有课（**默认开启**）。三种类型都支持，但底层不是同一个接口参数：
     * - 教室页 → `arranged`（该教室是否**已排课**，实测 1314 → 632）
     * - 教师 / 班级页 → `teached`（是否有授课任务，实测 4090 → 1031、4529 → 489）
     *
     * 名字刻意不叫 `teachedOnly`：教室并不"授课"，拿接口参数名当 UI 状态名会让人读错。
     * 网页端教室页的请求里同样带 `arranged`（它的 state 里有 `scheduleStatusList: [{是,1},{否,0}]`）。
     */
    val onlyWithLessons: Boolean = true,
    /** 只看外聘教师（教师页专用；接口参数 `outSource`，实测 4090 → 2241）。默认关 */
    val outSourceOnly: Boolean = false,
    /** 教室页：校区与楼栋 */
    val campusOptions: List<JwxtDictItem> = emptyList(),
    val buildingOptions: List<JwxtDictItem> = emptyList(),
    val campusCode: String = "",
    val buildingCode: String = "",
    /** 教师页：院系与性别（职称接口实测失效，不做） */
    val departOptions: List<JwxtDictItem> = emptyList(),
    val sexOptions: List<JwxtDictItem> = emptyList(),
    val departId: String = "",
    val sexId: String = "",
    /** 班级页：年级 → 学院 → 专业 三级级联（该列表接口不支持按班级名搜索） */
    val gradeOptions: List<JwxtDictItem> = emptyList(),
    val collegeOptions: List<JwxtDictItem> = emptyList(),
    val majorOptions: List<JwxtDictItem> = emptyList(),
    val gradeId: String = "",
    val collegeId: String = "",
    val majorId: String = "",
)

/**
 * 「全校课表查询」选择页的 ViewModel：三种课表类型**共用这一个**，按 [type] 走各自的列表接口与筛选参数。
 *
 * 筛选参数全部来自抓包实证（见 `plans/jwxt-schedule-query-plan.md`）：
 * - 教室：`classroomName` 名称搜索 + `campusCode` + `buildingCode`（全校 1314 条，必须靠筛选）；
 * - 教师：`name` + `depart` + `sex` + `teached`；
 * - 班级：`gradeCode` + `deptCode` + `teached`；选到专业后改用 `link/class`（它一次返回全部，不分页）。
 */
class JwxtScheduleQueryViewModel(
    private val api: ScheduleQueryApi = ScheduleQueryApi(),
    /** 学期列表在课表域的接口里（「我的课表」与这里共用同一个 termList） */
    private val timetableApi: TimetableApi = TimetableApi(),
    val type: ScheduleTargetType,
) : ViewModel() {

    /** 创建本 ViewModel 的工厂 */
    class Factory(private val type: ScheduleTargetType) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            JwxtScheduleQueryViewModel(type = type) as T
    }

    // 三种类型都默认开启「只看有课」：教室 1314→632、教师 4090→1031、班级 4529→489。
    // （网页端只有教师 / 班级默认带 teached=1，教室默认看全部；这里按使用习惯统一成默认开。）
    private val _uiState = MutableStateFlow(ScheduleQueryUiState())
    val uiState: StateFlow<ScheduleQueryUiState> = _uiState.asStateFlow()

    /**
     * 当前这一次查询是否分页。
     *
     * 只有「班级页选到专业」这一种情况走 `link/class`（一次返回全部，不再分页），所以直接由状态推导。
     * 以前它是个在 [query] 里被赋值的成员变量——读代码时必须先知道"调 query 会顺手改 paged"
     * 才看得懂后面的 hasMore，属于典型的隐式副作用。
     */
    private val paged: Boolean
        get() = !(type == ScheduleTargetType.CLASS && _uiState.value.majorId.isNotBlank())

    init {
        refresh()
    }

    /** 首次进页面 / 下拉刷新：第一次顺带取词典与学期，之后只重查第一页 */
    fun refresh() {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, requiresReLogin = false, loadError = null) }
            try {
                if (_uiState.value.terms.isEmpty()) loadTermsAndFilters()
                reload()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    /** 滚动到底自动加载下一页；失败时保留已加载的数据，只把提示留在 footer 上重试 */
    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMore || state.isRefreshing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val next = state.pageNumber + 1
                val page = query(next)
                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        rows = it.rows + page.rows.orEmpty(),
                        pageNumber = next,
                        hasMore = hasMoreAfter(next, page.total),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingMore = false) }
                onError(e)
            }
        }
    }

    /**
     * 点某一项：**联网**查它在哪个校区，再凑出查课表要用的目标。
     *
     * 名字里带 fetch 是想让调用方一眼看出它要发请求——实测校区与对象不匹配时课表接口返回空列表，
     * 所以这一步省不掉，调用方得为此准备一个等待态。
     */
    suspend fun fetchTargetWithCampus(row: ScheduleRow): JwxtScheduleTarget? = try {
        val campusCode = api.fetchScheduledCampus(_uiState.value.termCode, row.code, type.kblx)
            .getOrThrow()
            .firstOrNull()
            ?.id
            .orEmpty()
        JwxtScheduleTarget(type = type, code = row.code, name = row.name, campusCode = campusCode)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onError(e)
        null
    }

    // ── 筛选变更（都走同一个外壳：改状态 → 重查第一页）──

    fun setTerm(termCode: String) = changeFilter { _uiState.update { it.copy(termCode = termCode) } }

    fun setKeyword(keyword: String) = changeFilter { _uiState.update { it.copy(keyword = keyword) } }

    fun setOnlyWithLessons(only: Boolean) = changeFilter { _uiState.update { it.copy(onlyWithLessons = only) } }

    fun setDepart(id: String) = changeFilter { _uiState.update { it.copy(departId = id) } }

    fun setSex(id: String) = changeFilter { _uiState.update { it.copy(sexId = id) } }

    fun setOutSourceOnly(only: Boolean) = changeFilter { _uiState.update { it.copy(outSourceOnly = only) } }

    fun setBuilding(code: String) = changeFilter { _uiState.update { it.copy(buildingCode = code) } }

    /** 切校区：楼栋选项跟着换，所以先拉该校区的楼栋再重查 */
    fun setCampus(code: String) = changeFilter {
        _uiState.update { it.copy(campusCode = code, buildingCode = "", buildingOptions = emptyList()) }
        if (code.isNotBlank()) {
            _uiState.update { it.copy(buildingOptions = api.fetchBuildingList(code).getOrDefault(emptyList())) }
        }
    }

    /** 切年级：专业依赖「年级 + 学院」，先清掉 */
    fun setGrade(id: String) = changeFilter {
        _uiState.update { it.copy(gradeId = id, majorId = "", majorOptions = emptyList()) }
    }

    /** 切学院：拉该「年级 + 学院」下的专业 */
    fun setCollege(id: String) = changeFilter {
        _uiState.update { it.copy(collegeId = id, majorId = "", majorOptions = emptyList()) }
        val grade = _uiState.value.gradeId
        if (grade.isNotBlank() && id.isNotBlank()) {
            _uiState.update { it.copy(majorOptions = api.fetchLinkMajors(grade, id).getOrDefault(emptyList())) }
        }
    }

    fun setMajor(id: String) = changeFilter { _uiState.update { it.copy(majorId = id) } }

    // ── 内部实现 ──

    /** 筛选变化后的统一处理；[beforeReload] 用来先改状态或拉下级字典（都是挂起操作） */
    private fun changeFilter(beforeReload: suspend () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, requiresReLogin = false, loadError = null) }
            try {
                beforeReload()
                reload()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    /** 重查第一页并写进状态 */
    private suspend fun reload() {
        val page = query(1)
        _uiState.update {
            it.copy(
                isRefreshing = false,
                rows = page.rows.orEmpty(),
                pageNumber = 1,
                pageTotal = if (paged) totalPages(page.total) else 1,
                hasMore = hasMoreAfter(1, page.total),
            )
        }
    }

    /** 总页数（向上取整） */
    private fun totalPages(total: Int): Int =
        if (total <= 0) 1 else (total + PAGE_SIZE - 1) / PAGE_SIZE

    /** 取完第 [page] 页后还有没有下一页；不分页的查询永远没有（两处 hasMore 统一走它） */
    private fun hasMoreAfter(page: Int, total: Int): Boolean = paged && page * PAGE_SIZE < total

    /** 首次进页面拉「学期 + 本类型的筛选字典」；字典失败不影响列表（各自 getOrDefault 兜底） */
    private suspend fun loadTermsAndFilters() {
        val terms = timetableApi.fetchScheduleTerms().getOrThrow()
        val term = terms.firstOrNull { it.currentFlag } ?: terms.firstOrNull()
        _uiState.update { it.copy(terms = terms, termCode = term?.termCode.orEmpty()) }

        when (type) {
            ScheduleTargetType.CLASSROOM ->
                _uiState.update { it.copy(campusOptions = api.fetchCampusDict().getOrDefault(emptyList())) }

            ScheduleTargetType.TEACHER -> {
                val (depart, sex) = coroutineScope {
                    val departDeferred = async { api.fetchDepartList().getOrDefault(emptyList()) }
                    val sexDeferred = async { api.fetchSexList().getOrDefault(emptyList()) }
                    departDeferred.await() to sexDeferred.await()
                }
                _uiState.update { it.copy(departOptions = depart, sexOptions = sex) }
            }

            ScheduleTargetType.CLASS -> {
                val (grades, colleges) = coroutineScope {
                    val gradeDeferred = async { api.fetchLinkGrades().getOrDefault(emptyList()) }
                    val collegeDeferred = async { api.fetchLinkColleges().getOrDefault(emptyList()) }
                    gradeDeferred.await() to collegeDeferred.await()
                }
                _uiState.update { it.copy(gradeOptions = grades, collegeOptions = colleges) }
            }
        }
    }

    /** 查一页；三种类型各自取数后统一映射成 [ScheduleRow] */
    private suspend fun query(page: Int): JwxtPagedRows<ScheduleRow> {
        val state = _uiState.value
        return when (type) {
            ScheduleTargetType.CLASSROOM -> {
                val result = api.fetchClassroomList(
                    termCode = state.termCode,
                    pageNumber = page,
                    pageSize = PAGE_SIZE,
                    keyword = state.keyword,
                    campusCode = state.campusCode,
                    buildingCode = state.buildingCode,
                    arrangedOnly = state.onlyWithLessons,
                ).getOrThrow()
                JwxtPagedRows(result.total, result.rows.orEmpty().map { it.toScheduleRow() })
            }

            ScheduleTargetType.TEACHER -> {
                val result = api.fetchTeacherList(
                    termCode = state.termCode,
                    pageNumber = page,
                    pageSize = PAGE_SIZE,
                    keyword = state.keyword,
                    depart = state.departId,
                    sex = state.sexId,
                    teachedOnly = state.onlyWithLessons,
                    outSourceOnly = state.outSourceOnly,
                ).getOrThrow()
                JwxtPagedRows(result.total, result.rows.orEmpty().map { it.toScheduleRow() })
            }

            ScheduleTargetType.CLASS -> if (state.majorId.isNotBlank()) {
                // 选到专业：级联接口一次给全，不再分页（paged 由状态推导，见属性定义）
                val rows = api.fetchLinkClasses(state.gradeId, state.collegeId, state.majorId).getOrThrow()
                JwxtPagedRows(rows.size, rows.map { ScheduleRow(code = it.id, name = it.name) })
            } else {
                val result = api.fetchClassList(
                    termCode = state.termCode,
                    pageNumber = page,
                    pageSize = PAGE_SIZE,
                    deptCode = state.collegeId,
                    gradeCode = state.gradeId,
                    teachedOnly = state.onlyWithLessons,
                ).getOrThrow()
                JwxtPagedRows(result.total, result.rows.orEmpty().map { it.toScheduleRow() })
            }
        }
    }

    /** 失败归类：会话过期交给界面显示「重新登录」，其余原样显示错误信息 */
    private fun onError(e: Exception) {
        AppLog.e(TAG, "课表查询列表加载失败", e)
        val needLogin = e is SessionExpiredException
        _uiState.update {
            it.copy(
                isRefreshing = false,
                isLoadingMore = false,
                requiresReLogin = needLogin,
                loadError = if (needLogin) null else e.message,
            )
        }
    }

    private companion object {
        const val TAG = "JwxtScheduleQueryViewModel"

        /** 每页条数：网页教室/班级取 10、教师取 20，这里统一 20（教室 1314 条靠筛选而不是翻页） */
        const val PAGE_SIZE = 20
    }
}

private fun JwxtClassroomRow.toScheduleRow(): ScheduleRow = ScheduleRow(
    code = classroomCode,
    name = classroomName,
    subtitle = buildingName,
    tags = listOfNotNull(
        campusName.takeIf { it.isNotBlank() },
        scheduleStatusName.takeIf { it.isNotBlank() },
    ),
)

private fun JwxtTeacherRow.toScheduleRow(): ScheduleRow = ScheduleRow(
    code = code,
    name = name,
    subtitle = code,
    tags = listOfNotNull(
        depart.takeIf { it.isNotBlank() },
        sex.takeIf { it.isNotBlank() },
    ),
)

private fun JwxtClassRow.toScheduleRow(): ScheduleRow = ScheduleRow(
    code = classCode,
    name = className,
    subtitle = "",
    tags = listOfNotNull(
        gradeName.takeIf { it.isNotBlank() },
        deptName.takeIf { it.isNotBlank() },
        majorName.takeIf { it.isNotBlank() },
    ),
)
