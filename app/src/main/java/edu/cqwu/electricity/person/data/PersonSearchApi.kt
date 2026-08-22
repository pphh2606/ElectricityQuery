package edu.cqwu.electricity.person.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import edu.cqwu.electricity.logging.AppLog
import edu.cqwu.electricity.login.data.ServiceLoginManager
import edu.cqwu.electricity.login.data.SessionExpiredException
import edu.cqwu.electricity.payment.data.HttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URLEncoder

/**
 * 查找人员接口封装。
 *
 * 调用 ehall 通用人员选择组件 `choose_person.do`，按姓名关键字搜索校内人员。
 *
 * 流程与 [edu.cqwu.electricity.speakup.data.SpeakUpApi] 一致：
 * 1. 先通过 [ServiceLoginManager.ensureLogin] 完成 ehall CAS ticket 交换
 * 2. 再调用业务 API 获取人员列表
 *
 * 使用 [HttpClientFactory.shared]（共享 CookieJar，自动携带登录态 Cookie）。
 *
 * 响应结构（注意：该接口无统一 code 字段）：
 * ```
 * {"datas":{"data":{"pageSize":"20","pageNumber":"1","totalSize":6996.0,"rows":[
 *   {"name":"张袁","id":"20160059","deptCode":"405",
 *    "deptName":"继续教育学院、培训学院","sexName":"女","positions":null}
 * ]}}}
 * ```
 */
class PersonSearchApi {

    companion object {
        /** 人员搜索接口 */
        private const val CHOOSE_PERSON_URL =
            "https://ehall.cqwu.edu.cn/publicapp/sys/itservicecommon/widget/choose_person.do"

        /** 人员头像接口 */
        const val HEAD_PIC_URL =
            "https://ehall.cqwu.edu.cn/publicapp/sys/itservicecommon/common/headPic.do"

        /** 抓包中的实际 Referer（日程页） */
        private const val REFERER =
            "https://ehall.cqwu.edu.cn/publicapp/sys/rcglxt/mobile/myschedulenew/index.html"

        private const val TAG = "PersonSearchApi"

        /** 构建人员头像 URL */
        fun headPicUrl(personId: String): String = "$HEAD_PIC_URL?id=$personId"
    }

    private val gson = Gson()
    private val client get() = HttpClientFactory.shared

    /**
     * 分页搜索人员。
     *
     * @param keyword   姓名关键字（空串返回全部人员）
     * @param pageNumber 页码（从 1 开始）
     * @param pageSize   每页数量
     * @return 成功时返回 [PersonSearchResult]
     * @throws SessionExpiredException 用户未登录或 Cookie 过期
     */
    suspend fun search(
        keyword: String,
        pageNumber: Int = 1,
        pageSize: Int = 20,
    ): Result<PersonSearchResult> = withContext(Dispatchers.IO) {
        try {
            // 步骤 1：确保 ehall session 已初始化（choose_person 受 CAS 保护）
            ServiceLoginManager.ensureLogin(protectedUrl = CHOOSE_PERSON_URL)

            // 步骤 2：GET choose_person.do
            val url = buildString {
                append(CHOOSE_PERSON_URL)
                append("?SEARCHKEY=").append(URLEncoder.encode(keyword, "UTF-8"))
                append("&pageNumber=").append(pageNumber)
                append("&pageSize=").append(pageSize)
            }
            val request = Request.Builder()
                .url(url)
                .get()
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", REFERER)
                .build()

            AppLog.d(TAG, "GET $url")
            val response = client.newCall(request).execute()
            val body = response.body.string()

            val result = gson.fromJson(body, PersonSearchResponse::class.java)
            val page = result.datas?.data
                ?: throw RuntimeException("查找人员响应结构异常")

            val rows = page.rows ?: emptyList()
            val totalSize = page.totalSize
            val hasMore = pageNumber * pageSize < totalSize

            AppLog.d(TAG, "搜索成功: keyword=$keyword, page=$pageNumber, rows=${rows.size}, total=$totalSize")
            Result.success(PersonSearchResult(rows = rows, totalSize = totalSize.toInt(), hasMore = hasMore))
        } catch (e: SessionExpiredException) {
            AppLog.w(TAG, "Session 过期: ${e.message}")
            Result.failure(e)
        } catch (e: Exception) {
            AppLog.e(TAG, "查找人员请求失败", e)
            Result.failure(e)
        }
    }
}

/** 搜索返回结果 */
data class PersonSearchResult(
    val rows: List<PersonRow>,
    val totalSize: Int,
    val hasMore: Boolean,
)

/** 人员行数据 */
data class PersonRow(
    @SerializedName("name") val name: String? = null,
    /** 工号/学号 */
    @SerializedName("id") val id: String? = null,
    @SerializedName("deptCode") val deptCode: String? = null,
    /** 部门/学院 */
    @SerializedName("deptName") val deptName: String? = null,
    /** 性别 */
    @SerializedName("sexName") val sexName: String? = null,
    /** 职务（可为 null） */
    @SerializedName("positions") val positions: String? = null,
)

/** choose_person.do 顶层响应 */
private data class PersonSearchResponse(
    @SerializedName("datas") val datas: PersonSearchDatas? = null,
)

private data class PersonSearchDatas(
    @SerializedName("data") val data: PersonSearchPage? = null,
)

private data class PersonSearchPage(
    @SerializedName("rows") val rows: List<PersonRow>? = null,
    /** JSON 中为数字（6996.0），用 Double 容错 */
    @SerializedName("totalSize") val totalSize: Double = 0.0,
)
