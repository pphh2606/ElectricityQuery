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
    val apps: List<HomeApp> = emptyList()
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
    val nameLang: String? = null
)
