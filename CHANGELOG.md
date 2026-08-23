## 新增功能
- 首页首次加载的中央转圈改为顶部下拉刷新指示器。PullToRefreshBox 的 isRefreshing 条件合并 isLoading 状态，删除独立的 CircularProgressIndicator 分支，加载动画统一为下拉刷新表达。
## Bug 修复
- 修复扫一扫入口报错与无法打开的问题。桌面快捷方式不再把 mamp:// 自定义 scheme 喂给 WebView 触发 ERR_UNKNOWN_URL_SCHEME，首页列表点击也统一打开原生扫码页 Routes.SCAN，两条入口行为一致。
## 架构改进
- 首页点击与桌面快捷方式启动合并为统一分发。HomeAppLauncher.launch 单点解析原生界面、内置浏览器、外部弹窗与无动作四类动作，外部打开复用 WebViewUrlUtil.openCustomSchemeUrl 并共享 ExternalAppConfirmDialog 确认弹窗。
- 启动横切逻辑收敛到新增 AppLaunchEffects。自动更新检查、Cookie 静默验证、快捷方式分发与外部弹窗集中管理，AppShell 与 NavGraph 回归各自单一职责，两个进程级单次执行标记统一。
- 首页数据加载改为进程级缓存。HomeJsonLoader 的缓存提升为伴生对象，首页与添加快捷方式页共享同一份解析结果，避免重复读取与解析。
- 夜间模式改为系统原生实现并移除 appcompat。MainActivity 改用 ComponentActivity 并在 attachBaseContext 通过 applyOverrideConfiguration 注入 uiMode，切换时 recreate 重建，行为与 AppCompatDelegate 等价。
## 依赖变更
- 移除 AndroidX AppCompat 依赖。删除 appcompat 1.6.1 版本目录条目与实现声明，安装包体积减小约 10%。
