package top.fpsmaster.web.music.crypto

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 网易云音乐 web 端 "weapi" 加密。
 *
 * 这是网易云网页接口用的标准加密方案（长期稳定）：明文经过两层 AES-128-CBC，
 * 第一层用固定密钥，第二层用随机 16 位密钥；随机密钥再用固定的 RSA 公钥无填充加密。
 * 请求体最终为表单字段 `params` + `encSecKey`。
 *
 * 算法为公开事实（非某仓库的具体代码实现），此处为独立实现。
 */
object NeteaseCrypto {

    private const val PRESET_KEY = "0CoJUm6Qyw8W8jud"
    private const val IV = "0102030405060708"
    private const val BASE62 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    // RSA 公钥（固定）。指数 0x10001，模数如下。
    private const val RSA_EXPONENT = "010001"
    private const val RSA_MODULUS =
        "00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725" +
        "152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e0312" +
        "ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424" +
        "d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7"

    private val rsaModulus = BigInteger(RSA_MODULUS, 16)
    private val rsaExponent = BigInteger(RSA_EXPONENT, 16)

    /** weapi 加密结果：表单参数。 */
    data class Encrypted(val params: String, val encSecKey: String)

    /**
     * 加密明文（通常是 JSON 字符串）。
     *
     * @param plain 明文。
     * @param secKeyOverride 仅测试用途：固定随机密钥以获得确定输出。生产环境传 null。
     */
    fun weapi(plain: String, secKeyOverride: String? = null): Encrypted {
        val secKey = secKeyOverride ?: randomKey(16)
        val first = aesCbcBase64(plain, PRESET_KEY)
        val params = aesCbcBase64(first, secKey)
        val encSecKey = rsaEncrypt(secKey)
        return Encrypted(params, encSecKey)
    }

    private fun aesCbcBase64(text: String, key: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), "AES")
        val ivSpec = IvParameterSpec(IV.toByteArray(StandardCharsets.UTF_8))
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        val encrypted = cipher.doFinal(text.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(encrypted)
    }

    /**
     * 网易云 RSA：明文倒序 → 按字节转 BigInteger → c = m^e mod n（无填充）→ 左补零到 256 位十六进制。
     */
    private fun rsaEncrypt(text: String): String {
        val reversed = text.reversed()
        val message = BigInteger(1, reversed.toByteArray(StandardCharsets.UTF_8))
        val cipher = message.modPow(rsaExponent, rsaModulus)
        val hex = cipher.toString(16)
        return hex.padStart(256, '0')
    }

    private fun randomKey(length: Int): String {
        val sb = StringBuilder(length)
        val rnd = java.security.SecureRandom()
        repeat(length) { sb.append(BASE62[rnd.nextInt(BASE62.length)]) }
        return sb.toString()
    }
}
