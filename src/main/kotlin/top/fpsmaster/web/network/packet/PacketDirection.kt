package top.fpsmaster.web.network.packet

/**
 * 数据包传输方向
 */
enum class PacketDirection {
    /**
     * 从客户端发送到服务端
     */
    CLIENT_TO_SERVER,

    /**
     * 从服务端发送到客户端
     */
    SERVER_TO_CLIENT,

    /**
     * 双向通信
     */
    BIDIRECTIONAL
}
