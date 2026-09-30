package com.nearbymesh.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.nearbymesh.app.core.MeshCoordinator
import com.nearbymesh.app.service.MeshForegroundService
import com.nearbymesh.app.ui.screens.MainScreen
import com.nearbymesh.app.ui.theme.NearbyMeshTheme

import androidx.compose.runtime.*
import com.nearbymesh.app.ui.theme.AppThemeMode

class MainActivity : ComponentActivity() {

    private lateinit var coordinator: MeshCoordinator
    private var targetPeerId by mutableStateOf<String?>(null)
    private var targetPeerName by mutableStateOf("")

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // All needed permissions requested; launch background service and radio scan safely
        try {
            MeshForegroundService.start(this)
            coordinator.startMesh()
            coordinator.radioManager.checkStatus()
        } catch (t: Throwable) {
            android.util.Log.e("MainActivity", "Failed to start service after permission: ${t.message}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        coordinator = MeshCoordinator.getInstance(applicationContext)
        handleNotificationIntent(intent)

        setContent {
            val currentTheme by coordinator.appTheme.collectAsState()

            NearbyMeshTheme(themeMode = currentTheme) {
                MainScreen(
                    coordinator = coordinator,
                    currentTheme = currentTheme,
                    initialChatPeerId = targetPeerId,
                    initialChatPeerName = targetPeerName,
                    onRequestPermissions = { requestRequiredPermissions() },
                    onToggleTheme = {
                        val next = if (currentTheme == AppThemeMode.LIGHT) AppThemeMode.DARK else AppThemeMode.LIGHT
                        coordinator.setAppTheme(next)
                    }
                )
            }
        }

        // Defer permission prompt to after UI composition to prevent startup blank screen
        window.decorView.post {
            requestRequiredPermissions()
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: android.content.Intent?) {
        val peerId = intent?.getStringExtra(com.nearbymesh.app.service.NotificationHelper.EXTRA_PEER_ID)
        val peerName = intent?.getStringExtra(com.nearbymesh.app.service.NotificationHelper.EXTRA_PEER_NAME) ?: "Peer"
        if (peerId != null) {
            targetPeerId = peerId
            targetPeerName = peerName
        }
    }

    fun requestRequiredPermissions() {
        try {
            val permissions = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.RECORD_AUDIO
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
                permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }

            val missing = permissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }

            if (missing.isNotEmpty()) {
                permissionLauncher.launch(missing.toTypedArray())
            } else {
                MeshForegroundService.start(this)
                coordinator.startMesh()
                coordinator.radioManager.checkStatus()
            }
        } catch (t: Throwable) {
            android.util.Log.e("MainActivity", "Error requesting permissions: ${t.message}")
        }
    }
}
