package edu.cqwu.electricity.common.navigation

/**
 * 全项目路由表：路由常量 + 路由拼装函数。
 *
 * 从 `NavGraph.kt` 拆出——路由**注册**已按模块分散到各模块根目录的 `XxxNavigation.kt`，
 * 这里只保留路由本身的定义，跳转处仍写 `Routes.XXX`，引用方式不变。
 */
object Routes {
    /** 主页 Tab 容器（HorizontalPager：首页 + 我的） */
    const val MAIN_TABS = "main_tabs"
    const val SETTINGS = "settings"
    /** 账号管理页（独立页面，代码位于 accountmanagerv2 包） */
    const val ACCOUNT_MANAGER = "account_manager"
    /** 修改用户名页（登录别名 + 昵称） */
    const val USER_NAME_EDIT = "user_name_edit"
    /** 修改密码页 */
    const val PASSWORD_CHANGE = "password_change"
    /** 登录设备管理页（在线会话查看/踢出） */
    const val DEVICE_SESSION = "device_session"
    /** 日志记录页（登录/维护日志查看与筛选） */
    const val LOGIN_LOG = "login_log"
    const val PERSONALIZATION = "personalization"
    const val QR_CODE_SETTINGS = "qr_code_settings"
    const val QR_LOGIN = "qr_login"
    const val WEBVPN_SETTINGS = "webvpn_settings"
    const val ELECTRICITY_MAIN = "electricity_main"
    const val DETAIL = "detail/{detailType}/{roomId}"
    const val PAYMENT_SELECTION = "payment_selection"
    const val RECHARGE_RECORD = "recharge_record/{roomId}"
    const val USAGE_RECORD = "usage_record/{roomId}"
    const val SUBSIDY_RECORD = "subsidy_record/{roomId}"

    /** 扫码页面 */
    const val SCAN = "scan"

    /** 本地登录页面（支持可选 accountId 参数，预填指定账号条目） */
    const val LOGIN = "login?accountId={accountId}"

    /** 构造登录页路由；accountId 为空时登录页自动填充当前激活条目 */
    fun loginRoute(accountId: String = ""): String = "login?accountId=$accountId"
    /** 从内置浏览器进入的本地登录页面（返回时同时关闭 WebView） */
    const val WEBVIEW_LOGIN = "webview_login"
    /** 添加新账号（空白表单） */
    const val NEW_ACCOUNT_LOGIN = "new_account_login"

    /** Cookie 过期自动跳转登录（带从下往上覆盖动画） */
    const val COOKIE_EXPIRED_LOGIN = "cookie_expired_login"

    /** 通用内置浏览器路径 */
    const val UNIFIED_WEBVIEW = "unified_webview/{url}/{title}"

    /** 卡中心本地 UI 页面 */
    const val CARD_CENTER = "card_center"

    /** 学生绑定银行卡本地 UI 页面 */
    const val BANK_CARD_BIND = "bank_card_bind"

    /** 校园卡充值 — 学号输入+金额选择页面 */
    const val CARD_RECHARGE = "card_recharge"

    /** 校园卡充值 — 支付执行页面 */
    const val CARD_PAYMENT = "card_payment"

    /** 账户信息本地 UI 页面 */
    const val ACCOUNT_INFO = "account_info"

    /** 卡挂失本地 UI 页面 */
    const val CARD_LOST = "card_lost"

    /** 账单本地 UI 页面 */
    const val BILL = "bill"

    /** 通知公告本地 UI 页面 */
    const val NOTICE = "notice"

    /** 通知公告详情本地 UI 页面 */
    const val NOTICE_DETAIL = "notice_detail/{wid}"

    /** 构建通知详情路由 */
    fun noticeDetailRoute(wid: String): String {
        return "notice_detail/$wid"
    }

    /** 二维码显示页面路径 */
    const val QR_CODE = "qr_code/{qrCodeType}"

    /** 构建二维码页面路径 */
    fun qrCodeRoute(type: QrCodeType): String {
        return "qr_code/${type.name}"
    }

    /** H5 充值统一认证地址 */
    const val H5_RECHARGE_URL = "https://authserver.cqwu.edu.cn/authserver/login?service=https%3A%2F%2Felectricitypay.cqwu.edu.cn%2Fwechat%2Fwx%2Fauth%2Flogin"

    /** H5 WebView 路由（URL 固定，无需参数）*/
    const val RECHARGE_H5_WEBVIEW = "recharge_h5_webview"

