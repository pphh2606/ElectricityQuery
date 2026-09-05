package edu.cqwu.electricity.campusnetwork.speedtest.engine

import edu.cqwu.electricity.campusnetwork.speedtest.data.SpeedTestApi
import edu.cqwu.electricity.logging.AppLog
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

/** 测速阶段 */
enum class SpeedTestPhase { DOWNLOAD, PING, UPLOAD }

/**
 * 引擎 200ms 采样快照（供 UI 实时刷新）。
 * 各值在对应阶段进行中更新，未到阶段/未更新时为 0。
 */
data class SpeedTestTick(
    val phase: SpeedTestPhase = SpeedTestPhase.DOWNLOAD,
    val downloadMbps: Double = 0.0,
    val uploadMbps: Double = 0.0,
    val pingMs: Double = 0.0,
    val jitterMs: Double = 0.0,
    /** 当前阶段进度 0..1（(t+bonus)/max） */
    val progress: Float = 0f,
)

/** 完整测速结束后的结果（原始值，由调用方格式化上报） */
data class SpeedTestResult(
    val downloadMbps: Double,
    val uploadMbps: Double,
    val pingMs: Double,
    val jitterMs: Double,
)

/**
 * 测速引擎 —— Kotlin 移植官网 `/speedtest_engine.js`（LibreSpeed 单文件版）：
 * 顺序 D → 1s → P → 1s → U；下载 6 流 / 上传 3 流 / ping 串行；
 * grace 预热、200ms 采样、auto-bonus 结束判定、1.06 系数、4MiB 上传体全部照抄。
 *
 * 错误语义对齐 `xhr_ignoreErrors:1`：单流请求失败（含 429 code:40003）
 * 只重开该流的下一个请求，不中断整轮测速。
 *
 * 网络请求不带鉴权；会话（create/claim/complete）由调用方 [edu.cqwu.electricity.campusnetwork.ui.SpeedTestViewModel] 管理。
 */
