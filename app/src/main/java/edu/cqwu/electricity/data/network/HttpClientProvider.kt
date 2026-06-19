package edu.cqwu.electricity.data.network

import okhttp3.OkHttpClient

/**
 * 共享的 OkHttpClient 单例（向后兼容包装器）。
 *
 * 实际构建逻辑已迁移到 [HttpClientFactory]，此类保留以避免大规模修改调用方。
 * 所有属性和方法直接委托给 [HttpClientFactory]。
 *
 * 需在 Application.onCreate() 中调用 [init] 初始化。
 */
object SharedHttpClient {
    /** 委托给 HttpClientFactory.shared */
    val client: OkHttpClient get() = HttpClientFactory.shared

    fun init() {
        CookieStore.init()
    }

    /**
     * 为指定用户创建独立的 OkHttpClient（委托给 [HttpClientFactory.createForUser]）。
     */
    fun createClientForUser(username: String): OkHttpClient {
        return HttpClientFactory.createForUser(username)
    }
}

/**
 * 自定义 DNS 解析器（向后兼容重定向）。
 *
 * 实际实现已迁移到 [PreferIPv4Dns]（同包），此文件保留类型别名以兼容外部引用。
 * PreferIPv4Dns 已在同一包的 HttpClientFactory.kt 中定义。
 */
// PreferIPv4Dns 已在 HttpClientFactory.kt 中定义
