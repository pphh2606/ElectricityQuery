package edu.cqwu.electricity.common.util

import android.content.Context
import android.content.Intent
import kotlin.system.exitProcess

/**
 * 重启应用：清空任务栈重建启动页后结束当前进程。
 *
 * 供修改密码后刷新登录状态、清除存储后生效等场景复用。
 *
 * 启动 Intent 用 [android.content.pm.PackageManager.getLaunchIntentForPackage] 取，而不是硬编码
 * `MainActivity` 类——后者会让 theme 这个共享包反向依赖 app 模块。
 */
fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(intent)
    exitProcess(0)
}
