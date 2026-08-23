## 新增功能
- 账号管理支持一键免密切换与预填登录。点击已有账号先验证 CAS 登录态，有效则原子切换无需重新登录，失效则跳转登录页预填该学号，登录路由新增可选 username 参数。
- 应用启动自动恢复上次登录会话。启动时把当前激活账号的持久化 cookie 写回系统 CookieManager，退出登录接口预留在 LogoutApi 中，服务端暂无接口时记录日志跳过。
## Bug 修复
- 修复账号切换与删除后旧登录态残留。新增 SessionCleaner 统一清空系统 Cookie 与 WebView DOM 存储，CookieStore.removeAllCookies 改用 CountDownLatch 同步等待清除完成。
- 修复关于页交互与显示问题。联系方式点击后弹窗保持打开，构建信息不再依据构建来源显示 CI 标识。
## 架构改进
- 登录会话收敛为单一持久化仓库。AccountSessionStore 统一加密存储账号、密码与登录态 cookie 并记录激活账号，替代原 AccountStore 与 AccountManager，登录态提交与切换改为原子操作，成功前不改动当前登录态。
- 扫码登录与服务登录态统一走持久化提交。QrLoginApi 返回临时 Cookie 存储交由上层 commitLogin 激活，WebVPN 自动登录与 ticket 交换后的 cookie 合并进当前激活账号。
- 系统 CookieManager 只反映当前激活账号。HttpClientFactory 移除按用户创建客户端逻辑，CampusphereApi 改用共享客户端，WebVpnSessionManager 移除 Context 依赖。
- 设置页存储清理语义重构。WebView 清理覆盖 app_webview 真实数据目录与 DOM 存储，登录信息和 cookie 清理保留账号密码，相关多语言文案同步更新。
## 删除的文件
- 删除 AccountManager.kt 与 AccountStore.kt。原多用户 Cookie 管理与账号密码持久化逻辑并入 AccountSessionStore。
