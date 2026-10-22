package edu.cqwu.electricity.home.data

data class HomeResponse(
    val code: String = "",
    val priority: Int = 0,
    val datas: List<HomeCategory> = emptyList(),
    val message: String = ""
)

data class HomeCategory(
    val categoryId: String = "",
    val categoryName: String = "",
    val order: Int = 0,
    val apps: List<HomeApp> = emptyList(),
    /** 本地化前 JSON 里的中文分类名；只有命中译文时才填写，供搜索同时匹配原文 */
    val originalCategoryName: String? = null
)

data class HomeApp(
    val appId: String = "",
    val name: String = "",
    val appType: Int = 2,
    val iconUrl: String = "",
    val installUrl: String? = null,
    val openUrl: String = "",
    val version: String = "",
    val viewAuth: Int? = null,
    val accessAuth: Int = 0,
    val fromPlatformType: String = "",
    val fromPlatform: String = "",
    val serverDomain: String? = null,
    val appState: String = "",
    val developerId: String? = null,
    val appSource: Int = 1,
    val aliasName: String? = null,
    val nameLang: String? = null,
    /** 本地化前 JSON 里的中文应用名；只有命中译文时才填写，供搜索同时匹配原文 */
    val originalName: String? = null
)

/**
 * 首页多语言映射（`assets/i18n/home_apps_i18n.json`）。
 *
 * 结构是「以 appId / categoryId 为主键、语言标签为子键」，所以升级 home_apps.json 时
 * 键与值都不受影响；文件里不写简体中文，缺译文一律回退 JSON 原文。
 *
 * 两个 Map 声明为可空：Gson 反序列化不会走 Kotlin 默认值，字段缺失时会直接赋 null。
 */
data class HomeAppI18n(
    val schema: Int = 0,
    val apps: Map<String, Map<String, String>>? = null,
    val categories: Map<String, Map<String, String>>? = null
)

/**
 * 把 [i18n] 中 [languageKey] 对应的译文合并进首页数据。
 *
 * 纯函数，不依赖 Android 类型，便于 JVM 单测。规则：
 * - [languageKey] 为 null（简体中文）或 [i18n] 为 null 时原样返回；
 * - 单条缺失译文时保留中文原文，并把 id 记入 [unknownIds] 供调用方打日志；
 * - 服务端下发的 nameLang 有值时优先于映射文件；
 * - 命中译文时把中文原文写进 originalName / originalCategoryName，供搜索匹配。
 */
internal fun mergeHomeI18n(
    categories: List<HomeCategory>,
    i18n: HomeAppI18n?,
    languageKey: String?,
    unknownIds: MutableSet<String> = mutableSetOf()
): List<HomeCategory> {
    if (i18n == null || languageKey == null) return categories

    return categories.map { category ->
        val localizedCategory = i18n.categories?.get(category.categoryId)?.get(languageKey)
        if (localizedCategory == null) unknownIds += "category:${category.categoryId}"

        category.copy(
            categoryName = localizedCategory ?: category.categoryName,
            originalCategoryName = if (localizedCategory != null) category.categoryName else null,
            apps = category.apps.map { app ->
                val localizedName = app.nameLang?.takeIf { it.isNotBlank() }
                    ?: i18n.apps?.get(app.appId)?.get(languageKey)
                if (localizedName == null) unknownIds += app.appId

                app.copy(
                    name = localizedName ?: app.name,
                    originalName = if (localizedName != null) app.name else null
                )
            }
        )
    }
}
