## 新增功能
1. 首页的应用名和分类名会跟随应用语言切换显示；新增 assets/i18n/home_apps_i18n.json，由 mergeHomeI18n 纯函数按 appId 与 categoryId 查表合并译文，缺译文时回退 JSON 中文原文
2. 办事大厅的应用名和分类名同样支持多语言；因为登录后服务端数据会整体替换本地快照，HallI18n 改为在显示处按 ID 查表，而不是把译文合并进数据层
3. 英文界面下用中文关键词也能搜到首页应用；HomeApp 与 HomeCategory 新增 originalName、originalCategoryName 保存本地化前的原文，搜索条件同时匹配译文、原文与服务端别名
4. 切换语言后首页会静默刷新为新语言；HomeViewModel 新增 reloadForLanguageChange，以 LocalConfiguration 变化为信号比对语言键后重载，不显示下拉刷新指示器

## Bug 修复
1. 留言详情里较长的科室或姓名不再把右侧时间挤成竖排；信息行改为左侧 weight 1f 不限行、右侧时间单行加省略号，并按 Top 对齐
2. 通知列表搜索时不再重复显示结果标题；统计行左侧改为显示搜索关键词与结果数，删除列表内重复的 search_header 条目

## 界面与交互优化
1. 通知列表标题栏只保留「通知公告」；标题改用 TopAppBar 默认 titleLarge 与其他页面统一，总数从标题下移到列表统计行
2. 通知列表顶部统计行左侧改为「共 N 条通知」，不再显示「已加载 N 条」；由 ListStatsRow 的 loadedText 参数驱动，右侧页码保持不变
3. 通知详情标题放大到与其他页面一致；去掉 titleMedium 改用 TopAppBar 默认 titleLarge，并加单行省略避免与右侧操作按钮挤压
4. 留言列表改为与账单页一致的无阴影描边卡片；用 Card 搭配 1dp outlineVariant 描边与 16dp 圆角，条目间距 8dp，替换原来的分隔条样式
5. 留言咨询区列表改为图标加名称的整行样式；44dp surfaceVariant 图标底板配 Forum 图标与右箭头，名称不限行完整显示，行间用 0.5dp 分割线

## 架构改进
1. 语言键解析收敛到一处，首页与大厅共用；AppLanguage.currentI18nKey 优先读取 SettingsPreferences 的选择，只在跟随系统时回退 Locale.getDefault，简体中文返回 null
2. 首页数据缓存改为按语言键失效；HomeJsonLoader 同时缓存 categories 与 languageKey，语言不一致时重新解析并合并，避免切语言后拿到旧译文
3. 列表统计行左侧文案可收缩，长文案不再挤掉页码；ListStatsRow 左侧加 weight 1f 与 fill false 以及单行省略，右侧固定单行并加 16dp 起始间距

## 新增文件
1. app/src/main/assets/i18n/home_apps_i18n.json：首页应用与分类的多语言映射表
2. app/src/main/assets/i18n/hall_apps_i18n.json：办事大厅应用与分类的多语言映射表
