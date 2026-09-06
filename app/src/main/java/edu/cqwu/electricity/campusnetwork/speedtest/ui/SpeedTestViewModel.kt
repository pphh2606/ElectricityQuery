package edu.cqwu.electricity.campusnetwork.speedtest.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.cqwu.electricity.campusnetwork.common.CampusNetworkErrorKind
import edu.cqwu.electricity.campusnetwork.common.CampusNetworkException
import edu.cqwu.electricity.campusnetwork.common.toCampusUiMessage
import edu.cqwu.electricity.campusnetwork.speedtest.data.SpeedTestApi
import edu.cqwu.electricity.campusnetwork.speedtest.data.SpeedTestRecord
import edu.cqwu.electricity.campusnetwork.speedtest.data.SpeedTestSessionData
import edu.cqwu.electricity.campusnetwork.speedtest.engine.SpeedTestEngine
import edu.cqwu.electricity.campusnetwork.speedtest.engine.SpeedTestStats
import edu.cqwu.electricity.logging.AppLog
import edu.cqwu.electricity.theme.ui.UiMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 测速页整体运行状态 */
enum class SpeedTestRunStatus {
    /** 空闲/就绪，可开始 */
    IDLE,

    /** 排队中（等服务端转 active） */
    QUEUED,

    /** 测速进行中（引擎 D/P/U 阶段由 tick 呈现） */
    RUNNING,

    /** 已完成并上报成功 */
    COMPLETED,

    /** 出错（见 error），可重试 */
    ERROR,
}

data class SpeedTestUiState(
    val status: SpeedTestRunStatus = SpeedTestRunStatus.IDLE,
    /** queued 时的排队位次 */
    val position: Int? = null,
    /** 活跃测速数（顶部“活跃会话”） */
    val active: Int = 0,
    /** 排队总数（顶部“排队等候”） */
    val queue: Int = 0,
    /** complete 返回的结果 ID */
    val resultId: String? = null,
    /** 错误信息（ERROR 时非空） */
    val error: UiMessage? = null,
)

/**
 * 网速测试 ViewModel。
 *
 * 流程：POST /session 创建 →（queued 则轮询至 active/expired）→ claim →
 * 引擎跑 D/P/U → complete（字符串、2 位小数）→ 完成。
 * 约定：**任何未 complete 的退出**（停止/失败/离开页面）都会 DELETE 会话兜底释放，
 * 释放一次后置空 sessionId，避免与 onCleared 重复释放。
 * 异常归类复用 [CampusNetworkException] 并全量记 AppLog，不静默吞掉。
 */
