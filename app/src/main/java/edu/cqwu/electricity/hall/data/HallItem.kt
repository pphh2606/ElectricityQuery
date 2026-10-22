package edu.cqwu.electricity.hall.data

import android.content.Context
import com.google.gson.Gson
import edu.cqwu.electricity.logging.AppLog

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

/**
 * 大厅多语言映射（`assets/i18n/hall_apps_i18n.json`）。
 *
 * 结构是「以 appId / categoryId 为主键、语言标签为子键」。两个 Map 声明为可空：
 * Gson 反序列化不会走 Kotlin 默认值，字段缺失时会直接赋 null。
 */
data class HallI18nData(
    val schema: Int = 0,
    val apps: Map<String, Map<String, String>>? = null,
    val categories: Map<String, Map<String, String>>? = null,
)

/**
 * 按 [languageKey] 在映射表里查译文；简体中文、键缺失或译文为空一律回退 [fallback]。
 *
 * 纯函数，不依赖 Android 类型，便于 JVM 单测。
 */
internal fun lookupHallName(
    table: Map<String, Map<String, String>>?,
    key: String,
    languageKey: String?,
    fallback: String,
): String {
    if (table == null || languageKey == null) return fallback
    return table[key]?.get(languageKey)?.takeIf { it.isNotBlank() } ?: fallback
}

/**
 * 大厅名称解析器。
 *
 * 与首页的区别：大厅数据有「本地 JSON 兜底」与「服务端实时」两个来源，登录后服务端数据会
 * 整体替换本地数据，「收藏」与「搜索」两个 Tab 也都来自服务端，而服务端返回的 appName /
 * categoryName 同样是中文且没有多语言字段。所以译文不能像首页那样合并进数据 —— 那样只对
 * 本地快照生效，登录用户看到的仍是中文 —— 只能在显示处按 appId / categoryId 查表。
 *
 * 因为不修改数据本身，切语言后随组合重新求值即可，无需像首页那样再做 ViewModel 语言同步。
 */
class HallI18n private constructor(
    private val data: HallI18nData?,
    private val languageKey: String?,
) {
    /** 应用名：未命中译文时回退接口返回的中文 */
    fun appName(appId: String, fallback: String): String =
        lookupHallName(data?.apps, appId, languageKey, fallback)

    /** 分类名：未命中译文时回退接口返回的中文 */
    fun categoryName(categoryId: String, fallback: String): String =
        lookupHallName(data?.categories, categoryId, languageKey, fallback)

    companion object {
        private const val TAG = "HallI18n"
        private const val ASSET = "i18n/hall_apps_i18n.json"

        /** 空解析器：全部回退中文，供尚未加载时兜底 */
        val EMPTY = HallI18n(null, null)

        private val gson = Gson()

        /** 映射文件与语言无关，整份解析一次后长期复用 */
        @Volatile
        private var cachedData: HallI18nData? = null

        /**
         * 读取映射并绑定当前语言。文件缺失或损坏时返回空解析器，界面全部回退中文。
         *
         * 因为整份缓存，重复调用的代价仅为查表；[HallViewModel] 会在 IO 线程预热。
         */
        fun load(context: Context, languageKey: String?): HallI18n =
            HallI18n(loadData(context), languageKey)

        @Synchronized
        private fun loadData(context: Context): HallI18nData? {
            cachedData?.let { return it }

            val parsed = try {
                val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
                gson.fromJson(text, HallI18nData::class.java)
            } catch (e: Exception) {
                AppLog.w(TAG, "读取大厅多语言映射失败，回退中文: ${e.message}")
                null
            }
            cachedData = parsed
            return parsed
        }
    }
}
