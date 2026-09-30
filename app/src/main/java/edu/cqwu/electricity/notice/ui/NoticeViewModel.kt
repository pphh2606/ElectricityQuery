package edu.cqwu.electricity.notice.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import edu.cqwu.electricity.R
import edu.cqwu.electricity.notice.data.NoticeApi
import edu.cqwu.electricity.notice.data.NoticeItem
import edu.cqwu.electricity.common.net.SessionExpiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 通知公告列表页的 ViewModel。
 *
 * 挂在通知列表目的地的 `NavBackStackEntry` 作用域上，**出栈即销毁**：
 * 从首页重新进入是全新实例（列表必然重新加载），从详情返回则实例仍在、列表与滚动位置保留。
 * 「待打开通知」的预览数据不在这里，见 [NoticePreviewHolder]。
 */
class NoticeViewModel(application: Application) : AndroidViewModel(application) {

    // ── 列表状态（使用 mutableStateOf 使 Compose 可观察）──
    var items by mutableStateOf<List<NoticeItem>>(emptyList())
        private set
    var currentPage by mutableIntStateOf(0)
        private set
    var totalItem by mutableIntStateOf(0)
        private set
    var totalPage by mutableIntStateOf(1)
        private set
    var isLoading by mutableStateOf(true)
        private set
    var isLoadingMore by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    /** 是否因登录过期而失败（SessionExpiredException），UI 据此显示「重新登录」引导 */
    var requiresReLogin by mutableStateOf(false)
        private set

    val hasMore: Boolean get() = items.size < totalItem

    /**
     * 本实例是否还没加载过第一页。
     *
     * 本 ViewModel 挂在通知列表目的地的 `NavBackStackEntry` 作用域上：从首页进入时是**新实例**
     * （这里为初始值 true，于是必然加载）；从详情返回列表时是**同一个实例**、列表 entry 仍在返回栈中
     * （这里已被置为 false，于是保留列表内容与滚动位置）；退出首页后 entry 销毁、实例随之销毁，
     * 下次进入又是新实例。因此这里不需要任何页面去手动复位。
     */
    var needsInitialLoad by mutableStateOf(true)

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
        requiresReLogin = false

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
            totalPage = pageResult.totalPage
            currentPage = pageNo
            isLoading = false
            isLoadingMore = false
        }.onFailure { error ->
            isLoading = false
            isLoadingMore = false
            requiresReLogin = error is SessionExpiredException
            errorMessage = if (requiresReLogin) null else error.message ?: getApplication<Application>().getString(R.string.notice_list_load_failed)
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
        totalPage = 1
        errorMessage = null
        requiresReLogin = false
    }

    /**
     * 清除搜索状态
     */
    fun clearSearch() {
        searchKeyword = ""
        items = emptyList()
        currentPage = 0
        totalItem = 0
        totalPage = 1
        errorMessage = null
        requiresReLogin = false
    }
}
