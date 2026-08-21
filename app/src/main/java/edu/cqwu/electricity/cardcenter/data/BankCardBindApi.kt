package edu.cqwu.electricity.cardcenter.data

import com.google.gson.Gson
import edu.cqwu.electricity.logging.AppLog
import edu.cqwu.electricity.login.data.HtmlFormParser
import edu.cqwu.electricity.login.data.SessionExpiredException
import edu.cqwu.electricity.payment.data.HttpClientFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request

/**
 * 学生绑定银行卡 EPay API 封装。
 *
 * 复用 [HttpClientFactory.shared] 的 CAS/EPay 会话，页面与结果均为 HTML，
 * checkexist 为 JSON。所有 POST 均模拟原页面的表单提交。
 */
class BankCardBindApi {

    private val gson = Gson()

    companion object {
        private const val TAG = "BankCardBindApi"
        private const val EPAY_BASE = "http://218.194.176.214:8382"
        private const val PAGE_URL = "$EPAY_BASE/epay/thirdapp/bankcardbind"
        private const val CHECK_EXIST_URL = "$EPAY_BASE/epay/wxpage/checkexist"
        private const val BIND_URL = "$EPAY_BASE/epay/wxpage/dobind"
        private const val UNBIND_URL = "$EPAY_BASE/epay/wxpage/dounbind"
    }

    /** 获取银行卡绑定页面并解析银行列表。 */
    suspend fun fetchBankOptions(): Result<List<BankOption>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(PAGE_URL)
                .get()
                .build()
            val html = HttpClientFactory.shared.newCall(request).execute().body.string()
            HtmlFormParser.checkAndThrow(html)

            val banks = parseBankOptions(html)
            if (banks.isEmpty()) {
                throw RuntimeException("银行卡绑定页面未返回任何银行")
            }
            AppLog.d(TAG, "获取银行列表成功: ${banks.map { it.code }}")
            Result.success(banks)
        } catch (e: SessionExpiredException) {
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e(TAG, "获取银行列表失败", e)
            Result.failure(e)
        }
    }

    /** 查询指定银行是否已绑定。 */
    suspend fun checkBankStatus(bankCode: String): Result<BankCardBindStatus> =
        withContext(Dispatchers.IO) {
            try {
                val form = FormBody.Builder()
                    .add("bankcode", bankCode)
                    .build()
                val json = executeForm(CHECK_EXIST_URL, form)
                HtmlFormParser.checkAndThrow(json)

                val status = parseCheckStatus(json)
                AppLog.d(TAG, "查询绑定状态: bank=$bankCode, chkflag=${status.chkflag}")
                Result.success(status)
            } catch (e: SessionExpiredException) {
                Result.failure(e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(TAG, "查询绑定状态失败: bank=$bankCode", e)
                Result.failure(e)
            }
        }

    /** 提交银行卡绑定。卡号仅用于本次请求，不落盘、不记日志。 */
    suspend fun bindBankCard(cardNo: String, bankType: String): Result<BankCardBindResult> {
        val form = FormBody.Builder()
            .add("cardNo", cardNo)
            .add("banktype", bankType)
            .build()
        return postAndParseResult(BIND_URL, form, bankType)
    }

    /** 解绑指定银行。请求体按原页面 do_unbind JS 逻辑构造。 */
    suspend fun unbindBankCard(bankType: String): Result<BankCardBindResult> {
        val form = FormBody.Builder()
            .add("cardNo", "")
            .add("banktype", bankType)
            .build()
        return postAndParseResult(UNBIND_URL, form, bankType)
    }

    private suspend fun postAndParseResult(
        url: String,
        form: FormBody,
        bankType: String,
    ): Result<BankCardBindResult> = withContext(Dispatchers.IO) {
        try {
            val html = executeForm(url, form)
            HtmlFormParser.checkAndThrow(html)
            val result = parseBindResultHtml(html)
            AppLog.d(TAG, "银行卡操作完成: url=$url, bank=$bankType")
            Result.success(result)
        } catch (e: SessionExpiredException) {
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e(TAG, "银行卡操作失败: url=$url, bank=$bankType", e)
            Result.failure(e)
        }
    }

    private fun executeForm(url: String, form: FormBody): String {
        val request = Request.Builder()
            .url(url)
            .post(form)
            .addHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .addHeader("X-Requested-With", "XMLHttpRequest")
            .addHeader("Referer", PAGE_URL)
            .build()
        return HttpClientFactory.shared.newCall(request).execute().body.string()
    }

    internal fun parseBankOptions(html: String): List<BankOption> {
        val labelRegex = Regex(
            """<label[^>]*>.*?</label>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val valueRegex = Regex(
            """<input[^>]*name=["']bindroute["'][^>]*value=["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        val imgRegex = Regex(
            """<img[^>]*src=["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        val checkedRegex = Regex(
            """<input[^>]*name=["']bindroute["'][^>]*\bchecked\b""",
            RegexOption.IGNORE_CASE
        )

        return labelRegex.findAll(html).mapNotNull { label ->
            val value = valueRegex.find(label.value)?.groupValues?.getOrNull(1)?.trim()
            if (value.isNullOrBlank()) return@mapNotNull null
            val iconUrl = imgRegex.find(label.value)?.groupValues?.getOrNull(1)
                ?.trim()
                ?.let { toAbsoluteUrl(it) }
                ?: ""
            BankOption(
                code = value,
                iconUrl = iconUrl,
                checked = checkedRegex.containsMatchIn(label.value)
            )
        }.toList()
    }

    internal fun parseCheckStatus(json: String): BankCardBindStatus {
        return gson.fromJson(json, BankCardBindStatus::class.java)
    }

    internal fun parseBindResultHtml(html: String): BankCardBindResult {
        val title = extractMessage(html, "h2", "weui-msg__title")
        val desc = extractMessage(html, "p", "weui-msg__desc")
        if (title.isNullOrBlank() && desc.isNullOrBlank()) {
            throw RuntimeException("银行卡绑定结果页面结构已变更")
        }
        return BankCardBindResult(title = title.orEmpty(), desc = desc.orEmpty())
    }

    private fun extractMessage(html: String, tag: String, className: String): String? {
        val regex = Regex(
            """<$tag[^>]*class=["']$className["'][^>]*>(.*?)</$tag>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        return regex.find(html)?.groupValues?.getOrNull(1)
            ?.replace(Regex("""<[^>]*>"""), "")
            ?.replace("&nbsp;", " ")
            ?.trim()
            ?.ifBlank { null }
    }

    private fun toAbsoluteUrl(url: String): String {
        return when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("/") -> "$EPAY_BASE$url"
            else -> "$EPAY_BASE/$url"
        }
    }
}

/** 银行卡绑定页中的一个银行选项。 */
data class BankOption(
    val code: String,
    val iconUrl: String = "",
    val checked: Boolean = false,
)

/** checkexist 响应。chkflag="1" 表示已绑定。 */
data class BankCardBindStatus(
    val retmsg: String = "",
    val chkflag: String = "",
) {
    val isBound: Boolean get() = chkflag == "1"
}

/** dobind/dounbind 返回结果页中的标题与描述。 */
data class BankCardBindResult(
    val title: String,
    val desc: String,
) {
    val isSuccess: Boolean get() = title.contains("成功") || desc.contains("成功")
}
