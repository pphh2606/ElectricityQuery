package edu.cqwu.electricity.data.network.auth

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-CBC 加密工具类
 * 对应 Python 版的 encrypt_password() 和 rds() 函数
 *
 * 加密逻辑：
 * 1. 生成 64 位随机字符前缀
 * 2. 拼接 prefix + password 作为明文
 * 3. 使用 AES/CBC/PKCS5Padding，key=salt，IV=随机16位
 * 4. 输出 Base64
 */
object AesEncrypt {

    private const val CHARS = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678"
    private const val KEY_ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"

    private val secureRandom = SecureRandom()

    /**
     * 生成指定长度的随机字符串
     * 对应 Python 的 rds(length)
     */
    fun rds(length: Int): String {
        val sb = StringBuilder(length)
        repeat(length) {
            sb.append(CHARS[secureRandom.nextInt(CHARS.length)])
        }
        return sb.toString()
    }

    /**
     * 加密密码
     * 对应 Python 的 encrypt_password(password, pwd_salt)
     *
     * @param password 原始密码
     * @param salt 加密盐值（从登录页面提取的 pwdDefaultEncryptSalt）
     * @return Base64 编码的密文
     */
    fun encryptPassword(password: String, salt: String): String {
        // 1. 生成随机前缀 + 密码
        val plaintext = rds(64) + password

        // 2. 准备 key 和 IV
        val key = salt.toByteArray(Charsets.UTF_8)
        // Python 版本：IV 使用 rds(16).encode('utf-8')
        val ivString = rds(16)
        val iv = ivString.toByteArray(Charsets.UTF_8)

        // 3. 构建密钥规范（AES 要求 key 长度为 16/24/32 字节）
        //    这里直接截断或填充到 16 字节（与 Python Cryptodome 行为一致）
        val keyBytes = if (key.size >= 16) {
            key.copyOf(16)
        } else {
            key.copyOf(16).also { src ->
                (key.size until 16).forEach { src[it] = 0 }
            }
        }
        val secretKeySpec = SecretKeySpec(keyBytes, KEY_ALGORITHM)
        val ivSpec = IvParameterSpec(iv)

        // 4. 执行加密
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivSpec)
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // 5. 返回 Base64
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }
}