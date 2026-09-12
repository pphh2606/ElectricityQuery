## Bug 修复
1. 修复接入者信息页出错时提示样式与其它页面不一致的问题：错误态改用通用的 ReLoginContent，统一为文案加重试按钮
2. 修复一卡通充值、卡挂失与更新弹窗等页面键值行行距偏窄的问题：行距由 4dp 统一为 8dp

## 界面优化
1. 全应用卡片圆角统一为 16dp：一卡通、公告、支付方式、充值与意见反馈等页面原先的 12dp 圆角统一，观感与设置页一致
2. 键值行标签宽度与卡片底色统一：一卡通充值页改用公共常量 InfoLabelWidth，订单详情卡片底色改用 surfaceContainerLow

## 架构改进
1. 校园网络传输层合并为单个文件：新增 common/CampusHttp.kt 容纳传输基类、测速站地址常量与信封实现，认证网关接口改为继承基类只替换解码方式
2. 校园网络专用客户端收敛到 HttpClientFactory.campusClient：测速站与认证网关共用同一实例并统一 3 秒连接超时
3. 接入者信息功能整体迁入 accessor 包：campusnetworkinfo 包移除，ClientContext 系列更名为 Accessor，导航引用同步更新
4. 字段列表抽成可声明式列举的纯数据：新增 campusnetwork/ui/CampusFields.kt，接入者信息与网络服务两页改用它渲染，字段构建不再依赖 Resources
5. 功能网格上提为公共组件：新增 common/ui/FeatureGrid.kt，校园网络首页与一卡通首页共用，去掉各自的私有副本
6. 电表详情与测速接口的小幅收敛：分隔线改用公共 InfoRowDivider，下载探测的 chunk 大小改由引擎传入

## 删除的文件
1. 删除 campusnetworkinfo/data/CampusNetworkApi.kt：client-context 请求能力并入新的 accessor/data/AccessorApi.kt
2. 删除 common/CampusNetworkJson.kt：信封解析并入 common/CampusHttp.kt，测速站地址独立为 SpeedTestStation
