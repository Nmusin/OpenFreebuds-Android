package ru.nmusin.openfreebuds.model

data class BatteryState(
    val left: Int? = null,
    val right: Int? = null,
    val case: Int? = null,
    val isCharging: Boolean = false,
    val isInBox: Boolean = false
)

enum class AncMode(val id: Int) {
    OFF(0),
    ON(1),
    AWARENESS(2);

    companion object {
        fun fromId(id: Int): AncMode = entries.firstOrNull { it.id == id } ?: OFF
    }
}

enum class AncLevel(val id: Int) {
    GENERAL(0),
    COZY(1),
    ULTRA(2),
    DYNAMIC_AI(3);

    companion object {
        fun fromId(id: Int): AncLevel = entries.firstOrNull { it.id == id } ?: GENERAL
    }
}

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
