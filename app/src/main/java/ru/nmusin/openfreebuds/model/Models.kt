package ru.nmusin.openfreebuds.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BatteryState(
    val left: Int? = null,
    val right: Int? = null,
    val case: Int? = null,
    val isCharging: Boolean = false,
    val isInBox: Boolean = false
)

enum class AncMode(val id: Int, val title: String) {
    OFF(0, "Выкл"),
    ON(1, "Шумоподавление"),
    AWARENESS(2, "Прозрачность");

    companion object {
        fun fromId(id: Int): AncMode = entries.firstOrNull { it.id == id } ?: OFF
    }
}

enum class AncLevel(val id: Int, val title: String) {
    GENERAL(0, "Обычный (General)"),
    COZY(1, "Комфорт (Cozy)"),
    ULTRA(2, "Ультра (Ultra)"),
    DYNAMIC_AI(3, "Динамический AI");

    companion object {
        fun fromId(id: Int): AncLevel = entries.firstOrNull { it.id == id } ?: GENERAL
    }
}

data class AncState(
    val mode: AncMode = AncMode.OFF,
    val level: AncLevel = AncLevel.GENERAL
)

data class EqualizerPreset(
    val id: Int,
    val name: String,
    val isCustom: Boolean = false,
    val bands: List<Int> = List(10) { 0 }
)

data class DeviceIdentity(
    val modelName: String = "",
    val firmwareVersion: String = "",
    val subModel: String = "",
    val region: String = "",
    val revision: String = "",
    val serialNumber: String = ""
)

enum class FitVerdict {
    UNKNOWN,
    TESTING,
    GOOD_FIT,
    ADJUST_FIT,
    NOISY_ENVIRONMENT
}

data class FitTestState(
    val leftStatus: Int? = null,
    val rightStatus: Int? = null,
    val verdict: FitVerdict = FitVerdict.UNKNOWN,
    val isTesting: Boolean = false
)

enum class LogDirection {
    TX, // Host -> Earbuds
    RX, // Earbuds -> Host
    INFO
}

data class LogEntry(
    val id: Long = System.nanoTime(),
    val timestamp: String = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date()),
    val direction: LogDirection,
    val cmdHex: String,
    val summary: String,
    val rawHex: String,
    val details: String = ""
)
