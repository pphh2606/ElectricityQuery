package edu.cqwu.electricity.data.network

/**
 * API 配置文件，对应 Python 版 electricity_query_modified.py 的配置区
 */
object ApiConfig {

    const val BASE_URL = "https://electricitypay.cqwu.edu.cn"
    const val BUILDING_API = "$BASE_URL/wechat/wx/wechatNode/getAddrByNode"
    const val BALANCE_API = "$BASE_URL/wechat/wx/wechatData/getLeftValue"
    const val SIX_MONTH_API = "$BASE_URL/wechat/wx/wechatData/getSixMonthValue"
    const val MONTH_DAILY_API = "$BASE_URL/wechat/wx/wechatData/getRoomUsedData"
    const val CURRENT_DATA_API = "$BASE_URL/wechat/wx/wechatData/getCurrentData"
    const val RECHARGE_API = "$BASE_URL/wechat/wx/getCQPayOrder"

    // ========== 办事大厅 API ==========
    /**
     * 办事大厅受保护页面 URL（用于触发 CAS ticket 交换，建立 ehall JSESSIONID）。
     *
     * 必须是一个需要认证的页面（返回 302 重定向到 CAS），
     * 而非静态页面（如 index.html 返回 200，不触发 CAS 认证链）。
     * 通过 followRedirects=true 自动完成 CAS 重定向链：
     *   ehall → CAS → ehall（带 ticket）→ ehall 设置已认证 JSESSIONID
     */
    const val EHALL_APP_SHOW_URL = "https://ehall.cqwu.edu.cn/appshow"
    /** 用户收藏应用列表 API */
    const val FAVORITE_APPS_URL = "https://ehall.cqwu.edu.cn/jsonp/userFavoriteApps.json"
    /** 收藏单个应用的 API */
    const val FAVORITE_APP_URL = "https://ehall.cqwu.edu.cn/jsonp/favoriteApp"
    /** 取消收藏单个应用的 API */
    const val UNFAVORITE_APP_URL = "https://ehall.cqwu.edu.cn/jsonp/unFaviroteApp"
    /** 服务大厅数据中心 API（全部应用列表，含 favorite/favoriteCount 信息） */
    const val SERVICE_CENTER_DATA_URL = "https://ehall.cqwu.edu.cn/jsonp/serviceCenterData.json"

    // ========== 账号充值（学号模式）API ==========
    const val ROOM_LIST_API = "$BASE_URL/wechat/wx/findUserRoomList"

    // ========== 充值记录查询 API ==========
    const val GET_USER_API = "$BASE_URL/wechat/wx/getWechatUserByOpenId"
    const val BUY_LIST_API = "$BASE_URL/wechat/wx/wechatData/getRoomBuyList"

    // ==================== 支付网关 API ====================
    const val PAY_CASHIER_API = "https://pay.cqwu.edu.cn/pay/cashier"

    // ========== CAS 统一认证登录 ==========
    /** CAS 统一认证登录页 */
    const val LOGIN_URL = "https://authserver.cqwu.edu.cn/authserver/login"

    // ========== EPay 服务（支付码/乘车码）==========
    /** 支付码页面 URL，由 QrCodeApi 通过 followRedirects 自动完成 ticket 交换获取 */
    const val TARGET_URL = "http://218.194.176.214:8382/epay/thirdconsume/qrcode"

    /**
     * 默认请求头。
     *
     * **注意：** `User-Agent` 已改由 [UserAgentProvider] 动态管理，
     * 通过 [UserAgentInterceptor] 自动注入到所有 OkHttp 请求中。
     * 此处保留的 `User-Agent` 仅作为兜底默认值，实际请求中以拦截器为准。
     */
    val HEADERS: Map<String, String> = mapOf(
        "Accept" to "*/*",
        "sec-ch-ua" to "\"Android WebView\";v=\"147\", \"Not.A/Brand\";v=\"8\", \"Chromium\";v=\"147\"",
        "sec-ch-ua-mobile" to "?1",
        "sec-ch-ua-platform" to "\"Android\"",
        "Origin" to "https://electricitypay.cqwu.edu.cn",
        "Referer" to "https://electricitypay.cqwu.edu.cn/wxms/pages/user/user-add"
    )

    const val RSA_PUBLIC_KEY = """-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAsJEc7CIxbt5cPn3umQyO7Eu+ALarLPEE
vaZUY+adwzTlKeiBPYukjimpfKoqJjcdqg6hffLIKCcKRN9PTFi8Y8324+e6g37jC0ILUlXYdvQM
I8ftnXjROAioEK/rWClgY4eYFtURo5ytobco8CKwKvnDKrj/u7eExoWXUxvC0VKgz0Q8oKuh7UAM
BwVAvuBW6g6nIRqpC+pLFvzZegvNdjbwZZ2MekmsG6IdB8GDUc6ut1M14zojIIfI+NRStJ03EgjV
HqeNpuiR5bv98kgpnedLGfAFnMAxnIz2HKutbi0fWl4VhHqfApQoJZ16zi/R5WwJpxYDpxL/NAiW
P/S2OQIDAQAB
-----END PUBLIC KEY-----"""
}
