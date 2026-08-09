# 更改日志

## 新增功能
- 新增 WebVPN 代理开关和设置页：可在设置中统一开启或关闭学校 WebVPN，开启后所有网络请求会自动经过学校代理转发。
- 新增 WebVPN 自动登录：当请求遇到 401/403、重定向循环或 CAS 登录页时，自动使用已保存的账号密码完成统一身份认证并重试，需要验证码时会提示手动登录。
- 新增“跟随系统”语言选项：Android 13 及以上通过系统级 LocaleManager 直接切换应用语言，无需重启页面，并补充语言支持声明。

## Bug 修复
- 修复校园卡中心数据解析失败：放宽 weui-cell__ft 单元格的正则匹配，兼容 class 属性后还带其他属性时的 HTML。

## 架构改进
- 将 WebVPN 相关代码整理到独立 network 包：新增 URL 转换、拦截器、会话管理和全局开关，并让所有 OkHttp 客户端统一接入代理逻辑。
- 新增 HTTP 请求诊断日志：记录缴费服务大厅和 WebVPN 请求的完整重定向链路、Cookie 及失败原因，方便排查 “Too many follow-up requests” 这类网络问题。
- 新增 WebVPN 和语言相关单元测试：覆盖代理 URL 加解密、自动登录判定、语言标签映射等核心逻辑，测试源码不上传 GitHub。

## 工程配置
- 应用版本号从 1459 提升到 1469：
- 开启单元测试默认返回值并忽略 app/src/test 目录：让本地单元测试可以正常构建运行，同时避免测试源码进入 GitHub。

## 删除的文件
- 删除旧位置的 login/data/WebVpnEncoder.kt：原有 WebVPN URL 转换逻辑已迁移到 network/WebVpnEncoder.kt，功能不受影响。
