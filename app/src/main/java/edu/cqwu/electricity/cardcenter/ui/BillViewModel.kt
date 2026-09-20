package edu.cqwu.electricity.cardcenter.ui
import edu.cqwu.electricity.logging.AppLog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.cqwu.electricity.R
import edu.cqwu.electricity.cardcenter.data.BillFilter
import edu.cqwu.electricity.cardcenter.data.BillPageInfo
import edu.cqwu.electricity.cardcenter.data.CardCenterApi
import edu.cqwu.electricity.common.net.SessionExpiredException
import edu.cqwu.electricity.theme.ui.UiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 单个 Tab 最大记录数（防 OOM） */
private const val MAX_RECORDS = 500

/**
 * 合并翻页数据、去重。
 *
 * 独立为文件顶层函数，便于在 JVM 单测中直接验证分页合并规则。
 *
 * - 记录按 `billNo` 去重后截断到 [MAX_RECORDS]
 * - 条数封顶、或服务端返回空页时，把当前页当作最后一页，阻止继续翻页
 */
fun mergePageData(current: BillPageInfo, nextPage: BillPageInfo): BillPageInfo {
    val merged = (current.records + nextPage.records)
        .distinctBy { it.billNo }
        .take(MAX_RECORDS)
    val noMore = merged.size >= MAX_RECORDS || nextPage.records.isEmpty()
    return nextPage.copy(
        records = merged,
        totalPages = if (noMore) nextPage.currentPage else nextPage.totalPages
    )
}

/**
 * 账单页面 UI 状态数据类
 *
 * 替代 [BillScreen] 中原先 22 个散落的 `remember { mutableStateOf(...) }`。
 * ViewModel 在 Navigation 返回后存活，状态自动恢复，彻底解决"从 WebView 返回后自动重载"问题。
 *
 * 加载状态一律按 Tab 分键（[perTabLoading] / [loadingMoreTabs] / [perTabElapsed] / [moreElapsed]），
 * 单个 Tab 的慢请求不会锁死或取消其它 Tab。
 */
data class BillUiState(
    // ── 加载状态 ──
    val isRefreshing: Boolean = false,
    /** 正在翻页的 Tab 集合（每个 Tab 独立判断，互不阻塞） */
    val loadingMoreTabs: Set<Int> = emptySet(),
    val errorMessage: UiMessage? = null,
    val requiresReLogin: Boolean = false,

    // ── 加载计时 ──
    /** 各 Tab 翻页已耗时（秒），key=tabNo */
    val moreElapsed: Map<Int, Long> = emptyMap(),

    // ── 筛选条件（已生效） ──
    val activeTab: Int = 1,
    val showFilterPanel: Boolean = false,
    val searchQuery: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val filterIncome: Boolean = false,
    val filterExpense: Boolean = false,

    // ── 各标签页数据缓存（HorizontalPager 各 page 独立读取，避免预加载时显示错误数据） ──
    val tabCache: Map<Int, BillPageInfo> = emptyMap(),

    // ── 逐 Tab 加载状态（预加载 / 后台加载用） ──
    /** 各 Tab 是否正在加载中，key=tabNo */
    val perTabLoading: Map<Int, Boolean> = emptyMap(),
    /** 各 Tab 已加载的秒数 */
    val perTabElapsed: Map<Int, Long> = emptyMap(),
    /** 各 Tab 加载失败时的错误信息（key=tabNo, null=无错误） */
    val perTabError: Map<Int, UiMessage?> = emptyMap(),

    // ── 筛选条件（暂存，仅面板内编辑，应用前不触发请求） ──
    val tempSearchQuery: String = "",
    val tempStartDate: String = "",
    val tempEndDate: String = "",
    val tempIncome: Boolean = false,
    val tempExpense: Boolean = false,
)

