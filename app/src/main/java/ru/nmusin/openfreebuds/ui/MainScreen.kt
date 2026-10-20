package ru.nmusin.openfreebuds.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.nmusin.openfreebuds.bluetooth.BluetoothRfcommManager
import ru.nmusin.openfreebuds.bluetooth.ConnectionStatus
import ru.nmusin.openfreebuds.model.*
import ru.nmusin.openfreebuds.protocol.FreeBuds5iProtocol
import ru.nmusin.openfreebuds.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    rfcommManager: BluetoothRfcommManager,
    onSelectDevice: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val connectionStatus by rfcommManager.connectionStatus.collectAsState()
    val battery by rfcommManager.batteryState.collectAsState()
    val ancState by rfcommManager.ancState.collectAsState()
    val windSuppression by rfcommManager.windSuppression.collectAsState()
    val currentEqId by rfcommManager.currentEqualizerId.collectAsState()
    val identity by rfcommManager.deviceIdentity.collectAsState()
    val logs by rfcommManager.logEntries.collectAsState()

    var showLogsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (identity.modelName.isNotEmpty()) identity.modelName else "HUAWEI FreeBuds 5i",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            text = when (connectionStatus) {
                                ConnectionStatus.CONNECTED -> "Подключено"
                                ConnectionStatus.CONNECTING -> "Подключение..."
                                ConnectionStatus.DISCONNECTED -> "Отключено"
                                ConnectionStatus.ERROR -> "Ошибка подключения"
                            },
                            color = when (connectionStatus) {
                                ConnectionStatus.CONNECTED -> AccentGreen
                                ConnectionStatus.CONNECTING -> PrimaryBlue
                                else -> TextSecondary
                            },
                            fontSize = 12.sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showLogsDialog = true }) {
                        Icon(Icons.Default.Terminal, contentDescription = "Лог протокола", tint = TextPrimary)
                    }
                    IconButton(onClick = onSelectDevice) {
                        Icon(Icons.Default.Bluetooth, contentDescription = "Выбор устройства", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Battery Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Заряд аккумулятора", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = TextPrimary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        BatteryItem("Левый", battery.left)
                        BatteryItem("Правый", battery.right)
                        BatteryItem("Кейс", battery.case)
                    }
                }
            }

            // 2. Sound & Noise Control Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Режим звука и ANC", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = TextPrimary)
                        Text(
                            text = ancState.mode.title,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = when (ancState.mode) {
                                AncMode.ON -> PrimaryBlue
                                AncMode.AWARENESS -> AccentGreen
                                AncMode.OFF -> TextSecondary
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AncButton(
                            title = "Выкл",
                            selected = ancState.mode == AncMode.OFF,
                            modifier = Modifier.weight(1f)
                        ) {
                            scope.launch {
                                rfcommManager.sendFrame(FreeBuds5iProtocol.buildAncSetRequest(AncMode.OFF))
                            }
                        }
                        AncButton(
                            title = "Шумоподавление",
                            selected = ancState.mode == AncMode.ON,
                            modifier = Modifier.weight(1f)
                        ) {
                            scope.launch {
                                rfcommManager.sendFrame(FreeBuds5iProtocol.buildAncSetRequest(AncMode.ON, ancState.level))
                            }
                        }
                        AncButton(
                            title = "Прозрачность",
                            selected = ancState.mode == AncMode.AWARENESS,
                            modifier = Modifier.weight(1f)
                        ) {
                            scope.launch {
                                rfcommManager.sendFrame(FreeBuds5iProtocol.buildAncSetRequest(AncMode.AWARENESS))
                            }
                        }
                    }

                    // Level selector when ANC is ON
                    AnimatedVisibility(visible = ancState.mode == AncMode.ON) {
                        Column(modifier = Modifier.padding(top = 16.dp)) {
                            Text("Уровень шумоподавления", fontSize = 13.sp, color = TextSecondary)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val levels = listOf(
                                    AncLevel.COZY to "Комфорт",
                                    AncLevel.GENERAL to "Обычный",
                                    AncLevel.ULTRA to "Ультра"
                                )
                                levels.forEach { (lvl, title) ->
                                    val isSelected = ancState.level == lvl
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                rfcommManager.sendFrame(FreeBuds5iProtocol.buildAncSetRequest(AncMode.ON, lvl))
                                            }
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isSelected) PrimaryBlue.copy(alpha = 0.25f) else Color.Transparent
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isSelected) PrimaryBlue else Color(0xFF373A40)
                                        ),
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                    ) {
                                        Text(title, fontSize = 11.sp, color = if (isSelected) PrimaryBlue else TextPrimary, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = DarkSurface)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Wind suppression switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Подавление ветра", fontWeight = FontWeight.Medium, color = TextPrimary)
                            Text("Команда 2B94 (инверсная полярность)", fontSize = 11.sp, color = TextSecondary)
                        }
                        Switch(
                            checked = windSuppression,
                            onCheckedChange = { checked ->
                                scope.launch {
                                    rfcommManager.sendFrame(FreeBuds5iProtocol.buildWindSuppressionSetRequest(checked))
                                }
                            }
                        )
                    }
                }
            }

            // 3. Equalizer Presets Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Эквалайзер", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = TextPrimary)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val presets = listOf(
                            1 to "По умолч.",
                            2 to "Басы",
                            3 to "Высокие",
                            9 to "Голос"
                        )
                        presets.forEach { (id, label) ->
                            val isSelected = currentEqId == id
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        rfcommManager.sendFrame(FreeBuds5iProtocol.buildEqualizerSelectPresetRequest(id))
                                    }
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) PrimaryBlue else Color.Transparent
                                ),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Text(label, color = TextPrimary, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }

            // 4. Live Log Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Terminal, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Лог пакетов SPP", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextPrimary)
                        }
                        TextButton(onClick = { rfcommManager.clearLogs() }) {
                            Text("Очистить", fontSize = 12.sp, color = TextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (logs.isEmpty()) {
                        Text(
                            "Лог пуст. Подключитесь к наушникам для мониторинга обмена.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(DarkBackground)
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            logs.take(15).forEach { entry ->
                                LogRow(entry)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLogsDialog) {
        FullLogsDialog(logs = logs, onDismiss = { showLogsDialog = false }, onClear = { rfcommManager.clearLogs() })
    }
}

@Composable
fun LogRow(entry: LogEntry) {
    val dirColor = when (entry.direction) {
        LogDirection.TX -> PrimaryBlue
        LogDirection.RX -> AccentGreen
        LogDirection.INFO -> TextSecondary
    }
    val dirLabel = when (entry.direction) {
        LogDirection.TX -> "TX ->"
        LogDirection.RX -> "<- RX"
        LogDirection.INFO -> "INFO"
    }

    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(DarkSurface)
            .clickable { expanded = !expanded }
            .padding(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = dirLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = dirColor
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "[${entry.cmdHex}]",
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = entry.summary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1
                )
            }
            Text(text = entry.timestamp, fontSize = 9.sp, color = TextSecondary)
        }

        if (entry.details.isNotEmpty()) {
            Text(
                text = entry.details,
                fontSize = 10.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        if (expanded && entry.rawHex.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = entry.rawHex,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = TextSecondary,
                lineHeight = 12.sp
            )
        }
    }
}

@Composable
fun FullLogsDialog(
    logs: List<LogEntry>,
    onDismiss: () -> Unit,
    onClear: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    fun exportLogs() {
        val text = buildString {
            appendLine("=== OpenFreebuds SPP Live Log Export ===")
            appendLine("Date: " + java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()))
            appendLine("Entries count: ${logs.size}")
            appendLine("----------------------------------------")
            logs.reversed().forEach { entry ->
                val dirStr = if (entry.direction == LogDirection.TX) "TX ->" else if (entry.direction == LogDirection.RX) "<- RX" else "INFO"
                appendLine("[${entry.timestamp}] $dirStr [${entry.cmdHex}] ${entry.summary}")
                if (entry.details.isNotEmpty()) appendLine("  Details: ${entry.details}")
                if (entry.rawHex.isNotEmpty()) appendLine("  RAW: ${entry.rawHex}")
                appendLine()
            }
        }
        val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            putExtra(android.content.Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        context.startActivity(android.content.Intent.createChooser(sendIntent, "Экспорт логов SPP"))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Детальный лог SPP (${logs.size})", fontSize = 16.sp)
                Row {
                    TextButton(onClick = { exportLogs() }) { Text("Экспорт") }
                    TextButton(onClick = onClear) { Text("Очистить") }
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(logs) { entry ->
                    LogRow(entry)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

@Composable
fun BatteryItem(label: String, percent: Int?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(DarkSurface),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (percent != null) "$percent%" else "—",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = if (percent != null && percent > 20) AccentGreen else if (percent != null) DangerRed else TextSecondary
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, color = TextSecondary)
    }
}

@Composable
fun AncButton(
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) PrimaryBlue else DarkSurface
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp)
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            color = if (selected) Color.White else TextSecondary,
            maxLines = 1
        )
    }
}
