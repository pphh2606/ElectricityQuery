package edu.cqwu.electricity.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * WebView URL 判断工具类
 *
 * 集中管理所有与 WebView 当前网页 URL 相关的判断逻辑，
 * 消除 UnifiedWebViewScreen、PaymentWebViewEngine、PaymentSelectionScreen、HomeScreen
 * 之间散落的重复代码。
 *
 * 所有方法均为纯函数 / 静态调用，不持有任何状态。
 */
object WebViewUrlUtil {

    // ───────────────── 域名 / 路径常量 ─────────────────

    const val DOMAIN_ELECTRICITYPAY = "electricitypay.cqwu.edu.cn"
    const val DOMAIN_AUTHSERVER = "authserver"
    const val DOMAIN_WX_TENPAY = "wx.tenpay.com"
    const val DOMAIN_MCH_TENPAY = "mch.tenpay.com"
    const val DOMAIN_ALIPAY_MCLIENT = "mclient.alipay.com"
    const val DOMAIN_ALIPAY_RENDER = "render.alipay.com"
    const val DOMAIN_ALIPAY = "alipay"
    const val DOMAIN_PAY_CQWU = "pay.cqwu.edu.cn"
    const val PATH_SHOWSELECT = "showselect"
    const val PATH_PAY_PRE_SERVICE = "PayPreService"

    // ──────────── URL 判断方法 ────────────

    /**
     * 判断是否为充值成功确认页面。
     *
     * 条件：URL 包含 electricitypay 主域，且不包含 authserver（排除 CAS 认证页）。
     * 用于检测 WebView 是否已跳转回电量系统首页，以确认充值流程完成。
     */
    fun isPaymentSuccessUrl(url: String): Boolean =
        url.contains(DOMAIN_ELECTRICITYPAY) && !url.contains(DOMAIN_AUTHSERVER)

    /**
     * 判断 URL 是否为 CAS 统一认证登录页面。
     *
     * 匹配规则：URL 以 "https://authserver.cqwu.edu.cn/authserver/login?service=" 开头。
     *
     * 当 WebView 最终停留在此类 URL 上时，说明用户未登录（未被自动重定向）。
     * CAS 认证服务器在用户已持有有效 TGC Cookie 时会返回 302 重定向；
     * 否则返回 200 并渲染登录页面 HTML。
     *
     * @see <a href="https://authserver.cqwu.edu.cn/authserver/login">CAS Login</a>
     */
    fun isCasLoginUrl(url: String): Boolean =
        url.startsWith("https://${DOMAIN_AUTHSERVER}.cqwu.edu.cn/authserver/login?service=")

    /**
     * 判断指定 scheme 是否为非 http/https 的自定义协议。
     *
     * 自定义协议包括：weixin://, alipays://, intent://, tel://, mamp:// 等。
     * 此类协议需要在外部应用中打开，而非在 WebView 内加载。
     */
    fun isCustomScheme(scheme: String?): Boolean =
        scheme != null && !scheme.startsWith("http")

    /**
     * 判断 URL 是否为 http 或 https 协议。
     */
    fun isHttpScheme(url: String): Boolean =
        url.startsWith("http://") || url.startsWith("https://")

    /**
     * 判断是否为微信 H5 支付页面（mwebUrl）。
     *
     * 微信支付回调的 mwebUrl 格式：
     * - https://wx.tenpay.com/...
     * - https://mch.tenpay.com/...
     */
    fun isWechatPayUrl(url: String): Boolean =
        url.startsWith("https://$DOMAIN_WX_TENPAY/") || url.startsWith("https://$DOMAIN_MCH_TENPAY/")

    /**
     * 判断是否为支付宝支付页面。
     *
     * 仅对 http/https 链接检测，避免 alipays:// 自定义协议被误匹配。
     * 支付宝支付页域名：
     * - mclient.alipay.com
     * - render.alipay.com
     * - 含 "alipay"  兜底
     */
    fun isAlipayUrl(url: String): Boolean =
        url.startsWith("http") && (url.contains(DOMAIN_ALIPAY_MCLIENT) ||
            url.contains(DOMAIN_ALIPAY_RENDER) || url.contains(DOMAIN_ALIPAY))