/**
 * 账单页面 ViewModel
 *
 * 职责：
 * 1. 持有 [BillUiState] 驱动 UI 渲染
 * 2. 管理账单加载：H5 API（快 ~3s，仅 Tab1）和 HTML API（慢 ~15-20s，全部 4 个 Tab）并发加载
 * 3. 管理分页加载、筛选条件、标签切换
 * 4. 翻页状态与任务均按 Tab 分键：切换标签**不取消**进行中的请求，旧 Tab 结果到达后自动存入缓存
 * 5. 使用 [viewModelScope] 管理协程生命周期，避免竞态条件
 *
 * ViewModel 实例在 [androidx.navigation.NavHost] 返回后存活，
 * 因此从 WebView 或其他页面返回时状态自动恢复，无需重新请求。
 */
class BillViewModel : ViewModel() {

    // ==================== API 实例 ====================
    private val api = CardCenterApi()

    // ==================== UI 状态 ====================
    private val _uiState = MutableStateFlow(BillUiState())
    val uiState: StateFlow<BillUiState> = _uiState.asStateFlow()

    // ==================== 单次事件 ====================

    /** 页面级加载完成后滚动到列表顶部（携带 tabNo，只有该 Tab 可见时才滚动） */
    private val _scrollToTop = Channel<Int>(Channel.CONFLATED)
    val scrollToTop = _scrollToTop.receiveAsFlow()

    /** 显示 Snackbar 消息（如分页加载失败） */
    private val _snackbarMessage = Channel<UiMessage>(Channel.BUFFERED)
    val snackbarMessage = _snackbarMessage.receiveAsFlow()

    // ==================== per-tab 缓存 ====================

    /**
     * 按标签页分键的账单缓存。
     *
     * 生命周期与 ViewModel 绑定：ViewModel 存活时缓存有效，
     * ViewModel 销毁时自动释放，无需在 [CardCenterApi] 层保留静态引用。
     */
    private val tabCache: MutableMap<Int, BillPageInfo> = mutableMapOf()

    /** 逐 Tab 计时器 Job（必须在 init 之前声明，因为 init 中会调用 markTabLoading） */
    private val tabTimerJobs: MutableMap<Int, Job> = mutableMapOf()

    companion object {
        /** 全部 4 个 Tab 的 tabNo 列表 */
        private val ALL_TABS = listOf(1, 2, 4, 5)
        /** 仅 HTML 服务的 Tab（无筛选时，Tab 1 由 H5 服务） */
        private val HTML_ONLY_TABS = listOf(2, 4, 5)
    }

    init {
        // 首次进入：并发预加载 — H5 快速（Tab1）+ HTML 全量（全部 4 个 Tab）
        // ViewModel 返回复用时 init 不会重复执行，避免从 WebView 返回后二次触发。
        startConcurrentLoad()
    }

    // ==================== 协程管理 ====================

    /** H5 请求 Job（仅 Tab 1"全部"的快速路径） */
    private var h5Job: Job? = null

    /** HTML 请求 Job（一次请求返回全部 4 个 zone，服务多个 Tab） */
    private var htmlJob: Job? = null

    /** 分页加载 Job：按 Tab 分键，不同 Tab 各自独立，互不取消 */
    private val loadMoreJobs: MutableMap<Int, Job> = mutableMapOf()

    /** 加载更多计时器 Job：按 Tab 分键 */
    private val moreTimerJobs: MutableMap<Int, Job> = mutableMapOf()

    /** 请求世代号：被取消的旧请求不再清理新请求的逐 Tab 加载状态 */
    private var h5Gen = 0
    private var htmlGen = 0

    // ==================== 辅助判断 ====================

    /** 判断是否存在活跃的筛选条件 */
    private fun hasActiveFilter(): Boolean =
        _uiState.value.searchQuery.isNotBlank() ||
        _uiState.value.startDate.isNotBlank() ||
        _uiState.value.endDate.isNotBlank() ||
        _uiState.value.filterIncome ||
        _uiState.value.filterExpense

    /** H5 API 仅服务于"全部"标签页且无筛选时 */
    private fun useH5For(tabNo: Int): Boolean = !hasActiveFilter() && tabNo == 1

