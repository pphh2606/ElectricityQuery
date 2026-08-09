package edu.cqwu.electricity.network

import java.net.URI
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * WebVPN URL 加密转换工具
 *
 * 对应 Python 版 WebVPN AES‑128‑CBC 加密逻辑：
 * - 密钥与 IV 均为 "CASB2021EnLink!!" 的 UTF-8 字节（16 字节）
 * - 将外网 URL 的主机名加密后拼接到代理地址中
 *
 * 外网 URL → 代理 URL 格式：
 *   https://clientvpn.cqwu.edu.cn/{scheme}/webvpn{encrypted_host}{path}
 *
 * 示例：
 *   输入: https://jwc.cqwu.edu.cn/
 *   输出: https://clientvpn.cqwu.edu.cn/https/webvpn{encrypted_hex}/
 */
object WebVpnEncoder {

    const val PROXY_BASE = "https://clientvpn.cqwu.edu.cn"
    private val KEY_IV = "CASB2021EnLink!!".toByteArray(Charsets.UTF_8) // 16 bytes

    /**
     * AES-CBC 加密主机名，返回 hex 小写字符串
     */
    private fun encryptHost(host: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(KEY_IV, "AES")
        val ivSpec = IvParameterSpec(KEY_IV)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        val encrypted = cipher.doFinal(host.toByteArray(Charsets.UTF_8))
        // 转为 hex 小写（与 Python .hex() 一致）
        return encrypted.joinToString("") { "%02x".format(it) }
    }

    /**
     * 将外网 URL 转换为 WebVPN 代理 URL
     *
     * @param originalUrl 外网 URL，如 "https://jwc.cqwu.edu.cn/some/path?q=1"
     * @return 代理 URL
     * @throws IllegalArgumentException 如果 URL 格式无效
     */
    fun transform(originalUrl: String): String {
        val trimmed = originalUrl.trim()
        val uri = URI(trimmed)

        val scheme = uri.scheme
            ?: throw IllegalArgumentException("URL 缺少协议（如 https://）: $originalUrl")
        val host = uri.host
            ?: throw IllegalArgumentException("URL 缺少主机名: $originalUrl")

        // 处理非标准端口
        val hostWithPort = if (uri.port != -1 && uri.port != (if (scheme == "https") 443 else 80)) {
            "$host:${uri.port}"
        } else {
            host
        }

        val path = uri.rawPath ?: "/"
        val query = uri.rawQuery?.let { "?$it" } ?: ""
        val fragment = uri.rawFragment?.let { "#$it" } ?: ""

        val encrypted = encryptHost(hostWithPort)
        return "$PROXY_BASE/$scheme/webvpn$encrypted$path$query$fragment"
    }

    // ──────────── 反向解码方法 ────────────

    /**
     * 判断 URL 是否为 WebVPN 代理 URL。
     *
     * 匹配规则：以 [PROXY_BASE] 开头。
     */
    fun isWebVpnUrl(url: String): Boolean {
        return url.startsWith(PROXY_BASE)
    }

    /**
     * AES-CBC 解密主机名
     *
     * @param encryptedHex 加密后的 hex 字符串
     * @return 解密后的主机名
     */
    private fun decryptHost(encryptedHex: String): String {
        val encryptedBytes = encryptedHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(KEY_IV, "AES")
        val ivSpec = IvParameterSpec(KEY_IV)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        val decrypted = cipher.doFinal(encryptedBytes)
        return String(decrypted, Charsets.UTF_8)
    }

    /**
     * 将 WebVPN 代理 URL 还原为原始外网 URL。
     *
     * 解析 WebVPN URL 格式：
     *   https://clientvpn.cqwu.edu.cn/{scheme}/webvpn{encrypted_hex}{original_path}
     *
     * 示例：
     *   输入: https://clientvpn.cqwu.edu.cn/https/webvpn{encrypted_hex}/some/path?q=1
     *   输出: https://jwc.cqwu.edu.cn/some/path?q=1
     *
     * @param webVpnUrl WebVPN 代理 URL
     * @return 原始外网 URL
     * @throws IllegalArgumentException 如果 URL 格式无效或解密失败
     */
    fun decode(webVpnUrl: String): String {
        val trimmed = webVpnUrl.trim()
        require(isWebVpnUrl(trimmed)) { "不是有效的 WebVPN URL: $trimmed" }

        val uri = URI(trimmed)
        val path = uri.path ?: throw IllegalArgumentException("URL 缺少路径: $trimmed")

        // 去掉开头的 "/"，按 "/" 分段
        // segments[0] = scheme (https/http)
        // segments[1] = webvpn{encrypted_hex}
        // segments[2] = 原始 path（可选）
        val segments = path.trimStart('/').split("/", limit = 3)
        require(segments.size >= 2 && segments[1].startsWith("webvpn")) {
            "WebVPN URL 路径格式无效: $path"
        }

        val scheme = segments[0]
        val encryptedHex = segments[1].removePrefix("webvpn")
        val originalPath = if (segments.size > 2) "/${segments[2]}" else "/"

        val host = decryptHost(encryptedHex)

        val query = uri.rawQuery?.let { "?$it" } ?: ""
        val fragment = uri.rawFragment?.let { "#$it" } ?: ""

        return "$scheme://$host$originalPath$query$fragment"
    }
}
