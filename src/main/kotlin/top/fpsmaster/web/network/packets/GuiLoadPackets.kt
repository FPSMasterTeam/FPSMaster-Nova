package top.fpsmaster.web.network.packets

import top.fpsmaster.web.network.packet.ClientboundPacket
import top.fpsmaster.web.network.packet.ServerboundPacket
import top.fpsmaster.web.network.serializer.PacketBuffer

/**
 * GUI加载事件数据包
 *
 * 从Minecraft发送到UI，触发UI的加载动画
 */
class GuiLoadEventPacket : ClientboundPacket() {
    var eventType: String = "open"  // open, close, refresh
    var timestamp: Long = System.currentTimeMillis()
    var extraData: String? = null   // 额外数据

    override fun write(buffer: PacketBuffer) {
        buffer.writeString(eventType)
        buffer.writeLong(timestamp)
        buffer.writeString(extraData)
    }

    override fun read(buffer: PacketBuffer) {
        eventType = buffer.readString() ?: "open"
        timestamp = buffer.readLong()
        extraData = buffer.readString()
    }

    override fun toString(): String {
        return "GuiLoadEventPacket(event='$eventType', timestamp=$timestamp, data=$extraData)"
    }
}

/**
 * GUI加载确认数据包
 *
 * UI确认收到加载事件，Minecraft收到后开始渲染
 */
class GuiLoadAckPacket : ServerboundPacket() {
    var success: Boolean = false
    var message: String = ""
    var timestamp: Long = System.currentTimeMillis()

    override fun write(buffer: PacketBuffer) {
        buffer.writeBoolean(success)
        buffer.writeString(message)
        buffer.writeLong(timestamp)
    }

    override fun read(buffer: PacketBuffer) {
        success = buffer.readBoolean()
        message = buffer.readString() ?: ""
        timestamp = buffer.readLong()
    }

    override fun toString(): String {
        return "GuiLoadAckPacket(success=$success, message='$message', timestamp=$timestamp)"
    }
}