    /** 构造账单查询条件（整页加载与翻页共用，避免字段漂移） */
    private fun billFilter(pageNo: Int, tabNo: Int = 1) = BillFilter(
        tabNo = tabNo,
        pageNo = pageNo,
        tradeName = _uiState.value.searchQuery,
        startTime = _uiState.value.startDate,
        endTime = _uiState.value.endDate,
        timeType = 1,
        tradeDirect = buildSet {
            if (_uiState.value.filterIncome) add(2)
            if (_uiState.value.filterExpense) add(1)
        }
    )

    /** 写入某 Tab 的整页数据；仅当该 Tab 可见时请求回到列表顶部 */
    private fun putPageInfo(tabNo: Int, pageInfo: BillPageInfo) {
        tabCache[tabNo] = pageInfo
        _uiState.update { it.copy(tabCache = it.tabCache + (tabNo to pageInfo)) }
        if (_uiState.value.activeTab == tabNo) {
            _scrollToTop.trySend(tabNo)
        }
    }

    // ==================== 错误处理 ====================

    private fun handleBillError(error: Throwable) {
        _uiState.update {
            it.copy(
                isRefreshing = false,
                requiresReLogin = error is SessionExpiredException,
                errorMessage = if (error is SessionExpiredException) null else UiMessage(R.string.bill_load_failed)
            )
        }
    }

    /** 翻页失败的统一处理 */
    private fun onLoadMoreFailure(error: Throwable) {
        when {
            error is CancellationException -> Unit
            error is SessionExpiredException -> handleBillError(error)
            else -> _snackbarMessage.trySend(UiMessage(R.string.common_load_failed))
        }
    }

    // ==================== 并发加载（核心改动） ====================

    /**
     * 并发预加载：H5 和 HTML 各服务自己的 Tab，互不重叠。
     * - 无筛选：H5 API（快 ~3s）→ Tab 1；HTML API（慢 ~15-20s）→ Tab 2/4/5
     * - 有筛选：HTML API → 所有 Tab（H5 不支持筛选）
     *
     * @param force 跳过缓存命中检查强制请求（下拉刷新时使用）
     */
    private fun startConcurrentLoad(force: Boolean = false) {
        if (hasActiveFilter()) {
            // 有筛选：HTML 服务所有 Tab
            launchHtmlLoad(force = force, tabsToLoad = ALL_TABS)
        } else {
            // 无筛选：H5 服务 Tab 1，HTML 服务 Tab 2/4/5
            if (force || !tabCache.containsKey(1)) {
                launchH5Load()
            }
            val htmlTabs = HTML_ONLY_TABS.filter { force || !tabCache.containsKey(it) }
            if (htmlTabs.isNotEmpty()) {
                launchHtmlLoad(force = force, tabsToLoad = htmlTabs)
            }
        }
    }

    /**
     * 通过 H5 API 加载 Tab 1（全部），仅无筛选时使用。
     * 速度快 ~3s，但字段较少。HTML API 完成后会覆盖此结果。
     */
    private fun launchH5Load() {
        h5Job?.cancel()
        val gen = ++h5Gen
        h5Job = viewModelScope.launch {
            markTabLoading(1)
            try {
                val h5Response = api.fetchBillsH5(1).getOrThrow()
                putPageInfo(
                    1,
                    BillPageInfo(
                        records = h5Response.dtls?.map { it.toBillRecord() } ?: emptyList(),
                        currentPage = h5Response.pageno,
                        totalPages = h5Response.totalpage
                    )
                )
            } catch (e: SessionExpiredException) {
                AppLog.d("BillViewModel", "H5 接口会话过期，等待 HTML 请求兜底")
                // 不报错，等 HTML 请求兜底
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("BillViewModel", "H5 API 失败，等待 HTML API 兜底: ${e.message}")
            } finally {
                // 只有仍是当前这次请求时才清理加载状态，避免旧请求清掉新请求的加载态
                if (h5Gen == gen) markTabLoaded(1)
            }
        }
    }

