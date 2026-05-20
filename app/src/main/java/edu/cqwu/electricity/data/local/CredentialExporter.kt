package edu.cqwu.electricity.data.local

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 凭据（账号+密码）的加密导出/解密导入工具。
 *
 * 加密方案：
 * - 密钥派生：PBKDF2-HMAC-SHA256（10 万次迭代）
 * - 加密算法：AES-256-GCM（认证加密，防篡改）
 * - 输出格式：Base64(salt + iv + ciphertext + gcm_tag)
 *
 * 多账号支持：
 * 解密后的 JSON 结构为 {"v":1, "a":[{"u":"账号","p":"密码"}, ...]}
 * 天然支持多账号，为未来多账户功能预留。
 *
 * 使用方式：
 *   val encrypted = CredentialExporter.export(listOf("user" to "pass"), "exportPwd")
 *   val accounts = CredentialExporter.import(encrypted, "exportPwd")
 */
object CredentialExporter {

    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 16   // bytes
    private const val IV_LENGTH = 12     // bytes（GCM 推荐 12 字节）
    private const val GCM_TAG_LENGTH = 16 // bytes（128 位认证标签）
    private const val AES_ALGORITHM = "AES"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"

    private const val FORMAT_VERSION = 1

    private val secureRandom = SecureRandom()

    /**
     * 加密并导出账号凭据列表
     *
     * @param accounts 账号列表，每个元素为 (username, password)
     * @param password 用户设定的导出密码
     * @return Base64 编码的加密字符串
     */
    fun export(accounts: List<Pair<String, String>>, password: String): String {
        // 1. 构建 JSON
        val json = buildJson(accounts)

        // 2. 生成随机 salt 和 IV
        val salt = ByteArray(SALT_LENGTH).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { secureRandom.nextBytes(it) }

        // 3. PBKDF2 派生 AES 密钥
        val key = deriveKey(password, salt)

        // 4. AES-GCM 加密
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, AES_ALGORITHM), spec)

        val plaintext = json.toByteArray(Charsets.UTF_8)
        val ciphertext = cipher.doFinal(plaintext)
        // cipher.doFinal 输出为 "密文 + GCM 标签（16 字节）"

        // 5. 拼接: salt + iv + ciphertext（含 GCM tag）
        val output = ByteArray(SALT_LENGTH + IV_LENGTH + ciphertext.size)
        System.arraycopy(salt, 0, output, 0, SALT_LENGTH)
        System.arraycopy(iv, 0, output, SALT_LENGTH, IV_LENGTH)
        System.arraycopy(ciphertext, 0, output, SALT_LENGTH + IV_LENGTH, ciphertext.size)

        return Base64.encodeToString(output, Base64.NO_WRAP)
    }

    /**
     * 解密并导入账号凭据列表
     *
     * @param encryptedData Base64 编码的加密字符串
     * @param password 用户设定的导出密码
     * @return 账号列表，每个元素为 (username, password)；解密失败返回 null
     */
    fun import(encryptedData: String, password: String): List<Pair<String, String>>? {
        return try {
            val data = Base64.decode(encryptedData, Base64.NO_WRAP)
            if (data.size < SALT_LENGTH + IV_LENGTH + GCM_TAG_LENGTH) return null

            // 1. 拆解 salt 和 IV
            val salt = data.copyOfRange(0, SALT_LENGTH)
            val iv = data.copyOfRange(SALT_LENGTH, SALT_LENGTH + IV_LENGTH)
            val ciphertext = data.copyOfRange(SALT_LENGTH + IV_LENGTH, data.size)

            // 2. PBKDF2 派生密钥
            val key = deriveKey(password, salt)

            // 3. AES-GCM 解密（GCM 自动验证完整性）
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, AES_ALGORITHM), spec)

            val plaintext = cipher.doFinal(ciphertext)
            val json = String(plaintext, Charsets.UTF_8)

            // 4. 解析 JSON
            parseJson(json)
        } catch (e: Exception) {
            // 密码错误、数据被篡改、格式不合法等均返回 null
            null
        }
    }

    // ==================== 内部方法 ====================

    /**
     * PBKDF2 派生密钥
     */
    private fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        return factory.generateSecret(spec).encoded
    }

    /**
     * 构建 JSON 数据结构
     */
    private fun buildJson(accounts: List<Pair<String, String>>): String {
        val jsonArray = JSONArray()
        for ((username, password) in accounts) {
            val obj = JSONObject()
            obj.put("u", username)
            obj.put("p", password)
            jsonArray.put(obj)
        }
        val root = JSONObject()
        root.put("v", FORMAT_VERSION)
        root.put("a", jsonArray)
        return root.toString()
    }

    /**
     * 解析 JSON 数据结构
     */
    private fun parseJson(json: String): List<Pair<String, String>>? {
        val root = JSONObject(json)
        val version = root.optInt("v", 0)
        if (version != FORMAT_VERSION) return null

        val jsonArray = root.optJSONArray("a") ?: return null
        if (jsonArray.length() == 0) return null

        val accounts = mutableListOf<Pair<String, String>>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.optJSONObject(i) ?: continue
            val username = obj.optString("u", "") ?: ""
            val password = obj.optString("p", "") ?: ""
            if (username.isBlank() || password.isBlank()) continue
            accounts.add(username to password)
        }
        return accounts.ifEmpty { null }
    }
}
