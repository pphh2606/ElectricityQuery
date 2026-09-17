package edu.cqwu.electricity.jwxt.data

/**
 * 教务移动端地址常量。
 *
 * 系统为金智教育 easy-mobile（`https://jwfw.cqwu.edu.cn/jwmobile`），接口与网页同源：
 * - 业务接口：`$BASE/biz/` 下的 JSON 接口（需要 `Authorization: <JWT>` 请求头）
 * - 登录入口：`$AUTH_INDEX_URL` —— 它就是教务在 CAS 上登记的 `service`（抓包实证），
 *   访问它会一路 302：CAS 发 ticket → 教务验票建会话 → 302 到 `$INDEX_URL#/?token=<JWT>`。
 *
 * 详见 `docs/教务首页-仿写与接口对接方案.md` 与 `plans/jwxt-home-localization-plan.md`。
 */
object JwxtConstants {

    /** 移动教务根地址 */
    const val BASE = "https://jwfw.cqwu.edu.cn/jwmobile"

    /** 首页地址；同时作为 token（Authorization cookie）的存取位置，与网页端一致 */
    const val INDEX_URL = BASE + "/index"

    /** 登录入口 = CAS 的 service（抓包实证） */
    const val AUTH_INDEX_URL = BASE + "/auth/index"

    /** 服务端图标相对路径的前缀（接口返回形如 `icon/service/cjcx@2x.png`） */
    const val STATIC_PREFIX = BASE + "/static/"

    /**
     * `classTransferTypeCode` 的前两位（接口原文：`00:正常 01：调课 02：停课 03：补课`）。
     * 网页端据此在课程名前显示「调」/「补」标签：ViewModel 取码，UI 层映射成文案。
     */
    const val TRANSFER_TYPE_ADJUST = "01"
    const val TRANSFER_TYPE_MAKEUP = "03"

    /** 首页九宫格里「成绩查询」的服务标识（接口 `serviceKey`）；本项目已把它本地化，首页据此改跳本地页 */
    const val SERVICE_KEY_SCORE = "KW.CJCX_V5"

    /** 承载首页服务聚合与「更多服务」的应用 id（`api/v1/anon/app/list` 中的 index 应用） */
    private const val APP_ID_INDEX = "index"

    /** 通知中心所属应用 id（app/list 中的 newsCenterApp 应用） */
    private const val APP_ID_NEWS_CENTER = "newsCenterApp"

    /** 成绩查询所属应用 id（首页九宫格 `serviceKey = KW.CJCX_V5` 那一项） */
    private const val APP_ID_SCORE = "kwApp"

    /**
     * 把子应用的 hash 路由拼成完整网页地址。
     *
     * 规则来自抓包 JS —— 全能页 `p__OmnipotentPage` 的微前端路由注册函数：
     * ```
     * e.path = "/" + appId + e.path      // 即在子应用自己的路由前面拼上 appId
     * ```
     * 所以最终地址形如 `#/<appId>/<path>`。例：成绩查询 `appId=kwApp`、接口返回 `url=#/cjcx_v5`
     * → `…/jwmobile/index#/kwApp/cjcx_v5`（与实测地址一致）。
     *
     * 同一结构在服务端下发的 `assets/home_apps.json` 里也能印证：「我的课表」的
     * `openUrl` 是 `auth/index?service=index/home/timetable/newTimetable`，
     * 即 `index` 应用 + `home/timetable/newTimetable` 路由。
     *
     * @param appId 接口返回的 `appId`（`JwxtService.appId`）
     * @param hashRoute 接口返回的 `url`，形如 `#/cjcx_v5`（开头的 `#`/`/` 会被规整掉）
     */
    fun pageUrl(appId: String, hashRoute: String): String {
        val path = hashRoute.removePrefix("#").trimStart('/')
        return if (appId.isBlank()) {
            INDEX_URL + "#/" + path
        } else {
            INDEX_URL + "#/" + appId + "/" + path
        }
    }

    /** 把接口返回的相对路径图标补成完整地址；已是完整 URL 的原样返回 */
    fun iconUrl(path: String): String =
        if (path.startsWith("http")) path else STATIC_PREFIX + path

    /** 「更多服务」聚合页：index 应用的 `moreService` 路由 */
    val MORE_SERVICE_URL: String = pageUrl(APP_ID_INDEX, "#/home/service/more")

    /** 成绩查询网页：kwApp 应用的 `cjcx_v5` 路由（成绩页顶栏「网页版」打开它） */
    val SCORE_URL: String = pageUrl(APP_ID_SCORE, "#/cjcx_v5")

    /** 单条成绩的详情网页 `#/cjcx_v5/cjxq?id=…`（抓包实证的地址形态），同样交给内置浏览器打开 */
    fun scoreDetailUrl(id: String): String = pageUrl(APP_ID_SCORE, "#/cjcx_v5/cjxq?id=$id")

    /** 通知中心：newsCenterApp 应用的 `NewsCenter` 路由 */
    val NOTICE_CENTER_URL: String = pageUrl(APP_ID_NEWS_CENTER, "#/center")
}
