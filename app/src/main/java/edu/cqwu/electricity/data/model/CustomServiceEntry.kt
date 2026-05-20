package edu.cqwu.electricity.data.model

import java.util.UUID

/**
 * 用户自定义的网站快捷方式，保存在「我的服务」中。
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
