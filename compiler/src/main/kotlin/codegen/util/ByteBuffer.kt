package yukifuri.lang.lingspled.compiler.codegen.util

open class ByteBuffer(
    val order: ByteOrder = ByteOrder.LITTLE_ENDIAN
) {
    var ptr = 0
    var buffer = mutableListOf<Byte>()

    val size: Int 
        get() = buffer.size

    fun put(byte: Byte): ByteBuffer {
        buffer.add(byte)
        return this
    }

    fun put(short: Short, order: ByteOrder = this.order): ByteBuffer {
        val v = short.toInt() and 0xFFFF
        if (order == ByteOrder.LITTLE_ENDIAN) {
            buffer.add(v.toByte())
            buffer.add((v shr 8).toByte())
        } else {
            buffer.add((v shr 8).toByte())
            buffer.add(v.toByte())
        }
        return this
    }

    fun put(int: Int, order: ByteOrder = this.order): ByteBuffer {
        if (order == ByteOrder.LITTLE_ENDIAN) {
            buffer.add(int.toByte())
            buffer.add((int shr 8).toByte())
            buffer.add((int shr 16).toByte())
            buffer.add((int shr 24).toByte())
        } else {
            buffer.add((int shr 24).toByte())
            buffer.add((int shr 16).toByte())
            buffer.add((int shr 8).toByte())
            buffer.add(int.toByte())
        }
        return this
    }

    fun put(long: Long, order: ByteOrder = this.order): ByteBuffer {
        if (order == ByteOrder.LITTLE_ENDIAN) {
            for (i in 0 until 8) buffer.add((long shr (i * 8)).toByte())
        } else {
            for (i in 7 downTo 0) buffer.add((long shr (i * 8)).toByte())
        }
        return this
    }

    fun put(float: Float, order: ByteOrder = this.order): ByteBuffer {
        return put(float.toRawBits(), order)
    }

    fun put(double: Double, order: ByteOrder = this.order): ByteBuffer {
        return put(double.toRawBits(), order)
    }

    fun put(string: String, charset: Charset = Charset.UTF_16LE): ByteBuffer {
        val bytes = string.toByteArray(charset.javaCharset)
        // length-prefixed: 4-byte length (in bytes) + data
        put(bytes.size)
        for (b in bytes) buffer.add(b)
        return this
    }

    fun put(bytes: ByteArray): ByteBuffer {
        for (b in bytes) buffer.add(b)
        return this
    }

    fun put(bytes: List<Byte>): ByteBuffer {
        for (b in bytes) buffer.add(b)
        return this
    }

    fun getByte(): Byte {
        return buffer[ptr++]
    }

    fun getShort(order: ByteOrder = this.order): Short {
        val b0 = buffer[ptr].toInt() and 0xFF
        val b1 = buffer[ptr + 1].toInt() and 0xFF
        ptr += 2
        return if (order == ByteOrder.LITTLE_ENDIAN) {
            (b0 or (b1 shl 8)).toShort()
        } else {
            ((b0 shl 8) or b1).toShort()
        }
    }

    fun getInt(order: ByteOrder = this.order): Int {
        val b0 = buffer[ptr].toInt() and 0xFF
        val b1 = buffer[ptr + 1].toInt() and 0xFF
        val b2 = buffer[ptr + 2].toInt() and 0xFF
        val b3 = buffer[ptr + 3].toInt() and 0xFF
        ptr += 4
        return if (order == ByteOrder.LITTLE_ENDIAN) {
            b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
        } else {
            (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
        }
    }

    fun getLong(order: ByteOrder = this.order): Long {
        var result = 0L
        if (order == ByteOrder.LITTLE_ENDIAN) {
            for (i in 7 downTo 0) result = (result shl 8) or (buffer[ptr + i].toLong() and 0xFF)
        } else {
            for (i in 0 until 8) result = (result shl 8) or (buffer[ptr + i].toLong() and 0xFF)
        }
        ptr += 8
        return result
    }

    fun getFloat(order: ByteOrder = this.order): Float {
        return Float.fromBits(getInt(order))
    }

    fun getDouble(order: ByteOrder = this.order): Double {
        return Double.fromBits(getLong(order))
    }

    fun getString(byteLen: Int = getInt(), charset: Charset = Charset.UTF_16LE): String {
        val bytes = ByteArray(byteLen) { buffer[ptr + it] }
        ptr += byteLen
        return String(bytes, charset.javaCharset)
    }

    fun getBytes(byteLen: Int): List<Byte> {
        val bytes = buffer.subList(ptr, ptr + byteLen)
        ptr += byteLen
        return bytes
    }

    fun patchShort(offset: Int, value: Short) {
        val v = value.toInt() and 0xFFFF
        buffer[offset]     = v.toByte()
        buffer[offset + 1] = (v shr 8).toByte()
    }

    fun patchInt(offset: Int, value: Int) {
        buffer[offset]     = value.toByte()
        buffer[offset + 1] = (value shr 8).toByte()
        buffer[offset + 2] = (value shr 16).toByte()
        buffer[offset + 3] = (value shr 24).toByte()
    }

    fun clear() {
        ptr = 0
        buffer.clear()
    }

    fun build(): ByteArray = ByteArray(buffer.size) { buffer[it] }

    enum class ByteOrder { LITTLE_ENDIAN, BIG_ENDIAN }

    enum class Charset(val javaCharset: java.nio.charset.Charset) {
        UTF_8(Charsets.UTF_8),
        UTF_16LE(Charsets.UTF_16LE),
        UTF_16BE(Charsets.UTF_16BE),
        ASCII(Charsets.ISO_8859_1)
    }
}