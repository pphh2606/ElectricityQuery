package edu.cqwu.electricity.login.ui

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import edu.cqwu.electricity.R
import edu.cqwu.electricity.logging.AppLog
import edu.cqwu.electricity.login.data.QrLoginApi
import edu.cqwu.electricity.login.domain.SessionCoordinatorV2
import edu.cqwu.electricity.login.model.AuthSessionCommitV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 扫码登录页面 UI 状态。
 *
 * [QrLoginUiState.Ready.content] 为解码后的二维码内容字符串（本地渲染/保存/分享共用）。
 */
sealed interface QrLoginUiState {
    /** 初始化中（由 PullToRefreshBox 指示器替代加载动画） */
    data object Initializing : QrLoginUiState

    /** 已获取二维码 */
    data class Ready(val content: String) : QrLoginUiState

    /** 已扫码，待手机端确认 */
    data object Scanned : QrLoginUiState

    /** 已确认，正在提交认证 */
    data object Confirmed : QrLoginUiState

    /** 流程失败（可下拉刷新/重试） */
    data class Error(val message: String) : QrLoginUiState
}

/** 一次性 UI 事件（登录成功后的提示与退出由 UI 处理） */
sealed interface QrLoginEvent {
    /** 扫码登录成功，会话已提交并激活 */
    data object LoginSuccess : QrLoginEvent
}

/**
 * 扫码登录 ViewModel：把「取二维码 → 轮询扫码状态 → 提交认证」流程从 Composable 中抽出。
 *
 * 轮询由单一 [pollingJob] 驱动；下拉刷新/重试会先取消旧任务再重启，避免双轮询并发。
 * 页面销毁（离开导航栈）时 [viewModelScope] 自动取消轮询。
 */
class QrLoginViewModel(application: Application) : AndroidViewModel(application) {

    private val api = QrLoginApi()

    private val _uiState = MutableStateFlow<QrLoginUiState>(QrLoginUiState.Initializing)
    val uiState: StateFlow<QrLoginUiState> = _uiState.asStateFlow()

    /** 是否处于下拉刷新的加载阶段（仅取码阶段为 true，轮询期间为 false） */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _events = Channel<QrLoginEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var pollingJob: Job? = null

    init {
        startQrLogin()
    }

    /** 启动/重启扫码登录流程（首次进入由 init 触发，下拉刷新与重试复用同一入口）。 */
    fun startQrLogin() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            _isRefreshing.value = true
            _uiState.value = QrLoginUiState.Initializing

            // Step 1: 获取登录页，解析 lt/execution
            val pageResult = api.fetchLoginPage()
            if (pageResult.isFailure) {
                _uiState.value = QrLoginUiState.Error(
                    pageResult.exceptionOrNull()?.message ?: string(R.string.login_get_page_failed)
                )
                _isRefreshing.value = false
                return@launch
            }
            val pageData = pageResult.getOrThrow()

            // Step 2: 获取二维码 UUID
            val uuidResult = api.fetchQrCodeUuid()
            if (uuidResult.isFailure) {
                _uiState.value = QrLoginUiState.Error(
                    uuidResult.exceptionOrNull()?.message ?: string(R.string.qrcode_fetch_failed)
                )
                _isRefreshing.value = false
                return@launch
            }
            val uuid = uuidResult.getOrThrow()

            // Step 3: 下载二维码图片并解码为内容字符串
            val decodeResult = api.downloadAndDecodeQrCode(uuid)
            if (decodeResult.isFailure) {
                _uiState.value = QrLoginUiState.Error(
                    decodeResult.exceptionOrNull()?.message ?: string(R.string.qrcode_decode_failed)
                )
                _isRefreshing.value = false
                return@launch
            }

            _uiState.value = QrLoginUiState.Ready(decodeResult.getOrThrow())
            _isRefreshing.value = false

            // Step 4: 轮询扫码状态（失败静默重试，直到成功/过期才退出）
            while (true) {
                delay(1000)
                val statusResult = api.pollQrCodeStatus(uuid)
                if (statusResult.isFailure) continue

                when (val status = statusResult.getOrThrow()) {
                    "0" -> Unit // 等待扫码，继续轮询
                    "2" -> _uiState.value = QrLoginUiState.Scanned // 已扫码，等待确认（轮询不停止）
                    "1" -> {
                        _uiState.value = QrLoginUiState.Confirmed
                        val submitResult = api.submitQrLogin(pageData.lt, uuid, pageData.execution)
                        if (submitResult.isSuccess) {
                            val loginResult = submitResult.getOrThrow()
                            if (loginResult.username.isNotBlank()) {
                                loginResult.cookieStore?.let { tempStore ->
                                    try {
                                        withContext(Dispatchers.IO) {
                                            SessionCoordinatorV2.commitAndActivate(
                                                AuthSessionCommitV2(
                                                    username = loginResult.username,
                                                    cookies = tempStore.getAllCookies(),
                                                    // 扫码登录提取的 data-name="id" 即数字学号，直接随账号缓存
                                                    studentId = loginResult.username,
                                                )
                                            )
                                        }
                                    } catch (e: Exception) {
                                        AppLog.w("QrLoginViewModel", "保存登录会话失败", e)
                                    }
                                }
                            }
                            _events.send(QrLoginEvent.LoginSuccess)
                        } else {
                            _uiState.value = QrLoginUiState.Error(
                                submitResult.exceptionOrNull()?.message
                                    ?: string(R.string.login_failed)
                            )
                        }
                        break
                    }
                    "3" -> {
                        _uiState.value = QrLoginUiState.Error(string(R.string.login_qr_expired))
                        break
                    }
                }
            }
        }
    }

    private fun string(@StringRes id: Int): String = getApplication<Application>().getString(id)
}
