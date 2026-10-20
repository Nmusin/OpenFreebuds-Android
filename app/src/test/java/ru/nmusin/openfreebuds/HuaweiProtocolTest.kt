package ru.nmusin.openfreebuds

import org.junit.Assert.*
import org.junit.Test
import ru.nmusin.openfreebuds.model.AncLevel
import ru.nmusin.openfreebuds.model.AncMode
import ru.nmusin.openfreebuds.protocol.FreeBuds5iProtocol
import ru.nmusin.openfreebuds.protocol.HuaweiCrc
import ru.nmusin.openfreebuds.protocol.HuaweiFrame

class HuaweiProtocolTest {

    @Test
    fun testCrc16Xmodem() {
        val raw = byteArrayOf(
            0x5a, 0x00, 0x09, 0x00, 0x01, 0x08, 0x01, 0x00, 0x02, 0x00, 0x03, 0x00
        )
        val crc = HuaweiCrc.calculate(raw)
        assertEquals(0xfbb9, crc)

        val fullFrame = raw + byteArrayOf(0xfb.toByte(), 0xb9.toByte())
        assertTrue(HuaweiCrc.verify(fullFrame))
    }

    @Test
    fun testBigDataCapabilityFrame() {
        val reply = FreeBuds5iProtocol.buildBigDataCapabilityReply()
        assertEquals("5a0006000a0e020100fb3f", reply.toHexString())
    }

    @Test
    fun testAncStateParsingAndDescribing() {
        // Live 2B2A response: tag 1 = 02 01 -> level = 2 (Ultra), mode = 1 (ANC ON)
        val frameAncUltra = HuaweiFrame.fromHex("5a0007002b2a010202010511")
        assertNotNull(frameAncUltra)

        val parsed = FreeBuds5iProtocol.parseAncState(frameAncUltra!!)
        assertNotNull(parsed)
        assertEquals(AncMode.ON, parsed!!.mode)
        assertEquals(AncLevel.ULTRA, parsed.level)

        val desc = FreeBuds5iProtocol.describeFrame(frameAncUltra, isTx = false)
        assertTrue(desc.second.contains("Ультра"))
        assertTrue(desc.second.contains("Шумоподавление"))

        // Button press notify 2B03: tag 1 = 02 -> switch to Awareness
        val frameButtonNotify = HuaweiFrame.fromHex("5a0006002b03010102bc0d")
        assertNotNull(frameButtonNotify)
        val parsedButton = FreeBuds5iProtocol.parseAncState(frameButtonNotify!!)
        assertEquals(AncMode.AWARENESS, parsedButton!!.mode)
    }

    @Test
    fun testWindSuppressionInvertedPolarity() {
        val setOn = FreeBuds5iProtocol.buildWindSuppressionSetRequest(enabled = true)
        assertEquals("5a0006002b94010100aebe", setOn.toHexString())

        val setOff = FreeBuds5iProtocol.buildWindSuppressionSetRequest(enabled = false)
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
        val frame = HuaweiFrame.fromHex("5a0014000108010164020364004b03030000000402140a507c")
        assertNotNull(frame)

        val state = FreeBuds5iProtocol.parseBattery(frame!!)
        assertEquals(100, state.left)
        assertNull(state.right)
        assertEquals(75, state.case)
        assertFalse(state.isCharging)
    }

    @Test
    fun testCustomEqualizerOrderAndTruncation() {
        val bands = listOf(15, -10, -10, -10, 0, 25, 20, -30, 65, -10)
        val frame = FreeBuds5iProtocol.buildCustomEqualizerRequest(100, "Harman", bands)

        assertEquals(
            "5a0020002b4901016402010a050101030a0ff6f6f6001914e241f604064861726d616e59ec",
            frame.toHexString()
        )
    }
}
