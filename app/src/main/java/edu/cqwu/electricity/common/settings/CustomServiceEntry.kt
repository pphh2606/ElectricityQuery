package edu.cqwu.electricity.common.settings

import java.util.UUID

/**
 * 用户自定义的网站快捷方式，保存在「我的服务」中。
 *
 * 从 `home/data` 上移到设置基元层：它被「设置项 [SettingsKeys]」用来序列化自定义服务列表，
 * 留在 home 模块会让共享的设置项反向依赖功能模块。
 *
 * @property id 唯一标识，用于删除和 key
 * @property title 显示名称
 * @property url 网址（含 http/https 协议头）
 * @property iconUri 本地图标文件 URI（content:// 或 file://），可为 null
 */
data class CustomServiceEntry(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val url: String = "",
    val iconUri: String? = null
)
