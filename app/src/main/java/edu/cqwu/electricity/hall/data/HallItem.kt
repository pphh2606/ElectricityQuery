package edu.cqwu.electricity.hall.data

/**
 * 办事大厅用户分类应用 API（getUserCategoryAppList.json）响应结构。
 * 同时用于本地 assets/hall_apps.json 快照解析。
 */
data class UserCategoryAppListResponse(
    val result: String = "",
    val pcAppCategory: List<HallCategory> = emptyList(),
    /** 用户登录状态：true=已登录，false=未登录/Session 过期 */
    val hasLogin: Boolean = false,
)

/** 办事大厅应用分类 */
data class HallCategory(
    val categoryId: String = "",
    val categoryName: String = "",
    val appList: List<HallItem> = emptyList(),
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
 * 大厅搜索接口（serviceCenterData.json）响应结构。
 * 携带应用列表与两行索引标签（服务角色 / 服务类别）。
 */
data class ServiceCenterSearchResponse(
    val searchResult: List<HallItem> = emptyList(),
    val serviceLabels: List<HallServiceLabelGroup> = emptyList(),
    /** 用户登录状态：true=已登录，false=未登录/Session 过期 */
    val hasLogin: Boolean = false,
)

/** 一行索引标签组，例如「服务角色」「服务类别」 */
data class HallServiceLabelGroup(
    val serviceId: Int = 0,
    val serviceName: String = "",
    val labels: List<HallServiceLabel> = emptyList(),
)

/** 单个索引标签 */
data class HallServiceLabel(
    val labelId: String = "",
    val lableName: String = "",
    val subLabelList: List<HallServiceLabel>? = null,
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
 * 提取真实业务分类：排除 `categoryId == "all"` 的伪分类，并过滤空分类，保持服务端返回顺序。
 */
fun UserCategoryAppListResponse.extractCategories(): List<HallCategory> =
    pcAppCategory.filter { it.categoryId != "all" && it.appList.isNotEmpty() }

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
