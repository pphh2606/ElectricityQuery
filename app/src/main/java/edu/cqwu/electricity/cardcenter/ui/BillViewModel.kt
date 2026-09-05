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

/**
 * 账单页面 UI 状态数据类
 *
 * 替代 [BillScreen] 中原先 22 个散落的 `remember { mutableStateOf(...) }`。
 * ViewModel 在 Navigation 返回后存活，状态自动恢复，彻底解决"从 WebView 返回后自动重载"问题。
 */
data class BillUiState(
    // ── 加载状态 ──
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    /** 当前正在加载更多的是哪个 Tab（null 表示没有加载更多请求在运行） */
    val loadingMoreTab: Int? = null,
    val errorMessage: UiMessage? = null,
    val requiresReLogin: Boolean = false,
    val billPageInfo: BillPageInfo? = null,

    // ── 加载计时 ──
    val loadMoreElapsed: Long = 0L,

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
 * 4. 标签切换时**不取消**进行中的请求，旧 Tab 结果到达后自动存入缓存
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

    /** 加载完成后滚动到列表顶部 */
    private val _scrollToTop = Channel<Unit>(Channel.BUFFERED)
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
        /** 单个 Tab 最大记录数（防 OOM） */
        private const val MAX_RECORDS = 500
    }

    init {
        // 首次进入：并发预加载 — H5 快速（Tab1）+ HTML 全量（全部 4 个 Tab）
        // ViewModel 返回复用时 init 不会重复执行，避免从 WebView 返回后二次触发。
        startConcurrentLoad()
    }

    // ==================== 协程管理 ====================

    /** H5 请求 Job（仅 Tab 1"全部"的快速路径） */
    private var h5Job: Job? = null

    /** HTML 请求 Job（一次请求返回全部 4 个 zone，服务所有 Tab） */
    private var htmlJob: Job? = null

    /** 分页加载 Job：独立于 h5Job/htmlJob，避免干扰预加载 */
    private var loadMoreJob: Job? = null

    /** 加载更多计时器 Job */
    private var moreTimerJob: Job? = null

    // ==================== 辅助判断 ====================

    /** 判断是否存在活跃的筛选条件 */
    private fun hasActiveFilter(): Boolean =
        _uiState.value.searchQuery.isNotBlank() ||
        _uiState.value.startDate.isNotBlank() ||
        _uiState.value.endDate.isNotBlank() ||
        _uiState.value.filterIncome ||
        _uiState.value.filterExpense

    /** H5 API 仅服务于"全部"标签页且无筛选时 */
    private fun useH5(): Boolean = !hasActiveFilter() && _uiState.value.activeTab == 1

    // ==================== 错误处理 ====================

    private fun handleBillError(error: Throwable) {
        _uiState.update {
            it.copy(
                isLoading = false,
                isRefreshing = false,
                requiresReLogin = error is SessionExpiredException,
                errorMessage = if (error is SessionExpiredException) null else UiMessage(R.string.bill_load_failed)
            )
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
        h5Job = viewModelScope.launch {
            markTabLoading(1)
            try {
                val h5Response = api.fetchBillsH5(1).getOrThrow()
                val pageInfo = BillPageInfo(
                    records = h5Response.dtls?.map { it.toBillRecord() } ?: emptyList(),
                    currentPage = h5Response.pageno,
                    totalPages = h5Response.totalpage
                )
                tabCache[1] = pageInfo
                val isActiveTab = _uiState.value.activeTab == 1
                _uiState.update {
                    it.copy(
                        tabCache = it.tabCache + (1 to pageInfo),
                        billPageInfo = if (isActiveTab) pageInfo else it.billPageInfo,
                        isLoading = if (isActiveTab) false else it.isLoading
                    )
                }
                if (isActiveTab) {
                    _scrollToTop.trySend(Unit)
                }
            } catch (e: SessionExpiredException) {
                // 不报错，等 HTML 请求兜底
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("BillViewModel", "H5 API 失败，等待 HTML API 兜底: ${e.message}")
            } finally {
                markTabLoaded(1)
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
        htmlJob = viewModelScope.launch {
            val uncachedTabs = if (force) tabsToLoad else tabsToLoad.filter { !tabCache.containsKey(it) }
            uncachedTabs.forEach { markTabLoading(it) }

            try {
                val state = _uiState.value
                val currentFilter = BillFilter(
                    tabNo = 1,  // tabNo 被服务器忽略，用 1 即可
                    pageNo = 1,
                    tradeName = state.searchQuery,
                    startTime = state.startDate,
                    endTime = state.endDate,
                    timeType = 1,
                    tradeDirect = buildSet {
                        if (state.filterIncome) add(2)
                        if (state.filterExpense) add(1)
                    }
                )
                val result = api.fetchBillsAllZones(currentFilter)
                result.onSuccess { zoneMap ->
                    // 仅保存 tabsToLoad 范围内的 Tab 数据，H5 和 HTML 互不干扰
                    val filteredZoneMap = zoneMap.filterKeys { it in uncachedTabs }
                    filteredZoneMap.forEach { (tabNo, pageInfo) ->
                        tabCache[tabNo] = pageInfo
                    }
                    // 清除逐 Tab 错误状态
                    _uiState.update { it.copy(perTabError = emptyMap()) }

                    val activeTab = _uiState.value.activeTab
                    val pageInfo = filteredZoneMap[activeTab] ?: filteredZoneMap.values.firstOrNull()
                        ?: _uiState.value.billPageInfo

                    _uiState.update {
                        it.copy(
                            tabCache = it.tabCache + filteredZoneMap,
                            billPageInfo = pageInfo,
                            isLoading = false,
                            isRefreshing = false
                        )
                    }
                    _scrollToTop.trySend(Unit)
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
                uncachedTabs.forEach { markTabLoaded(it) }
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
     */
    fun loadBills(isRefresh: Boolean = false) {
        // 取消所有进行中的请求
        h5Job?.cancel()
        htmlJob?.cancel()
        loadMoreJob?.cancel()
        // 刷新时保留旧缓存，避免白屏；筛选变更时才清空
        if (!isRefresh) {
            clearAllCache()
        }

        _uiState.update {
            it.copy(
                isLoading = !isRefresh,
                isRefreshing = isRefresh,
                errorMessage = null,
                requiresReLogin = false,
                perTabError = emptyMap()
            )
        }

        // 计时器由 launchH5Load / launchHtmlLoad 内部的 markTabLoading 管理
        startConcurrentLoad(force = isRefresh)
    }

    // ==================== 分页加载（不变） ====================

    /**
     * 加载下一页
     *
     * - 合并到已有记录列表
     * - 去重（按 billNo）
     * - 最大 500 条防 OOM
     */
    fun loadNextPage() {
        val current = _uiState.value.billPageInfo ?: return
        if (!current.hasNext) return

        val expectedTab = _uiState.value.activeTab

        // 同 Tab、同标识符过滤：避免重复触发
        if (_uiState.value.isLoadingMore) {
            // 同 Tab 正在加载中 → 跳过
            if (_uiState.value.loadingMoreTab == expectedTab) return
            // 不同 Tab → 取消旧 Job，新 Tab 抢占
            loadMoreJob?.cancel()
        }

        val nextPageNo = current.currentPage + 1
        loadMoreJob = viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoadingMore = true, loadingMoreTab = expectedTab) }
                startMoreTimer()

                if (useH5()) {
                    // H5 API：仅返回当前页数据，只更新当前 Tab 缓存
                    val result = api.fetchBillsH5(nextPageNo)
                    result.onSuccess { h5Response ->
                        val nextPage = BillPageInfo(
                            records = h5Response.dtls?.map { it.toBillRecord() } ?: emptyList(),
                            currentPage = h5Response.pageno,
                            totalPages = h5Response.totalpage
                        )
                        // 无论当前 Tab 是否变化，数据仍保存到 original tab 缓存
                        savePageData(expectedTab, current, nextPage)
                    }.onFailure { error ->
                        if (error is CancellationException) return@onFailure
                        if (error is SessionExpiredException) {
                            handleBillError(error)
                        } else {
                            _snackbarMessage.trySend(UiMessage(R.string.common_load_failed))
                        }
                    }
                } else {
                    // HTML API：服务器始终返回全部 4 个 zone
                    val state = _uiState.value
                    val nextFilter = BillFilter(
                        tabNo = state.activeTab,
                        pageNo = nextPageNo,
                        tradeName = state.searchQuery,
                        startTime = state.startDate,
                        endTime = state.endDate,
                        timeType = 1,
                        tradeDirect = buildSet {
                            if (state.filterIncome) add(2)
                            if (state.filterExpense) add(1)
                        }
                    )
                    val result = api.fetchBillsAllZones(nextFilter)
                    result.onSuccess { zoneMap ->
                        val nextPage = zoneMap[expectedTab] ?: return@onSuccess
                        val mergedPageInfo = mergePageData(current, nextPage)

                        // 仅更新 expectedTab 的缓存，其他 Tab 的 zone 数据是第 nextPageNo 页，不能覆盖
                        tabCache[expectedTab] = mergedPageInfo
                        _uiState.update {
                            val newCache = it.tabCache.toMutableMap()
                            newCache[expectedTab] = mergedPageInfo
                            it.copy(
                                tabCache = newCache,
                                billPageInfo = if (it.activeTab == expectedTab) mergedPageInfo else it.billPageInfo
                            )
                        }
                    }.onFailure { error ->
                        if (error is CancellationException) return@onFailure
                        if (error is SessionExpiredException) {
                            handleBillError(error)
                        } else {
                            _snackbarMessage.trySend(UiMessage(R.string.common_load_failed))
                        }
                    }
                }
            } finally {
                stopMoreTimer()
                // 仅当当前 Job 对应的 Tab 仍持有 loadingMoreTab 时才重置
                // 防止被取消的旧 Job 的 finally 覆盖新 Job 的状态
                _uiState.update {
                    if (it.loadingMoreTab == expectedTab) {
                        it.copy(isLoadingMore = false, loadingMoreTab = null)
                    } else {
                        it
                    }
                }
            }
        }
    }

    /**
     * 合并翻页数据、去重（内部工具方法）
     */
    private fun mergePageData(current: BillPageInfo, nextPage: BillPageInfo): BillPageInfo {
        val merged = (current.records + nextPage.records)
            .distinctBy { it.billNo }
            .take(MAX_RECORDS)
        val capped = merged.size >= MAX_RECORDS
        return nextPage.copy(
            records = merged,
            // 达到上限后，将当前页标记为最后一页，阻止继续翻页
            totalPages = if (capped) nextPage.currentPage else nextPage.totalPages
        )
    }

    /**
     * 保存翻页数据到缓存（无论当前 Tab 是否变化）
     */
    private fun savePageData(tabNo: Int, current: BillPageInfo, nextPage: BillPageInfo) {
        val mergedPageInfo = mergePageData(current, nextPage)
        tabCache[tabNo] = mergedPageInfo
        _uiState.update {
            val newCache = it.tabCache.toMutableMap()
            newCache[tabNo] = mergedPageInfo
            it.copy(
                billPageInfo = if (it.activeTab == tabNo) mergedPageInfo else it.billPageInfo,
                tabCache = newCache
            )
        }
    }

    // ==================== 标签切换（不取消旧请求） ====================

    /**
     * 切换标签页
     *
     * 关键变化：**不再取消旧 Tab 的请求**。
     * - 优先从 [tabCache] 恢复数据
     * - 无缓存时：如果 HTML 请求正在运行，显示加载状态等待结果；否则启动新的 HTML 请求
     */
    fun switchTab(tabNo: Int) {
        if (_uiState.value.activeTab == tabNo) return

        // 优先从缓存恢复
        val cached = tabCache[tabNo]
        if (cached != null) {
            _uiState.update {
                it.copy(
                    activeTab = tabNo,
                    billPageInfo = cached,
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = null,
                    requiresReLogin = false
                )
            }
            return
        }

        // 无缓存，切换到加载状态
        _uiState.update {
            it.copy(
                activeTab = tabNo,
                billPageInfo = null,
                isLoading = true,
                errorMessage = null,
                requiresReLogin = false
            )
        }

        // Tab 1 无筛选时走 H5 快速路径
        if (!hasActiveFilter() && tabNo == 1) {
            launchH5Load()
            return
        }

        // Tab 2/4/5 或有筛选时走 HTML
        if (htmlJob?.isActive == true) {
            val alreadyLoading = _uiState.value.perTabLoading[tabNo] == true
            if (!alreadyLoading) {
                markTabLoading(tabNo)
            }
            return
        }

        // 没有 HTML 请求在运行 → 启动新的 HTML 请求
        val tabsToLoad = if (hasActiveFilter()) ALL_TABS else HTML_ONLY_TABS
        launchHtmlLoad(tabsToLoad = tabsToLoad)
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
                showFilterPanel = false,
                billPageInfo = null
                // isLoading 由 loadBills() 统一设置
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

    // ==================== 加载计时器 ====================

    private fun startMoreTimer() {
        moreTimerJob?.cancel()
        _uiState.update { it.copy(loadMoreElapsed = 0L) }
        moreTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                _uiState.update { it.copy(loadMoreElapsed = it.loadMoreElapsed + 1) }
            }
        }
    }

    private fun stopMoreTimer() {
        moreTimerJob?.cancel()
        moreTimerJob = null
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
