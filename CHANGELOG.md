# 更新日志

## 新增功能
新增独立账号管理页，支持多账号切换、编辑删除与添加账号：新增 AccountManagerScreen 独立页面并注册导航路由，切换前经 SessionManager 校验 CAS 登录态，失效时跳转登录页，以登录时间区分同学号多条目
二维码圆角新增总开关，默认关闭时圆角不生效：新增 qr_code_corner_radius_enabled 设置项，各二维码界面改用 effectiveQrCodeCornerRadius 派生属性，开关关闭时圆角强制归零
首页我的服务区改为编辑模式或有服务时才显示并带显隐动画，「+ 自定义网站」按钮仅编辑模式出现：服务 LazyRow 包入 AnimatedVisibility 控制显隐，添加按钮按 isEditMode 条件渲染

## 界面调整
选择语言、打开网址、自定义网站、外部应用确认等弹窗统一迁移为自适应半屏或全屏可滚动形式：各调用方由 BottomSheetDialog 迁移到 ListSheetDialog，内容超高先停半展开、不高时不撑满且底部不留白
账单、电费、反馈、服务大厅、建言等九个页面弹窗统一迁移为自适应弹窗：各页面统一替换为 ListSheetDialog 并移除冗余内边距
日志级别、夜间模式、取色器、更新下载来源等设置弹窗统一迁移为自适应列表弹窗：由 BottomSheetDialog 迁移为 ListSheetDialog，移除手写 verticalScroll 与 fullscreen 参数
更新弹窗改为内容内部滚动且底部操作区固定：UpdateFoundSheet 迁移为 ListSheetDialog，内容区加 verticalScroll，跳过版本勾选与操作按钮固定底部
外部应用确认弹窗改为居中标题加两个全宽按钮的样式：ExternalAppConfirmDialog 重排为居中消息加全宽取消/确认按钮，与安全提醒弹窗布局一致
付款浮层与网页浏览弹窗移除键盘弹出时自动展开的逻辑：删除全屏参数与键盘弹出时自动展开 sheet 的 LaunchedEffect 监听
页面切换动画默认由滑动改为拟物风格：PAGE_TRANSITION 默认值由 SLIDE 改为 CUPERTINO
二维码圆角设置改为开关加条件展开的滑块：圆角卡片重构为开关行加 AnimatedVisibility 展开的滑块，移除居中百分比标题与固定刻度标签
楼栋选择与个性化字体大小的展开箭头改为平滑旋转动画：固定箭头配合 animateFloatAsState 旋转 90 度

## Bug 修复
编辑预填学号后清除已保存密码占位状态，防止误用旧账号密码：updateUsername 时清空 accountId 与 hasSavedPassword，占位密码改从预填条目读取
首页无服务退出编辑时保留等高占位，避免收起动画高度塌缩：服务为空时渲染等高占位 Box，维持 AnimatedVisibility 收起动画可见

## 架构改进
账号存储改为按条目 ID 唯一，同一学号可保存多个登录条目：SavedAccount 新增 UUID id，各接口改为按 accountId 定位，LoginViewModel 与 WebVpnSessionManager 同步适配，旧格式数据直接丢弃
会话读取改为直接获取激活账号对象：getActiveUser 与 getAccount 合并为 getActiveAccount，各模块调用点同步更新
登录路由参数由学号改为账号条目 ID，登录页按条目预填：LOGIN 路由查询参数从 username 迁移为 accountId，LoginScreen 改收 initialAccountId
通用弹窗组件简化为单一自适应形式，并抽出新的列表弹窗组件：BottomSheetDialog 移除 fullscreen 及 bottomBar、fixedHeader 分支，新增 ListSheetDialog 用 verticalScroll 承载内容、底部空白随内容滚动

## 资源更新
各语言同步移除余额查询中、点击展开、打开失败、支付处理中等不再使用的文案：删除 dashboard_querying_balance、home_open_failed 等失效 string key
移除账号管理弹窗旧标题与当前标记，并为多语言补齐切换账号状态提示：删除 account_manager_title 与 account_manager_current，新增 account_manager_switch 系列的本地化翻译
设置页新增账号管理与二维码圆角开关相关文案：strings_settings.xml 新增 settings_account_manager、account_manager_v2 系列及 qrcode_settings_corner_radius_switch 等 key

## 删除的文件
删除原账号管理底部弹窗及删除账号确认弹窗：移除 AccountManagerSheet 与 DeleteAccountSheet，功能由独立 AccountManagerScreen 页面取代