class SpeedTestViewModel(
    private val api: SpeedTestApi = SpeedTestApi(),
) : ViewModel() {

    private val engine = SpeedTestEngine(api)

    private val _state = MutableStateFlow(SpeedTestUiState())
    val state: StateFlow<SpeedTestUiState> = _state.asStateFlow()

    /** 引擎实时采样（UI 用） */
    val tick = engine.tick

    /** 最近测速记录（rank/stats）；为空时 UI 隐藏该区域 */
    private val _recent = MutableStateFlow<List<SpeedTestRecord>>(emptyList())
    val recent: StateFlow<List<SpeedTestRecord>> = _recent.asStateFlow()

    private var runJob: Job? = null

    init {
        loadRecent()
    }

    /** 拉取最近测速记录；失败仅记录日志、保留空列表（区域隐藏），不阻塞测速主流程 */
    fun loadRecent() {
        viewModelScope.launch {
            api.fetchRankStats(limit = RECENT_LIMIT)
                .onSuccess { _recent.value = it }
                .onFailure { e ->
                    AppLog.e(TAG, "加载最近测速失败: ${e.message}", e)
                }
        }
    }

    @Volatile
    private var sessionId: String? = null

    @Volatile
    private var completed = false

    /** 是否有进行中的流程（创建/排队/测速） */
    fun isBusy(): Boolean = runJob?.isActive == true || engine.isRunning

    fun startTest() {
        if (runJob?.isActive == true) return
        runJob = viewModelScope.launch {
            runFlow()
            runJob = null
        }
    }

    /** 用户停止：abort 引擎；引擎不在跑（排队中）则取消流程 */
    fun stopTest() {
        if (engine.isRunning) {
            AppLog.d(TAG, "用户停止测速")
            engine.abort()
        } else {
            runJob?.cancel()
        }
    }

    /** 错误后重试 */
    fun retry() {
        _state.value = SpeedTestUiState()
        startTest()
    }

    private suspend fun runFlow() {
        completed = false
        sessionId = null
        _state.value = SpeedTestUiState(status = SpeedTestRunStatus.QUEUED)
        try {
            runFlowBody()
        } finally {
            // 未 complete 的任何退出：DELETE 兜底释放会话（幂等，释放后清空）
            val sid = sessionId
            if (!completed && sid != null) {
                sessionId = null
                withContext(NonCancellable) {
                    api.releaseSession(sid)
                }
            }
        }
    }

    private suspend fun runFlowBody() = coroutineScope {
        // 1) 创建会话
        val created = api.createSession().getOrElse {
            failWith(it)
            return@coroutineScope
        }
        val id = created.sessionId
        if (id.isNullOrBlank()) {
            failWith(RuntimeException("创建会话未返回 sessionId"))
            return@coroutineScope
        }
        sessionId = id
        updateSessionNumbers(created)

        // 2) queued → 轮询至 active / expired
        var session = created
        while (session.status == "queued") {
            _state.update {
                it.copy(
                    status = SpeedTestRunStatus.QUEUED,
                    position = session.position ?: it.position,
                    active = session.active ?: it.active,
                    queue = session.queue ?: it.queue,
                )
            }
            delay(1_000)
            val q = api.querySession(id).getOrElse {
                failWith(it)
                return@coroutineScope
            }
            session = q
            if (q.status == "expired") {
                failWith(CampusNetworkException(CampusNetworkErrorKind.SERVER, userMessage = "会话已过期，请重新开始"))
                return@coroutineScope
            }
        }

        // 3) 抢占并进入测速
        if (session.status != "active") {
            failWith(CampusNetworkException(CampusNetworkErrorKind.SERVER, userMessage = "会话状态异常：${session.status}"))
            return@coroutineScope
        }
        val claimed = api.claimSession(id).getOrElse {
            failWith(it)
            return@coroutineScope
        }
        updateSessionNumbers(claimed)
        _state.update { it.copy(status = SpeedTestRunStatus.RUNNING) }

        // 4) 引擎测速（子协程随本 scope 自动清理）
        val engineJob = engine.launch(this)
        engineJob.join()

        val result = engine.result ?: run {
            // 用户停止（无结果）：交给外层 finally 释放，状态回 IDLE
            _state.value = SpeedTestUiState()
            return@coroutineScope
        }

        // 5) complete 上报（实测为字符串、保留 2 位小数）
        api.completeSession(
            sessionId = id,
            download = SpeedTestStats.formatFixed2(result.downloadMbps),
            upload = SpeedTestStats.formatFixed2(result.uploadMbps),
            ping = SpeedTestStats.formatFixed2(result.pingMs),
            jitter = SpeedTestStats.formatFixed2(result.jitterMs),
        ).onSuccess { comp ->
            completed = true
            _state.update {
                it.copy(
                    status = SpeedTestRunStatus.COMPLETED,
                    resultId = comp.resultId,
                    active = comp.active ?: 0,
                    queue = comp.queue ?: 0,
                )
            }
            AppLog.d(TAG, "测速完成 resultId=${comp.resultId}")
            loadRecent()
        }.onFailure { e ->
            AppLog.e(TAG, "结果上报失败: ${e.message}", e)
            _state.update {
                it.copy(status = SpeedTestRunStatus.ERROR, error = e.toCampusUiMessage())
            }
        }
    }

    private suspend fun failWith(e: Throwable) {
        AppLog.e(TAG, "测速流程失败: ${e.message}", e)
        _state.update {
            it.copy(status = SpeedTestRunStatus.ERROR, error = e.toCampusUiMessage())
        }
    }

    private fun updateSessionNumbers(s: SpeedTestSessionData) {
        _state.update {
            it.copy(
                active = s.active ?: it.active,
                queue = s.queue ?: it.queue,
                position = s.position ?: it.position,
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        engine.abort()
        val sid = sessionId
        if (!completed && sid != null) {
            sessionId = null
            // 协程已随 viewModelScope 取消，用 NonCancellable 执行兜底释放
            viewModelScope.launch {
                withContext(NonCancellable) {
                    api.releaseSession(sid)
                }
            }
        }
    }

    private companion object {
        const val TAG = "SpeedTestViewModel"

        /** 最近测速一次拉取的条数 */
        const val RECENT_LIMIT = 50
    }
}
