package edu.cqwu.electricity.campusnetwork.portal.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.cqwu.electricity.R
import edu.cqwu.electricity.campusnetwork.portal.data.PortalApi
import edu.cqwu.electricity.campusnetwork.portal.data.PortalMabDevice
import edu.cqwu.electricity.campusnetwork.portal.data.PortalOnlineInfo
import edu.cqwu.electricity.campusnetwork.portal.data.parseMabDevices
import edu.cqwu.electricity.campusnetwork.portal.data.parseNotices
import edu.cqwu.electricity.campusnetwork.portal.data.parseServices
import edu.cqwu.electricity.logging.AppLog
import edu.cqwu.electricity.theme.ui.UiMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 网络服务页状态。
 *
 * 数据全部来自**认证网关**（eportal，222.179.99.144），与「接入者信息」页的 SAM 档案
 * 是两个不同服务器的来源，字段同名也不代表同一个值。
 */
data class PortalServiceUiState(
    /**
     * 加载 / 刷新中。
     *
     * 首次加载与下拉刷新共用同一状态：页面统一由 `PullToRefreshBox` 的顶部指示器呈现，
     * 不再区分"整页加载态"（原 `isLoading` 已合并到此）。
     */
    val isRefreshing: Boolean = false,
    /** 操作进行中（切换服务 / 下线 / 无感认证），期间操作入口置灰 */
    val isApplying: Boolean = false,
    /** 需要用户处理的错误（网关不可达、业务失败）；"网关未报告会话"不算错误，见 [noSession] */
    val error: UiMessage? = null,
    /** 一次性成功提示（消费后清空） */
    val notice: UiMessage? = null,
    /**
     * 网关返回 `result:"fail"`：**当前没有可用的门户会话**。
     *
     * 不等同于"不能上网"——实测设备通过**无感认证自动上线**时，网络已放行、SAM 在线表也有记录，
     * 但 eportal 的门户会话表里没有条目，同样会返回 fail（此时公网可正常访问）。
     * 因此界面文案保持中性并保留认证入口，不做"未认证"的断言。
     */
    val noSession: Boolean = false,
    /** 会话凭证；由网关响应回填，仅内存持有，不持久化 */
    val userIndex: String? = null,
    val info: PortalOnlineInfo? = null,
    /** 可切换服务候选（来自会话响应，不额外请求） */
    val services: List<String> = emptyList(),
    /** 已绑定无感认证设备（wait 态拿不到设备列表，此时为空） */
    val mabDevices: List<PortalMabDevice> = emptyList(),
    /** 网关通知（如弱密码提醒） */
    val notices: List<String> = emptyList(),
    /** 剩余可用秒数（本地倒计时，不轮询接口） */
    val remainingSeconds: Long? = null,
)

/**
 * 网络服务 ViewModel。
 *
 * 生命周期跟随导航栈条目（由 NavGraph 路由内 `viewModel()` 创建），离开页面即销毁，
 * `userIndex` 与自助服务链接都不会残留。
 *
 * **不做认证态探测**：实测网关按请求源 IP 定位会话，空 `userIndex` 查询即返回完整身份并
 * 回填凭证，因此一次查询同时完成"判在线 / 取凭证 / 取数据"。
 * 判在线只看 `result`（`success`/`wait`），`fail` 的 message 与"凭证不匹配"场景完全一致，
 * 不参与判断。
 */
