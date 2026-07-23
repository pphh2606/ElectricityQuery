# 更改日志

## 新增功能
- 新增「我有话说」模块，首页点击"我有话说"卡片可进入咨询区列表页面，查看各科室留言或发布新留言
- 咨询区列表页支持下拉刷新，展示所有可留言的科室（如基建后勤处等），点击科室可选择"发布留言"或"留言浏览"
- 留言浏览功能：查看指定咨询区的留言列表，支持滚动到底自动加载下一页（分页机制，每页 10 条），以及下拉刷新获取最新数据
- 留言详情页：展示留言标题、正文、图片以及官方回复内容，支持下拉刷新和分享链接（将留言的 ehall 页面 URL 复制到剪切板）
- 留言详情页包含评价区域，已评价的留言会显示"是否已解决问题"和五星评分（HFPF 字段存储评分 1-5 分）
- 留言详情中的图片支持加载 ehall 服务器上的原图和缩略图（通过 ImageItem 模型拼接完整 URL）
- 发布留言前自动预设 ehall 用户角色（通过 roles.do 获取 ROLEID 后调用 setupRole.do），确保 WebView 加载发布页面时不会因角色未设置而报无权限
- 新增 6 语言国际化支持：中文简体、英文、法文、日文、中文繁体、阿拉伯文的有话要说模块文案（strings_speakup.xml）
- Coil 图片加载器改用 HttpClientFactory 共享的 OkHttpClient，自动携带 ehall 认证 Cookie，解决从 ehall 服务器加载图片时因无 Cookie 返回 HTML 而非图片的问题

## 架构改进
- 新增 SpeakUpApi 网络请求封装类，统一封装咨询区列表获取（getZxq.do）、留言列表/详情获取（getZxxx.do）、角色预设（roles.do + setupRole.do）等 API 调用，复用 HttpClientFactory.shared 的 Cookie 管理和 ServiceLoginManager 的 CAS 登录流程
- 新增 ConsultationArea / ConsultationMessage / ImageItem 三组数据模型，通过 Gson 的 @SerializedName 注解映射 ehall 接口 JSON 字段到 Kotlin 属性
- 新增 SpeakUpViewModel 和 MessageListViewModel 两个 ViewModel，分别管理咨询区列表和留言列表的加载、分页、刷新状态，MessageListViewModel 使用 ViewModelProvider.Factory 模式传入 areaCode 参数
- 留言详情页的 ViewModel（MessageDetailViewModel）内嵌在 MessageDetailScreen.kt 中，通过 fetchMessageDetail API 获取单条详情
- 导航层新增 SPEAK_UP / SPEAK_UP_MESSAGES / SPEAK_UP_DETAIL 三条路由，SPEAK_UP_MESSAGES 和 SPEAK_UP_DETAIL 为参数化路由（携带 areaCode/areaName 或 wid），并提供 speakUpMessagesRoute / speakUpDetailRoute 辅助方法构建路由字符串
- HomeAppIds 新增 SPEAK_UP 常量（appId: 6251198080206918），HomeScreen 的 handleAppClick 和 NavGraph 的深度链接处理均新增对该 appId 的分发逻辑

## 依赖变更
- VERSION_CODE 从 1420 升至 1440