    /**
     * 判断是否为 showselect 支付选择页面。
     *
     * showselect 页面特征：
     * - URL 包含 "PayPreService"（支付预处理接口）
     * - URL 包含 "showselect"（支付选择页路径）
     * - URL 包含 "pay.cqwu.edu.cn"（支付主域）
     */
    fun isShowselectUrl(url: String): Boolean =
        url.contains(PATH_PAY_PRE_SERVICE) || url.contains(PATH_SHOWSELECT) || url.contains(DOMAIN_PAY_CQWU)

    // ──────────── 外部 Intent 打开 ────────────

    /**
     * 尝试在外部应用中打开自定义协议链接。
     *
     * 处理逻辑：
     * 1. 解析 URL 的 scheme
     * 2. 非 http/https → 通过 Intent 在外部打开
     * 3. intent:// 特殊处理：使用 Intent.parseUri 正确解析内嵌参数
     * 4. 降级方案：若 Intent.parseUri 失败，从 fragment 提取实际 scheme 重试
     *
     * @param context Android Context（用于 startActivity）
     * @param url     要打开的 URL
     * @param tag     日志标签（调用方可传入自己的 TAG 以便区分来源）
     * @return true 表示 URL 已被处理（外部打开成功或失败但已记录日志）；
     *         false 表示这是 http/https 链接，调用方应在 WebView 内加载
     */
    fun openCustomSchemeUrl(context: Context, url: String, tag: String = "WebViewUrlUtil"): Boolean {
        val uri = Uri.parse(url)
        val scheme = uri.scheme
        if (!isCustomScheme(scheme)) return false

        Log.d(tag, "拦截到自定义协议: scheme=$scheme, url=${url.take(200)}...")
        return try {
            val intent: Intent = if (scheme == "intent") {
                Log.d(tag, ">>> 使用 Intent.parseUri 解析 intent:// URL")
                Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            } else {
                Intent(Intent.ACTION_VIEW, uri)
            }
            val pm = context.packageManager
            val resolveInfo = pm.resolveActivity(intent, 0)
            if (resolveInfo == null) {
                Log.d(tag, "resolveActivity 未找到处理方，仍尝试 startActivity...")
            } else {
                Log.d(tag, ">>> 找到可处理 Intent 的应用: ${resolveInfo.loadLabel(pm)}")
            }
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.e(tag, "未安装可处理 $scheme 的应用: ${e.message}")
            // intent:// 降级方案：从 fragment 提取实际 scheme
            if (scheme == "intent") {
                val actualScheme = extractSchemeFromIntentUrl(url)
                if (actualScheme != null) {
                    Log.d(tag, ">>> 降级方案: 提取到实际 scheme=$actualScheme")
                    val fallbackIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("$actualScheme://${uri.authority}${uri.path}?${uri.query}")
                    )
                    try {
                        context.startActivity(fallbackIntent)
                        return true
                    } catch (e2: ActivityNotFoundException) {
                        Log.e(tag, "降级方案也失败: ${e2.message}")
                    }
                }
            }
            false
        } catch (e: Exception) {
            Log.e(tag, "启动外部应用失败: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    /**
     * 从 intent:// URL 的 fragment 中提取实际 scheme（如 alipays）。
     *
     * intent:// URL 格式示例：
     * intent://platformapi/startApp?... #Intent;scheme=alipays;...
     *
     * @param intentUrl 完整的 intent:// URL
     * @return 提取到的实际 scheme，如 "alipays"；未找到返回 null
     */
    fun extractSchemeFromIntentUrl(intentUrl: String): String? {
        val fragment = intentUrl.substringAfter("#Intent;", "").substringBefore(";end")
        Log.d(tag, ">>> extractSchemeFromIntentUrl: fragment=$fragment")
        for (part in fragment.split(";")) {
            val trimmed = part.trim()
            if (trimmed.startsWith("scheme=")) {
                return trimmed.removePrefix("scheme=")
            }
        }
        return null
    }

    /**
     * 供内部日志使用的默认 TAG。
     * 外部调用方应传入自己的 TAG 以便区分来源。
     */
    private const val tag = "WebViewUrlUtil"
}
