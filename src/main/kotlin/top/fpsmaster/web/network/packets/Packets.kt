package top.fpsmaster.web.network.packets

import top.fpsmaster.web.network.packet.*
import top.fpsmaster.web.network.serializer.PacketBuffer

/**
 * 握手数据包
 *
 * 用于客户端连接时的握手
 */
class HandshakePacket : ClientboundPacket() {
    var protocolVersion: Int = 1
    var clientVersion: String = ""

    override fun write(buffer: PacketBuffer) {
        buffer.writeInt(protocolVersion)
        buffer.writeString(clientVersion)
    }

    override fun read(buffer: PacketBuffer) {
        protocolVersion = buffer.readInt()
        clientVersion = buffer.readString() ?: ""
    }

    override fun toString(): String {
        return "HandshakePacket(version=$protocolVersion, client='$clientVersion')"
    }
}

/**
 * 握手响应数据包
 *
 * 服务端对握手请求的响应
 */
class HandshakeResponsePacket : ServerboundPacket() {
    var success: Boolean = false
    var message: String = ""
    var serverVersion: String = ""

    override fun write(buffer: PacketBuffer) {
        buffer.writeBoolean(success)
        buffer.writeString(message)
        buffer.writeString(serverVersion)
    }

    override fun read(buffer: PacketBuffer) {
        success = buffer.readBoolean()
        message = buffer.readString() ?: ""
        serverVersion = buffer.readString() ?: ""
    }

    override fun toString(): String {
        return "HandshakeResponsePacket(success=$success, message='$message', server='$serverVersion')"
    }
}

/**
 * 心跳数据包
 *
 * 用于保持连接活跃
 */
class HeartbeatPacket : BidirectionalPacket() {
    var timestamp: Long = System.currentTimeMillis()

    override fun write(buffer: PacketBuffer) {
        buffer.writeLong(timestamp)
    }

    override fun read(buffer: PacketBuffer) {
        timestamp = buffer.readLong()
    }

    override fun toString(): String {
        return "HeartbeatPacket(timestamp=$timestamp)"
    }
}

/**
 * 执行命令数据包
 *
 * 从UI发送命令到Minecraft
 */
class ExecuteCommandPacket : ClientboundPacket() {
    var command: String = ""

    override fun write(buffer: PacketBuffer) {
        buffer.writeString(command)
    }

    override fun read(buffer: PacketBuffer) {
        command = buffer.readString() ?: ""
    }

    override fun toString(): String {
        return "ExecuteCommandPacket(command='$command')"
    }
}

/**
 * 命令响应数据包
 *
 * 命令执行的响应结果
 */
class CommandResponsePacket : ServerboundPacket() {
    var success: Boolean = false
    var response: String = ""
    var error: String? = null

    override fun write(buffer: PacketBuffer) {
        buffer.writeBoolean(success)
        buffer.writeString(response)
        buffer.writeString(error)
    }

    override fun read(buffer: PacketBuffer) {
        success = buffer.readBoolean()
        response = buffer.readString() ?: ""
        error = buffer.readString()
    }

    override fun toString(): String {
        return "CommandResponsePacket(success=$success, response='$response', error=$error)"
    }
}

/**
 * 获取玩家信息数据包
 *
 * 请求当前玩家信息
 */
class PlayerInfoRequestPacket : ClientboundPacket() {
    override fun write(buffer: PacketBuffer) {
        // 无数据
    }

    override fun read(buffer: PacketBuffer) {
        // 无数据
    }

    override fun toString(): String = "PlayerInfoRequestPacket()"
}

/**
 * 玩家信息数据包
 *
 * 返回玩家详细信息
 */
class PlayerInfoPacket : ServerboundPacket() {
    var playerName: String = ""
    var health: Float = 0f
    var food: Int = 0
    var position: Position = Position()
    var dimension: String = ""

    data class Position(
        var x: Double = 0.0,
        var y: Double = 0.0,
        var z: Double = 0.0,
        var yaw: Float = 0f,
        var pitch: Float = 0f
    )

    override fun write(buffer: PacketBuffer) {
        buffer.writeString(playerName)
        buffer.writeFloat(health)
        buffer.writeInt(food)
        buffer.writeDouble(position.x)
        buffer.writeDouble(position.y)
        buffer.writeDouble(position.z)
        buffer.writeFloat(position.yaw)
        buffer.writeFloat(position.pitch)
        buffer.writeString(dimension)
    }

    override fun read(buffer: PacketBuffer) {
        playerName = buffer.readString() ?: ""
        health = buffer.readFloat()
        food = buffer.readInt()
        position = Position(
            x = buffer.readDouble(),
            y = buffer.readDouble(),
            z = buffer.readDouble(),
            yaw = buffer.readFloat(),
            pitch = buffer.readFloat()
        )
        dimension = buffer.readString() ?: ""
    }

    override fun toString(): String {
        return "PlayerInfoPacket(player='$playerName', health=$health, food=$food, pos=$position, dim='$dimension')"
    }
}

/**
 * 日志消息数据包
 *
 * 从Minecraft发送日志到UI
 */
class LogMessagePacket : ServerboundPacket() {
    var level: LogLevel = LogLevel.INFO
    var message: String = ""
    var timestamp: Long = System.currentTimeMillis()

    enum class LogLevel {
        TRACE, DEBUG, INFO, WARN, ERROR
    }

    override fun write(buffer: PacketBuffer) {
        buffer.writeInt(level.ordinal)
        buffer.writeString(message)
        buffer.writeLong(timestamp)
    }

