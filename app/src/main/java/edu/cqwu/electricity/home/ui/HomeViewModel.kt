package edu.cqwu.electricity.home.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.Coil
import coil.request.CachePolicy
import coil.request.ImageRequest
import edu.cqwu.electricity.R
import edu.cqwu.electricity.settings.data.SettingsKeys
import edu.cqwu.electricity.settings.data.SettingsPreferences
import edu.cqwu.electricity.home.data.CustomServiceEntry
import edu.cqwu.electricity.home.data.HomeApp
import edu.cqwu.electricity.home.data.HomeCategory
import edu.cqwu.electricity.home.data.HomeJsonLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUiState(
    val categories: List<HomeCategory> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val isEditMode: Boolean = false,
    val myServiceIds: Set<String> = emptySet(),
    val customServices: List<CustomServiceEntry> = emptyList()
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val jsonLoader = HomeJsonLoader(application)
    private val settingsPreferences = SettingsPreferences(application)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // 先从持久化存储加载已收藏的服务 ID 和自定义服务列表
        val savedIds = settingsPreferences.get(SettingsKeys.MY_SERVICES)
        val savedCustomServices = settingsPreferences.get(SettingsKeys.CUSTOM_SERVICES)
        // 先展示 Loading 状态，不阻塞主线程
        _uiState.value = HomeUiState(
            isLoading = true,
            myServiceIds = savedIds,
            customServices = savedCustomServices
        )

        // 异步加载 JSON 数据
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                jsonLoader.loadCategories()
            }
            result.onSuccess { categories ->
                _uiState.update {
                    it.copy(categories = categories, isLoading = false, error = null)
                }
                // 后台预加载图标到 Coil 缓存
                prefetchIcons(categories)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isLoading = false, error = error.message ?: getApplication<Application>().getString(R.string.common_load_failed))
                }
            }
        }
    }

    /**
     * 手动下拉刷新首页数据。
     * 先清空 HomeJsonLoader 的内存缓存，再重新从 assets 加载。
     * 刷新指示器的显示时长由 UI 层控制，ViewModel 只负责状态驱动。
     */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }

            // 清空缓存，确保重新读取资产文件
            jsonLoader.invalidateCache()
            val result = withContext(Dispatchers.IO) {
                jsonLoader.loadCategories()
            }

            result
                .onSuccess { categories ->
                    _uiState.update {
                        it.copy(categories = categories, isRefreshing = false, error = null)
                    }
                    // 刷新后后台重新预加载图标
                    prefetchIcons(categories)
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isRefreshing = false, error = e.message ?: getApplication<Application>().getString(R.string.common_refresh_failed))
                    }
                }
        }
    }

    /**
     * 设置搜索关键词。
     * 如果 query 非空且不在搜索模式，自动进入搜索模式；
     * 如果 query 为空且处于搜索模式，仅清空文本（不退出搜索模式）。
     */
    fun setSearchQuery(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                isSearching = query.isNotEmpty() || it.isSearching
            )
        }
    }

    /**
     * 进入搜索模式（清空历史搜索词）
     */
    fun enterSearchMode() {
        _uiState.update { it.copy(isSearching = true, searchQuery = "") }
    }

    /**
     * 退出搜索模式并清空搜索词
     */
    fun clearSearch() {
        _uiState.update { it.copy(isSearching = false, searchQuery = "") }
    }

    /**
     * 预加载所有首页图标到 Coil 的缓存中（内存缓存 + 磁盘缓存）。
     * 作为 suspend 函数，由调用方在协程中调度。
     */
    private suspend fun prefetchIcons(categories: List<HomeCategory>) {
        val context = getApplication<Application>()
        val imageLoader = Coil.imageLoader(context)
        withContext(Dispatchers.IO) {
            categories.forEach { category ->
                category.apps.forEach { app ->
                    if (app.iconUrl.isNotBlank()) {
                        val request = ImageRequest.Builder(context)
                            .data(app.iconUrl)
                            .size(128) // 缩放到目标尺寸以节省缓存空间
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build()
                        imageLoader.enqueue(request)
                    }
                }
            }
        }
    }

    // ── 我的服务（编辑模式） ──

    /**
     * 从当前分类中找出所有已收藏的完整 [HomeApp] 列表。
     */
    val myServiceApps: List<HomeApp>
        get() {
            val ids = _uiState.value.myServiceIds
            if (ids.isEmpty()) return emptyList()
            return _uiState.value.categories
                .flatMap { it.apps }
                .filter { it.appId in ids }
        }

    /**
     * 进入编辑模式（同时退出搜索模式）。
     */
    fun enterEditMode() {
        _uiState.update {
            it.copy(isEditMode = true, isSearching = false, searchQuery = "")
        }
    }

    /**
     * 退出编辑模式，并将当前收藏集合持久化保存。
     */
    fun exitEditMode() {
        val ids = _uiState.value.myServiceIds
        settingsPreferences.set(SettingsKeys.MY_SERVICES, ids)
        _uiState.update { it.copy(isEditMode = false) }
    }

    /**
     * 将指定 appId 添加到我的服务。
     */
    fun addToMyServices(appId: String) {
        _uiState.update {
            it.copy(myServiceIds = it.myServiceIds + appId)
        }
    }

    /**
     * 将指定 appId 从我的服务中移除。
     */
    fun removeFromMyServices(appId: String) {
        _uiState.update {
            it.copy(myServiceIds = it.myServiceIds - appId)
        }
    }

    // ── 自定义网站服务 ──

    /**
     * 添加一个自定义网站快捷方式并持久化保存。
     */
    fun addCustomService(title: String, url: String, iconUri: String?) {
        val entry = CustomServiceEntry(title = title, url = url, iconUri = iconUri)
        _uiState.update { state ->
            state.copy(customServices = state.customServices + entry)
        }
        // 持久化放到 IO 线程，不阻塞主线程
        viewModelScope.launch(Dispatchers.IO) {
            settingsPreferences.set(SettingsKeys.CUSTOM_SERVICES, _uiState.value.customServices)
        }
    }

    /**
     * 移除指定 id 的自定义网站快捷方式并持久化保存。
     */
    fun removeCustomService(id: String) {
        _uiState.update { state ->
            state.copy(customServices = state.customServices.filter { it.id != id })
        }
        viewModelScope.launch(Dispatchers.IO) {
            settingsPreferences.set(SettingsKeys.CUSTOM_SERVICES, _uiState.value.customServices)
        }
    }
}
