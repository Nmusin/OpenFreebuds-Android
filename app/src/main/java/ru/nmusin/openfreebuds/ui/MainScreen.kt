package ru.nmusin.openfreebuds.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.nmusin.openfreebuds.bluetooth.BluetoothRfcommManager
import ru.nmusin.openfreebuds.bluetooth.ConnectionStatus
import ru.nmusin.openfreebuds.model.AncMode
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
    val ancMode by rfcommManager.ancMode.collectAsState()
    val windSuppression by rfcommManager.windSuppression.collectAsState()
    val identity by rfcommManager.deviceIdentity.collectAsState()

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

            // 2. Noise Control Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Управление шумом", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = TextPrimary)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AncButton(
                            title = "Выкл",
                            selected = ancMode == AncMode.OFF,
                            modifier = Modifier.weight(1f)
                        ) {
                            scope.launch {
                                rfcommManager.sendFrame(FreeBuds5iProtocol.buildAncSetRequest(AncMode.OFF))
                            }
                        }
                        AncButton(
                            title = "Шумоподавление",
                            selected = ancMode == AncMode.ON,
                            modifier = Modifier.weight(1f)
                        ) {
                            scope.launch {
                                rfcommManager.sendFrame(FreeBuds5iProtocol.buildAncSetRequest(AncMode.ON))
                            }
                        }
                        AncButton(
                            title = "Прозрачность",
                            selected = ancMode == AncMode.AWARENESS,
                            modifier = Modifier.weight(1f)
                        ) {
                            scope.launch {
                                rfcommManager.sendFrame(FreeBuds5iProtocol.buildAncSetRequest(AncMode.AWARENESS))
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
                            Text("Устраняет шум ветра в микрофонах", fontSize = 12.sp, color = TextSecondary)
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

            // 3. Find My Earbuds Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Поиск наушников", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = TextPrimary)
                    Text("Воспроизведение громкого звукового сигнала", fontSize = 12.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        var leftRinging by remember { mutableStateOf(false) }
                        var rightRinging by remember { mutableStateOf(false) }

                        Button(
                            onClick = {
                                leftRinging = !leftRinging
                                scope.launch {
                                    rfcommManager.sendFrame(FreeBuds5iProtocol.buildFindMyBudsRequest(isRight = false, start = leftRinging))
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (leftRinging) DangerRed else DarkSurface),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (leftRinging) "Стоп (Левый)" else "Звонок (Левый)", color = TextPrimary, fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                rightRinging = !rightRinging
                                scope.launch {
                                    rfcommManager.sendFrame(FreeBuds5iProtocol.buildFindMyBudsRequest(isRight = true, start = rightRinging))
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (rightRinging) DangerRed else DarkSurface),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (rightRinging) "Стоп (Правый)" else "Звонок (Правый)", color = TextPrimary, fontSize = 12.sp)
                        }
                    }
                }
            }

            // 4. Equalizer Presets Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Эквалайзер", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = TextPrimary)
                    Spacer(modifier = Modifier.height(12.dp))

                    var selectedEq by remember { mutableIntStateOf(1) }
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
                            OutlinedButton(
                                onClick = {
                                    selectedEq = id
                                    scope.launch {
                                        rfcommManager.sendFrame(FreeBuds5iProtocol.buildEqualizerSelectPresetRequest(id))
                                    }
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selectedEq == id) PrimaryBlue else Color.Transparent
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
        }
    }
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
