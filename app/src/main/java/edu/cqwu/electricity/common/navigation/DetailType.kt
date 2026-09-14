package edu.cqwu.electricity.common.navigation

/**
 * 详情页类型。
 *
 * 它只用于拼详情页路由（[Routes.detailRoute]）与标识详情页种类，因此和路由表一起放在导航层——
 * 留在电费模块里会让共享层反向依赖业务模块。
 */
enum class DetailType {
    METER_STATUS        // 电表实时状态
}