    /**
     * 通过 HTML API 加载指定 Tab 的数据。
     * 服务器始终返回全部 zone，但仅将 [tabsToLoad] 中的 Tab 保存到缓存。
     *
     * @param force 跳过缓存检查强制请求（下拉刷新时使用）
     * @param tabsToLoad 需要加载的 Tab 列表（无筛选时 = [HTML_ONLY_TABS]，有筛选时 = [ALL_TABS]）
     */
    private fun launchHtmlLoad(force: Boolean = false, tabsToLoad: List<Int> = ALL_TABS) {
        htmlJob?.cancel()
        val gen = ++htmlGen
        htmlJob = viewModelScope.launch {
            val uncachedTabs = if (force) tabsToLoad else tabsToLoad.filter { !tabCache.containsKey(it) }
            uncachedTabs.forEach { markTabLoading(it) }

            try {
                val result = api.fetchBillsAllZones(billFilter(pageNo = 1))
                result.onSuccess { zoneMap ->
                    // 仅保存 tabsToLoad 范围内的 Tab 数据，H5 和 HTML 互不干扰
                    val filteredZoneMap = zoneMap.filterKeys { it in uncachedTabs }
                    tabCache.putAll(filteredZoneMap)
                    _uiState.update {
                        it.copy(
                            tabCache = it.tabCache + filteredZoneMap,
                            perTabError = emptyMap(),
                            isRefreshing = false
                        )
                    }
                    // 只有当前可见 Tab 的数据到达时才回到顶部；后台补数据的 Tab 不打扰用户
                    val activeTab = _uiState.value.activeTab
                    if (filteredZoneMap.containsKey(activeTab)) {
                        _scrollToTop.trySend(activeTab)
                    }
                }.onFailure { error ->
                    if (error is SessionExpiredException) {
                        handleBillError(error)
                    } else {
                        // 对尚未缓存的 Tab 记录逐 Tab 错误状态
                        val failedTabs = uncachedTabs.filter { !tabCache.containsKey(it) }
                        if (failedTabs.isNotEmpty()) {
                            val errorMsg = UiMessage(R.string.bill_load_failed)
                            _uiState.update {
                                it.copy(
                                    perTabError = it.perTabError + failedTabs.associateWith { errorMsg }
                                )
                            }
                        }
                        // 所有 Tab 都没有缓存时显示页面级错误
                        val hasAnyCache = ALL_TABS.any { tabCache.containsKey(it) }
                        if (!hasAnyCache) {
                            handleBillError(error)
                        } else {
                            AppLog.w("BillViewModel", "HTML API 失败，但已有缓存数据: ${error.message}")
                        }
                    }
                }
            } finally {
                if (htmlGen == gen) uncachedTabs.forEach { markTabLoaded(it) }
            }
        }
    }

    // ==================== 核心数据加载（下拉刷新 / 筛选） ====================

    /**
     * 加载账单数据（入口）
     *
     * 用于下拉刷新和筛选条件变更。
     * - 筛选变更（isRefresh=false）：清空缓存，重新加载
     * - 下拉刷新（isRefresh=true）：保留旧缓存，新请求到达后无缝覆盖，避免白屏
     *
     * 翻页状态在此**同步**清空，不等被取消的阻塞请求返回，否则翻页会被锁住直到超时。
     */
    fun loadBills(isRefresh: Boolean = false) {
        // 作废所有在途请求：世代号自增后，被取消的旧请求不再清理逐 Tab 加载状态
        h5Gen++
        htmlGen++
        // 取消所有进行中的请求
        h5Job?.cancel()
        htmlJob?.cancel()
        loadMoreJobs.values.forEach { it.cancel() }
        moreTimerJobs.values.forEach { it.cancel() }
        loadMoreJobs.clear()
        moreTimerJobs.clear()

        // 翻页状态立即复位
        _uiState.update {
            it.copy(loadingMoreTabs = emptySet(), moreElapsed = emptyMap())
        }

        // 刷新时保留旧缓存，避免白屏；筛选变更时才清空
        if (!isRefresh) {
            clearAllCache()
        }

        _uiState.update {
            it.copy(
                isRefreshing = isRefresh,
                errorMessage = null,
                requiresReLogin = false,
                perTabError = emptyMap()
            )
        }

        startConcurrentLoad(force = isRefresh)
    }

    // ==================== 分页加载 ====================

