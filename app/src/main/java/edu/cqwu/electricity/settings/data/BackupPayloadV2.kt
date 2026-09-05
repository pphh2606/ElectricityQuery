package edu.cqwu.electricity.settings.data

import android.content.Context
import androidx.annotation.StringRes
import edu.cqwu.electricity.R
import edu.cqwu.electricity.login.data.AccountSessionStore
import edu.cqwu.electricity.login.data.CookiesBackup

/**
 * 备份载荷：把"内容来源/目标"与"页面 UI"解耦，
 * 使同一套导出/导入页面可承载不同数据类型（设置项 / Cookie 登录态；未来可扩展密码）。
 */
interface BackupPayloadV2 {
    /** 生成当前导出内容文本 */
    fun exportText(context: Context): String

    /** 校验并把备份写回；true=成功写回（或合法空数据），false=内容非法未写入 */
    fun importJson(context: Context, json: String): Boolean

    /** 导入成功后是否需要重启应用（设置项需重启刷新内存；Cookie 追加无需） */
    val restartOnImport: Boolean

    @get:StringRes val exportTitleRes: Int
    @get:StringRes val importTitleRes: Int
    val fileName: String
}

/** 设置项备份 */
object SettingsBackupPayloadV2 : BackupPayloadV2 {
    override fun exportText(context: Context): String = SettingsBackup.exportJson(context)

    override fun importJson(context: Context, json: String): Boolean =
        SettingsBackup.importJson(context, json)

    override val restartOnImport: Boolean = true
    override val exportTitleRes: Int = R.string.settings_backup_export_title
    override val importTitleRes: Int = R.string.settings_backup_import_title
    override val fileName: String = "settings_backup.json"
}

/** 账号 Cookie（登录态）备份：追加导入、不动当前账号 */
object CookiesBackupPayloadV2 : BackupPayloadV2 {
    override fun exportText(context: Context): String = CookiesBackup.exportJson()

    override fun importJson(context: Context, json: String): Boolean {
        val drafts = CookiesBackup.decode(json) ?: return false
        AccountSessionStore.importAccounts(drafts)
        return true
    }

    override val restartOnImport: Boolean = false
    override val exportTitleRes: Int = R.string.settings_cookie_export_title
    override val importTitleRes: Int = R.string.settings_cookie_import_title
    override val fileName: String = "cookies_backup.json"
}
