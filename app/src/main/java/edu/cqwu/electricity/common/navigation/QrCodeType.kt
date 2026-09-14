package edu.cqwu.electricity.common.navigation

/**
 * 二维码类型：支付码 / 乘车码。
 *
 * 它只用于拼二维码页路由（[Routes.qrCodeRoute]）与页面标识，因此和路由表一起放在导航层——
 * 留在二维码模块里会让共享层反向依赖业务模块。
 */
enum class QrCodeType {
    PAY,
    BUS
}
