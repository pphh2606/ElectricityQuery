package edu.cqwu.electricity.notice.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import edu.cqwu.electricity.notice.data.NoticeApi
import edu.cqwu.electricity.notice.data.NoticeDetailQp
import edu.cqwu.electricity.notice.data.NoticeItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 通知公告 ViewModel
 *
 * 列表数据和详情数据缓存在此，导航切换时不会销毁。
 * 从通知页返回首页时调用 [clear] 清空缓存。
 */
class NoticeViewModel : ViewModel() {

    // ── 列表状态（使用 mutableStateOf 使 Compose 可观察）──
    var items by mutableStateOf<List<NoticeItem>>(emptyList())
        private set
    var currentPage by mutableIntStateOf(0)
        private set
    var totalItem by mutableIntStateOf(0)
        private set
    var isLoading by mutableStateOf(true)
        private set
    var isLoadingMore by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    val hasMore: Boolean get() = items.size < totalItem

    /**
     * 控制从首页进入通知页时是否需要刷新列表。
     * 从通知页返回首页时设为 true，从详情返回列表时保持 false。
     */
    var listRefreshEnabled by mutableStateOf(true)

    // ── 搜索状态 ──
    var searchKeyword by mutableStateOf("")
        private set

    private val api = NoticeApi()

    /**
     * 加载指定页
     *
     * @param keyword 搜索关键词，非 null 时按 cti 搜索
     */
    suspend fun loadPage(pageNo: Int, isRefresh: Boolean = false, keyword: String? = null) {
        if (isRefresh) isLoading = true else isLoadingMore = true
        errorMessage = null

        val result = withContext(Dispatchers.IO) {
            api.fetchNoticePage(pageNo, keyword)
        }

        result.onSuccess { pageResult ->
            items = if (isRefresh || pageNo == 0) {
                pageResult.items
            } else {
                items + pageResult.items
            }
            totalItem = pageResult.totalItem
            currentPage = pageNo
            isLoading = false
            isLoadingMore = false
        }.onFailure { error ->
            isLoading = false
            isLoadingMore = false
            errorMessage = error.message ?: "加载通知公告失败"
        }
    }

    /**
     * 设置搜索关键词并清空旧数据（搜索由 UI 层直接调用 loadPage）
     */
    fun search(keyword: String) {
        searchKeyword = keyword
        items = emptyList()
        currentPage = 0
        totalItem = 0
        errorMessage = null
    }

    /**
     * 清除搜索状态
     */
    fun clearSearch() {
        searchKeyword = ""
        items = emptyList()
        currentPage = 0
        totalItem = 0
        errorMessage = null
    }

    // ── 详情缓存 ──
    private val detailCache = mutableMapOf<String, NoticeDetailQp>()

    /**
     * 获取缓存的详情，如果未缓存则请求 API
     */
    suspend fun getDetail(wid: String): NoticeDetailQp? {
        detailCache[wid]?.let { return it }

        val result = withContext(Dispatchers.IO) {
            api.fetchNoticeDetail(wid)
        }
        result.onSuccess { qp ->
            detailCache[wid] = qp
            return qp
        }
        return null
    }

    /**
     * 直接存入详情缓存（用于下拉刷新后缓存更新）
     */
    fun putDetail(wid: String, detail: NoticeDetailQp) {
        detailCache[wid] = detail
    }

    /**
     * 移除指定通知的详情缓存（从详情页返回时调用）
     */
    fun removeDetail(wid: String) {
        detailCache.remove(wid)
    }

    /**
     * 清空所有缓存（从通知页返回首页时调用）
     */
    fun clear() {
        items = emptyList()
        currentPage = 0
        totalItem = 0
        isLoading = true
        isLoadingMore = false
        errorMessage = null
        detailCache.clear()
    }
}
