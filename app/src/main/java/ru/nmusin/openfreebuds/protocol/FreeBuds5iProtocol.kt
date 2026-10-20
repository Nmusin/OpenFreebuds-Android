package ru.nmusin.openfreebuds.protocol

import ru.nmusin.openfreebuds.model.*

/**
 * High-level Protocol engine for HUAWEI FreeBuds 5i (T0014).
 * Directly translated and verified against huawei_protocol.py and live logs.
 */
object FreeBuds5iProtocol {

    // Services
    const val SERVICE_DEVICE_MANAGEMENT = 0x01
    const val SERVICE_MANAGEMENT = 0x0A
    const val SERVICE_INTELLIGENT = 0x2B

    // Commands
    const val CMD_DEVICE_INFO = 0x07
    const val CMD_BATTERY_READ = 0x08
    const val CMD_BATTERY_NOTIFY = 0x27
    const val CMD_BIG_DATA_CAPABILITY = 0x0E
    const val CMD_DEVICE_IDENTITY = 0x0A
    const val CMD_ANC_WRITE = 0x04
    const val CMD_ANC_READ = 0x2A
    const val CMD_WIND_SUPPRESSION_WRITE = 0x94
    const val CMD_WIND_SUPPRESSION_READ = 0x95
    const val CMD_EQ_WRITE = 0x49
    const val CMD_EQ_READ = 0x4A
    const val CMD_FIND_MY_BUDS_WRITE = 0x5D
    const val CMD_FIND_MY_BUDS_STATE = 0x5E
    const val CMD_FIT_TEST_WRITE = 0x26
    const val CMD_FIT_TEST_NOTIFY = 0x27

    // --- Frame Builders ---

    /**
     * Handshake reply for 0A0E big-data capability query.
     * Prevents spontaneous disconnections on FreeBuds 5i.
     */
    fun buildBigDataCapabilityReply(): HuaweiFrame {
        return HuaweiFrame(
            service = SERVICE_MANAGEMENT,
            command = CMD_BIG_DATA_CAPABILITY,
            params = listOf(TlvParam(tag = 2, value = byteArrayOf(0x00)))
        )
    }

    /**
     * Read battery request (01 08 params 1, 2, 3).
     */
    fun buildBatteryReadRequest(): HuaweiFrame {
        return HuaweiFrame(
            service = SERVICE_DEVICE_MANAGEMENT,
            command = CMD_BATTERY_READ,
            params = listOf(
                TlvParam(1, byteArrayOf()),
                TlvParam(2, byteArrayOf()),
                TlvParam(3, byteArrayOf())
            )
        )
    }

    /**
     * Read device identity info (2B 0A params 1, 2, 3).
     */
    fun buildDeviceIdentityRequest(): HuaweiFrame {
        return HuaweiFrame(
            service = SERVICE_INTELLIGENT,
            command = CMD_DEVICE_IDENTITY,
            params = listOf(
                TlvParam(1, byteArrayOf()),
                TlvParam(2, byteArrayOf()),
                TlvParam(3, byteArrayOf())
            )
        )
    }

    /**
     * Set ANC mode and level (2B 04 tag 1 = [mode, level]).
     * Level 0xFF = keep current level.
     */
    fun buildAncSetRequest(mode: AncMode, level: AncLevel = AncLevel.GENERAL): HuaweiFrame {
        val levelByte = if (mode == AncMode.ON) level.id.toByte() else 0xFF.toByte()
        val data = if (mode == AncMode.OFF) byteArrayOf(0x00, 0x00) else byteArrayOf(mode.id.toByte(), levelByte)
        return HuaweiFrame(
            service = SERVICE_INTELLIGENT,
            command = CMD_ANC_WRITE,
            params = listOf(TlvParam(1, data))
        )
    }

    /**
     * Read ANC state (2B 2A param 1, 2).
     */
    fun buildAncReadRequest(): HuaweiFrame {
        return HuaweiFrame(
            service = SERVICE_INTELLIGENT,
            command = CMD_ANC_READ,
            params = listOf(
                TlvParam(1, byteArrayOf()),
                TlvParam(2, byteArrayOf())
            )
        )
    }

    /**
     * Set Wind noise suppression toggle (2B 94).
     * IMPORTANT: Inverted wire polarity! 0 = ON, 1 = OFF.
     */
    fun buildWindSuppressionSetRequest(enabled: Boolean): HuaweiFrame {
        val wireValue = if (enabled) 0x00.toByte() else 0x01.toByte()
        return HuaweiFrame(
            service = SERVICE_INTELLIGENT,
            command = CMD_WIND_SUPPRESSION_WRITE,
            params = listOf(TlvParam(1, byteArrayOf(wireValue)))
        )
    }

    /**
     * Read Wind noise suppression status (2B 95).
     */
    fun buildWindSuppressionReadRequest(): HuaweiFrame {
        return HuaweiFrame(
            service = SERVICE_INTELLIGENT,
            command = CMD_WIND_SUPPRESSION_READ,
            params = listOf(TlvParam(1, byteArrayOf()))
        )
    }

    /**
     * Set built-in equalizer preset (2B 49).
     * IDs: 1 (default), 2 (bass), 3 (treble), 9 (voices).
     */
    fun buildEqualizerSelectPresetRequest(presetId: Int): HuaweiFrame {
        return HuaweiFrame(
            service = SERVICE_INTELLIGENT,
            command = CMD_EQ_WRITE,
            params = listOf(TlvParam(1, byteArrayOf(presetId.toByte())))
        )
    }

