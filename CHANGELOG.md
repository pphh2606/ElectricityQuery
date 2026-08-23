# 更新日志

## 新增功能
公告详情页的下载类链接现在会交给系统浏览器打开并下载：WebView 注册 DownloadListener，将服务端 Content-Disposition 附件响应转交外部浏览器处理
挂失与意见反馈提交时会弹出全屏加载层，模糊背景并阻断误操作：LoadingDialog 接入全局 Sheet 可见性状态驱动 Haze 模糊，替代按钮内的加载转圈

## Bug 修复
修复公告长内容撑破屏幕宽度的问题：详情页 HTML 注入 CSS 限制内容最大宽度并开启长词换行
修复 WebView 深色模式加载期间页面闪亮色的问题：在 onPageCommitVisible 首帧提交前即应用深色模式，onPageFinished 兜底

## 架构改进
夜间模式切换不再重建整个界面：改用 AppCompatDelegate.setDefaultNightMode 统一管理，移除 attachBaseContext 手动注入 uiMode 与 recreate 重建机制
移除充值、支付、收藏等按钮内的加载转圈：删除内嵌 CircularProgressIndicator，统一用文字加禁用态表达处理中状态
移除仪表盘加载骨架屏：加载中只保留顶部房间信息卡，删除 LoadingSkeleton 组件
简化楼层展开列表逻辑：移除 floorRoomRefreshVersion 刷新版本号机制，展开时只显示房间数量不再渲染加载、失败与重试状态
清理选楼、电费首页与 ViewModel 中的调试日志：移除 DEBUG_expand 与 EMS_showQR 等 AppLog 输出

## 依赖变更
重新引入 AppCompat 组件库：新增 appcompat 1.6.1 版本声明与依赖，Activity 基类和主题父样式切换为 AppCompat 实现

## 文案调整
挂失确认提示去掉开头的警告表情符号：各语言 strings_cardcenter.xml 中 card_lost_warning 移除 ⚠ 字符
