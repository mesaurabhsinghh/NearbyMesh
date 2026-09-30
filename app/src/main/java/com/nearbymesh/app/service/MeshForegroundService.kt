package com.nearbymesh.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.nearbymesh.app.R
import com.nearbymesh.app.core.MeshCoordinator
import com.nearbymesh.app.ui.MainActivity

class MeshForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "nearby_mesh_service_channel"
        const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            try {
                val intent = Intent(context, MeshForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (t: Throwable) {
                android.util.Log.e("MeshService", "Failed to start foreground service: ${t.message}")
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, MeshForegroundService::class.java)
                context.stopService(intent)
            } catch (t: Throwable) {
                android.util.Log.e("MeshService", "Failed to stop service: ${t.message}")
            }
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        try {
            createNotificationChannel()

            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NearbyMesh::RelayWakeLock")
            wakeLock?.acquire(24 * 60 * 60 * 1000L) // Safe partial wakelock for mesh packet relaying

            val notification = buildNotification()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            MeshCoordinator.getInstance(this).startMesh()
        } catch (t: Throwable) {
            android.util.Log.e("MeshService", "Error in onCreate: ${t.message}")
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NearbyMesh")
            .setContentText("Offline mesh active")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(false)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NearbyMesh Background Service",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Silent background mesh node relay"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)

            NotificationHelper.initChannels(this)
        }
    }

    override fun onDestroy() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        MeshCoordinator.getInstance(this).stopMesh()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
