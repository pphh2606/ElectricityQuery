package edu.cqwu.electricity.settings.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import edu.cqwu.electricity.R
import edu.cqwu.electricity.login.data.CredentialExporter
import edu.cqwu.electricity.login.data.SavedAccount
import edu.cqwu.electricity.login.domain.SessionCoordinatorV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 登录凭据（账号+密码）导出/导入的一次性结果事件 */
sealed interface CredentialTransferEvent {
    /** 导出成功：加密文本已复制到剪贴板 */
    data object ExportDone : CredentialTransferEvent

    /** 导入成功：[count] 个账号已作为新条目保存（不激活） */
    data class ImportDone(val count: Int) : CredentialTransferEvent

    /** 失败（口令错误 / 数据损坏等） */
    data class Error(val message: String) : CredentialTransferEvent
}

/**
 * 登录凭据（账号+密码，加密文件）的导出/导入 ViewModel（设置 → 备份与恢复使用）。
 *
 * 与 Cookie 备份的区别：凭据文件含密码（PBKDF2 + AES-GCM 加密），导出前建议配合
 * 系统锁屏验证；导入只把账号存为**新条目、不激活不切换**，用户之后到
 * 账号/登录设置页手动切换登录（侵入最小化）。
 */
class CredentialBackupViewModel(application: Application) : AndroidViewModel(application) {

    private val _events = Channel<CredentialTransferEvent>(Channel.BUFFERED)
    val events: Flow<CredentialTransferEvent> = _events.receiveAsFlow()

    /** 当前激活账号的用户名（导出对话框展示用）；未登录为空串 */
    fun currentUsername(): String = SessionCoordinatorV2.currentAccount()?.username.orEmpty()

    /** 导出：加密全量"记住密码"账号并复制到剪贴板。 */
    fun exportCredentials(exportPassword: String) {
        viewModelScope.launch {
            val encrypted = withContext(Dispatchers.IO) {
                runCatching {
                    val accounts = SessionCoordinatorV2.allAccounts()
                        .filter { it.rememberPassword && !it.password.isNullOrBlank() }
                        .map { it.username to it.password!! }
                    CredentialExporter.export(accounts, exportPassword)
                }
            }
            encrypted
                .onSuccess { text ->
                    val clipboard = getApplication<Application>()
                        .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("credential_export", text))
                    _events.send(CredentialTransferEvent.ExportDone)
                }
                .onFailure { e ->
                    _events.send(
                        CredentialTransferEvent.Error(text(R.string.login_error_export_failed, e.message ?: ""))
                    )
                }
        }
    }

    /** 导入：解密账密文件后把账号存为新条目（不激活、不动 Cookie 与当前登录态）。 */
    fun importCredentials(data: String, exportPassword: String) {
        viewModelScope.launch {
            val accounts = withContext(Dispatchers.IO) {
                runCatching { CredentialExporter.import(data, exportPassword) }
            }
            accounts
                .onSuccess { list ->
                    if (list.isNullOrEmpty()) {
                        _events.send(CredentialTransferEvent.Error(text(R.string.login_error_import_credential)))
                        return@launch
                    }
                    val drafts = list.map { (username, password) ->
                        SavedAccount(
                            id = "", // 导入时由 AccountSessionStore 重新生成
                            username = username,
                            password = password,
                            rememberPassword = true,
                        )
                    }
                    SessionCoordinatorV2.importCredentials(drafts)
                    _events.send(CredentialTransferEvent.ImportDone(count = list.size))
                }
                .onFailure { e ->
                    _events.send(
                        CredentialTransferEvent.Error(text(R.string.login_error_parse_credential, e.message ?: ""))
                    )
                }
        }
    }

    private fun text(@StringRes id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)
}
