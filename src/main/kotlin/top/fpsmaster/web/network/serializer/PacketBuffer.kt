package top.fpsmaster.web.network.serializer

import java.io.*
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * 网络数据包缓冲区
 *
 * 提供类型安全的读写操作，支持各种数据类型的序列化
 */
class PacketBuffer {
    private val buffer: ByteArrayOutputStream = ByteArrayOutputStream()
    private val outputStream: DataOutputStream = DataOutputStream(buffer)
    private var inputStream: DataInputStream? = null
    private var inputBuffer: ByteArray? = null

    /**
     * 写入布尔值
     */
    fun writeBoolean(value: Boolean) = outputStream.writeBoolean(value)

    /**
     * 写入字节
     */
    fun writeByte(value: Int) = outputStream.writeByte(value)

    /**
     * 写入无符号字节
     */
    fun writeUnsignedByte(value: Int) = outputStream.writeByte(value and 0xFF)

    /**
     * 写入短整型
     */
    fun writeShort(value: Int) = outputStream.writeShort(value)

    /**
     * 写入整型
     */
    fun writeInt(value: Int) = outputStream.writeInt(value)

    /**
     * 写入长整型
     */
    fun writeLong(value: Long) = outputStream.writeLong(value)

    /**
     * 写入浮点型
     */
    fun writeFloat(value: Float) = outputStream.writeFloat(value)

    /**
     * 写入双精度型
     */
    fun writeDouble(value: Double) = outputStream.writeDouble(value)

    /**
     * 写入字符
     */
    fun writeChar(value: Char) = outputStream.writeChar(value.code)

    /**
     * 写入字符串（UTF-8编码）
     */
    fun writeString(value: String?) {
        if (value == null) {
            writeInt(-1)
            return
        }
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        writeInt(bytes.size)
        outputStream.write(bytes)
    }

    /**
     * 写入字节数组
     */
    fun writeByteArray(value: ByteArray?) {
        if (value == null) {
            writeInt(-1)
            return
        }
        writeInt(value.size)
        outputStream.write(value)
    }

    /**
     * 写入整型列表
     */
    fun writeIntList(value: List<Int>?) {
        if (value == null) {
            writeInt(-1)
            return
        }
        writeInt(value.size)
        value.forEach { writeInt(it) }
    }

    /**
     * 写入字符串列表
     */
    fun writeStringList(value: List<String>?) {
        if (value == null) {
            writeInt(-1)
            return
        }
        writeInt(value.size)
        value.forEach { writeString(it) }
    }

    /**
     * 写入JSON对象
     */
    fun writeJson(value: com.google.gson.JsonElement?) {
        if (value == null) {
            writeString(null)
            return
        }
        writeString(value.toString())
    }

    // ============ 读取操作 ============

    /**
     * 准备缓冲区用于读取
     */
    fun prepareForRead() {
        inputBuffer = buffer.toByteArray()
        inputStream = DataInputStream(ByteArrayInputStream(inputBuffer))
    }

    /**
     * 读取布尔值
     */
    fun readBoolean(): Boolean = checkInputStream().readBoolean()

    /**
     * 读取字节
     */
    fun readByte(): Byte = checkInputStream().readByte()

    /**
     * 读取无符号字节
     */
    fun readUnsignedByte(): Int = checkInputStream().readUnsignedByte()

    /**
     * 读取短整型
     */
    fun readShort(): Short = checkInputStream().readShort()

    /**
     * 读取无符号短整型
     */
    fun readUnsignedShort(): Int = checkInputStream().readUnsignedShort()

    /**
     * 读取整型
     */
    fun readInt(): Int = checkInputStream().readInt()

    /**
     * 读取长整型
     */
    fun readLong(): Long = checkInputStream().readLong()

    /**
     * 读取浮点型
     */
    fun readFloat(): Float = checkInputStream().readFloat()

    /**
     * 读取双精度型
     */
    fun readDouble(): Double = checkInputStream().readDouble()

    /**
     * 读取字符
     */
    fun readChar(): Char = checkInputStream().readChar()

    /**
     * 读取字符串（UTF-8编码）
     */
    fun readString(): String? {
        val length = readInt()
        if (length < 0) return null
        val bytes = ByteArray(length)
        checkInputStream().readFully(bytes)
        return String(bytes, StandardCharsets.UTF_8)
    }

    /**
     * 读取字节数组
     */
    fun readByteArray(): ByteArray? {
        val length = readInt()
        if (length < 0) return null
        val bytes = ByteArray(length)
        checkInputStream().readFully(bytes)
        return bytes
    }

    /**
     * 读取整型列表
     */
    fun readIntList(): List<Int>? {
        val size = readInt()
        if (size < 0) return null
        return List(size) { readInt() }
    }

    /**
     * 读取字符串列表
     */
    fun readStringList(): List<String>? {
        val size = readInt()
        if (size < 0) return null
        return List(size) { readString()!! }
    }

    /**
     * 读取JSON对象
     */
    fun readJson(): com.google.gson.JsonElement? {
        val jsonStr = readString() ?: return null
        return com.google.gson.Gson().fromJson(jsonStr, com.google.gson.JsonElement::class.java)
    }

    /**
     * 获取字节数组
     */
    fun toByteArray(): ByteArray = buffer.toByteArray()

    /**
     * 检查输入流是否可用
     */
    private fun checkInputStream(): DataInputStream {
        return inputStream ?: throw IllegalStateException("Buffer not prepared for reading. Call prepareForRead() first.")
    }

    /**
     * 清空缓冲区
     */
    fun clear() {
        buffer.reset()
        inputStream = null
        inputBuffer = null
    }

    /**
     * 获取当前缓冲区大小
     */
    fun size(): Int = buffer.size()

    companion object {
        /**
         * 从字节数组创建PacketBuffer
         */
        fun fromBytes(bytes: ByteArray): PacketBuffer {
            val buffer = PacketBuffer()
            buffer.inputBuffer = bytes
            buffer.inputStream = DataInputStream(ByteArrayInputStream(bytes))
            return buffer
        }
    }
}
