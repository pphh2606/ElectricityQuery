package edu.cqwu.electricity.payment.data

import edu.cqwu.electricity.login.data.AccountManager
import edu.cqwu.electricity.login.data.CookieStoreOkHttpJar
import edu.cqwu.electricity.login.data.UserAgentInterceptor
import edu.cqwu.electricity.login.data.UserAwareCookieJar
import edu.cqwu.electricity.network.WebVpnInterceptor
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

    fun createForUser(username: String): OkHttpClient {
        val userStore = AccountManager.getCookiesForUser(username)
        return create(cookieJar = UserAwareCookieJar(userStore))
    }

    fun create(
        cookieJar: CookieJar? = null,
        followRedirects: Boolean = true,
        connectTimeout: Long = DEFAULT_TIMEOUT_SECONDS,
        readTimeout: Long = DEFAULT_TIMEOUT_SECONDS,
        writeTimeout: Long = DEFAULT_TIMEOUT_SECONDS,
        includeWebVpn: Boolean = true,
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
            val webVpnInterceptor = WebVpnInterceptor(cookieJar)
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