    /** 构建充值记录页路径 */
    fun rechargeRecordRoute(roomId: String): String {
        return "recharge_record/$roomId"
    }

    /** 构建用量报表页路径 */
    fun usageRecordRoute(roomId: String): String {
        return "usage_record/$roomId"
    }

    /** 构建补助记录页路径 */
    fun subsidyRecordRoute(roomId: String): String {
        return "subsidy_record/$roomId"
    }

    /** 构建详情页路径 */
    fun detailRoute(detailType: DetailType, roomId: String): String {
        return "detail/${detailType.name.lowercase()}/$roomId"
    }

    /** 备份与恢复子页 */
    const val SETTINGS_BACKUP_RESTORE = "settings_backup_restore"

    /** 设置备份：导出页 */
    const val SETTINGS_BACKUP_EXPORT = "settings_backup_export"

    /** 设置备份：导入页 */
    const val SETTINGS_BACKUP_IMPORT = "settings_backup_import"

    /** Cookie 备份：导出页 */
    const val SETTINGS_COOKIE_EXPORT = "settings_cookie_export"

    /** Cookie 备份：导入页 */
    const val SETTINGS_COOKIE_IMPORT = "settings_cookie_import"

    /** 关于页 */
    const val ABOUT = "about"

    /** 配置页 */
    const val CONFIG = "config"

    /** 浏览器标识设置页 */
    const val USER_AGENT_SETTINGS = "user_agent_settings"

    /** 编辑/添加浏览器标识页 */
    const val USER_AGENT_EDIT = "user_agent_edit/{entryId}"

    /** 构建编辑浏览器标识页路由 */
    fun userAgentEditRoute(entryId: String): String = "user_agent_edit/$entryId"

    /** 意见反馈页 */
    const val FEEDBACK = "feedback"

    /** 构建通用内置浏览器路由（url、title 需 URL 编码）*/
    fun unifiedWebViewRoute(url: String, title: String = ""): String {
        return "unified_webview/${java.net.URLEncoder.encode(url, "UTF-8")}/${java.net.URLEncoder.encode(title, "UTF-8")}"
    }

    /** 缴费服务大厅 */
    const val FEE_SERVICE_HALL = "fee_service_hall"

    /** 缴费服务大厅 — 直接跳转到订单 tab */
    const val FEE_SERVICE_HALL_ORDERS = "fee_service_hall_orders"

    /** 我的信息（原生页面） */
    const val MY_INFO = "my_info"

    /** 添加快捷方式 */
    const val ADD_SHORTCUT = "add_shortcut"

    /** 清除存储空间 */
    const val STORAGE_CLEAR = "storage_clear"

    /** 有话要说 — 咨询区列表 */
    const val SPEAK_UP = "speak_up"

    /** 有话要说 — 留言列表 */
    const val SPEAK_UP_MESSAGES = "speak_up_messages/{areaCode}/{areaName}"

    /** 有话要说 — 留言详情 */
    const val SPEAK_UP_DETAIL = "speak_up_detail/{wid}"

    /** 构建留言列表路由 */
    fun speakUpMessagesRoute(areaCode: String, areaName: String): String {
        return "speak_up_messages/$areaCode/${java.net.URLEncoder.encode(areaName, "UTF-8")}"
    }

    /** 构建留言详情路由 */
    fun speakUpDetailRoute(wid: String): String {
        return "speak_up_detail/$wid"
    }

    /** 查找人员（原生页面） */
    const val PERSON_SEARCH = "person_search"

    /** 校园网络（campusnetwork 模块，入口页） */
    const val CAMPUS_NETWORK = "campus_network"

    /** 校园网络 — 接入者信息 */
    const val CAMPUS_NETWORK_ACCESSOR_INFO = "campus_network_accessor_info"

    /** 校园网络 — 校内网络测速 */
    const val CAMPUS_NETWORK_SPEED_TEST = "campus_network_speed_test"

    /** 校园网络 — 网络服务（认证网关 eportal 本地化页） */
    const val CAMPUS_NETWORK_PORTAL_SERVICE = "campus_network_portal_service"

    /** 教务首页（jwfw /jwmobile，本地化） */
    const val JWXT_HOME = "jwxt_home"

    /** 教务「更多服务」（首页九宫格的二级页，本地化） */
    const val JWXT_MORE_SERVICE = "jwxt_more_service"
}
