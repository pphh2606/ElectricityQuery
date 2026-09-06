package edu.cqwu.electricity.login.domain

import edu.cqwu.electricity.logging.AppLog
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch

/**
 * 会话域认证器 —— 每个凭据域（clientvpn 代理、ehall/campusphere 等服务域）一个实例。
 *
 * 唯一职责：**in-flight 单飞去重**。同一会话并发调用 [ensureActive] 时只有第一个真正执行
 * 认证，其余在闸门上等待并复用结果（修复 Campus 双线程重复登录竞态）；失败异常原样抛给
 * 等待者，不静默吞掉。认证动作经 [authenticator] 注入，与具体登录协议解耦。
 *
 * 不做 cookie 预判/活性缓存：是否真需要认证由调用方决定（拦截器已判 302；服务域每次
 * 真实走链校验，避免"服务端已失效但 cookie 残留"导致跳过重认证）。
 */
class AuthSession internal constructor(
    private val domainName: String,
    private val authenticator: () -> Unit,
) {
    private class AuthGate {
        val latch = CountDownLatch(1)

        @Volatile
        var failure: Throwable? = null
    }

    private val inflight = ConcurrentHashMap<String, AuthGate>()

    /**
     * 确保本会话域完成一次认证。并发调用共享同一次认证（等待者复用结果）；
     * 认证失败异常原样上抛。
     */
    fun ensureActive() {
        val gate = AuthGate()
        val existing = inflight.putIfAbsent(GATE_KEY, gate)
        if (existing != null) {
            try {
                existing.latch.await()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw RuntimeException("等待会话认证时被中断", e)
            }
            existing.failure?.let { throw it }
            return
        }

        try {
            AppLog.d(TAG, "会话域 [$domainName] 认证开始")
            authenticator()
        } catch (t: Throwable) {
            gate.failure = t
            throw t
        } finally {
            gate.latch.countDown()
            inflight.remove(GATE_KEY, gate)
        }
    }

    private companion object {
        const val TAG = "AuthSession"
        const val GATE_KEY = "auth"
    }
}

/** 会话域注册表：按 [domain] 去重，保证每域只有一个 in-flight 闸门。 */
object SessionRegistry {

    private val sessions = ConcurrentHashMap<String, AuthSession>()

    /** 取或建指定域的会话；已存在则直接返回（同一闸门），避免并发重复建锁。 */
    fun getOrCreate(domain: String, authenticator: () -> Unit): AuthSession {
        return sessions.computeIfAbsent(domain) {
            AuthSession(domain, authenticator)
        }
    }
}
