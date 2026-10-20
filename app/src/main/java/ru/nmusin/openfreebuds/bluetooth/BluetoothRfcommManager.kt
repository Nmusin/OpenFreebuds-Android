package ru.nmusin.openfreebuds.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.nmusin.openfreebuds.model.*
import ru.nmusin.openfreebuds.protocol.FreeBuds5iProtocol
import ru.nmusin.openfreebuds.protocol.HuaweiFrame
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

class BluetoothRfcommManager(
    private val scope: CoroutineScope
) {
    private val TAG = "RfcommManager"

    // Standard SPP UUID and FreeBuds 5i channel 16
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus = _connectionStatus.asStateFlow()

    private val _batteryState = MutableStateFlow(BatteryState())
    val batteryState = _batteryState.asStateFlow()

    private val _ancState = MutableStateFlow(AncState())
    val ancState = _ancState.asStateFlow()

    private val _windSuppression = MutableStateFlow(false)
    val windSuppression = _windSuppression.asStateFlow()

    private val _currentEqualizerId = MutableStateFlow(1)
    val currentEqualizerId = _currentEqualizerId.asStateFlow()

    private val _deviceIdentity = MutableStateFlow(DeviceIdentity())
    val deviceIdentity = _deviceIdentity.asStateFlow()

    private val _fitTestState = MutableStateFlow(FitTestState())
    val fitTestState = _fitTestState.asStateFlow()

    private val _logEntries = MutableStateFlow<List<LogEntry>>(emptyList())
    val logEntries = _logEntries.asStateFlow()

    private var readerJob: Job? = null

    fun addLog(entry: LogEntry) {
        val current = _logEntries.value.toMutableList()
        current.add(0, entry) // Newest on top
        if (current.size > 200) {
            _logEntries.value = current.take(200)
        } else {
            _logEntries.value = current
        }
    }

    fun clearLogs() {
        _logEntries.value = emptyList()
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        _connectionStatus.value = ConnectionStatus.CONNECTING
        addLog(
            LogEntry(
                direction = LogDirection.INFO,
                cmdHex = "CONNECT",
                summary = "Подключение...",
                rawHex = device.address,
                details = "Инициализация RFCOMM сокета (канал 16)"
            )
        )
        try {
            Log.d(TAG, "Connecting to ${device.address} via RFCOMM SPP...")
            val s = try {
                val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                m.invoke(device, 16) as BluetoothSocket
            } catch (e: Exception) {
                device.createRfcommSocketToServiceRecord(SPP_UUID)
            }

            s.connect()
            socket = s
            inputStream = s.inputStream
            outputStream = s.outputStream

            _connectionStatus.value = ConnectionStatus.CONNECTED
            addLog(
                LogEntry(
                    direction = LogDirection.INFO,
                    cmdHex = "CONNECTED",
                    summary = "Соединение установлено",
                    rawHex = device.address,
                    details = "Канал RFCOMM активен"
                )
            )

            startReaderLoop()

            // Initialize handshake and queries
            initDevice()

            true
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed", e)
            _connectionStatus.value = ConnectionStatus.ERROR
            addLog(
                LogEntry(
                    direction = LogDirection.INFO,
                    cmdHex = "ERROR",
                    summary = "Ошибка подключения",
                    rawHex = "",
                    details = e.message ?: "Unknown socket error"
                )
            )
            disconnect()
            false
        }
    }

    private suspend fun initDevice() {
        sendFrame(FreeBuds5iProtocol.buildBatteryReadRequest())
        sendFrame(FreeBuds5iProtocol.buildDeviceIdentityRequest())
        sendFrame(FreeBuds5iProtocol.buildWindSuppressionReadRequest())
        sendFrame(FreeBuds5iProtocol.buildAncReadRequest())
        sendFrame(FreeBuds5iProtocol.buildEqualizerReadRequest())
    }

    fun disconnect() {
        readerJob?.cancel()
        readerJob = null
        try {
            inputStream?.close()
            outputStream?.close()
            socket?.close()
        } catch (ignored: Exception) {}
        socket = null
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
    }

    suspend fun sendFrame(frame: HuaweiFrame): Boolean = withContext(Dispatchers.IO) {
        try {
            val bytes = frame.toByteArray()
            val cmdHex = "%02X%02X".format(frame.service, frame.command)
            val desc = FreeBuds5iProtocol.describeFrame(frame, isTx = true)

            outputStream?.write(bytes)
            outputStream?.flush()

            Log.d(TAG, "TX -> [$cmdHex]: ${frame.toHexString()}")
            addLog(
                LogEntry(
                    direction = LogDirection.TX,
                    cmdHex = cmdHex,
                    summary = desc.first,
                    rawHex = frame.toHexString(),
                    details = desc.second
                )
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send frame", e)
            false
        }
    }

    private fun startReaderLoop() {
        readerJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(1024)
            val streamBuffer = mutableListOf<Byte>()

            while (isActive && socket?.isConnected == true) {
                try {
                    val bytesRead = inputStream?.read(buffer) ?: -1
                    if (bytesRead <= 0) break

                    for (i in 0 until bytesRead) {
                        streamBuffer.add(buffer[i])
                    }

                    // Parse frames from stream
                    while (streamBuffer.size >= 7) {
                        val magicIdx = streamBuffer.indexOf(0x5A.toByte())
                        if (magicIdx == -1) {
                            streamBuffer.clear()
                            break
                        }
                        if (magicIdx > 0) {
                            repeat(magicIdx) { streamBuffer.removeAt(0) }
                        }
                        if (streamBuffer.size < 7) break

                        val len = ((streamBuffer[1].toInt() and 0xFF) shl 8) or (streamBuffer[2].toInt() and 0xFF)
                        val totalFrameSize = len + 5

                        if (streamBuffer.size < totalFrameSize) {
                            break
                        }

                        val frameBytes = streamBuffer.subList(0, totalFrameSize).toByteArray()
                        repeat(totalFrameSize) { streamBuffer.removeAt(0) }

                        val frame = HuaweiFrame.parse(frameBytes)
                        if (frame != null) {
                            handleIncomingFrame(frame)
                        }
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        Log.e(TAG, "Reader loop exception", e)
                        disconnect()
                    }
                    break
                }
            }
        }
    }

    private suspend fun handleIncomingFrame(frame: HuaweiFrame) {
        val cmdHex = "%02X%02X".format(frame.service, frame.command)
        val desc = FreeBuds5iProtocol.describeFrame(frame, isTx = false)

        Log.d(TAG, "RX <- [$cmdHex]: ${frame.toHexString()}")
        addLog(
            LogEntry(
                direction = LogDirection.RX,
                cmdHex = cmdHex,
                summary = desc.first,
                rawHex = frame.toHexString(),
                details = desc.second
            )
        )

        // 1. Handshake 0A0E Big-Data capability query
        if (frame.service == FreeBuds5iProtocol.SERVICE_MANAGEMENT &&
            frame.command == FreeBuds5iProtocol.CMD_BIG_DATA_CAPABILITY) {
            sendFrame(FreeBuds5iProtocol.buildBigDataCapabilityReply())
            return
        }

        // 2. Battery status (read response 01 08 or push notify 01 27)
        if (frame.service == FreeBuds5iProtocol.SERVICE_DEVICE_MANAGEMENT &&
            (frame.command == FreeBuds5iProtocol.CMD_BATTERY_READ || frame.command == FreeBuds5iProtocol.CMD_BATTERY_NOTIFY)) {
            _batteryState.value = FreeBuds5iProtocol.parseBattery(frame, _batteryState.value)
            return
        }

        // 3. ANC state (response 2B 2A or on-device button push 2B 03)
        if (frame.service == FreeBuds5iProtocol.SERVICE_INTELLIGENT) {
            if (frame.command == FreeBuds5iProtocol.CMD_ANC_READ || frame.command == FreeBuds5iProtocol.CMD_ANC_NOTIFY) {
                val parsedAnc = FreeBuds5iProtocol.parseAncState(frame, _ancState.value)
                if (parsedAnc != null) {
                    _ancState.value = parsedAnc
                }
                if (frame.command == FreeBuds5iProtocol.CMD_ANC_NOTIFY) {
                    // Button press notify: request fresh status frame to sync level
                    sendFrame(FreeBuds5iProtocol.buildAncReadRequest())
                }
                return
            }

            // 4. Equalizer (2B 4A)
            if (frame.command == FreeBuds5iProtocol.CMD_EQ_READ) {
                val eqId = FreeBuds5iProtocol.parseEqualizer(frame)
                if (eqId != null) {
                    _currentEqualizerId.value = eqId
                }
                return
            }

            // 5. Wind suppression (read response or push 2B 95)
            if (frame.command == FreeBuds5iProtocol.CMD_WIND_SUPPRESSION_READ) {
                val state = FreeBuds5iProtocol.parseWindSuppression(frame)
                if (state != null) _windSuppression.value = state
                return
            }

            // 6. Device Identity (2B 0A)
            if (frame.command == FreeBuds5iProtocol.CMD_DEVICE_IDENTITY) {
                _deviceIdentity.value = FreeBuds5iProtocol.parseDeviceIdentity(frame)
                return
            }

            // 7. Fit Test Notify (2B 27)
            if (frame.command == FreeBuds5iProtocol.CMD_FIT_TEST_NOTIFY) {
                _fitTestState.value = FreeBuds5iProtocol.parseFitTestNotify(frame, _fitTestState.value)
                return
            }
        }
    }
}