class PortalServiceViewModel(
    private val api: PortalApi = PortalApi(),
) : ViewModel() {

    private val _state = MutableStateFlow(PortalServiceUiState())
    val state: StateFlow<PortalServiceUiState> = _state.asStateFlow()

    private var countdownJob: Job? = null

    init {
        load()
    }

    /**
     * 加载 / 刷新会话（空 userIndex 由网关按源 IP 定位）。
     *
     * 首次加载与下拉刷新同一入口：已有内容时保留内容，只转顶部指示器。
     */
    fun load() {
        if (_state.value.isRefreshing) return
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true, error = null, notice = null) }
            fetchInfo()
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    /** 切换服务；成功后界面就地刷新，失败以服务端原文提示 */
    fun switchService(serviceName: String) {
        val userIndex = _state.value.userIndex ?: return
        if (_state.value.isApplying) return
        viewModelScope.launch {
            _state.update { it.copy(isApplying = true, error = null, notice = null) }
            api.switchService(userIndex, serviceName)
                .onSuccess { result ->
                    if (result.isSuccess) {
                        _state.update {
                            it.copy(
                                notice = UiMessage(
                                    res = R.string.portal_service_switched,
                                    args = listOf(serviceName),
                                ),
                            )
                        }
                        fetchInfo()
                    } else {
                        _state.update { it.copy(error = UiMessage(raw = result.message)) }
                    }
                }
                .onFailure { fail(it) }
            _state.update { it.copy(isApplying = false) }
        }
    }

    /** 断开网络；成功后本机即刻离线，界面切"网关未报告会话"态 */
    fun logout() {
        val userIndex = _state.value.userIndex ?: return
        if (_state.value.isApplying) return
        viewModelScope.launch {
            _state.update { it.copy(isApplying = true, error = null) }
            api.logout(userIndex)
                .onSuccess { result ->
                    if (result.isSuccess) {
                        applyNoSession()
                    } else {
                        _state.update { it.copy(error = UiMessage(raw = result.message)) }
                    }
                }
                .onFailure { fail(it) }
            _state.update { it.copy(isApplying = false) }
        }
    }

    /**
     * 无感认证开关（当前设备）。
     *
     * 取消操作的服务端响应存在 `result:"success"` + `message:"取消无感认证失败"` 的矛盾组合，
     * 因此这里**忽略 message**，一律以刷新后的 `hasMabInfo` 实际值为准。
     */
    fun setMab(enable: Boolean) {
        val userIndex = _state.value.userIndex ?: return
        if (_state.value.isApplying) return
        viewModelScope.launch {
            _state.update { it.copy(isApplying = true, error = null) }
            api.setMab(userIndex, enable)
                .onSuccess { result ->
                    if (result.isSuccess) {
                        fetchInfo()
                    } else {
                        // 超限等业务失败：原文交给界面（如「一个账户只能绑定2台…」）
                        _state.update { it.copy(error = UiMessage(raw = result.message)) }
                    }
                }
                .onFailure { fail(it) }
            _state.update { it.copy(isApplying = false) }
        }
    }

    /** 消费一次性提示（界面弹完 Snackbar 后调用） */
    fun consumeNotice() {
        _state.update { it.copy(notice = null) }
    }

    // ────────────────────────── 内部 ──────────────────────────

    /**
     * 拉取会话。
     *
     * `wait` 是过渡态（字段不完整但身份可用），按固定间隔重试至多 [WAIT_RETRY] 次；
     * 到达上限仍为 wait 也照常展示已返回字段，**不当作失败**。
     * `fail` 则表示网关当前没有本机会话——既可能是真的未认证，也可能是设备经无感认证上线、
     * 或网关会话表尚未同步（实测此时公网仍可访问），因此不断言"未认证"。
     */
    private suspend fun fetchInfo() {
        var attempt = 0
        while (true) {
            val info = api.onlineInfo().getOrElse { e ->
                fail(e)
                return
            }
            if (!info.isOnline) {
                applyNoSession()
                return
            }
            if (info.isSuccess || attempt >= WAIT_RETRY) {
                applyOnline(info)
                return
            }
            attempt++
            delay(WAIT_INTERVAL_MS)
        }
    }

    private fun applyOnline(info: PortalOnlineInfo) {
        if (!info.isSuccess) {
            AppLog.e(TAG, "在线信息仍不完整（result=${info.result}），按已返回字段展示")
        }
        _state.update {
            it.copy(
                noSession = false,
                userIndex = info.userIndex ?: it.userIndex,
                info = info,
                services = parseSafely("服务列表") { parseServices(info) },
                mabDevices = parseSafely("无感认证设备") { parseMabDevices(info.mabInfo) },
                notices = parseSafely("网关通知") { parseNotices(info.notify) },
                error = null,
            )
        }
        startCountdown(info.maxLeavingTime)
    }

    private fun applyNoSession() {
        countdownJob?.cancel()
        _state.update {
            it.copy(
                noSession = true,
                userIndex = null,
                info = null,
                services = emptyList(),
                mabDevices = emptyList(),
                notices = emptyList(),
                remainingSeconds = null,
                error = null,
            )
        }
    }

    /**
     * 解析失败不阻断会话展示：`mabInfo` 等字段存在复用时结构可能不符预期，
     * 但会话数据本身有效。失败一律记日志，不静默吞掉。
     */
    private fun <T> parseSafely(desc: String, block: () -> List<T>): List<T> = try {
        block()
    } catch (e: Exception) {
        AppLog.e(TAG, "$desc 解析失败: ${e.message}", e)
        emptyList()
    }

    /**
     * 统一错误处理。
     *
     * 界面只呈现一种错误文案（无法连接认证服务）：用户无法据异常类型采取不同行动，
     * 细分无意义；**具体分类与原始异常全部进 AppLog**，排查时看日志即可。
     */
    private fun fail(e: Throwable) {
        AppLog.e(TAG, "认证网关请求失败: ${e.message}", e)
        _state.update { it.copy(error = UiMessage(res = R.string.portal_error_unreachable)) }
    }

    /** 剩余时长本地递减；不轮询接口，刷新/操作后由服务端值重新校准 */
    private fun startCountdown(rawLeavingTime: String?) {
        countdownJob?.cancel()
        val seconds = parseLeavingSeconds(rawLeavingTime)
        _state.update { it.copy(remainingSeconds = seconds) }
        if (seconds == null || seconds <= 0) return
        countdownJob = viewModelScope.launch {
            var left = seconds
            while (left > 0) {
                delay(1_000)
                left -= 1
                _state.update { it.copy(remainingSeconds = left) }
            }
        }
    }

    private companion object {
        const val TAG = "PortalServiceViewModel"

        /** `wait` 过渡态的重试上限与间隔 */
        const val WAIT_RETRY = 3
        const val WAIT_INTERVAL_MS = 300L
    }
}

/**
 * 解析网关的中文剩余时长（形如「0 天 0 小时 19 分钟 50 秒」）为秒数。
 *
 * 只匹配出现的单位，缺失单位按 0 计；一个单位都没匹配上返回 null（格式变化时界面显示 `--`）。
 */
internal fun parseLeavingSeconds(raw: String?): Long? {
    if (raw.isNullOrBlank()) return null
    var matched = false
    fun part(unit: String): Long {
        val value = Regex("(\\d+)\\s*$unit").find(raw) ?: return 0
        matched = true
        return value.groupValues[1].toLong()
    }
    val days = part("天")
    val hours = part("小时")
    val minutes = part("分钟")
    val seconds = part("秒")
    return if (matched) days * 86_400 + hours * 3_600 + minutes * 60 + seconds else null
}
