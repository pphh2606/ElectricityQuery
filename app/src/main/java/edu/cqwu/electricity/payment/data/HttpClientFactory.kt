package edu.cqwu.electricity.payment.data

import edu.cqwu.electricity.login.data.CookieStoreOkHttpJar
import edu.cqwu.electricity.login.data.UserAgentInterceptor
import edu.cqwu.electricity.webvpn.WebVpnInterceptor
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * Central OkHttpClient factory for the app.
 */
object HttpClientFactory {

    private const val DEFAULT_TIMEOUT_SECONDS = 15L

    /**
     * pay.cqwu.edu.cn 业务 API 专用客户端。
     *
     * 注意：刻意不挂 CookieJar —— pay 业务 API 的鉴权凭证是 JWT（`X-Token` 头，
     * 由 [edu.cqwu.electricity.login.data.PaySessionManager] 统一管理），不依赖 cookie；
     * token 交换链路（/casLogin/ → dlyscas）走 [shared]（共享 CookieJar 桥接 CookieManager）。
     */
    val payClient: OkHttpClient by lazy {
        create(
            connectTimeout = 10,
            readTimeout = 10,
            writeTimeout = 10,
        )
    }

    val shared: OkHttpClient by lazy {
        create(cookieJar = CookieStoreOkHttpJar)
    }

    /**
     * CAS 重定向链客户端（服务登录 ticket 交换、pay token 获取等）：
     * 共享 CookieJar（桥接 CookieManager）、不自动跟随重定向（由调用方手动跟随）。
     */
    val casFlowClient: OkHttpClient by lazy {
        create(
            cookieJar = CookieStoreOkHttpJar,
            followRedirects = false,
        )
    }

    val webVpnImageClient: OkHttpClient by lazy {
        create(
            cookieJar = CookieStoreOkHttpJar,
            swallowSessionExpired = true,
        )
    }

    fun create(
        cookieJar: CookieJar? = null,
        followRedirects: Boolean = true,
        connectTimeout: Long = DEFAULT_TIMEOUT_SECONDS,
        readTimeout: Long = DEFAULT_TIMEOUT_SECONDS,
        writeTimeout: Long = DEFAULT_TIMEOUT_SECONDS,
        includeWebVpn: Boolean = true,
        swallowSessionExpired: Boolean = false,
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(connectTimeout, TimeUnit.SECONDS)
            .readTimeout(readTimeout, TimeUnit.SECONDS)
            .writeTimeout(writeTimeout, TimeUnit.SECONDS)
            .dns(PreferIPv4Dns)
            .addInterceptor(UserAgentInterceptor)
            .followRedirects(followRedirects)
            .followSslRedirects(followRedirects)

        if (includeWebVpn) {
            val webVpnInterceptor = WebVpnInterceptor(
                cookieJar = cookieJar,
                swallowSessionExpired = swallowSessionExpired,
            )
            builder.addInterceptor(webVpnInterceptor)
            builder.addNetworkInterceptor(webVpnInterceptor)
        }

        if (cookieJar != null) {
            builder.cookieJar(cookieJar)
        }

        return builder.build()
    }

    /**
     * Prefers IPv4 addresses because the campus authserver IPv6 endpoint is unreliable.
     */
    private object PreferIPv4Dns : Dns {
        private val fallbackDns = Dns.SYSTEM

        override fun lookup(hostname: String): List<InetAddress> {
            val allAddresses = fallbackDns.lookup(hostname)
            val ipv4 = mutableListOf<InetAddress>()
            val ipv6 = mutableListOf<InetAddress>()
            for (addr in allAddresses) {
                if (addr is Inet4Address) ipv4.add(addr)
                else if (addr is Inet6Address) ipv6.add(addr)
            }
            return ipv4 + ipv6
        }
    }
}
