## 新增功能
1. 账号管理页新增手机绑定、邮箱绑定、高级设置三个入口，点开用内置浏览器进入学校统一身份认证页面；经 onNavigateToWebView 回调与 unifiedWebViewRoute 跳转
2. 修改密码页新增「忘记密码」，与登录页共用新抽出的 PasswordRecoverySheet 弹窗
3. 网页唤起外部应用前先弹确认，点「确定」才跳转；WebViewExternalApp 用 resolveActivityInfo 取应用名填入文案，确认走 Snackbar 的 INFO 类型
4. 手机上没装对应 App 时完全静默，不再出现 ERR_UNKNOWN_URL_SCHEME 界面；查不到可处理应用就只记日志并接管 URL，可见性靠 AndroidManifest 的 queries 通配声明而非 QUERY_ALL_PACKAGES
5. 长按复制文字后高亮自动消失；SelectableContainer 监听剪贴板写入并重建 SelectionContainer 清掉选区状态
6. 主题色弹窗改用 R、G、B 三个滑块调色并实时显示数值，「自定义颜色」一栏同时显示当前 HEX；Slider 轨道按通道自绘渐变，拖动仍即拖即应用

## Bug 修复
1. 修改密码不再报解密失败或验证码错误；取盐、取验证码与提交复用同一个 PageSession，替换原先每次请求都新建隔离客户端的写法
2. 刷新或重试时错误浮层不再一闪而过、加载指示立刻出现；reloadWithLoading 先清错误状态再置加载态，并补上 API 23 以下的旧版 onReceivedError 签名兜底
3. 首页、用电明细、充值记录、用量报表加载失败统一为居中提示加「重试」按钮；这些位置改用 ReLoginContent，requiresReLogin 传 false
4. 下拉刷新失败不再把已有内容顶掉；首页在 categories 非空时错误只走 Snackbar 提示
5. 今日课程与考试卡片单接口失败不再显示成「今日无课」「暂无考试」；改传 hasError 布尔量，失败时内容区留白
6. 添加快捷方式与删除账号不再卡住界面；列表失败改为下拉重试，删除账号包 try finally 保证加载弹窗一定收起
7. 键盘不再遮挡银行卡绑定页按钮与底部提示条；Scaffold 补 imePadding，Snackbar 的 windowInsetsPadding 取导航栏与输入法并集
8. 同一用户名重复登录不再堆出多条账号记录；commitLogin 命中同名条目时原地更新并复用原 id

## 界面与交互优化
1. 全项目轮廓输入框统一 12dp 圆角，标签浮起后停在左侧图标右边；新增 AppOutlinedTextField，桥接 TextFieldState 以使用仅 state 重载开放的 labelPosition
2. 详情页键值行外观统一且键与值都能长按复制；InfoRow 内置选择容器，LabeledFieldRow 不再使用
3. 账号行与浏览器标识行的删除按钮点击区域变大、图标不再跳动；删除改用 IconButton，勾选、删除、占位三态统一占 48dp
4. 二维码在窄屏自动缩小且四周留白相等；固定尺寸改为 widthIn 加 aspectRatio，QrCodeView 按画布短边取正方形并用 drawWithCache 缓存路径

## 架构改进
1. 内置浏览器从单文件拆成状态、回调、外跳三块且行为不变；状态收敛为 UnifiedWebViewUiState，客户端移到 UnifiedWebViewClients.kt，WebSettings 初始化抽成共享函数
2. 多步 CAS 接口有了统一会话抽象；HttpClientFactory 新增 IsolatedSession 与 PageSession 供修改密码、修改用户名、设备会话复用
3. 加载指示器与默认色板收敛到统一实现；SwipeRefreshLayout 换成 Material3 的 PullToRefreshBox，Android 12 以下改用固定种子色生成默认色板

## 依赖变更
1. 移除 SwipeRefreshLayout 依赖；libs.versions.toml 与 app/build.gradle.kts 同步删除对应版本与库声明

## 删除的文件
1. 删除 LabeledFieldRow.kt 与 theme/ui/Color.kt；键值行由 InfoRow 承担，内置紫色色值不再使用
