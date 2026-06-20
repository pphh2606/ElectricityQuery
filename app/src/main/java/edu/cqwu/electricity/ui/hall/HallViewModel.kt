package edu.cqwu.electricity.ui.hall

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import edu.cqwu.electricity.data.model.HallItem
import edu.cqwu.electricity.data.network.auth.SessionExpiredException
import edu.cqwu.electricity.data.repository.HallFavoriteApi
import edu.cqwu.electricity.data.repository.HallJsonLoader
import edu.cqwu.electricity.data.repository.HallServiceCenterApi
import edu.cqwu.electricity.util.ToastUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HallUiState(
    /** 当前选中的 Tab：0=全部，1=收藏 */
    val selectedTab: Int = 0,
    /** 「全部」Tab 数据（优先使用服务端数据，回退到本地 JSON） */
    val allItems: List<HallItem> = emptyList(),
    /** 「收藏」Tab 数据（来自网络请求） */
    val favoriteItems: List<HallItem> = emptyList(),
    /** 下拉刷新中 */
    val isRefreshing: Boolean = false,
    /** 收藏数据加载中 */
    val isFavoriteLoading: Boolean = false,
    /** 是否需要重新登录（Session 过期） */
    val requiresReLogin: Boolean = false,
    /** 错误提示消息 */
    val errorMessage: String? = null,
    /** 是否已登录（成功加载到服务端数据） */
    val isLoggedIn: Boolean = false,
    /** 正在切换收藏状态的应用 ID（用于 UI loading 指示器） */
    val togglingFavoriteAppId: String? = null,
    /** Snackbar 事件：Pair<消息文本, 类型>，UI 消费后置 null */
    val snackbarEvent: Pair<String, ToastUtils.Type>? = null,
)

class HallViewModel(application: Application) : AndroidViewModel(application) {

    private val jsonLoader = HallJsonLoader(application)
    private val favoriteApi = HallFavoriteApi()
    private val serviceCenterApi = HallServiceCenterApi()

    private val _uiState = MutableStateFlow(HallUiState())
    val uiState: StateFlow<HallUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // 1. 先加载本地 JSON 作为兜底（立即显示）
            val localResult = withContext(Dispatchers.IO) {
                jsonLoader.loadItems()
            }
            localResult.onSuccess { items ->
                _uiState.update { it.copy(allItems = items) }
            }

            // 2. 提前初始化 ehall session（CAS ticket 交换）
            withContext(Dispatchers.IO) {
                try {
                    favoriteApi.initEhallSession()
                } catch (e: SessionExpiredException) {
                    Log.d("HallViewModel", "ehall session 初始化失败（未登录），保持本地 JSON")
                } catch (e: java.net.UnknownHostException) {
                    Log.w("HallViewModel", "ehall 服务器不可达（无校园网？），保持本地 JSON")
                }
            }

