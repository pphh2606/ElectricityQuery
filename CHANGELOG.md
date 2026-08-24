# 更新日志

## 新增功能
新增修改用户名页面，支持设置登录别名与昵称并保存到 CAS：新增 UserNameEditApi、UserNameEditViewModel、UserNameEditScreen 三个文件并注册 USER_NAME_EDIT 导航路由，账号管理页修改用户名入口由占位改为可点击跳转
别名校验改为用户修改时即时触发并直接显示可用或不可用：ViewModel 的 onAliasChange 每次修改即调用 checkAlias.do 校验，保存前再兜底校验一次，网络失败时静默等待保存兜底
修改用户名页支持下拉刷新与会话过期引导重新登录：页面用 PullToRefreshBox 刷新当前值，响应为 CAS 登录页时置 requiresReLogin 状态跳转登录页

## 界面调整
自适应列表弹窗组件 ListSheetDialog 统一重命名为 BottomSheetDialogV2：组件函数更名，登录、设置、账单、电费、反馈等全部调用方同步更新引用，行为不变
设置页内容区改为可上下滚动：设置列表加 verticalScroll 与 rememberScrollState，内容超高时可滚动查看完整选项
账号管理页修改用户名与修改密码行支持点击：PlaceholderRow 增加可选 onClick 参数，有回调时可点击跳转，无回调时保持原占位禁用样式

## 资源更新
各语言新增修改用户名页面全套文案：strings_settings.xml 新增 user_name_edit_title、user_name_edit_tip、user_name_edit_alias_available 等 12 个 key，提示语含添加登录别名后不可再修改。
