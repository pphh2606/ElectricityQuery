package edu.cqwu.electricity.hall.data

/**
 * 办事大厅 JSON 响应结构（本地 assets/hall_apps.json）
 */
data class HallSearchResponse(
    val searchResult: List<HallItem> = emptyList()
)

/**
 * 收藏 API (userFavoriteApps.json) 响应结构
 */
data class UserFavoritesResponse(
    val searchResult: List<HallItem> = emptyList(),
    val userFavFolders: List<Any> = emptyList(),
    val hasLogin: Boolean = false,
    val contextPath: String = "",
)

/**
 * 服务大厅数据中心 API (serviceCenterData.json) 响应结构
 * 与服务端返回的应用列表完全一致，包含 favorite / favoriteCount 等信息。
 *
 * 服务器即使未登录也会返回数据，但 hasLogin=false。
 * 调用方需检查 [hasLogin] 字段决定是否使用服务端数据。
 */
data class ServiceCenterDataResponse(
    val searchResult: List<HallItem> = emptyList(),
    /** 用户登录状态：true=已登录，false=未登录/Session 过期 */
    val hasLogin: Boolean = false,
)

/**
 * 办事大厅中的单个应用项
 */
data class HallItem(
    val appId: String = "",
    val appName: String = "",
    val middleIcon: String = "",
    val description: String? = null,
    val favorite: Boolean = false,
    val favoriteCount: Int = 0,
)

/**
 * 收藏/取消收藏 API 的响应结构
 */
data class FavoriteAppResponse(
    val result: String = "",
    val hasLogin: Boolean = false,
    val contextPath: String = "",
) {
    val isSuccess: Boolean get() = result == "success"
}
