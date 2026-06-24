package edu.cqwu.electricity.data.network.pay.electricityrecharge

import android.util.Base64
import java.net.URL
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/**
 * RSA 加密工具类
 * 对应 Python 版 encrypt_rsa() 和 build_authorization() 函数
 */
object RSAEncrypt {

    private const val TRANSFORMATION = "RSA/ECB/PKCS1Padding"
    private const val ALGORITHM = "RSA"

    private const val RSA_PUBLIC_KEY = """-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAsJEc7CIxbt5cPn3umQyO7Eu+ALarLPEE
vaZUY+adwzTlKeiBPYukjimpfKoqJjcdqg6hffLIKCcKRN9PTFi8Y8324+e6g37jC0ILUlXYdvQM
I8ftnXjROAioEK/rWClgY4eYFtURo5ytobco8CKwKvnDKrj/u7eExoWXUxvC0VKgz0Q8oKuh7UAM
BwVAvuBW6g6nIRqpC+pLFvzZegvNdjbwZZ2MekmsG6IdB8GDUc6ut1M14zojIIfI+NRStJ03EgjV
HqeNpuiR5bv98kgpnedLGfAFnMAxnIz2HKutbi0fWl4VhHqfApQoJZ16zi/R5WwJpxYDpxL/NAiW
P/S2OQIDAQAB
-----END PUBLIC KEY-----"""

    private val publicKey by lazy {
        val keyBytes = RSA_PUBLIC_KEY
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\n", "")
            .replace("\r", "")
            .trim()
        val decoded = Base64.decode(keyBytes, Base64.DEFAULT)
        val keySpec = X509EncodedKeySpec(decoded)
        val keyFactory = KeyFactory.getInstance(ALGORITHM)
        keyFactory.generatePublic(keySpec)
    }

    /**
     * RSA 加密数据，返回 Base64 编码的密文
     * 对应 Python 的 encrypt_rsa()
     */
    fun encrypt(data: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    /**
     * 根据 URL 构造 Authorization 头的值
     * 对应 Python 的 build_authorization()
     *
     * 逻辑：提取 userId → 每个字符 ASCII+1 → RSA 加密 → Base64
     */
    fun buildAuthorization(urlString: String): String {
        val url = URL(urlString)
        val query = url.query ?: throw IllegalArgumentException("URL 中无查询参数")

        val params = query.split("&").associate {
            val parts = it.split("=", limit = 2)
            parts[0] to (parts.getOrNull(1) ?: "")
        }

        val rawValue = when {
            params.containsKey("userId") -> params["userId"]!!
            params.containsKey("userid") -> params["userid"]!!
            params.containsKey("password") -> params["password"]!!
            params.containsKey("openId") -> params["openId"]!!
            params.containsKey("id") && url.path.contains("updateWechatUser") -> params["id"]!!
            else -> throw IllegalArgumentException("URL 中未找到 userId/userid/password/openId/id 参数")
        }

        // 每个字符 ASCII + 1
        val shifted = rawValue.map { it + 1 }.joinToString("")

        return encrypt(shifted)
    }
}