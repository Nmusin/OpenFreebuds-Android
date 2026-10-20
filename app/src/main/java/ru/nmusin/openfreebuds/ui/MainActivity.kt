package ru.nmusin.openfreebuds.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import ru.nmusin.openfreebuds.service.FreeBudsService
import ru.nmusin.openfreebuds.ui.theme.OpenFreebudsTheme

class MainActivity : ComponentActivity() {

    private var freeBudsService: FreeBudsService? = null
    private val isServiceBound = mutableStateOf(false)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as FreeBudsService.LocalBinder
            freeBudsService = binder.getService()
            isServiceBound.value = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            freeBudsService = null
            isServiceBound.value = false
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permissions
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions()

        val intent = Intent(this, FreeBudsService::class.java)
        startService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        setContent {
            OpenFreebudsTheme {
                val bound by isServiceBound
                var showDevicePicker by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()

                if (bound && freeBudsService != null) {
                    MainScreen(
                        rfcommManager = freeBudsService!!.rfcommManager,
                        onSelectDevice = { showDevicePicker = true }
                    )

                    if (showDevicePicker) {
                        DevicePickerDialog(
                            onDismiss = { showDevicePicker = false },
                            onDeviceSelected = { device ->
                                showDevicePicker = false
                                scope.launch {
                                    freeBudsService!!.rfcommManager.connect(device)
                                }
                            }
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isServiceBound.value) {
            unbindService(serviceConnection)
            isServiceBound.value = false
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    @SuppressLint("MissingPermission")
    @Composable
    private fun DevicePickerDialog(
        onDismiss: () -> Unit,
        onDeviceSelected: (BluetoothDevice) -> Unit
    ) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter: BluetoothAdapter? = bluetoothManager.adapter
        val bondedDevices = adapter?.bondedDevices?.toList() ?: emptyList()

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Выберите наушники") },
            text = {
                if (bondedDevices.isEmpty()) {
                    Text("Нет сопряженных устройств Bluetooth")
                } else {
                    LazyColumn {
                        items(bondedDevices) { device ->
                            ListItem(
                                headlineContent = { Text(device.name ?: "Неизвестное устройство") },
                                supportingContent = { Text(device.address) },
                                modifier = Modifier.clickable { onDeviceSelected(device) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        )
    }
}