class SpeedTestEngine(
    private val api: SpeedTestApi,
) {
    private val _tick = MutableStateFlow(SpeedTestTick())
    val tick: StateFlow<SpeedTestTick> = _tick.asStateFlow()

    private var job: Job? = null

    /** 是否正在测速 */
    val isRunning: Boolean get() = job?.isActive == true

    /** 完整跑完后的结果 */
    var result: SpeedTestResult? = null
        private set

    private val activeCalls = ConcurrentLinkedQueue<Call>()

    /**
     * 在 [scope] 中启动一轮测速并返回其 Job（调用方可 join 等待完成）。
     * 已在运行则直接返回当前 Job；abort() 会取消该 Job。
     */
    fun launch(scope: CoroutineScope): Job {
        if (job?.isActive == true) return job!!
        job?.cancel()
        result = null
        _tick.value = SpeedTestTick()
        val launched = scope.launch(Dispatchers.IO) {
            run()
        }
        job = launched
        return launched
    }

    /** 中断：取消运行协程并关闭所有进行中的请求 */
    fun abort() {
        job?.cancel()
        job = null
        cancelActiveCalls()
    }

    private suspend fun run() {
        val order = SpeedTestSettings.TEST_ORDER
        for (c in order) {
            when (c) {
                'D' -> downloadPhase()
                'P' -> pingPhase()
                'U' -> uploadPhase()
                '_' -> delay(1_000)
            }
        }
        // 正常跑完才设置结果（取消/异常不会走到这里）
        val last = _tick.value
        result = SpeedTestResult(
            downloadMbps = last.downloadMbps,
            uploadMbps = last.uploadMbps,
            pingMs = last.pingMs,
            jitterMs = last.jitterMs,
        )
    }

    // ─────────────────────────────────────────────
    //  共享工具
    // ─────────────────────────────────────────────

    private fun register(call: Call) {
        activeCalls.add(call)
    }

    private fun unregister(call: Call) {
        activeCalls.remove(call)
    }

    private fun cancelActiveCalls() {
        while (true) {
            val call = activeCalls.poll() ?: break
            try {
                call.cancel()
            } catch (e: Exception) {
                AppLog.w(TAG, "取消请求失败", e)
            }
        }
    }

    // ─────────────────────────────────────────────
    //  下载阶段
    // ─────────────────────────────────────────────

    private suspend fun downloadPhase() {
        _tick.update { it.copy(phase = SpeedTestPhase.DOWNLOAD, progress = 0f) }
        val startedAt = System.currentTimeMillis()
        val totalLoaded = AtomicLong(0)
        val streamScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            for (i in 0 until SpeedTestSettings.DL_STREAMS) {
                streamScope.launch {
                    if (i > 0) delay(SpeedTestSettings.STREAM_DELAY_MS * i)
                    downloadStreamLoop(totalLoaded)
                }
            }
            measurementLoop(
                startedAt = startedAt,
                total = totalLoaded,
                graceSec = SpeedTestSettings.DL_GRACE_SEC,
                maxSec = SpeedTestSettings.TIME_DL_MAX_SEC,
                onValue = { mbps ->
                    _tick.update { it.copy(downloadMbps = mbps) }
                },
            )
        } finally {
            streamScope.cancel()
            cancelActiveCalls()
        }
    }

    /** 单条下载流：循环 GET garbage 边读边计数；失败（含 429）后重开下一请求 */
    private suspend fun downloadStreamLoop(totalLoaded: AtomicLong) {
        val buffer = ByteArray(64 * 1024)
        while (coroutineContext.isActive) {
            var failed = false
            val call = api.newDownloadCall(randomR())
            register(call)
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        AppLog.w(TAG, "下载探测非 2xx: HTTP ${response.code}")
                        failed = true
                        return@use
                    }
                    val source = response.body.source()
                    while (coroutineContext.isActive) {
                        val n = source.read(buffer, 0, buffer.size)
                        if (n < 0) break
                        totalLoaded.addAndGet(n.toLong())
                    }
                }
            } catch (e: IOException) {
                failed = true
                if (!coroutineContext.isActive) return
                AppLog.w(TAG, "下载流异常（将重开）: ${e.message}")
            } finally {
                unregister(call)
            }
            if (!coroutineContext.isActive) return
            if (failed) delay(150) // 失败轻微节流，避免 429 时忙转
        }
    }

    // ─────────────────────────────────────────────
    //  上传阶段
    // ─────────────────────────────────────────────

    private suspend fun uploadPhase() {
        _tick.update { it.copy(phase = SpeedTestPhase.UPLOAD, progress = 0f) }
        val startedAt = System.currentTimeMillis()
        val totalLoaded = AtomicLong(0)
        val streamScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            // 预生成 4MiB 随机载荷并复用（与引擎 Blob 复用一致）
            val payload = ByteArray(SpeedTestSettings.UL_BLOB_BYTES).also { bytes ->
                java.util.Random().nextBytes(bytes)
            }
            for (i in 0 until SpeedTestSettings.UL_STREAMS) {
                streamScope.launch {
                    if (i > 0) delay(SpeedTestSettings.STREAM_DELAY_MS * i)
                    uploadStreamLoop(totalLoaded, payload)
                }
            }
            measurementLoop(
                startedAt = startedAt,
                total = totalLoaded,
                graceSec = SpeedTestSettings.UL_GRACE_SEC,
                maxSec = SpeedTestSettings.TIME_UL_MAX_SEC,
                onValue = { mbps ->
                    _tick.update { it.copy(uploadMbps = mbps) }
                },
            )
        } finally {
            streamScope.cancel()
            cancelActiveCalls()
        }
    }

    /** 单条上传流：循环 POST 4MiB 随机体；失败（含 429）后重开下一请求 */
    private suspend fun uploadStreamLoop(totalLoaded: AtomicLong, payload: ByteArray) {
        while (coroutineContext.isActive) {
            var failed = false
            val call = api.newUploadCall(randomR(), payload) { written ->
                totalLoaded.addAndGet(written.toLong())
            }
            register(call)
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        AppLog.w(TAG, "上传探测非 2xx: HTTP ${response.code}")
                        failed = true
                    }
                }
            } catch (e: IOException) {
                failed = true
                if (!coroutineContext.isActive) return
                AppLog.w(TAG, "上传流异常（将重开）: ${e.message}")
            } finally {
                unregister(call)
            }
            if (!coroutineContext.isActive) return
            if (failed) delay(150)
        }
    }

    // ─────────────────────────────────────────────
    //  Ping 阶段（串行；照抄引擎 warmup + 最小 ping + EWMA jitter）
    // ─────────────────────────────────────────────

    private suspend fun pingPhase() {
        _tick.update { it.copy(phase = SpeedTestPhase.PING, progress = 0f) }
        val accumulator = PingJitterAccumulator()
        var attempt = 0
        var consecutiveFailures = 0
        // 第 0 次为预热请求（丢弃），其后为有效样本；失败不计数并重试
        while (attempt < SpeedTestSettings.COUNT_PING && coroutineContext.isActive) {
            val warmup = attempt == 0
            val t0 = System.currentTimeMillis()
            val ok = pingOnce()
            val rtt = (System.currentTimeMillis() - t0).toDouble()
            if (ok) {
                consecutiveFailures = 0
                accumulator.sample(rtt, warmup)
                attempt++
            } else {
                consecutiveFailures++
                if (consecutiveFailures >= PING_MAX_FAILURES) {
                    // 兜底：持续失败不再无限重试（引擎会一直重试，这里防 UI 卡死）
                    AppLog.w(TAG, "ping 连续失败 $consecutiveFailures 次，跳过剩余探测")
                    attempt = SpeedTestSettings.COUNT_PING
                }
                if (!coroutineContext.isActive) return
                delay(150)
            }
            _tick.update {
                it.copy(
                    pingMs = accumulator.pingMs,
                    jitterMs = accumulator.jitterMs,
                    progress = (attempt.toFloat() / SpeedTestSettings.COUNT_PING).coerceIn(0f, 1f),
                )
            }
        }
    }

    private suspend fun pingOnce(): Boolean {
        val call = api.newPingCall(randomR())
        register(call)
        return try {
            call.execute().use { it.isSuccessful }
        } catch (e: IOException) {
            false
        } finally {
            unregister(call)
        }
    }

    // ─────────────────────────────────────────────
    //  grace + 200ms 采样 + auto-bonus（照抄引擎 dlTest/ulTest 的 interval）
    // ─────────────────────────────────────────────

    private suspend fun measurementLoop(
        startedAt: Long,
        total: AtomicLong,
        graceSec: Double,
        maxSec: Int,
        onValue: (mbps: Double) -> Unit,
    ) {
        var graceDone = false
        var measureStart = startedAt
        var bonusMs = 0.0
        val graceMs = (graceSec * 1000).toLong()

        while (coroutineContext.isActive) {
            delay(SpeedTestSettings.SAMPLE_INTERVAL_MS)
            val now = System.currentTimeMillis()
            val t = now - startedAt
            if (!graceDone && t > graceMs) {
                if (total.get() > 0) {
                    // 引擎在越过 grace 后重置基准重新计时
                    measureStart = now
                    total.set(0)
                    graceDone = true
                } else if (t > graceMs + SpeedTestSettings.GRACE_STALL_FALLBACK_MS) {
                    // 兜底：长期 0 字节避免空转（正常场景不会走到）
                    AppLog.w(TAG, "grace 后长时间无数据，提前进入测量")
                    measureStart = now
                    graceDone = true
                }
            }
            if (graceDone) {
                val sec = (now - measureStart) / 1000.0
                val speed = if (sec > 0) total.get() / sec else 0.0
                // 引擎：bonus(ms) = 5.0 * speed(bytes/s) / 100000，单次封顶 400ms
                bonusMs += (SpeedTestSettings.AUTO_BONUS_RATE * speed)
                    .coerceAtMost(SpeedTestSettings.AUTO_BONUS_CAP_MS.toDouble())
                onValue(SpeedTestStats.mbps(speed))
                val progress = ((t + bonusMs) / (maxSec * 1000.0)).toFloat().coerceIn(0f, 1f)
                _tick.update { it.copy(progress = progress) }
                if ((t + bonusMs) / 1000.0 > maxSec) break
            }
        }
    }

    private fun randomR(): String = java.lang.Double.toString(java.lang.Math.random())

    private companion object {
        const val TAG = "SpeedTestEngine"

        /** ping 阶段连续失败上限（超出则跳过剩余探测，防止无网环境卡死） */
        const val PING_MAX_FAILURES = 25
    }
}
