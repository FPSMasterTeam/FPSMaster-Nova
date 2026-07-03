package top.fpsmaster.web.music.crypto

/**
 * QQ 系列服务通用的 hash33（"getGTK" / ptqrtoken 算法）。
 *
 * `h = (h << 5) + h + c` 对每个字符累加，最后与 0x7fffffff 取与。
 * 用于：ptqrtoken(qrsig) 与 g_tk(p_skey/musickey, 种子 5381)。
 */
object QQHash {
    fun hash33(s: String, seed: Int = 0): Int {
        var h = seed
        for (c in s) {
            h = (h shl 5) + h + c.code
        }
        return h and 0x7fffffff
    }
}
