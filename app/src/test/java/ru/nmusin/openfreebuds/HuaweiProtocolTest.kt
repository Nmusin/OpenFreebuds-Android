package ru.nmusin.openfreebuds

import org.junit.Assert.*
import org.junit.Test
import ru.nmusin.openfreebuds.model.AncMode
import ru.nmusin.openfreebuds.protocol.FreeBuds5iProtocol
import ru.nmusin.openfreebuds.protocol.HuaweiCrc
import ru.nmusin.openfreebuds.protocol.HuaweiFrame

class HuaweiProtocolTest {

    @Test
    fun testCrc16Xmodem() {
        // Battery read frame before CRC: 5a 00 09 00 01 08 01 00 02 00 03 00
        val raw = byteArrayOf(
            0x5a, 0x00, 0x09, 0x00, 0x01, 0x08, 0x01, 0x00, 0x02, 0x00, 0x03, 0x00
        )
        val crc = HuaweiCrc.calculate(raw)
        // Expected CRC: 0xfbb9
        assertEquals(0xfbb9, crc)

        val fullFrame = raw + byteArrayOf(0xfb.toByte(), 0xb9.toByte())
        assertTrue(HuaweiCrc.verify(fullFrame))
    }

    @Test
    fun testBigDataCapabilityFrame() {
        val reply = FreeBuds5iProtocol.buildBigDataCapabilityReply()
        // Expected from spp_readonly logs and huawei_protocol.py: 5a0006000a0e020100fb3f
        assertEquals("5a0006000a0e020100fb3f", reply.toHexString())
    }

    @Test
    fun testWindSuppressionInvertedPolarity() {
        val setOn = FreeBuds5iProtocol.buildWindSuppressionSetRequest(enabled = true)
        // Wire 0 = ON: 5a0006002b94010100aebe
        assertEquals("5a0006002b94010100aebe", setOn.toHexString())

        val setOff = FreeBuds5iProtocol.buildWindSuppressionSetRequest(enabled = false)
        // Wire 1 = OFF: 5a0006002b94010101be9f
        assertEquals("5a0006002b94010101be9f", setOff.toHexString())
    }

    @Test
    fun testFindMyBudsFrames() {
        val leftStart = FreeBuds5iProtocol.buildFindMyBudsRequest(isRight = false, start = true)
        assertEquals("5a0007002b5d010200006f63", leftStart.toHexString())

        val rightStop = FreeBuds5iProtocol.buildFindMyBudsRequest(isRight = true, start = false)
        assertEquals("5a0007002b5d010201014c73", rightStop.toHexString())
    }

    @Test
    fun testBatteryZeroLevelIsIgnored() {
        // Frame with right bud in case (tag 2 = 64 00 4B, where 00 is right)
        // 5a0014000108010164020364004b03030000000402140a507c
        val frame = HuaweiFrame.fromHex("5a0014000108010164020364004b03030000000402140a507c")
        assertNotNull(frame)

        val state = FreeBuds5iProtocol.parseBattery(frame!!)
        assertEquals(100, state.left)
        assertNull(state.right) // 0x00 must not become 0%
        assertEquals(75, state.case)
        assertFalse(state.isCharging)
    }

    @Test
    fun testCustomEqualizerOrderAndTruncation() {
        // Slot 100, Harman curve
        val bands = listOf(15, -10, -10, -10, 0, 25, 20, -30, 65, -10)
        val frame = FreeBuds5iProtocol.buildCustomEqualizerRequest(100, "Harman", bands)

        // Expected live frame: 5a0020002b4901016402010a050101030a0ff6f6f6001914e241f604064861726d616e59ec
        assertEquals(
            "5a0020002b4901016402010a050101030a0ff6f6f6001914e241f604064861726d616e59ec",
            frame.toHexString()
        )
    }
}
