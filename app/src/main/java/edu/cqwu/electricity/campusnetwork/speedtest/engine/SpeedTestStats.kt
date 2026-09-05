package edu.cqwu.electricity.campusnetwork.speedtest.engine

import java.util.Locale
import kotlin.math.abs

/**
 * 速率/格式与延迟-抖动计算工具。
 *
 * 口径与官网引擎逐行一致：Mbps = bytes/s × 8 × 1.06 ÷ 1e6；
 * ping 取最小；jitter 为相邻 RTT 差的指数加权（EWMA），参数照抄引擎。
 */
object SpeedTestStats {

    /** 字节速率 → Mbps（引擎 dlStatus/ulStatus 公式） */
    fun mbps(bytesPerSecond: Double): Double {
        val divisor = if (SpeedTestSettings.USE_MEBIBITS) 1048576.0 else SpeedTestSettings.MEGA
        return bytesPerSecond * 8.0 * SpeedTestSettings.OVERHEAD_COMPENSATION_FACTOR / divisor
    }

    /** complete 上报/排行使用的字符串格式（保留 2 位小数） */
    fun formatFixed2(value: Double): String = String.format(Locale.US, "%.2f", value)

    /** 界面大数字：Mbps 类保留 1 位小数；ms 类取整 */
    fun formatMbps(value: Double): String = String.format(Locale.US, "%.1f", value)

    fun formatMs(value: Double): String = kotlin.math.round(value).toString()
}

/**
 * Ping/Jitter 累加器 —— 严格复刻引擎 pingTest：
 * - 第 1 次请求为预热（丢弃）；
 * - 之后 ping 取「最小值」；
 * - jitter = |本次RTT - 上次RTT| 的 EWMA：上升 j*0.3+i*0.7，下降 j*0.8+i*0.2。
 */
class PingJitterAccumulator {

    private var prevInstspd = 0.0
    var pingMs: Double = 0.0
        private set
    var jitterMs: Double = 0.0
        private set

    /**
     * 上报一次测量（warmup=true 表示预热请求，丢弃）。
     * @param instspd 本次 RTT（毫秒，应 ≥1）
     */
    fun sample(instspd: Double, warmup: Boolean) {
        if (warmup) return
        var spd = instspd
        if (spd < 1) spd = prevInstspd
        if (spd < 1) spd = 1.0
        val instjitter = abs(spd - prevInstspd)

        if (prevInstspd == 0.0) {
            // 第一次有效样本：仅设 ping 与基线
            pingMs = spd
        } else {
            if (spd < pingMs) pingMs = spd
            if (jitterMs == 0.0) jitterMs = instjitter
            else jitterMs = if (instjitter > jitterMs) {
                jitterMs * 0.3 + instjitter * 0.7
            } else {
                jitterMs * 0.8 + instjitter * 0.2
            }
        }
        prevInstspd = spd
    }
}
