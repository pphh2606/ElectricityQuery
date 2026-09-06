package edu.cqwu.electricity.payment.data

import edu.cqwu.electricity.common.net.CookieParser
import edu.cqwu.electricity.common.net.CookieStore
import edu.cqwu.electricity.common.net.HttpClientFactory
import edu.cqwu.electricity.common.net.RedirectChainFollower
import edu.cqwu.electricity.logging.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.text.iterator

/**
 * pay.cqwu.edu.cn 域 JWT 凭证管理器（对齐 [edu.cqwu.electricity.login.domain.CasAuthFlow]）。
 *
 * pay 业务 API 的鉴权凭证是短期 JWT（`datalook_reimbursement_token`，约 24 小时有效），
 * 通过 dlyscas 302 Location 的 `token=` 参数获取（见 [edu.cqwu.electricity.common.net.RedirectChainFollower.followToLocationToken]），
 * 与 ePay/ehall 等"ticket 交换后 Set-Cookie JSESSIONID"的 cookie 凭证机制不同。
 *
 * [ensureToken] 幂等获取有效 token：本地读取 → 解析 JWT `exp` 判断是否过期（**过期判定基于
 * JWT 内容而非 cookie 是否存在**，修复"token 过期后不重新获取"的问题）→ 未过期直接返回，
 * 过期/缺失走重定向链静默刷新。token 存于 CookieManager（与网页端行为一致）。
 */
object PaySessionManager {

    private const val TAG = "PaySessionManager"

    /** pay JWT token 的 cookie 名 */
    const val TOKEN_COOKIE_NAME = "datalook_reimbursement_token"

    private const val PAY_DOMAIN = PayApiBase.PAY_DOMAIN
    private const val CAS_LOGIN_URL = "$PAY_DOMAIN/casLogin/"

    /** token 可能存在的三个路径（兼容 CookieManager 路径匹配差异） */
    private val TOKEN_URLS = listOf(PAY_DOMAIN, "$PAY_DOMAIN/", "$PAY_DOMAIN/casLogin/")

    private val tokenMutex = Mutex()

    /** 复用共享 CookieJar（桥接 CookieManager，自动携带 CASTGC、自动完成 CAS ticket 交换） */
    private val client get() = HttpClientFactory.casFlowClient

    /**
     * 幂等获取有效 token。
     *
     * @return 未过期的 JWT
     * @throws edu.cqwu.electricity.common.net.SessionExpiredException 未登录（CAS 会话失效，重定向链被引导到 CAS 登录页）
     */
    suspend fun ensureToken(): String {
        // 快速路径 + 锁内双检（避免并发重复刷新）
        readValidToken()?.let { return it }
        return tokenMutex.withLock {
            readValidToken()?.let { return@withLock it }
            val token = withContext(Dispatchers.IO) { refreshToken() }
            storeToken(token)
            AppLog.d(TAG, "token 刷新成功，长度=${token.length}")
            token
        }
    }

    /** 清除本地 token（登出/切换账号/强制刷新时调用） */
    fun clearToken() {
        TOKEN_URLS.forEach { url ->
            runCatching { CookieStore.setCookie(url, "$TOKEN_COOKIE_NAME=; Max-Age=0") }
        }
    }

    /** 本地 token 且未过期（快速路径复用） */
    private fun readValidToken(): String? {
        for (url in TOKEN_URLS) {
            val token = CookieParser.getValue(CookieStore.getCookie(url), TOKEN_COOKIE_NAME)
            if (!token.isNullOrBlank() && !isExpired(token)) return token
        }
        return null
    }

    private fun storeToken(token: String) {
        TOKEN_URLS.forEach { url ->
            CookieStore.setCookie(url, "$TOKEN_COOKIE_NAME=$token")
        }
    }

    /** JWT 是否过期：解析 payload 的 exp（秒），无法解析视为过期（触发刷新兜底） */
    private fun isExpired(token: String): Boolean {
        val exp = decodeJwtExp(token) ?: return true
        return exp <= System.currentTimeMillis() / 1000
    }

    /** 解析 JWT payload 的 exp 字段（仅解码读值，不验签；不依赖 JSON 库，JVM 单测可跑） */
    internal fun decodeJwtExp(token: String): Long? {
        val payload = token.split(".").getOrNull(1) ?: return null
        return runCatching {
            val json = String(base64UrlDecode(payload), Charsets.UTF_8)
            Regex(""""exp"\s*:\s*(\d+)""").find(json)
                ?.groupValues?.getOrNull(1)?.toLongOrNull()?.takeIf { it > 0 }
        }.getOrNull()
    }

    /**
     * 重定向链获取新 token：GET /casLogin/ → JS/302 链（CAS ticket 自动交换）→ dlyscas 302 → 提取 token。
     */
    private fun refreshToken(): String {
        return RedirectChainFollower.followToLocationToken(
            client = client,
            startUrl = CAS_LOGIN_URL,
            tag = TAG,
        )
    }

    // ── Base64Url 解码（纯 Kotlin，JVM 单测可跑；minSdk 21 不支持 java.util.Base64）──

    private const val BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    internal fun base64UrlDecode(input: String): ByteArray {
        val normalized = input.replace('-', '+').replace('_', '/')
            .let { it + "=".repeat((4 - it.length % 4) % 4) }
        val out = ArrayList<Byte>(normalized.length * 3 / 4)
        var buffer = 0
        var bits = 0
        for (c in normalized) {
            if (c == '=') break
            val value = BASE64_ALPHABET.indexOf(c)
            if (value < 0) continue
            buffer = (buffer shl 6) or value
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.add(((buffer shr bits) and 0xFF).toByte())
            }
        }
        return out.toByteArray()
    }
}