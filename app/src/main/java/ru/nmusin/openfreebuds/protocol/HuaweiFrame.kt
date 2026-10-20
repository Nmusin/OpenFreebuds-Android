package ru.nmusin.openfreebuds.protocol

import java.io.ByteArrayOutputStream

data class TlvParam(
    val tag: Int,
    val value: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as TlvParam
        return tag == other.tag && value.contentEquals(other.value)
    }

    override fun hashCode(): Int {
        var result = tag
        result = 31 * result + value.contentHashCode()
        return result
    }
}

data class HuaweiFrame(
    val service: Int,
    val command: Int,
    val params: List<TlvParam>
) {
    fun findParam(tag: Int): ByteArray? = params.firstOrNull { it.tag == tag }?.value

    fun toByteArray(): ByteArray {
        val tlvStream = ByteArrayOutputStream()
        for (param in params) {
            tlvStream.write(param.tag)
            tlvStream.write(param.value.size)
            tlvStream.write(param.value)
        }
        val tlvBytes = tlvStream.toByteArray()

        // len = len(TLV) + 3 (service(1) + command(1) + 1)
        val payloadLen = tlvBytes.size + 3

        val frameStream = ByteArrayOutputStream()
        frameStream.write(MAGIC)
        frameStream.write((payloadLen shr 8) and 0xFF)
        frameStream.write(payloadLen and 0xFF)
        frameStream.write(SEQ)
        frameStream.write(service)
        frameStream.write(command)
        frameStream.write(tlvBytes)

        val rawBeforeCrc = frameStream.toByteArray()
        val crc = HuaweiCrc.calculate(rawBeforeCrc)

        frameStream.write((crc shr 8) and 0xFF)
        frameStream.write(crc and 0xFF)

        return frameStream.toByteArray()
    }

    fun toHexString(): String = toByteArray().joinToString("") { "%02x".format(it) }

    companion object {
        const val MAGIC = 0x5A
        const val SEQ = 0x00

        fun parse(data: ByteArray): HuaweiFrame? {
            if (data.size < 7) return null
            if ((data[0].toInt() and 0xFF) != MAGIC) return null
            if (!HuaweiCrc.verify(data)) return null

            val len = ((data[1].toInt() and 0xFF) shl 8) or (data[2].toInt() and 0xFF)
            if (data.size != len + 5) return null

            val service = data[4].toInt() and 0xFF
            val command = data[5].toInt() and 0xFF

            val params = mutableListOf<TlvParam>()
            var idx = 6
            val endIdx = data.size - 2 // Before CRC

            while (idx < endIdx) {
                if (idx + 1 >= endIdx) break
                val tag = data[idx].toInt() and 0xFF
                val tlvLen = data[idx + 1].toInt() and 0xFF
                idx += 2
                if (idx + tlvLen > endIdx) break
                val value = data.copyOfRange(idx, idx + tlvLen)
                params.add(TlvParam(tag, value))
                idx += tlvLen
            }

            return HuaweiFrame(service, command, params)
        }

        fun fromHex(hex: String): HuaweiFrame? {
            val cleanHex = hex.replace(" ", "")
            val bytes = ByteArray(cleanHex.length / 2) { i ->
                cleanHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            return parse(bytes)
        }
    }
}