    /**
     * Set custom 10-band equalizer preset in slot 100-102 (2B 49).
     * Strict TLV order: tag 1, tag 2, tag 5, tag 3, tag 4.
     * Name truncated to maximum 24 UTF-8 bytes on character boundaries.
     */
    fun buildCustomEqualizerRequest(slotId: Int, name: String, bands: List<Int>): HuaweiFrame {
        require(slotId in 100..102) { "Slot must be 100..102" }
        require(bands.size == 10) { "Must have exactly 10 frequency bands" }

        val truncatedNameBytes = truncateUtf8(name, 24)
        val bandBytes = ByteArray(10) { i -> bands[i].coerceIn(-100, 100).toByte() }

        return HuaweiFrame(
            service = SERVICE_INTELLIGENT,
            command = CMD_EQ_WRITE,
            params = listOf(
                TlvParam(1, byteArrayOf(slotId.toByte())),
                TlvParam(2, byteArrayOf(10.toByte())), // 10 rows
                TlvParam(5, byteArrayOf(0x01)),         // Mode active flag
                TlvParam(3, bandBytes),                 // 10 band bytes
                TlvParam(4, truncatedNameBytes)         // Capped UTF-8 name
            )
        )
    }

    /**
     * "Find my earbuds" ringer (2B 5D tag 1 = [side, state]).
     * side: 0 = left, 1 = right; state: 0 = start ringing, 1 = stop.
     */
    fun buildFindMyBudsRequest(isRight: Boolean, start: Boolean): HuaweiFrame {
        val side = if (isRight) 0x01.toByte() else 0x00.toByte()
        val state = if (start) 0x00.toByte() else 0x01.toByte()
        return HuaweiFrame(
            service = SERVICE_INTELLIGENT,
            command = CMD_FIND_MY_BUDS_WRITE,
            params = listOf(TlvParam(1, byteArrayOf(side, state)))
        )
    }

    /**
     * Fit test control (2B 26): 01 00 = start, 03 00 = exit/reset.
     */
    fun buildFitTestControlRequest(start: Boolean): HuaweiFrame {
        val tag = if (start) 1 else 3
        return HuaweiFrame(
            service = SERVICE_INTELLIGENT,
            command = CMD_FIT_TEST_WRITE,
            params = listOf(TlvParam(tag, byteArrayOf()))
        )
    }

    // --- Parsers ---

    /**
     * Parse battery from 01 08 (read response) or 01 27 (notify push).
     * Treats 0x00 as unreported level (bud docked in case).
     */
    fun parseBattery(frame: HuaweiFrame, current: BatteryState = BatteryState()): BatteryState {
        var left = current.left
        var right = current.right
        var case = current.case
        var isCharging = current.isCharging
        var isInBox = current.isInBox

        val levels = frame.findParam(2)
        if (levels != null && levels.size >= 3) {
            val l = levels[0].toInt() and 0xFF
            val r = levels[1].toInt() and 0xFF
            val c = levels[2].toInt() and 0xFF
            if (l != 0) left = l
            if (r != 0) right = r
            if (c != 0) case = c
        }

        val chargingFlags = frame.findParam(3)
        if (chargingFlags != null && chargingFlags.isNotEmpty()) {
            isCharging = chargingFlags.any { it.toInt() == 1 }
        }

        val inBoxFlags = frame.findParam(5)
        if (inBoxFlags != null && inBoxFlags.isNotEmpty()) {
            isInBox = inBoxFlags.any { it.toInt() == 1 }
        }

        return BatteryState(left, right, case, isCharging, isInBox)
    }

    /**
     * Parse wind suppression state from 2B 95 (read or notify).
     * Wire 0 = ON, wire 1 = OFF.
     */
    fun parseWindSuppression(frame: HuaweiFrame): Boolean? {
        val p1 = frame.findParam(1) ?: return null
        if (p1.isEmpty()) return null
        return (p1[0].toInt() and 0xFF) == 0
    }

    /**
     * Parse device identity from 2B 0A response.
     */
    fun parseDeviceIdentity(frame: HuaweiFrame): DeviceIdentity {
        fun getString(tag: Int): String {
            val bytes = frame.findParam(tag) ?: return ""
            return runCatching { String(bytes, Charsets.US_ASCII) }.getOrDefault("")
        }

        return DeviceIdentity(
            serialNumber = getString(1),
            modelName = getString(2),
            region = getString(3),
            revision = getString(4),
            subModel = getString(5),
            firmwareVersion = getString(6)
        )
    }

    /**
     * Parse fit test notify (2B 27 tag 1 = 0..5).
     * Verdict rule: left == 0 && right == 2 -> GOOD_FIT.
     */
    fun parseFitTestNotify(frame: HuaweiFrame, current: FitTestState): FitTestState {
        val p1 = frame.findParam(1) ?: return current
        if (p1.isEmpty()) return current
        val code = p1[0].toInt() and 0xFF

        var left = current.leftStatus
        var right = current.rightStatus

        when (code) {
            0, 1, 4 -> left = code
            2, 3, 5 -> right = code
        }

        val verdict = when {
            left == 4 || right == 5 -> FitVerdict.NOISY_ENVIRONMENT
            left == 0 && right == 2 -> FitVerdict.GOOD_FIT
            left != null && right != null -> FitVerdict.ADJUST_FIT
            else -> FitVerdict.TESTING
        }

        return FitTestState(left, right, verdict, isTesting = (verdict == FitVerdict.TESTING))
    }

    private fun truncateUtf8(text: String, maxBytes: Int): ByteArray {
        var result = text
        while (result.toByteArray(Charsets.UTF_8).size > maxBytes && result.isNotEmpty()) {
            result = result.dropLast(1)
        }
        return result.toByteArray(Charsets.UTF_8)
    }
}
