package com.nearbymesh.app.core.transport.wifip2p

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.*
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WifiP2pPeerDevice(
    val deviceName: String,
    val deviceAddress: String,
    val status: Int,
    val isGroupOwner: Boolean = false
)

data class WifiP2pConnectionState(
    val isConnected: Boolean = false,
    val isGroupOwner: Boolean = false,
    val groupOwnerAddress: String? = null
)

class WifiP2pManagerHelper(
    private val context: Context,
    private val onConnectionEstablished: (isGroupOwner: Boolean, groupOwnerAddress: String?) -> Unit,
    private val onDisconnected: () -> Unit
) {
    companion object {
        private const val TAG = "WifiP2pHelper"
    }

    private val manager: WifiP2pManager? = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null

    private val _peers = MutableStateFlow<List<WifiP2pPeerDevice>>(emptyList())
    val peers: StateFlow<List<WifiP2pPeerDevice>> = _peers.asStateFlow()

    private val _connectionState = MutableStateFlow(WifiP2pConnectionState())
    val connectionState: StateFlow<WifiP2pConnectionState> = _connectionState.asStateFlow()

    private var isReceiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    manager?.requestPeers(channel) { peersList ->
                        val list = peersList.deviceList.map { device ->
                            WifiP2pPeerDevice(
                                deviceName = device.deviceName,
                                deviceAddress = device.deviceAddress,
                                status = device.status,
                                isGroupOwner = device.isGroupOwner
                            )
                        }
                        _peers.value = list
                        Log.d(TAG, "Wi-Fi P2P peers discovered: ${list.size}")
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                    if (networkInfo?.isConnected == true) {
                        manager?.requestConnectionInfo(channel) { info ->
                            val isGo = info.isGroupOwner
                            val goAddress = info.groupOwnerAddress?.hostAddress
                            Log.d(TAG, "Wi-Fi P2P Connected! Group Owner=$isGo, Address=$goAddress")
                            _connectionState.value = WifiP2pConnectionState(
                                isConnected = true,
                                isGroupOwner = isGo,
                                groupOwnerAddress = goAddress
                            )
                            onConnectionEstablished(isGo, goAddress)
                        }
                    } else {
                        Log.d(TAG, "Wi-Fi P2P Disconnected")
                        _connectionState.value = WifiP2pConnectionState(isConnected = false)
                        onDisconnected()
                    }
                }
            }
        }
    }

    fun init() {
        try {
            channel = manager?.initialize(context, Looper.getMainLooper(), null)
            val filter = IntentFilter().apply {
                addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            isReceiverRegistered = true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to register receiver: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun discoverPeers() {
        try {
            manager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "Wi-Fi P2P discoverPeers initiated")
                }

                override fun onFailure(reason: Int) {
                    Log.e(TAG, "Wi-Fi P2P discoverPeers failed: $reason")
                }
            })
        } catch (e: Throwable) {
            Log.e(TAG, "Error in discoverPeers: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(deviceAddress: String) {
        val config = WifiP2pConfig().apply {
            this.deviceAddress = deviceAddress
            this.groupOwnerIntent = 15 // Prefer becoming Group Owner if possible
        }
        manager?.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Connection initiated to $deviceAddress")
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "Connection failed to $deviceAddress: $reason")
            }
        })
    }

    fun release() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
            isReceiverRegistered = false
        }
    }
}
