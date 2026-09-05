## 新增功能
1. 设置页备份与恢复新增登录凭据导入导出：账号加密码以加密文本导出并复制到剪贴板，导出前沿用系统锁屏验证，解密导入的账号作为新条目保存并保留密码，需到账号或登录设置页手动切换登录，不触碰系统Cookie与当前登录态
2. 登录页其他登录方式弹窗原凭据登录入口改为导入Cookie或账密：点击直达设置备份与恢复页统一操作，登录页三点菜单及凭据就地导入导出流程整体移除，凭据与Cookie备份入口收拢到同一页面

## Bug 修复
1. 修复切换账号或清除登录态时系统Cookie从未被真正清除：CookieStore 的 removeAllCookies 原先在无 Looper 的后台线程调用 WebKit 接口会抛异常且被静默吞掉，现统一投递主线程执行并同步等待删除与 flush 完成，超时与异常均记录诊断日志

## 架构改进
1. 登录会话提交入口收敛为统一门面：AccountSessionStore.commitLogin 改为私有，账密与扫码登录成功路径统一经 SessionCoordinatorV2 提交 AuthSessionCommitV2 域值对象原子激活，杜绝旁路调用
2. WebVPN 自动登录改由组合根直连网络层：AutoLoginCoordinatorV2 移除 ensureWebVpn 转发层，ElectricityApp 直接注入 WebVpnSessionManager 认证回调，协调器仅保留业务二级站服务会话职责
3. 登录凭据导出导入逻辑自登录页迁入设置域：新增 CredentialBackupViewModel 承载加密导出、解密导入与一次性事件通知，导入导出对话框由 login.ui 包迁移至 settings.ui 包，登录页 LoginViewModel 同步瘦身
4. 移除未接线的会话状态发布机制：AccountSessionStore 不再维护会话状态 StateFlow，激活账号维度状态只由持久层单一数据源反映

## 改进
1. 为十余处静默或降级的异常捕获补全诊断日志：CookieStore、二维码生成、日期解析、下载探测、更新请求、WebView 深色模式、favicon 地址生成、URL 校验与设置解析等 catch 分支统一记录 AppLog，异常不再无声吞掉
2. 消除硬编码中文文案并补齐多语言：电费房间学号未就绪的失败提示改为专用字符串资源并在八个语言包同步新增，MyRoomViewModel 不再以内嵌文案参数拼装错误消息

## 删除的文件
1. 删除未接线的会话状态流模型文件 SessionStateV2.kt：会话状态 StateFlow 无任何订阅方，随登录域收敛一并移除避免死代码
