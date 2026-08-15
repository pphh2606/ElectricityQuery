## 新增功能
- 个性化设置中新增字体大小调节，可以按 80% 到 150% 缩放应用文字。通过 `Slider` 调整 `fontScale` 并持久化到 `SettingsKeys.FONT_SCALE`，主题层用 `CompositionLocalProvider` 覆盖 `LocalDensity.fontScale`，使缩放对全应用生效。
- 关于页新增检查更新入口，可选择测试版或稳定版更新通道。新增 `UpdateRepository` 拉取 `ElectricityQuery-assets` 仓库的 `ci.json` / `stable.json` 元数据，展示版本号、包大小和更新说明，并支持复制链接或跳转下载。
- 更新检查数据层独立成 `update/data` 模块。`UpdateRepository` 通过 jsDelivr 多节点和 GitHub raw 兜底获取元数据，并显式关闭 WebVPN 拦截，避免校园代理影响更新请求。

## 架构改进
- 新增一套支持应用字体缩放的弹层包装组件。`AppScaledAlertDialog`、`AppScaledDropdownMenu`、`AppScaledExposedDropdownMenu` 和 `ProvideAppScaledDensity` 向独立窗口组件注入应用级 `Density`，保证弹窗、菜单、日期选择器和加载框的布局一致。
- 底部弹窗、日期选择器和加载框统一走应用级字体缩放。`BottomSheetDialog`、`DatePickerField`、`LoadingDialog` 内部改用 `ProvideAppScaledDensity` 包裹内容，避免系统窗口默认 `fontScale` 与页面设置不一致。
- 字体缩放状态纳入统一设置模型。`AppSettingsState` 新增 `fontScale` 与 `updateFontScale()`，`MainActivity` 将其传入 `电费查询Theme` 供全局主题消费。
- 新增更新与字体设置的多语言资源。简中、繁中、英文、法语、日语和阿拉伯语同步补齐 `personalization_font_scale`、`update_*` 等文案，保证新功能在支持的语言下完整显示。

## 工程配置
- GitHub Actions 新增 CI 更新包发布与 Release 稳定版同步。`build.yml` 在推送到 main 时把 Release APK 和 `ci.json` 元数据提交到 `ElectricityQuery-assets`，在 GitHub Release 发布时通过 `sync_latest_stable.py` 拉取最新 APK 并生成 `stable.json`。
- 构建改用仓库内置的固定调试签名。新增 `signing/debug.keystore` 和 `fixedDebug` signingConfig，CI 通过 `CI_TEST_SIGNING=true` 使用该密钥，避免 release 构建依赖随机 debug 签名。
- CI Python 脚本纳入版本控制。`.gitignore` 增加 `.github/scripts/*.py` 例外规则，`sync_latest_stable.py` 和 `write_assets_metadata.py` 随仓库维护。
- 应用构建版本号递增到 1544。`app/version.properties` 的 `VERSION_CODE` 从 1543 更新为 1544。

## 删除的文件
- 删除 values-v21 与 values-night-v21 的主题覆盖文件。移除 API 21 专属的 `Theme.电费查询` 样式，系统栏颜色统一由基础 `themes.xml` 的透明配置处理，减少主题样式重复维护。
