package edu.cqwu.electricity.campusnetwork.speedtest.engine

/**
 * 测速引擎常量 —— 逐项镜像官网 `fortest/speedtest_engine.js`（LibreSpeed 单文件版）worker 内 settings 默认值。
 *
 * 页面端可能额外覆盖（如缩短时长），此处先保持与引擎源文件一致；
 * 需要调 UX 时长时只改这里即可。
 */
object SpeedTestSettings {

    /** 测试顺序：D=下载、P=延迟、U=上传，"_" 表示阶段间停顿 1s */
    const val TEST_ORDER = "D_P_U"

    /** 下载阶段最大时长（秒） */
    const val TIME_DL_MAX_SEC = 15

    /** 上传阶段最大时长（秒） */
    const val TIME_UL_MAX_SEC = 15

    /** 下载预热（grace）秒数，此段时间数据不计入速率 */
    const val DL_GRACE_SEC = 1.5

    /** 上传预热（grace）秒数 */
    const val UL_GRACE_SEC = 3.0

    /** 延迟探测次数 */
    const val COUNT_PING = 10

    /** 下载并发流数 */
    const val DL_STREAMS = 6

    /** 上传并发流数 */
    const val UL_STREAMS = 3

    /** 各流启动间隔（毫秒），引擎 xhr_multistreamDelay */
    const val STREAM_DELAY_MS = 300L

    /** 状态采样/上报间隔（毫秒），引擎 interval */
    const val SAMPLE_INTERVAL_MS = 200L

    /** 下载探测 ckSize 参数（服务端垃圾数据分块校验大小，官网 garbagePhp_chunkSize） */
    const val GARBAGE_CK_SIZE = 100

    /** 上传探测单请求体字节数（移动端 Chrome 时官网为 4MiB，本 App 移动端固定此值） */
    const val UL_BLOB_BYTES = 4 * 1024 * 1024

    /** 吞吐补偿系数（引擎 overheadCompensationFactor），校准 TCP/头部开销 */
    const val OVERHEAD_COMPENSATION_FACTOR = 1.06

    /** 是否以 Mebibit(2^20) 计，官网 useMebibits=false → 十进制 1e6 */
    const val USE_MEBIBITS = false

    /** 自动时长 bonus 系数：bonus(ms) = 5 * speed(bytes/s) / 100000，单次上限 ms */
    const val AUTO_BONUS_RATE = 5.0 / 100000.0
    const val AUTO_BONUS_CAP_MS = 400L

    /** 速率基准：1e6 比特 = 1 Megabit */
    const val MEGA = 1_000_000.0

    /** grace 后仍无任何数据的兜底等待（毫秒），避免极端空转 */
    const val GRACE_STALL_FALLBACK_MS = 5_000L
}
