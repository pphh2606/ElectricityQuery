# 登录域惯例化重构 · 实施计划（dev 分支）

> 原则：界面观感不变；行为优先、小步提交、每步可编译可回归；复用现有实现，只做结构收敛；优先纯领域/低风险改动。
> 本文件跟随 dev 分支推进，供逐阶段追踪；若需要公开到 main 可再归档。

## 目标架构（最终形态，分阶段达成）

```
login/
├─ model/   纯数据与领域模型（Account/SessionStateV2，无 Android 依赖）
├─ data/    存取实现（EncryptedStore、CookieJar、AccountSessionStore 保留为底层）
├─ domain/  业务门面（AuthRepository、SessionManager、AutoLoginCoordinator、QrLoginCoordinator）
└─ ui/      无状态 Screen + ViewModel（复用现有 UI）
```

依赖方向：ui → domain → data/model。敏感物只在最内层。

## 分阶段清单（每阶段独立提交，编译 + 全量单测通过后提交）

### Stage 1：会话状态建模先行
- 新增 `login/model/SessionStateV2.kt`：sealed `LoggedOut / Active(accountId, username, hasCookies)` +
  纯函数 `sessionStateOf(account?)`（无 Android 依赖，可 JVM 单测）。
- `AccountSessionStore` 暴露 `sessionState: StateFlow<SessionStateV2>`，在激活/登出清 cookie/删除/清空/合并等入口统一发布状态（仅加一行调用，不改既有签名）。
- 单测：`sessionStateOf` 覆盖无账号/激活/无 cookie 三态。

### Stage 2：会话提交模型化（登录路径去重）
- 新增 `login/model/AuthSessionCommitV2`（username/password/remember/cookies/studentId 的领域值对象）。
- `AccountSessionStore.commitSession(input)` 作为统一提交入口；账密、扫码两条登录成功路径改用该模型提交（不改变登录结果与 UI 行为）。
- 说明：原本计划的 `LoginMethod` 接口抽象并入 Stage 3 的 SessionManager 门面时一同落地（避免产生未接线代码）。

### Stage 3：SessionManager 门面（含登录方法抽象）
- 新增 `login/domain/SessionCoordinatorV2`（会话变更门面：commitAndActivate/activate/restoreActive/delete/clear*/import，内部委托 AccountSessionStore），登录提交、启动恢复、账号切换/删除等核心变更点已接入门面；
- 只读查询（getActiveAccount/getAllAccounts 等）调用方广泛，仍保留直读 store，门面同时提供读取方法供新代码；
- `LoginMethod` 接口抽象暂缓：认证动作与现有 UI 轮询流程强耦合，直接抽取会产生未接线代码，待后续需要时连同认证协调一并引入（见 Stage 3b，可选）。

### Stage 4：自动登录协调器（已完成）
- 新增 `login/domain/AutoLoginCoordinatorV2`：业务二级站服务会话 `ensureService`（委托 ServiceLoginManager）与 WebVPN `ensureWebVpn`（委托 WebVpnSessionManager，单飞去重、失败不回滚）；
- 业务调用点已迁移（HallFavorite/PersonSearch/Campusphere/SpeakUp 的前置会话 + WebVpnInterceptor 会话认证回调），删除对 ServiceLoginManager 的直接 import。

### Stage 5：存储接口化（已完成）
- 新增 `login/data/KeyValueStoreV2`（窄接口 + SharedPrefsStoreV2 适配），AccountSessionStore 持久化介质改为经该接口，新增 `initForTesting(fake)` 支持 JVM 测试注入内存实现；测试补 `org.json:json` testImplementation（Android 桩不支持序列化测试）。
- Cookie 存取现状已是接口化设计（CookieStoreOkHttpJar/CookieJarBridge 回调桥接 + UserAwareCookieJar），按惯例无需再造一层，本阶段不再新增 CookieJarRepository。

### Stage 6：备份 v2（可选加密）——已按用户选择不实施
- 用户确认：设置/Cookie 备份继续**明文 JSON、不加提示与二次确认**；
- 可选加密（PBKDF2+AES-GCM 口令容器 + 导入导出页入口）作为未来扩展记录在本节，不在 dev 实现。

## 完成判据（已满足）
- dev 分支已完成 Stage 1-5，Stage 6 按用户选择收尾；编译与全量单测通过；
- main 既有提交保持不动；dev 各 Stage 均为行为不变的收敛/抽象接入（无用户可见 UI 变化），真机回归建议见关闭说明。
