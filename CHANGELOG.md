# 修改日志

## 架构改进
- 项目源码从“先按 data/ui/util 分层、再按功能分包”调整为“先按功能建包”，每个功能包内最多保留 data/ui/util 一级目录，并同步更新 Kotlin 包名与 import 引用（代码中的包路径和引用声明）、AndroidManifest 入口类路径、ProGuard 混淆保护规则和 README 目录说明，让电费、设置、主题等功能代码集中且路径与包名一致
- 把集中存放的 Models.kt 数据模型按功能拆成电费、支付、通知、校园卡中心四组，并把原 ElectricityApi 中校园卡相关的账户、挂失、账单接口拆到新的 CardCenterApi，避免一个文件混装多个功能的代码

## Bug 修复
- 修复目录合并与 API 拆分引发的编译问题，包括充值记录页与仪表盘页复制函数重名、NoticeApi 换包后引用失效、CardCenterApi 缺少账单详情 URL 方法，现 Debug/Release 均可正常构建

## 构建改进
- 在版本号自动递增逻辑旁补充注释，说明每次编译都会自动加一，无需手动备份 version.properties（版本号文件）
