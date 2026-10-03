package edu.cqwu.electricity.webview.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import edu.cqwu.electricity.logging.AppLog
import java.net.URISyntaxException

/**
 * 网页发起的「打开外部应用」协议处理，模型照搬 Via 浏览器 7.2.1：
 * 先查明设备上有没有能处理该 scheme 的应用，没有就完全静默（也不让内核去加载而报
 * net::ERR_UNKNOWN_URL_SCHEME），有才交给调用方弹确认。
 *
 * resolveActivityInfo 属「查询」类 API，会被包可见性过滤；可见性靠 AndroidManifest 的
 * <queries> 声明放开（VIEW + BROWSABLE + scheme="*"）。若某设备上该通配不生效，查询会恒
 * 返回 null，所有 App 链接都会退化为静默——那时需要补回 QUERY_ALL_PACKAGES。
 */
object WebViewExternalApp {

    private const val TAG = "WebViewExternalApp"

    /** 放行给 WebView 自己处理的 scheme；不排除的话，点一个 javascript:void(0) 锚点也会弹询问 */
    private val PASSTHROUGH_SCHEMES = setOf(
        "javascript", "data", "blob", "about", "file", "content",
        "ws", "wss", "ftp", "view-source",
    )

    /** 取小写 scheme；无 scheme 返回 null */
    fun schemeOf(url: String): String? = Uri.parse(url).scheme?.lowercase()

    /**
     * http/https 留在内置浏览器；放行黑名单与无 scheme 交回 WebView；
     * 其余一律外跳（含 tel / sms / mailto / geo / market 与各家 App 私有协议）。
     */
    fun shouldHandleAsExternal(url: String): Boolean {
        val scheme = schemeOf(url) ?: return false
        return scheme != "http" && scheme != "https" && scheme !in PASSTHROUGH_SCHEMES
    }

    /**
     * 构造外部 Intent：intent:// 由 parseUri 解析内嵌参数，普通 scheme 等价于 ACTION_VIEW
     * （也就是「原样交给系统」）。解析失败返回 null。
     */
    fun buildIntent(url: String): Intent? = try {
        Intent.parseUri(url, Intent.URI_INTENT_SCHEME).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
    } catch (_: URISyntaxException) {
        null
    }

    /**
     * 查询能处理该 Intent 的应用名。
     *
     * @return null = 没有应用能处理（调用方应完全静默）；
     *         空串 = 命中的是系统选择器（多个应用都能处理）；
     *         其它 = 应用显示名。
     */
    fun resolveAppName(context: Context, intent: Intent): String? = try {
        val pm = context.packageManager
        // 用 Intent.resolveActivityInfo（API 1 起可用）；PackageManager.resolveActivityInfo 是 API 33+
        val info = intent.resolveActivityInfo(pm, 0)
        when {
            info == null -> null
            "android" == info.packageName -> ""
            else -> pm.getApplicationLabel(info.applicationInfo).toString()
        }
    } catch (e: Exception) {
        // 这段跑在 WebView 回调栈里，抛出去会直接崩应用
        AppLog.w(TAG, "查询可处理应用失败: ${e.message}")
        null
    }

    /**
     * 启动外部应用。用户在选择器里取消不会抛异常，因此返回 true 不等于「真的打开了」。
     */
    fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        AppLog.w(TAG, "没有应用可处理该链接")
        false
    }
}
