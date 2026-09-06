package edu.cqwu.electricity.common.net

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
     * 网络运行时依赖（组合根在 App 启动时注入一次，须先于任何 client 的首次创建）：
     * - [webVpnLogin]：WebVPN 会话自动登录（检测到需登录时为受保护 URL 建立会话）。
     * - [userAgent]：当前生效的 User-Agent（由用户设置驱动，见 settings.data.UserAgentProvider）。
     */
    @Volatile
    private var webVpnLogin: ((String) -> Unit)? = null

    @Volatile
    private var userAgent: (() -> String)? = null

    /** 由 [edu.cqwu.electricity.app.ElectricityApp] 启动时调用，注入网络栈所需的业务回调。 */
    fun initRuntime(webVpnLogin: (String) -> Unit, userAgent: () -> String) {
        this.webVpnLogin = webVpnLogin
        this.userAgent = userAgent
    }

    /**
     * pay.cqwu.edu.cn 业务 API 专用客户端。
     *
     * 与其余 WebVPN 客户端一致，统一挂 CookieStoreOkHttpJar（桥接系统 CookieManager）：
     * pay 业务 API 的鉴权凭证是 JWT（`X-Token` 头，由 [PaySessionManager] 统一管理），
     * token 本身也以 cookie（datalook_reimbursement_token）存于 CookieManager 供网页端使用，
     * 统一 CookieJar 便于管理且不影响业务调用。
     */
    val payClient: OkHttpClient by lazy {
        create(
            cookieJar = CookieStoreOkHttpJar,
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

    /**
     * 账号隔离客户端：用账号持久化 cookie 构建独立 UserCookieStore + UserAwareCookieJar，
     * 直连 authserver（修改用户名/密码、设备管理、认证日志、登出等 CAS 页面接口共用）。
     *
     * @param cookies 账号持久化的 cookie 集合
     * @param followRedirects 是否自动跟随重定向（登出等需拿到首个 3xx 响应时传 false）
     */
    fun createIsolated(
        cookies: Map<String, Map<String, String>>,
        followRedirects: Boolean = true,
    ): OkHttpClient {
        val store = UserCookieStore().also { it.loadFrom(cookies) }
        return create(
            cookieJar = UserAwareCookieJar(store),
            includeWebVpn = false,
            followRedirects = followRedirects,
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
            .followRedirects(followRedirects)
            .followSslRedirects(followRedirects)

        // UA 注入：读取组合根注入的当前 User-Agent；未注入时保持 okhttp 默认，不覆盖
        builder.addInterceptor { chain ->
            val ua = userAgent?.invoke()
            if (ua != null) {
                chain.proceed(chain.request().newBuilder().header("User-Agent", ua).build())
            } else {
                chain.proceed(chain.request())
            }
        }

        if (includeWebVpn) {
            // 应用层：URL 改写（可换 host）+ 登录后重试一次 + 过期吞没（图片等异步场景）
            builder.addInterceptor(WebVpnUrlTransformer(cookieJar, swallowSessionExpired))
            // 网络层：只 proceed 一次，检测需登录时触发会话登录并抛重试信号
            builder.addNetworkInterceptor(
                WebVpnInterceptor(
                    cookieJar = cookieJar,
                    sessionAuthenticator = { protectedUrl ->
                        val login = webVpnLogin
                        if (login != null) {
                            login(protectedUrl)
                        } else {
                            throw SessionExpiredException(
                                "WebVPN 自动登录不可用（未在启动时注入会话登录器）",
                                SessionExpiryReason.LOGIN_REJECTED,
                            )
                        }
                    },
                ),
            )
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
