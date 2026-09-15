package edu.cqwu.electricity.jwxt.moreservice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.cqwu.electricity.common.net.SessionExpiredException
import edu.cqwu.electricity.jwxt.data.JwxtApi
import edu.cqwu.electricity.jwxt.data.JwxtConstants
import edu.cqwu.electricity.jwxt.data.JwxtServiceGroup
import edu.cqwu.electricity.logging.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 一条服务：名称、图标地址（已补全）、落地页（已拼好 appId 路由） */
data class JwxtMoreServiceItem(
    val name: String,
    val iconUrl: String,
    val pageUrl: String,
)

/** 分组下的一个分类（学籍 / 选课 / 考务 / 其他 / 评教） */
data class JwxtMoreServiceCategory(
    val name: String,
    val services: List<JwxtMoreServiceItem>,
)

/** 一个分组（全部 / 查询 / 申请）：chip 显示 [name] + [count]，列表显示 [categories] */
data class JwxtMoreServiceGroup(
    val name: String,
    val count: Int,
    val categories: List<JwxtMoreServiceCategory>,
)

data class JwxtMoreServiceUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val groups: List<JwxtMoreServiceGroup> = emptyList(),
    val selectedGroupIndex: Int = 0,
    /** 会话过期（CAS 票据失效或业务码 401），UI 显示「重新登录」 */
    val requiresReLogin: Boolean = false,
    val errorMessage: String? = null,
) {
    /** 当前选中分组的分类列表 */
    val currentCategories: List<JwxtMoreServiceCategory>
        get() = groups.getOrNull(selectedGroupIndex)?.categories.orEmpty()
}

/**
 * 「更多服务」页 ViewModel。
 *
 * 数据来自 `biz/home/listLabelServiceSet`：分组 → 分类 → 服务（比首页多一层分类）。
 * 会话过期与其它失败分别落进 [JwxtMoreServiceUiState.requiresReLogin] 与
 * [JwxtMoreServiceUiState.errorMessage]，不做自动重试——重新登录后由页面的 ON_RESUME 触发一次 [load]。
 */
class JwxtMoreServiceViewModel(
    private val api: JwxtApi = JwxtApi(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(JwxtMoreServiceUiState())
    val uiState: StateFlow<JwxtMoreServiceUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** 切换分组（点击顶部 chip）。下标收敛到合法范围，避免刷新后分组数量变化导致越界 */
    fun selectGroup(index: Int) {
        _uiState.update { state ->
            if (state.groups.isEmpty()) {
                state
            } else {
                state.copy(selectedGroupIndex = index.coerceIn(0, state.groups.lastIndex))
            }
        }
    }

    /** 加载全部分组；[isRefresh] 为 true 时保留下拉刷新指示器而不是整页加载态 */
    fun load(isRefresh: Boolean = false) {
        _uiState.update {
            it.copy(
                isLoading = !isRefresh,
                isRefreshing = isRefresh,
                requiresReLogin = false,
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            try {
                val groups = buildGroups(api.fetchServiceSets().getOrThrow())
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        isRefreshing = false,
                        groups = groups,
                        // 刷新后分组数量可能变化，把选中下标收敛回合法范围
                        selectedGroupIndex = state.selectedGroupIndex
                            .coerceIn(0, (groups.size - 1).coerceAtLeast(0)),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(TAG, "更多服务加载失败", e)
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
     * 接口结构 → UI 结构：分组 → 分类 → 服务。
     *
     * 分类里没有服务时整段丢弃（页面上不显示空标题）；图标与落地页在这里一次性补全，
     * UI 层只管渲染。
     */
    private fun buildGroups(groups: List<JwxtServiceGroup>): List<JwxtMoreServiceGroup> =
        groups.map { group ->
            JwxtMoreServiceGroup(
                name = group.labelName,
                count = group.serviceNum,
                categories = group.serviceList.orEmpty().mapNotNull { category ->
                    val services = category.serviceList.orEmpty().map { service ->
                        JwxtMoreServiceItem(
                            name = service.serviceName,
                            iconUrl = JwxtConstants.iconUrl(service.serviceIcon),
                            pageUrl = JwxtConstants.pageUrl(service.appId, service.url),
                        )
                    }
                    if (services.isEmpty()) null else JwxtMoreServiceCategory(category.categoryName, services)
                },
            )
        }

    private companion object {
        const val TAG = "JwxtMoreServiceViewModel"
    }
}
