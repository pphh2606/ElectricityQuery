# 更改日志

## 架构改进
- 之前 `CasAuthApi`、`QrLoginApi`、`SessionChecker` 各自写了一套从 HTML 里提取表单字段的代码，现在统一收到 `HtmlFormParser` 一个地方，谁要用直接调就行
- 之前项目里有 6 处各自创建 `OkHttpClient` 的代码，配置不统一还容易遗漏，现在统一用 `HttpClientFactory` 工厂来创建，默认带好 IPv4 优先 DNS 和 UserAgent 拦截器
- 之前验证 Cookie 是否有效、检测 CAS 登录页、执行 CAS ticket 交换这三个能力分散在 `SessionValidator`、`SessionChecker`、`CampusphereApi` 三个类里，现在全部收拢到 `SessionManager`，一个类搞定所有会话相关操作
- `AccountManager` 从 `CookieStore.kt` 里独立出来成为单独文件，新增 `commitLoginCookies()` 方法，登录成功后一次性把临时 Cookie 迁移到正式存储，不再手动逐条复制
- `SessionValidator` 和 `SessionChecker` 保留原接口不变，但内部实现委托给新的 `SessionManager` 和 `HtmlFormParser`，老代码不用改
- `UserAgentInterceptor` 从独立文件挪到 `UserAgentProvider.kt` 里，少一个文件
- `SharedHttpClient` 改成 `HttpClientFactory` 的委托包装器，保持老代码兼容
- 之前尝试的 `api/`、`auth/`、`cookie/`、`crypto/` 子目录拆分方案撤回，保持 `data/network/` 扁平结构

## Bug 修复
- 之前 `CasAuthApi.loginForUser()` 登录时直接用持久化的 Cookie 存储，登录失败后脏 Cookie 会残留，下次登录可能出问题；现在每次登录创建全新的临时 `UserCookieStore`，登录失败自动丢弃，登录成功才通过 `commitLoginCookies()` 提交到正式存储
- 之前扫码登录成功后用 `for` 循环逐条复制 Cookie 到 `AccountManager`，新旧 Cookie 可能混杂；现在改用 `commitLoginCookies()` 先清空再批量写入，保证干净
- `LoginViewModel` 移除了登录流程中大量调试用的诊断日志，代码更清爽

## 删除的文件
- `SessionChecker.kt` — 逻辑合并到 `SessionValidator.kt` + `HtmlFormParser`
- `SessionExpiredException.kt` — 合并到 `SessionValidator.kt`
- `UserAgentInterceptor.kt` — 合并到 `UserAgentProvider.kt`
- `CasAuthApi.kt` 里的 `PreferIPv4Dns`、`SharedHttpClient` — 迁移到 `HttpClientFactory`
- `CasAuthApi.kt` 和 `QrLoginApi.kt` 里的 `extractRegex`、`extractInputValue` 方法 — 迁移到 `HtmlFormParser`
- `CookieStore.kt` 里的 `AccountManager`、`AutoSwitchResult` — 独立为 `AccountManager.kt`
- `LoginRepository` 中间层去掉 — `LoginViewModel` 改为直接调用 `CasAuthApi`

## 依赖变更
- `VERSION_CODE` 从 1249 升至 1253
- `.gitignore` 新增 `/backup` 目录忽略
