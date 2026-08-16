## Bug 修复
- 应用启动后的自动更新检查只执行一次，不再因界面重组或 Activity 重建反复触发。`AppShell` 使用进程级 `startupUpdateCheckDone` 标记配合 `LaunchedEffect(Unit)`，保证同一进程内只检查一次。
- “不再提示此版本”整行都可以点击切换，不用精确点到复选框。`UpdateFoundSheet` 给整行添加 `Modifier.clickable` 并统一走 `toggleSkipThisVersion()`，避免小点击区域操作不便。
## 架构改进
- 更新检查改为“先到先得”的提前退出策略，检测到任一镜像返回更高版本就立即结束，不再等待全部镜像。`UpdateRepository.check()` 用 `async` 加 `select` 消费首个有效结果，超时后取消剩余请求。
- 更新请求改为非阻塞回调式执行，并支持及时取消。`fetchInfo()` 通过 `suspendCancellableCoroutine` 包装 OkHttp `Callback`，`invokeOnCancellation` 会取消对应 `Call`，整体由 `withTimeout(timeoutMs)` 统一兜底。
- 没有新版本时也会保留首个有效镜像元数据作为兜底。`UpdateRepository` 在未找到更高版本时返回 `fallback`，同时保留“下载链接为空则忽略”的校验。
## 工程配置
- 应用版本号更新到 1605。
