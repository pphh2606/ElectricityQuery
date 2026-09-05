package edu.cqwu.electricity.login.domain

import edu.cqwu.electricity.login.data.AccountSessionStore
import edu.cqwu.electricity.login.data.SavedAccount
import edu.cqwu.electricity.login.model.AuthSessionCommitV2

/**
 * 会话协调器 V2（domain 门面）：账号/会话的**读写**统一从这里发起，
 * 内部委托 [AccountSessionStore]。UI/ViewModel 等上层一律经本门面访问，
 * 直接操作 [AccountSessionStore] 仅保留给 login/data 层内部实现使用。
 */
object SessionCoordinatorV2 {

    /** 当前激活账号条目 */
    fun currentAccount(): SavedAccount? = AccountSessionStore.getActiveAccount()

    /** 全部账号条目（按最后登录时间降序） */
    fun allAccounts(): List<SavedAccount> = AccountSessionStore.getAllAccounts()

    /** 按条目 id 取账号 */
    fun accountById(accountId: String): SavedAccount? = AccountSessionStore.getAccountById(accountId)

    /** 登录成功后提交会话并原子激活（账密/扫码统一入口） */
    fun commitAndActivate(input: AuthSessionCommitV2) = AccountSessionStore.commitSession(input)

    /** 切换到指定条目（内部会清空系统 Cookie 后写入该账号会话） */
    fun activate(accountId: String) = AccountSessionStore.activate(accountId)

    /** 启动时恢复当前账号登录态到系统 CookieManager */
    fun restoreActive() = AccountSessionStore.restoreActiveSession()

    /** 删除账号条目（若为激活条目会回到未登录） */
    fun delete(accountId: String) = AccountSessionStore.deleteAccount(accountId)

    /** 清除所有账号登录状态（保留账号与密码） */
    fun clearLoginStates() = AccountSessionStore.clearAllLoginStates()

    /** 清空全部账号数据与登录态 */
    fun clearAll() = AccountSessionStore.clearAllData()

    /** 追加导入 Cookie 备份账号（新 UUID、不激活） */
    fun importAccounts(drafts: List<SavedAccount>) = AccountSessionStore.importAccounts(drafts)

    /** 追加导入账密凭据账号（保留密码、新 UUID、不激活） */
    fun importCredentials(drafts: List<SavedAccount>) = AccountSessionStore.importCredentials(drafts)
}
