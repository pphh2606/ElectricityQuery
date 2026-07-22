# 更改日志

## 架构改进
- `ServiceLoginManager` 登录流程从 OkHttp 自动跟随重定向全面改写为手动逐步跟踪 302 重定向链，适配学校 IAP 网关认证机制（原 CAS 标准 ticket 交换流程已被 IAP 中间页面阻断），确保第三方服务 session Cookie 获取成功
- `ensureLogin` 改用 `followRedirects=false` 的 OkHttpClient 配合 `CookieStoreOkHttpJar` 桥接 CookieManager，手动控制每一步 302 跳转，避免被 IAP 的 JS 中间页面（如 api.campushoy.com）阻断登录链路
- 新增 `resolveUrl()` 方法统一处理绝对 URL、相对路径和协议相对 URL 的重定向地址解析，替换原先依赖 OkHttp 内部解析的隐式逻辑
- 新增 `extractJsRedirect()` 防御性方法，通过正则匹配 `window.location.href` 等 JS 跳转模式，作为 HTTP 302 重定向的兜底检测
- `ensureLogin` 新增 CAS 登录页检测（通过 `SessionManager.isCasLoginPage` 判断响应体），当 CASTGC Cookie 失效时立即抛出 `SessionExpiredException` 而非静默失败
- 重定向链增加 `MAX_REDIRECTS=10` 上限保护和 4xx/5xx 终止逻辑，防止无限循环；新增 `IOException` 声明让调用方可以区分网络错误与会话过期

## 依赖变更
- VERSION_CODE 从 1417 升至 1420
