package edu.cqwu.electricity.campusnetwork.common

import com.google.gson.JsonIOException
import com.google.gson.JsonSyntaxException
import edu.cqwu.electricity.R
import edu.cqwu.electricity.theme.ui.UiMessage
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 校园网络（campusnetwork）模块共享的错误分类。
 *
 * 抽到 common 包，避免 speedtest 与 campusnetworkinfo 两个功能相互依赖；
 * 供两个功能的 API 层共用以归一化的错误类型上抛，
 * 原始异常由 [toCampusNetworkException] 归类，技术细节进 AppLog，不静默吞掉。
 */
enum class CampusNetworkErrorKind {
    /** 未连接/未登录校园网（连接超时、连接被拒等，通常是 ERR_CONNECTION_TIMED_OUT） */
    CAMPUS_OFFLINE,

    /** 本机无网络 / 域名无法解析 */
    NO_NETWORK,

    /** 服务端返回业务错误（code != 0 / 非 2xx），优先展示服务端 message */
    SERVER,

    /** 响应体解析失败 */
    PARSE,

    /** 其它未归类异常 */
    UNKNOWN,
}

/**
 * 校园网络公共异常。
 *
 * @param kind 错误分类，驱动 UI 文案
 * @param userMessage 可直接展示给用户的消息（可为 null，由 UI 兜底）
 */
class CampusNetworkException(
    val kind: CampusNetworkErrorKind,
    val userMessage: String? = null,
    cause: Throwable? = null,
) : Exception(userMessage ?: cause?.message, cause)

/** 把任意异常归类为 [CampusNetworkException] */
internal fun Throwable.toCampusNetworkException(): CampusNetworkException {
    val kind = when (this) {
        is CampusNetworkException -> return this
        is SocketTimeoutException, is ConnectException -> CampusNetworkErrorKind.CAMPUS_OFFLINE
        is UnknownHostException -> CampusNetworkErrorKind.NO_NETWORK
        is JsonSyntaxException, is JsonIOException, is IllegalStateException -> CampusNetworkErrorKind.PARSE
        is IOException -> CampusNetworkErrorKind.UNKNOWN
        else -> CampusNetworkErrorKind.UNKNOWN
    }
    return CampusNetworkException(kind, cause = this)
}

/**
 * 校园网错误 → 界面提示。分类决定文案；服务端消息（[CampusNetworkException.userMessage]）优先原样展示。
 * 供 speedtest 与 campusnetworkinfo 两个功能的 ViewModel 共用，避免各写一份。
 */
fun Throwable.toCampusUiMessage(): UiMessage = when (this) {
    is CampusNetworkException -> when (kind) {
        CampusNetworkErrorKind.CAMPUS_OFFLINE ->
            UiMessage(res = R.string.campus_network_error_need_campus)
        CampusNetworkErrorKind.NO_NETWORK ->
            UiMessage(res = R.string.campus_network_error_no_network)
        CampusNetworkErrorKind.SERVER ->
            UiMessage(res = R.string.campus_network_error_generic, raw = userMessage)
        CampusNetworkErrorKind.PARSE ->
            UiMessage(res = R.string.campus_network_error_parse)
        CampusNetworkErrorKind.UNKNOWN ->
            UiMessage(res = R.string.campus_network_error_generic)
    }
    else -> UiMessage(res = R.string.campus_network_error_generic)
}
