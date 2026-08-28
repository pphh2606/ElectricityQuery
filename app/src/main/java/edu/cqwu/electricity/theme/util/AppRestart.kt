package edu.cqwu.electricity.theme.util

import android.content.Context
import android.content.Intent
import edu.cqwu.electricity.app.MainActivity
import kotlin.system.exitProcess

/**
 * 重启应用：清空任务栈重建 MainActivity 后结束当前进程。
 *
 * 供修改密码后刷新登录状态、清除存储后生效等场景复用。
 */
fun restartApp(context: Context) {
    val intent = Intent(context, MainActivity::class.java)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(intent)
    exitProcess(0)
}
