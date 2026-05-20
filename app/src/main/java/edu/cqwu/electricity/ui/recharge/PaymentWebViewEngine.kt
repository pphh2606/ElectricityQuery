package edu.cqwu.electricity.ui.recharge

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import edu.cqwu.electricity.data.model.PaymentMethod
import edu.cqwu.electricity.util.WebViewUrlUtil

/**
 * 支付 WebView 引擎
 *
 * 管理一个隐藏 WebView，加载 showselect 支付选择页面，
 * 通过 JS 注入选择支付方式并触发 AJAX 调用 gotToPay 接口。
 *
 * showselect 页面已包含完整的 jQuery 逻辑：
 * - `#next.click()` → 读取 selected radio → 读取隐藏字段
 *   → $.ajax POST /pay/cashier/gotToPay → success → 导航到 mwebUrl
 * - `setInterval(queryOrderStatus, 1500)` → 轮询订单状态
 *
 * 核心作用：保持完整的浏览器会话（Cookie）连续性，
 * 解决 OkHttp 与 WebView 之间会话断裂导致 CAS 认证失败的问题。
 */
class PaymentWebViewEngine(
    private val webView: WebView
) {
    companion object {
        private const val TAG = "PaymentWebViewEngine"
    }

    // 回调
    var onShowselectPageReady: (() -> Unit)? = null
    var onMwebUrlDetected: ((mwebUrl: String) -> Unit)? = null
    var onWechatIntentDetected: ((url: String) -> Unit)? = null
    var onNavigationChanged: ((url: String) -> Unit)? = null
    var onError: ((error: String) -> Unit)? = null

    // 状态
    private var isShowselectReady = false
    private var hasInjectedJs = false
    private var detectedMwebUrl: String? = null

    /**
     * 初始化隐藏 WebView，加载 payUrl（showselect 页面）
     *
     * @param payUrl showselect 页面 URL
     * @param orderId 订单 ID（日志用）
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun initialize(payUrl: String, orderId: String) {
        Log.d(TAG, "=== 初始化 PaymentWebViewEngine ===")
        Log.d(TAG, "payUrl: $payUrl")
        Log.d(TAG, "orderId: $orderId")

        webView.apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            settings.javaScriptEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.domStorageEnabled = true
            settings.userAgentString = edu.cqwu.electricity.data.network.UserAgentProvider.getActiveUserAgent()

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    if (url != null) {
                        Log.d(TAG, "onPageStarted: $url")
                        onNavigationChanged?.invoke(url)
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (url != null) {
                        Log.d(TAG, "onPageFinished: $url")

                        // 检测 showselect 页面加载完成
                        // showselect 页面加载后，jQuery、隐藏字段、radio buttons 都已就绪
                        if (!isShowselectReady && WebViewUrlUtil.isShowselectUrl(url)) {
                            isShowselectReady = true
                            Log.d(TAG, ">>> showselect 页面加载完成，等待 JS 注入指令")
                            onShowselectPageReady?.invoke()
                        }
                    }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val url = request?.url?.toString() ?: return false
                    Log.d(TAG, "shouldOverrideUrlLoading: $url")
                    onNavigationChanged?.invoke(url)

                    // ★ 非 http/https 协议必须先于域名检查处理！
                    // 如果先检查域名，alipays:// 会被 url.contains("alipay") 误匹配，
                    // 导致 WebView 尝试加载 alipays:// 并抛出 net::ERR_UNKNOWN_URL_SCHEME
                    if (view?.context != null && WebViewUrlUtil.openCustomSchemeUrl(view.context, url, TAG)) {
                        onWechatIntentDetected?.invoke(url)
                        return true
                    }

                    // 检测到 mwebUrl（微信 H5 支付页）
                    // showselect 页面的 gotToPay AJAX 成功回调会执行：
                    // window.location.href = msg.data.mwebUrl
                    // 或 $("form").submit() → 服务器返回 302 → mwebUrl
                    if (WebViewUrlUtil.isWechatPayUrl(url)) {
                        Log.d(TAG, ">>> 检测到 mwebUrl: ${url.take(100)}...")
                        detectedMwebUrl = url
                        onMwebUrlDetected?.invoke(url)
                        return false // WebView 继续加载
                    }

                    // 检测支付宝支付页面（仅对 http/https 链接检测，避免 alipays:// 误匹配）
                    if (WebViewUrlUtil.isAlipayUrl(url)) {
                        Log.d(TAG, ">>> 检测到支付宝支付页面: ${url.take(100)}...")
                        detectedMwebUrl = url
                        onMwebUrlDetected?.invoke(url)
                        return false
                    }

                    return false
                }

                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    if (url == null) return false
                    // 非 http/https 协议 → 在外部打开
                    if (view?.context != null && WebViewUrlUtil.openCustomSchemeUrl(view.context, url, TAG)) {
                        onWechatIntentDetected?.invoke(url)
                        return true
                    }
                    return false
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: android.webkit.WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    val errorMsg = "WebView 加载错误: ${error?.description ?: "未知错误"}"
                    Log.e(TAG, errorMsg)
                    onError?.invoke(errorMsg)
                }
            }

            // 加载 showselect 支付页面
            // showselect 页面包含:
            // - 隐藏字段: orderNo, orderId, publictype, openId
            // - radio buttons: 02,WAP(微信), 01,WAP(支付宝)
            // - #next 按钮: 点击后 AJAX 调用 gotToPay
            // - 轮询: setInterval(queryOrderStatus, 1500)
            // Android 6 系统 WebView 会尝试修改 headers Map，
            // 必须使用可变 HashMap 避免 UnsupportedOperationException
            val headers = mapOf("Referer" to "https://pay.cqwu.edu.cn/")
            loadUrl(payUrl, HashMap(headers))
        }
    }

    /**
     * 注入 JavaScript：选择支付方式并触发 gotToPay 调用。
     *
     * showselect 页面已有完整的 jQuery 逻辑：
     * ```javascript
     * $("#next").click(function() {
     *     var channel = $("input[type=radio]:checked").val().split(",");
     *     $.ajax({
     *         type: "POST",
     *         url: "/pay/cashier/gotToPay",
     *         data: { payType, publictype, orderTradeNo, ... },
     *         success: function(msg) {
     *             if (msg.messageCode == '0') {
     *                 // 使用 mwebUrl 导航到微信支付页
     *                 window.location.href = msg.data.mwebUrl;
     *             }
     *         }
     *     });
     * });
     * ```
     *
     * 我们只需要:
     * 1. 选择对应支付方式的 radio button
     * 2. 触发 #next.click()
     *
     * @param method 用户选择的支付方式 (WECHAT/ALIPAY)
     */
    fun injectAndSubmit(method: PaymentMethod) {
        if (hasInjectedJs) {
            Log.d(TAG, "JS 已注入，跳过重复注入")
            return
        }

        val radioValue = "${method.payType},WAP"
        Log.d(TAG, ">>> 注入 JS: 选择 radio=$radioValue (${method.displayName})")

        val jsCode = """
            (function() {
                try {
                    // 1. 选择 radio button
                    var radios = document.querySelectorAll('input[name="payway"]');
                    var found = false;
                    for (var i = 0; i < radios.length; i++) {
                        if (radios[i].value === '$radioValue') {
                            radios[i].checked = true;
                            found = true;
                            console.log('[WVEngine] Selected radio: ' + radios[i].value);
                            break;
                        }
                    }
                    if (!found) {
                        console.log('[WVEngine] Radio not found: $radioValue');
                    }
                    
                    // 2. 触发 #next.click() → showselect 页面的 jQuery 会处理 gotToPay AJAX
                    var nextBtn = document.getElementById('next');
                    if (nextBtn) {
                        console.log('[WVEngine] Clicking #next button');
                        nextBtn.click();
                    } else {
                        console.log('[WVEngine] #next button not found');
                        
                        // 降级方案：直接构造 gotToPay AJAX 请求
                        var orderNo = document.getElementById('orderNo');
                        var publictype = document.getElementById('publictype');
                        var openId = document.getElementById('openId');
                        if (orderNo) {
                            var xhr = new XMLHttpRequest();
                            xhr.open('POST', '/pay/cashier/gotToPay', false);
                            xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded; charset=UTF-8');
                            xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
                            var params = 'payType=${method.payType}' +
                                '&publictype=' + (publictype ? publictype.value : '') +
                                '&orderTradeNo=' + orderNo.value +
                                '&userIp=218.194.188.173' +
                                '&tradeType=WAP' +
                                '&openId=' + (openId ? openId.value : '');
                            xhr.send(params);
                            console.log('[WVEngine] gotToPay response: ' + xhr.responseText);
                        }
                    }
                } catch(e) {
                    console.log('[WVEngine] Error: ' + e.message);
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(jsCode, object : android.webkit.ValueCallback<String> {
            override fun onReceiveValue(result: String?) {
                Log.d(TAG, "JS 注入结果: $result")
                hasInjectedJs = true
            }
        })
    }

    /**
     * 获取检测到的 mwebUrl
     */
    fun getDetectedMwebUrl(): String? = detectedMwebUrl

    /**
     * 获取 WebView 实例
     */
    fun getWebView(): WebView = webView

    /**
     * 释放资源
     */
    fun destroy() {
        Log.d(TAG, "销毁 PaymentWebViewEngine")
        try {
            webView.stopLoading()
            webView.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "销毁异常: ${e.message}")
        }
    }

}