    override fun read(buffer: PacketBuffer) {
        level = LogLevel.entries[buffer.readInt()]
        message = buffer.readString() ?: ""
        timestamp = buffer.readLong()
    }

    override fun toString(): String {
        return "LogMessagePacket(level=$level, message='$message', timestamp=$timestamp)"
    }
}

/**
 * UI事件数据包
 *
 * 从UI发送事件到Minecraft
 */
class UIEventPacket : ClientboundPacket() {
    var eventType: String = ""
    var eventData: String? = null

    override fun write(buffer: PacketBuffer) {
        buffer.writeString(eventType)
        buffer.writeString(eventData)
    }

    override fun read(buffer: PacketBuffer) {
        eventType = buffer.readString() ?: ""
        eventData = buffer.readString()
    }

    override fun toString(): String {
        return "UIEventPacket(type='$eventType', data=$eventData)"
    }
}

/**
 * 模块列表请求数据包
 *
 * 从UI请求当前模块快照
 */
class ModuleListRequestPacket : ClientboundPacket() {
    override fun write(buffer: PacketBuffer) {
        // No payload
    }

    override fun read(buffer: PacketBuffer) {
        // No payload
    }

    override fun toString(): String = "ModuleListRequestPacket()"
}

enum class ModuleValueType {
    BOOLEAN,
    NUMBER,
    STRING
}

/**
 * 模块列表同步数据包
 *
 * 从Minecraft向UI同步模块列表和开关状态
 */
class ModuleListPacket : ServerboundPacket() {
    var modules: MutableList<ModuleEntry> = mutableListOf()

    data class ModuleEntry(
        var moduleId: String = "",
        var category: String = "",
        var enabled: Boolean = false,
        var values: MutableList<ModuleValueEntry> = mutableListOf()
    )

    data class ModuleValueEntry(
        var valueId: String = "",
        var type: ModuleValueType = ModuleValueType.BOOLEAN,
        var booleanValue: Boolean = false,
        var numberValue: Double = 0.0,
        var stringValue: String? = null,
        var minimum: Double = 0.0,
        var maximum: Double = 0.0,
        var increment: Double = 1.0,
        var unit: String = ""
    )

    override fun write(buffer: PacketBuffer) {
        buffer.writeInt(modules.size)
        modules.forEach { module ->
            buffer.writeString(module.moduleId)
            buffer.writeString(module.category)
            buffer.writeBoolean(module.enabled)
            buffer.writeInt(module.values.size)
            module.values.forEach { value ->
                buffer.writeString(value.valueId)
                buffer.writeInt(value.type.ordinal)
                buffer.writeBoolean(value.booleanValue)
                buffer.writeDouble(value.numberValue)
                buffer.writeString(value.stringValue)
                buffer.writeDouble(value.minimum)
                buffer.writeDouble(value.maximum)
                buffer.writeDouble(value.increment)
                buffer.writeString(value.unit)
            }
        }
    }

    override fun read(buffer: PacketBuffer) {
        val moduleCount = buffer.readInt()
        modules = MutableList(moduleCount) {
            ModuleEntry(
                moduleId = buffer.readString() ?: "",
                category = buffer.readString() ?: "",
                enabled = buffer.readBoolean(),
                values = MutableList(buffer.readInt()) {
                    ModuleValueEntry(
                        valueId = buffer.readString() ?: "",
                        type = ModuleValueType.entries[buffer.readInt()],
                        booleanValue = buffer.readBoolean(),
                        numberValue = buffer.readDouble(),
                        stringValue = buffer.readString(),
                        minimum = buffer.readDouble(),
                        maximum = buffer.readDouble(),
                        increment = buffer.readDouble(),
                        unit = buffer.readString() ?: ""
                    )
                }
            )
        }
    }

    override fun toString(): String {
        return "ModuleListPacket(modules=$modules)"
    }
}

/**
 * 模块开关数据包
 *
 * 从UI更新指定模块的启用状态
 */
class ModuleTogglePacket : ClientboundPacket() {
    var moduleId: String = ""
    var enabled: Boolean = false

    override fun write(buffer: PacketBuffer) {
        buffer.writeString(moduleId)
        buffer.writeBoolean(enabled)
    }

    override fun read(buffer: PacketBuffer) {
        moduleId = buffer.readString() ?: ""
        enabled = buffer.readBoolean()
    }

    override fun toString(): String {
        return "ModuleTogglePacket(moduleId='$moduleId', enabled=$enabled)"
    }
}

/**
 * 模块值更新数据包
 *
 * 从UI更新指定模块值
 */
class ModuleValueUpdatePacket : ClientboundPacket() {
    var moduleId: String = ""
    var valueId: String = ""
    var type: ModuleValueType = ModuleValueType.BOOLEAN
    var booleanValue: Boolean = false
    var numberValue: Double = 0.0
    var stringValue: String? = null

    override fun write(buffer: PacketBuffer) {
        buffer.writeString(moduleId)
        buffer.writeString(valueId)
        buffer.writeInt(type.ordinal)
        buffer.writeBoolean(booleanValue)
        buffer.writeDouble(numberValue)
        buffer.writeString(stringValue)
    }

    override fun read(buffer: PacketBuffer) {
        moduleId = buffer.readString() ?: ""
        valueId = buffer.readString() ?: ""
        type = ModuleValueType.entries[buffer.readInt()]
        booleanValue = buffer.readBoolean()
        numberValue = buffer.readDouble()
        stringValue = buffer.readString()
    }

    override fun toString(): String {
        return "ModuleValueUpdatePacket(moduleId='$moduleId', valueId='$valueId', type=$type, booleanValue=$booleanValue, numberValue=$numberValue, stringValue=$stringValue)"
    }
}