    /**
     * 加载指定标签页的下一页。
     *
     * 关键约定：
     * - 只按 [tabNo] 操作：游标取自该 Tab 自己的缓存，**不读 activeTab**
     * - 只做同 Tab 防重；**不取消其它 Tab 的翻页请求**
     * - 结果只写回 [tabNo] 自己的缓存，绝不污染其它 Tab
     *
     * @param tabNo 目标标签页（1=全部, 2=未付款, 4=成功, 5=失败）
     */
    fun loadNextPage(tabNo: Int) {
        val current = tabCache[tabNo] ?: return
        if (!current.hasNext) return
        if (loadMoreJobs[tabNo]?.isActive == true) return

        val nextPageNo = current.currentPage + 1
        loadMoreJobs[tabNo] = viewModelScope.launch {
            val self = coroutineContext[Job]
            _uiState.update { it.copy(loadingMoreTabs = it.loadingMoreTabs + tabNo) }
            startMoreTimer(tabNo)

            try {
                if (useH5For(tabNo)) {
                    // H5 API：仅返回当前页数据
                    api.fetchBillsH5(nextPageNo)
                        .onSuccess { h5Response ->
                            savePageData(
                                tabNo,
                                current,
                                BillPageInfo(
                                    records = h5Response.dtls?.map { it.toBillRecord() } ?: emptyList(),
                                    currentPage = h5Response.pageno,
                                    totalPages = h5Response.totalpage
                                )
                            )
                        }
                        .onFailure { onLoadMoreFailure(it) }
                } else {
                    // HTML API：服务器始终返回全部 4 个 zone，只取自己那一区
                    api.fetchBillsAllZones(billFilter(nextPageNo, tabNo))
                        .onSuccess { zoneMap ->
                            zoneMap[tabNo]?.let { savePageData(tabNo, current, it) }
                        }
                        .onFailure { onLoadMoreFailure(it) }
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                // 仅当自己仍是该 Tab 的当前任务时才复位，防止被取消的旧任务覆盖新任务状态
                if (loadMoreJobs[tabNo] === self) {
                    loadMoreJobs.remove(tabNo)
                    stopMoreTimer(tabNo)
                    _uiState.update { it.copy(loadingMoreTabs = it.loadingMoreTabs - tabNo) }
                }
            }
        }
    }

    /** 保存翻页数据到该 Tab 自己的缓存 */
    private fun savePageData(tabNo: Int, current: BillPageInfo, nextPage: BillPageInfo) {
        val mergedPageInfo = mergePageData(current, nextPage)
        tabCache[tabNo] = mergedPageInfo
        _uiState.update {
            it.copy(tabCache = it.tabCache + (tabNo to mergedPageInfo))
        }
    }

    // ==================== 标签切换（不取消旧请求） ====================

    /**
     * 切换标签页
     *
     * 关键变化：**不再取消旧 Tab 的请求**。
     * - 优先从 [tabCache] 恢复数据
     * - 无缓存时：若对应请求已在运行则只等结果，否则启动新的请求
     */
    fun switchTab(tabNo: Int) {
        if (_uiState.value.activeTab == tabNo) return

        _uiState.update {
            it.copy(
                activeTab = tabNo,
                errorMessage = null,
                requiresReLogin = false
            )
        }

        // 有缓存：直接显示（每个 Tab 的滚动位置由 UI 层的 LazyListState 各自保留）
        if (tabCache[tabNo] != null) return

        // 无缓存：Tab 1 无筛选时走 H5 快速路径；已在跑就不重复发请求
        if (useH5For(tabNo)) {
            if (h5Job?.isActive != true) launchH5Load()
            return
        }

        // Tab 2/4/5 或有筛选时走 HTML
        if (htmlJob?.isActive != true) {
            launchHtmlLoad(tabsToLoad = if (hasActiveFilter()) ALL_TABS else HTML_ONLY_TABS)
        }
    }

    // ==================== 缓存管理 ====================

    /** 清空所有标签页的缓存（筛选条件变更时调用） */
    private fun clearAllCache() {
        tabCache.clear()
        _uiState.update { it.copy(tabCache = emptyMap()) }
    }

    // ==================== 逐 Tab 加载状态管理 ====================

    private fun markTabLoading(tabNo: Int) {
        _uiState.update {
            it.copy(
                perTabLoading = it.perTabLoading + (tabNo to true),
                perTabElapsed = it.perTabElapsed + (tabNo to 0L)
            )
        }
        startTabTimer(tabNo)
    }

    private fun markTabLoaded(tabNo: Int) {
        stopTabTimer(tabNo)
        _uiState.update {
            it.copy(perTabLoading = it.perTabLoading + (tabNo to false))
        }
    }

    // ==================== 筛选相关 ====================

    /** 展开/收起筛选面板 */
    fun toggleFilterPanel() {
        _uiState.update {
            if (!it.showFilterPanel) {
                it.copy(
                    showFilterPanel = true,
                    tempSearchQuery = it.searchQuery,
                    tempStartDate = it.startDate,
                    tempEndDate = it.endDate,
                    tempIncome = it.filterIncome,
                    tempExpense = it.filterExpense
                )
            } else {
                it.copy(showFilterPanel = false)
            }
        }
    }

    /** 应用筛选条件 → 重新加载 */
    fun applyFilter() {
        _uiState.update {
            it.copy(
                searchQuery = it.tempSearchQuery,
                startDate = it.tempStartDate,
                endDate = it.tempEndDate,
                filterIncome = it.tempIncome,
                filterExpense = it.tempExpense,
                showFilterPanel = false
            )
        }
        clearAllCache()  // 筛选条件变更，缓存不再有效
        loadBills()
    }

    /** 重置暂存筛选条件 */
    fun resetFilter() {
        _uiState.update {
            it.copy(
                tempSearchQuery = "",
                tempStartDate = "",
                tempEndDate = "",
                tempIncome = false,
                tempExpense = false
            )
        }
    }

    // ── 暂存值更新方法（筛选面板内编辑）──

    fun onSearchQueryChange(value: String) {
        _uiState.update { it.copy(tempSearchQuery = value) }
    }

    fun onStartDateChange(value: String) {
        _uiState.update { it.copy(tempStartDate = value) }
    }

    fun onEndDateChange(value: String) {
        _uiState.update { it.copy(tempEndDate = value) }
    }

    fun onIncomeCheckedChange(value: Boolean) {
        _uiState.update { it.copy(tempIncome = value) }
    }

    fun onExpenseCheckedChange(value: Boolean) {
        _uiState.update { it.copy(tempExpense = value) }
    }

    // ==================== 翻页计时器（按 Tab 分键） ====================

    private fun startMoreTimer(tabNo: Int) {
        moreTimerJobs.remove(tabNo)?.cancel()
        _uiState.update { it.copy(moreElapsed = it.moreElapsed + (tabNo to 0L)) }
        moreTimerJobs[tabNo] = viewModelScope.launch {
            while (true) {
                delay(1000L)
                _uiState.update {
                    it.copy(moreElapsed = it.moreElapsed + (tabNo to (it.moreElapsed[tabNo] ?: 0L) + 1))
                }
            }
        }
    }

    private fun stopMoreTimer(tabNo: Int) {
        moreTimerJobs.remove(tabNo)?.cancel()
    }

    // ==================== 逐 Tab 计时器 ====================

    private fun startTabTimer(tabNo: Int) {
        tabTimerJobs[tabNo]?.cancel()
        _uiState.update {
            it.copy(perTabElapsed = it.perTabElapsed + (tabNo to 0L))
        }
        tabTimerJobs[tabNo] = viewModelScope.launch {
            while (true) {
                delay(1000L)
                _uiState.update {
                    val currentElapsed = it.perTabElapsed[tabNo] ?: 0L
                    it.copy(perTabElapsed = it.perTabElapsed + (tabNo to currentElapsed + 1))
                }
            }
        }
    }

    private fun stopTabTimer(tabNo: Int) {
        tabTimerJobs[tabNo]?.cancel()
        tabTimerJobs.remove(tabNo)
    }
}
