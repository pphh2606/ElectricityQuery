# 更新日志

## 新增功能
1. 新增修改密码页面，账号管理里可以直接修改登录密码了：本地化实现 CAS 的 mobilePasswordChange.do 接口，旧密码、新密码与图形验证码经 AES-CBC 加密后提交，会话失效时自动引导重新登录
2. 删除账号时真正注销服务端会话，不再只是删除本地记录：LogoutApi 由占位实现升级为真实请求 authserver 的 logout 接口，仅响应码 3xx 视为登出成功，每次调用用账号 cookie 构建隔离的 OkHttpClient 并禁用重定向跟随
3. 通用确认弹窗显示时背景同步开启模糊效果：AppScaledAlertDialog 通过 DisposableEffect 联动全局 LocalSheetVisibilityState，弹窗出现即模糊、关闭即复位，与加载弹窗和底部弹窗行为保持一致

## Bug 修复
1. 修复修改密码等页面被误判为登录页导致会话失效误报的问题：CAS 登录页特征信号移除 pwdDefaultEncryptSalt 密码盐标记，仅保留登录表单与 CASTGC 两个信号，避免含加密盐的正常页面触发重新登录

## 架构改进
1. 通用 UI 组件从主题模块迁入公共模块统一管理：AppScaledWindows、BottomSheetDialog、日期选择、折线图、二维码等 9 个组件由 theme/ui 移入 common/ui，自定义网站与打开链接弹窗分别归入 home 和 profile 模块，全部引用方 import 同步更新
2. WebVpn 网络组件独立成包，日志脱敏工具归入日志模块：编码器、拦截器、会话管理、设置 4 个类从 network/ 迁入 webvpn/，LogRedactor 由 feedback/util 移至 logging/ 统一管理
3. 应用重启逻辑抽取为公共工具供多处复用：StorageClearScreen 的私有重启方法抽出为 theme/util/AppRestart.kt 的 restartApp，清除存储、修改密码后刷新登录状态等场景共用同一实现

## 其他
1. 修改密码页面文案补齐六种语言，入口文案去掉省略号规范显示：中英法日阿繁中六套 strings_settings 新增 24 条修改密码相关字符串，入口文案由修改密码…规范为修改密码
2. README 目录说明修正为重构后的实际包结构：文档中代码路径由 ui/ 前缀更新为 common/ 等现路径，避免文档与代码结构脱节
