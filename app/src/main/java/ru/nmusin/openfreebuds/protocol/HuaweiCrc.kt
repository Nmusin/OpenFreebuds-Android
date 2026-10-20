package ru.nmusin.openfreebuds.protocol

/**
 * CRC16/XMODEM calculation matching Huawei SPP protocol specification:
 * Polynomial: 0x1021, Initial: 0x0000, no reflection, no final XOR.
 */
object HuaweiCrc {
    private const val POLYNOMIAL = 0x1021

    fun calculate(data: ByteArray, offset: Int = 0, length: Int = data.size): Int {
        var crc = 0x0000
        for (i in offset until (offset + length)) {
            val byte = data[i].toInt() and 0xFF
            crc = crc xor (byte shl 8)
            for (j in 0 until 8) {
                crc = if ((crc and 0x8000) != 0) {
                    (crc shl 1) xor POLYNOMIAL
                } else {
                    crc shl 1
                }
                crc = crc and 0xFFFF
            }
        }
        return crc
    }

    fun verify(frame: ByteArray): Boolean {
        if (frame.size < 5) return false
        val expectedCrc = ((frame[frame.size - 2].toInt() and 0xFF) shl 8) or
                (frame[frame.size - 1].toInt() and 0xFF)
        val actualCrc = calculate(frame, 0, frame.size - 2)
        return expectedCrc == actualCrc
    }
}
