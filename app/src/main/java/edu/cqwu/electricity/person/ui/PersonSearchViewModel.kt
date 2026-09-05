package edu.cqwu.electricity.person.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.cqwu.electricity.common.net.SessionExpiredException
import edu.cqwu.electricity.person.data.PersonRow
import edu.cqwu.electricity.person.data.PersonSearchApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 查找人员 ViewModel。
 *
 * 管理人员列表的搜索、分页加载和刷新状态。
 * - 进入页面自动搜索一次（关键词为空 → 返回全部人员）
 * - 输入关键字仅更新输入值，点击搜索图标（或键盘搜索键）后才发起请求
 * - 关键词为空时搜索返回全部人员
 * - 关键词变化时通过 generation 标记丢弃过期请求结果
 */
class PersonSearchViewModel : ViewModel() {

    /** UI 状态 */
    sealed class UiState {
        /** 初始态（未输入关键词） */
        data object Idle : UiState()

        data object Loading : UiState()

        data class Success(
            val rows: List<PersonRow>,
            val hasMore: Boolean,
            /** 共搜索到的人数（totalSize） */
            val totalSize: Int,
            /** 当前页码（从 1 起） */
            val currentPage: Int,
            /** 总页数 = ceil(totalSize / pageSize) */
            val totalPages: Int,
        ) : UiState()

        data class Error(
            val message: String,
            val requiresReLogin: Boolean = false,
        ) : UiState()
    }

    private val api = PersonSearchApi()
    private val pageSize = 20
    private var currentPage = 1
    private var currentKeyword = ""

    /** 搜索代数：关键词每变化一次 +1，用于丢弃过期请求结果 */
    private var searchGeneration = 0
    private var searchJob: Job? = null

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** 是否正在加载更多 */
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _keyword = MutableStateFlow("")
    val keyword: StateFlow<String> = _keyword.asStateFlow()

    init {
        // 进入页面自动搜索一次（此时关键词为空 → 搜索全部人员）
        search()
    }

    /** 关键词输入回调（UI 层直接绑定输入框，仅更新输入值，不触发搜索） */
    fun onKeywordChange(value: String) {
        _keyword.value = value
        // 输入变化后作废在途请求并回到初始态，等待点击搜索图标
        searchGeneration++
        searchJob?.cancel()
        _isLoadingMore.value = false
        _uiState.value = UiState.Idle
    }

    /** 点击搜索图标（或键盘搜索键）触发搜索；关键词为空时搜索全部人员 */
    fun search() {
        startSearch(_keyword.value)
    }

    /** 下拉刷新：与 [search] 行为一致（用当前关键词重新搜索） */
    fun refresh() = search()

    /** 清空关键词并回到初始态 */
    fun clearKeyword() = onKeywordChange("")

    /** 计算总页数（totalSize <= 0 时为 0 页） */
    private fun totalPagesOf(totalSize: Int): Int =
        if (totalSize <= 0) 0 else (totalSize + pageSize - 1) / pageSize

    private fun startSearch(keyword: String) {
        searchJob?.cancel()
        searchGeneration++
        val generation = searchGeneration
        searchJob = viewModelScope.launch {
            val trimmed = keyword.trim()
            currentKeyword = trimmed
            currentPage = 1
            if (generation == searchGeneration) {
                _uiState.value = UiState.Loading
            }
            val result = api.search(trimmed, 1, pageSize)
            // 关键词已变化则丢弃过期结果
            if (generation != searchGeneration) return@launch
            result.fold(
                onSuccess = { r ->
                    _uiState.value = UiState.Success(
                        rows = r.rows,
                        hasMore = r.hasMore,
                        totalSize = r.totalSize,
                        currentPage = 1,
                        totalPages = totalPagesOf(r.totalSize),
                    )
                },
                onFailure = { e ->
                    _uiState.value = UiState.Error(
                        message = e.message ?: "",
                        requiresReLogin = e is SessionExpiredException,
                    )
                },
            )
        }
    }

    /** 滚动到底部时加载下一页 */
    fun loadMore() {
        if (_isLoadingMore.value || _uiState.value !is UiState.Success) return
        val state = _uiState.value as UiState.Success
        if (!state.hasMore) return

        val generation = searchGeneration
        val keyword = currentKeyword
        _isLoadingMore.value = true
        currentPage++
        viewModelScope.launch {
            val result = api.search(keyword, currentPage, pageSize)
            // 关键词已变化则丢弃过期结果
            if (generation != searchGeneration) return@launch
            result.fold(
                onSuccess = { r ->
                    _uiState.value = UiState.Success(
                        rows = state.rows + r.rows,
                        hasMore = r.hasMore,
                        totalSize = state.totalSize,
                        currentPage = currentPage,
                        totalPages = state.totalPages,
                    )
                },
                onFailure = { _ ->
                    currentPage-- // 回退页码，保留现有列表
                },
            )
            _isLoadingMore.value = false
        }
    }
}
