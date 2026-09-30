package com.nearbymesh.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.nearbymesh.app.core.MeshCoordinator

class NearbyMeshApplication : Application() {

    companion object {
        const val EMERGENCY_CHANNEL_ID = "nearby_mesh_emergency_channel"
    }

    override fun onCreate() {
        super.onCreate()
        try {
            createNotificationChannels()
            // Initialize coordinator safely
            MeshCoordinator.getInstance(this)
        } catch (t: Throwable) {
            android.util.Log.e("NearbyMeshApp", "App init error: ${t.message}")
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val emergencyChannel = NotificationChannel(
                EMERGENCY_CHANNEL_ID,
                "Emergency SOS Distress Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority alarms received from nearby mesh nodes"
                enableVibration(true)
                enableLights(true)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(emergencyChannel)
        }
    }
}
