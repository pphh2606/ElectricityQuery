package edu.cqwu.electricity.data.network

import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * 统一的 OkHttpClient 工厂。
 *
 * 消除项目中散落的 OkHttpClient 构建代码（原有 6 处），
 * 统一使用 PreferIPv4Dns + UserAgentInterceptor 基础配置。
 *
 * 提供以下创建方式：
 * - [shared] — 全局共享客户端（桥接系统 CookieManager）
 * - [createForUser] — 按用户隔离的客户端（UserAwareCookieJar）
 * - [createNoRedirect] — 禁用重定向的客户端（扫码登录提交认证用）
 * - [createWithTimeout] — 自定义超时的客户端
 */
object HttpClientFactory {

    /** 默认超时时间 */
    private const val DEFAULT_TIMEOUT_SECONDS = 15L

    /**
     * 全局共享的 OkHttpClient（桥接系统 CookieManager）。
     *
     * 使用 CookieStoreOkHttpJar 将 OkHttp Cookie 读写桥接到
     * android.webkit.CookieManager，所有业务 API 共享同一 Cookie Session。
     */
    val shared: OkHttpClient by lazy {
        buildClient(
            cookieJar = CookieStoreOkHttpJar,
            followRedirects = true,
        )
    }

    /**
     * 为指定用户创建独立的 OkHttpClient。
     *
     * 使用 UserAwareCookieJar 绑定到该用户的独立 UserCookieStore，
     * 与系统 CookieManager 完全隔离。
     */
    fun createForUser(username: String): OkHttpClient {
        val userStore = AccountManager.getCookiesForUser(username)
        return buildClient(
            cookieJar = UserAwareCookieJar(userStore),
            followRedirects = true,
        )
    }

    /**
     * 禁用重定向的客户端。
     *
     * 用于扫码登录提交认证场景：CAS 返回 302 + Set-Cookie: CASTGC，
     * 只需获取第一个 302 响应的 Cookie，无需跟随重定向链。
     */
    fun createNoRedirect(cookieJar: CookieJar? = null): OkHttpClient {
        return buildClient(
            cookieJar = cookieJar,
            followRedirects = false,
        )
    }

    /**
     * 自定义超时时间的客户端。
     *
     * 用于特殊场景（如 HTML 版账单查询需要 30 秒超时）。
     */
    fun createWithTimeout(
        connectTimeout: Long = DEFAULT_TIMEOUT_SECONDS,
        readTimeout: Long = DEFAULT_TIMEOUT_SECONDS,
        writeTimeout: Long = DEFAULT_TIMEOUT_SECONDS,
    ): OkHttpClient {
        return buildClient(
            connectTimeout = connectTimeout,
            readTimeout = readTimeout,
            writeTimeout = writeTimeout,
            followRedirects = true,
        )
    }

    /**
     * 自定义 DNS 解析器：优先使用 IPv4 地址。
     *
     * 日志数据显示 authserver.cqwu.edu.cn 的 IPv6 地址端口 80 不可用，
     * 导致 OkHttp 先尝试 IPv6 连接等待 15 秒超时后，才回退到 IPv4（120ms 成功）。
     * 此解析器将 IPv4 地址排在 IPv6 前面，彻底避免 15 秒的 IPv6 连接超时。
     */
    private object PreferIPv4Dns : Dns {
        private val fallbackDns = Dns.SYSTEM
        override fun lookup(hostname: String): List<InetAddress> {
            val allAddresses = fallbackDns.lookup(hostname)
            val ipv4 = mutableListOf<InetAddress>()
            val ipv6 = mutableListOf<InetAddress>()
            for (addr in allAddresses) {
                if (addr is java.net.Inet4Address) ipv4.add(addr)
                else if (addr is java.net.Inet6Address) ipv6.add(addr)
            }
            return ipv4 + ipv6
        }
    }

    /**
     * 基础构建方法，所有公开方法最终都调用此方法。
     */
    private fun buildClient(
        cookieJar: CookieJar? = null,
        followRedirects: Boolean = true,
        connectTimeout: Long = DEFAULT_TIMEOUT_SECONDS,
        readTimeout: Long = DEFAULT_TIMEOUT_SECONDS,
        writeTimeout: Long = DEFAULT_TIMEOUT_SECONDS,
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(connectTimeout, TimeUnit.SECONDS)
            .readTimeout(readTimeout, TimeUnit.SECONDS)
            .writeTimeout(writeTimeout, TimeUnit.SECONDS)
            .dns(PreferIPv4Dns)
            .addInterceptor(UserAgentInterceptor)
            .followRedirects(followRedirects)
            .followSslRedirects(followRedirects)

        if (cookieJar != null) {
            builder.cookieJar(cookieJar)
        }

        return builder.build()
    }
}
