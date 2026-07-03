package top.fpsmaster.web.music.crypto

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

/**
 * QQ 音乐 web 端请求签名（"zzc" 版本）。
 *
 * 用于 `https://u.y.qq.com/cgi-bin/musics.fcg` 的 `sign` 查询参数：
 * 对请求体 JSON 取 SHA-1（大写十六进制），从中按固定索引取头尾字符，
 * 中段由 SHA-1 的每个字节与固定扰动数组做 XOR 后 base64（去掉 `\ / + =`）。
 * 最终 `("zzc" + head + middle + tail).toLowerCase()`。
 *
 * 算法为公开事实，此处为独立实现。
 */
object QQSign {

    private val PART1_INDEXES = intArrayOf(23, 14, 6, 36, 16, 7, 19)
    private val PART2_INDEXES = intArrayOf(16, 1, 32, 12, 19, 27, 8, 5)
    private val SCRAMBLE = intArrayOf(
        89, 39, 179, 150, 218, 82, 58, 252, 177, 52,
        186, 123, 120, 64, 242, 133, 143, 161, 121, 179,
    )

    fun sign(payload: String): String {
        val sha1 = MessageDigest.getInstance("SHA-1")
            .digest(payload.toByteArray(StandardCharsets.UTF_8))
        val hashHex = sha1.joinToString("") { "%02X".format(it) }

        val part1 = buildString { for (i in PART1_INDEXES) append(hashHex[i]) }
        val part2 = buildString { for (i in PART2_INDEXES) append(hashHex[i]) }

        val part3 = ByteArray(SCRAMBLE.size)
        for (i in SCRAMBLE.indices) {
            val hashByte = hashHex.substring(i * 2, i * 2 + 2).toInt(16)
            part3[i] = (SCRAMBLE[i] xor hashByte).toByte()
        }
        val b64 = Base64.getEncoder().encodeToString(part3)
            .replace(Regex("[\\\\/+=]"), "")

        return "zzc$part1$b64$part2".lowercase()
    }
}