            // 3. 尝试加载服务端数据
            loadServiceData()
        }
    }

    // ═══════════════ 服务端数据加载 ═══════════════

    /**
     * 从 [HallServiceCenterApi] 加载全部应用列表（含 favorite/favoriteCount）。
     * 成功时替换 [allItems] 并标记 [isLoggedIn=true]；
     * 失败时保持本地 JSON 数据，[isLoggedIn=false]。
     */
    private suspend fun loadServiceData() {
        val result = withContext(Dispatchers.IO) {
            serviceCenterApi.fetchServiceData()
        }
        result.onSuccess { items ->
            _uiState.update { it.copy(allItems = items, isLoggedIn = true) }
            Log.d("HallViewModel", "服务端数据加载成功，应用数: ${items.size}, isLoggedIn=true")
        }.onFailure { error ->
            Log.w("HallViewModel", "服务端数据加载失败: ${error.message}，使用本地 JSON 兜底")
        }
    }

    // ═══════════════ Tab 切换 ═══════════════

    /**
     * 切换 Tab。
     * 切换到「收藏」Tab 时，如果尚未加载过数据且不在加载中，则自动触发加载。
     */
    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
        if (index == 1) {
            val state = _uiState.value
            if (state.favoriteItems.isEmpty() && !state.isFavoriteLoading) {
                loadFavorites()
            }
        }
    }

    // ═══════════════ 收藏数据加载 ═══════════════

    /**
     * 加载收藏应用列表。
     * 通过 [HallFavoriteApi] 请求网络，自动携带登录 Cookie。
     * 未登录 / Session 过期时设置 [requiresReLogin] 供 UI 显示登录提示。
     *
     * 内部有防重入保护，正在加载时重复调用会被忽略。
     */
    fun loadFavorites() {
        // ═══ 防重入 ═══
        if (_uiState.value.isFavoriteLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isFavoriteLoading = true, errorMessage = null, requiresReLogin = false) }
            val result = withContext(Dispatchers.IO) {
                favoriteApi.fetchFavorites()
            }
            result.onSuccess { items ->
                _uiState.update { it.copy(favoriteItems = items, isFavoriteLoading = false) }
            }.onFailure { error ->
                val requiresReLogin = error is SessionExpiredException
                _uiState.update {
                    it.copy(
                        isFavoriteLoading = false,
                        requiresReLogin = requiresReLogin,
                        errorMessage = if (requiresReLogin) {
                            "登录已过期，请重新登录"
                        } else {
                            error.message ?: "加载失败"
                        },
                    )
                }
            }
        }
    }

    // ═══════════════ 下拉刷新 ═══════════════

    // ═══════════════ 收藏切换 ═══════════════

    /**
     * 切换指定应用的收藏状态。
     *
     * 通过网络 API 执行收藏/取消收藏操作，成功后：
     * 1. 在 [allItems] 中更新该应用的 [favorite] 状态
     * 2. 如果该应用在 [favoriteItems] 中，同步更新
     * 3. 使用 [togglingFavoriteAppId] 控制 UI loading 指示器
     *
     * 防重入：仅当 [togglingFavoriteAppId] 为 null 时才能发起新操作
     */
    fun toggleFavorite(item: HallItem) {
        // ═══ 防重入 ═══
        if (_uiState.value.togglingFavoriteAppId != null) return

        val newFavorite = !item.favorite
        viewModelScope.launch {
            _uiState.update { it.copy(togglingFavoriteAppId = item.appId) }
            val result: Result<Unit> = withContext(Dispatchers.IO) {
                favoriteApi.toggleFavorite(item.appId, newFavorite)
            }
            result.onSuccess {
                _uiState.update { state ->
                    state.copy(
                        allItems = state.allItems.map { app ->
                            if (app.appId == item.appId) app.copy(favorite = newFavorite)
                            else app
                        },
                        favoriteItems = state.favoriteItems.map { app ->
                            if (app.appId == item.appId) app.copy(favorite = newFavorite)
                            else app
                        },
                        togglingFavoriteAppId = null,
                        snackbarEvent = if (newFavorite) "已收藏" to ToastUtils.Type.SUCCESS
                                        else "已取消收藏" to ToastUtils.Type.SUCCESS,
                    )
                }
            }.onFailure { error ->
                val requiresReLogin = error is SessionExpiredException
                _uiState.update {
                    it.copy(
                        togglingFavoriteAppId = null,
                        requiresReLogin = requiresReLogin,
                        errorMessage = if (requiresReLogin) "登录已过期，请重新登录"
                                       else error.message ?: "切换收藏失败",
                        snackbarEvent = if (requiresReLogin) null
                                        else (error.message ?: "切换收藏失败") to ToastUtils.Type.ERROR,
                    )
                }
            }
        }
    }

    /** 消费 Snackbar 事件（UI 调用后将事件置 null） */
    fun clearSnackbarEvent() {
        _uiState.update { it.copy(snackbarEvent = null) }
    }

    // ═══════════════ 下拉刷新 ═══════════════

    /**
     * 下拉刷新：同时刷新本地全部列表、服务端数据，以及（如果已加载过）收藏列表。
     * 使用 try/finally 确保 [isRefreshing] 始终被复位。
     */
    fun refresh() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isRefreshing = true, togglingFavoriteAppId = null) }
                jsonLoader.invalidateCache()

                // 1. 加载本地 JSON 兜底
                val allResult = withContext(Dispatchers.IO) { jsonLoader.loadItems() }
                allResult.onSuccess { items ->
                    _uiState.update { it.copy(allItems = items) }
                }

                // 2. 尝试加载服务端数据
                val serverResult = withContext(Dispatchers.IO) { serviceCenterApi.fetchServiceData() }
                serverResult.onSuccess { items ->
                    _uiState.update { it.copy(allItems = items, isLoggedIn = true) }
                }

                // 3. 如果当前在收藏 Tab 或已加载过收藏数据，同步刷新
                val state = _uiState.value
                if (state.selectedTab == 1 || state.favoriteItems.isNotEmpty()) {
                    val favResult = withContext(Dispatchers.IO) { favoriteApi.fetchFavorites() }
                    favResult.onSuccess { items ->
                        _uiState.update { it.copy(favoriteItems = items) }
                    }.onFailure { error ->
                        val requiresReLogin = error is SessionExpiredException
                        _uiState.update {
                            it.copy(
                                requiresReLogin = requiresReLogin,
                                errorMessage = if (requiresReLogin) "登录已过期，请重新登录" else null,
                            )
                        }
                    }
                }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }
}
