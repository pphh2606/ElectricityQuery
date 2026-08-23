package edu.cqwu.electricity.home.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import edu.cqwu.electricity.R
import edu.cqwu.electricity.app.Routes
import edu.cqwu.electricity.logging.AppLog
import edu.cqwu.electricity.qrcode.data.QrCodeType
import edu.cqwu.electricity.webview.util.WebViewUrlUtil

/**
 * 首页应用点击 / 桌面快捷方式启动的统一分发结果。
 * 两条入口共用 [HomeAppLauncher.resolve]，保证行为一致。
 */
sealed class HomeAppLaunch {
    /** 进入原生界面，[route] 为 NavGraph 路由字符串 */
    data class Native(val route: String) : HomeAppLaunch()

    /** 在内置浏览器打开 http(s) 页面 */
    data class WebPage(val url: String, val title: String) : HomeAppLaunch()

    /** 打开外部应用（自定义 scheme，如 mamp://），需弹窗确认后执行 */
    data class External(val url: String, val name: String) : HomeAppLaunch()

    /** 无可执行动作（openUrl 为空且非已知原生应用） */
    data object DoNothing : HomeAppLaunch()
}

/**
 * 首页应用点击分发器：根据 appId / openUrl 解析出统一动作。
 * 与桌面快捷方式共用的唯一判定点，避免两处各自实现导致行为漂移。
 */
object HomeAppLauncher {

    fun resolve(appId: String, name: String, openUrl: String): HomeAppLaunch = when (appId) {
        HomeAppIds.PAY_QR -> HomeAppLaunch.Native(Routes.qrCodeRoute(QrCodeType.PAY))
        HomeAppIds.BUS_QR -> HomeAppLaunch.Native(Routes.qrCodeRoute(QrCodeType.BUS))
        HomeAppIds.BANK_CARD_BIND -> HomeAppLaunch.Native(Routes.BANK_CARD_BIND)
        HomeAppIds.DORM_ELECTRICITY -> HomeAppLaunch.Native(Routes.ELECTRICITY_MAIN)
        HomeAppIds.CARD_CENTER -> HomeAppLaunch.Native(Routes.CARD_CENTER)
        HomeAppIds.NOTICE -> HomeAppLaunch.Native(Routes.NOTICE)
        HomeAppIds.FEE_SERVICE_HALL -> HomeAppLaunch.Native(Routes.FEE_SERVICE_HALL)
        HomeAppIds.MY_INFO -> HomeAppLaunch.Native(Routes.MY_INFO)
        HomeAppIds.SPEAK_UP -> HomeAppLaunch.Native(Routes.SPEAK_UP)
        HomeAppIds.SCAN -> HomeAppLaunch.Native(Routes.SCAN)
        else -> when {
            openUrl.isBlank() -> HomeAppLaunch.DoNothing
            WebViewUrlUtil.isHttpScheme(openUrl) -> HomeAppLaunch.WebPage(openUrl, name)
            else -> HomeAppLaunch.External(openUrl, name)
        }
    }

    /**
     * 解析并执行应用启动动作，首页点击与桌面快捷方式共用：
     * 原生界面 / http(s) 内置浏览器 / 自定义 scheme 外部弹窗 / 无动作。
     *
     * @param navigate 原生路由与 WebView 路由的导航执行（接收路由字符串）
     * @param onExternal 外部打开动作回调 (url, name)，由调用方决定弹窗确认
     */
    fun launch(
        appId: String,
        name: String,
        openUrl: String,
        navigate: (String) -> Unit,
        onExternal: (url: String, name: String) -> Unit,
    ) {
        when (val action = resolve(appId, name, openUrl)) {
            is HomeAppLaunch.Native -> navigate(action.route)
            is HomeAppLaunch.WebPage -> navigate(Routes.unifiedWebViewRoute(action.url, action.title))
            is HomeAppLaunch.External -> onExternal(action.url, action.name)
            HomeAppLaunch.DoNothing -> {}
        }
    }
}

/**
 * 外部应用打开器：自定义 scheme 的外部 Intent 跳转。
 * 基础打开复用 [WebViewUrlUtil.openCustomSchemeUrl]（含 intent:// 解析），
 * 仅补充 mamp:// 降级与失败提示。
 */
object ExternalAppOpener {

    private const val TAG = "ExternalAppOpener"

    fun open(context: Context, appName: String, url: String, onFailure: (String) -> Unit) {
        if (WebViewUrlUtil.openCustomSchemeUrl(context, url, TAG)) return

        // mamp:// 降级：尝试 campusnextins:// 打开今日校园 App
        if (url.startsWith("mamp://")) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("campusnextins://")))
                return
            } catch (e: ActivityNotFoundException) {
                AppLog.w(TAG, "降级 campusnextins:// 也失败: ${e.message}")
            }
        }
        onFailure(context.getString(R.string.home_install_campus_app, appName))
    }
}
