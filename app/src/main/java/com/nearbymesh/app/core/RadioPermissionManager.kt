package com.nearbymesh.app.core

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RadioHardwareStatus(
    val isBluetoothEnabled: Boolean = true,
    val isWifiEnabled: Boolean = true,
    val isLocationServiceEnabled: Boolean = true,
    val hasNearbyPermissions: Boolean = true,
    val hasLocationPermission: Boolean = true,
    val hasStoragePermission: Boolean = true,
    val hasAudioPermission: Boolean = true
) {
    val isAllReady: Boolean
        get() = isBluetoothEnabled && isWifiEnabled && isLocationServiceEnabled && hasNearbyPermissions && hasLocationPermission && hasStoragePermission
}

class RadioPermissionManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val _status = MutableStateFlow(RadioHardwareStatus())
    val status: StateFlow<RadioHardwareStatus> = _status.asStateFlow()

    init {
        try {
            checkStatus()
        } catch (t: Throwable) {
            android.util.Log.e("RadioPermission", "Initial checkStatus error: ${t.message}")
        }
    }

    fun checkStatus(): RadioHardwareStatus {
        val btAdapter = bluetoothManager?.adapter
        val isBtOn = try {
            btAdapter?.isEnabled == true
        } catch (_: Throwable) {
            false
        }

        val isWifiOn = try {
            wifiManager?.isWifiEnabled == true
        } catch (_: Throwable) {
            true
        }

        val isGpsOn = if (locationManager != null) {
            try {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            } catch (_: Throwable) {
                true
            }
        } else true

        val hasLocPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasNearbyPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else true

        val hasStoragePerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        val hasAudioPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val current = RadioHardwareStatus(
            isBluetoothEnabled = isBtOn,
            isWifiEnabled = isWifiOn,
            isLocationServiceEnabled = isGpsOn,
            hasNearbyPermissions = hasNearbyPerm,
            hasLocationPermission = hasLocPerm,
            hasStoragePermission = hasStoragePerm,
            hasAudioPermission = hasAudioPerm
        )
        _status.value = current
        return current
    }

    fun getBluetoothEnableIntent(): Intent {
        return Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
    }

    fun getWifiSettingsIntent(): Intent {
        return Intent(Settings.ACTION_WIFI_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    fun getLocationSettingsIntent(): Intent {
        return Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    fun getAppSettingsIntent(): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
}
