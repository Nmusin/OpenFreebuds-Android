package ru.nmusin.openfreebuds.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import ru.nmusin.openfreebuds.bluetooth.BluetoothRfcommManager

class FreeBudsService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    lateinit var rfcommManager: BluetoothRfcommManager
        private set

    inner class LocalBinder : Binder() {
        fun getService(): FreeBudsService = this@FreeBudsService
    }

    override fun onCreate() {
        super.onCreate()
        rfcommManager = BluetoothRfcommManager(serviceScope)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Служба OpenFreebuds активна"))
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        rfcommManager.disconnect()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "OpenFreebuds Monitor",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("HUAWEI FreeBuds 5i")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "openfreebuds_service"
        const val NOTIFICATION_ID = 1001
    }
}
