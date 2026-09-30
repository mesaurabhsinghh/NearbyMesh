package com.nearbymesh.app.core

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.BatteryManager
import android.util.Log
import com.google.gson.Gson
import com.nearbymesh.app.core.emergency.ActiveEmergencyAlert
import com.nearbymesh.app.core.emergency.EmergencyManager
import com.nearbymesh.app.core.filetransfer.FileTransferManager
import com.nearbymesh.app.core.filetransfer.FileTransferSession
import com.nearbymesh.app.core.mesh.*
import com.nearbymesh.app.core.security.CryptoEngine
import com.nearbymesh.app.core.transport.ble.BleDiscoveredDevice
import com.nearbymesh.app.core.transport.ble.BleGattManager
import com.nearbymesh.app.core.transport.ble.BleManager
import com.nearbymesh.app.core.transport.bluetooth.BluetoothRfcommManager
import com.nearbymesh.app.core.transport.simulator.SimulatedHopLog
import com.nearbymesh.app.core.transport.simulator.VirtualMeshSimulator
import com.nearbymesh.app.core.transport.socket.P2PSocketClient
import com.nearbymesh.app.core.transport.socket.P2PSocketServer
import com.nearbymesh.app.core.transport.wifip2p.WifiP2pManagerHelper
import com.nearbymesh.app.core.models.*
import com.nearbymesh.app.core.voice.ActiveCallInfo
import com.nearbymesh.app.core.voice.VoiceCallManager
import com.nearbymesh.app.ui.theme.AppThemeMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.UUID


data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val senderNodeId: String,
    val senderName: String,
    val recipientNodeId: String,
    val text: String,
    val isFromMe: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val hopCount: Int = 0,
    val hopPath: List<String> = emptyList(),
    var status: MessageStatus = MessageStatus.SENT,
    val isEncrypted: Boolean = true
)

data class MeshPeer(
    val nodeId: String,
    val nickname: String,
    val batteryPercent: Int = -1,
    val estimatedDistance: Double = 0.0,
    val rssi: Int = -60,
    val connectionType: String = "Bluetooth",
    val publicKeyBase64: String? = null,
    val hopsAway: Int = 1,
    val lastSeen: Long = System.currentTimeMillis(),
    val bluetoothAddress: String? = null,
    val bluetoothDevice: BluetoothDevice? = null
)

data class MeshStats(
    val packetsRouted: Long = 0,
    val duplicatesDropped: Long = 0,
    val totalBytesSent: Long = 0,
    val totalBytesReceived: Long = 0
)

/**
 * Main coordinator linking Security, Mesh Routing, BLE, Bluetooth RFCOMM, Wi-Fi P2P,
 * File Transfer, Emergency Beacon, and Offline Voice Calling.
 */
class MeshCoordinator(private val context: Context) {

    companion object {
        private const val TAG = "MeshCoordinator"
        private val gson = Gson()

        @Volatile
        private var instance: MeshCoordinator? = null

        fun getInstance(context: Context): MeshCoordinator {
            return instance ?: synchronized(this) {
                instance ?: MeshCoordinator(context.applicationContext).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // 1. Security & Identity
    val cryptoEngine = CryptoEngine(context)
    val myNodeId = cryptoEngine.nodeId

    var myNickname: String = "Phone-${myNodeId.take(4)}"

    // 2. Mesh Routing
    val meshRouter = MeshRouter(myNodeId = myNodeId)

    // 3. Physical Transports (Bluetooth RFCOMM + BLE + Wi-Fi Direct)
    val bleManager = BleManager(context)
    val bleGattManager = BleGattManager(context) { packet ->
        meshRouter.processIncomingPacket(packet, "BLE_GATT")
    }.apply {
        getMyNodeId = { myNodeId }
        getMyNickname = { myNickname }
        getMyPublicKey = { cryptoEngine.publicKeyBase64 }
        onPeerHandshakeReceived = { nodeId, nickname, pubKey ->
            registerPeerFromHandshake(nodeId, nickname, pubKey)
        }
    }
    val bluetoothRfcommManager = BluetoothRfcommManager(context) { packet ->
        meshRouter.processIncomingPacket(packet, "BLUETOOTH_RFCOMM")
    }
    val radioManager = RadioPermissionManager(context)
    var socketServer: P2PSocketServer? = null
    var socketClient: P2PSocketClient? = null
    lateinit var wifiP2pHelper: WifiP2pManagerHelper

    // 4. Higher-Level Engines
    lateinit var fileTransferManager: FileTransferManager
    lateinit var emergencyManager: EmergencyManager
    lateinit var virtualSimulator: VirtualMeshSimulator

    // 5. Offline Voice Calling Engine
    val voiceCallManager = VoiceCallManager(
        context = context,
        myNodeId = myNodeId,
        myNickname = myNickname,
        sendPacket = { packet ->
            meshRouter.sendPacket(packet)
        }
    )
    val activeCall: StateFlow<ActiveCallInfo?> = voiceCallManager.activeCall

    // Long Range Mode (Enabled by default to maximize distance)
    private val _isLongRangeBoostEnabled = MutableStateFlow(true)
    val isLongRangeBoostEnabled: StateFlow<Boolean> = _isLongRangeBoostEnabled.asStateFlow()

    fun toggleLongRangeBoost() {
        _isLongRangeBoostEnabled.value = !_isLongRangeBoostEnabled.value
        triggerScan()
    }

    // Profile & Settings State
    private val prefs = context.getSharedPreferences("nearbymesh_prefs", Context.MODE_PRIVATE)
    private val _userProfile = MutableStateFlow(
        UserProfile(
            displayName = prefs.getString("display_name", "My Device") ?: "My Device",
            avatarId = prefs.getInt("avatar_id", 0),
            profileImageUri = prefs.getString("profile_image_uri", null)
        )
    )
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    fun updateProfileImage(uri: String?) {
        val updated = _userProfile.value.copy(profileImageUri = uri, avatarId = 0)
        _userProfile.value = updated
        if (uri != null) {
            prefs.edit().putString("profile_image_uri", uri).putInt("avatar_id", 0).apply()
        } else {
            prefs.edit().remove("profile_image_uri").apply()
        }
    }

    fun updateAvatarId(avatarId: Int) {
        val updated = _userProfile.value.copy(avatarId = avatarId, profileImageUri = null)
        _userProfile.value = updated
        prefs.edit().putInt("avatar_id", avatarId).remove("profile_image_uri").apply()
    }

    fun updateDisplayName(name: String) {
        val trimmed = name.trim().ifEmpty { "My Device" }
        val updated = _userProfile.value.copy(displayName = trimmed)
        _userProfile.value = updated
        myNickname = trimmed
        prefs.edit().putString("display_name", trimmed).apply()
    }

    // ==========================================
    // REAL PERSISTENT SETTINGS (Saved in Prefs)
    // ==========================================

    // 1. App Theme (LIGHT, DARK, SYSTEM)
    val initialTheme = when (prefs.getString("theme_mode", "DARK")) {
        "LIGHT" -> AppThemeMode.LIGHT
        "SYSTEM" -> AppThemeMode.SYSTEM
        else -> AppThemeMode.DARK
    }
    private val _appTheme = MutableStateFlow(initialTheme)
    val appTheme: StateFlow<AppThemeMode> = _appTheme.asStateFlow()

    fun setAppTheme(theme: AppThemeMode) {
        _appTheme.value = theme
        prefs.edit().putString("theme_mode", theme.name).apply()
    }

    // 2. Nearby Visibility
    private val _isNearbyVisibilityEnabled = MutableStateFlow(prefs.getBoolean("nearby_visibility", true))
    val isNearbyVisibilityEnabled: StateFlow<Boolean> = _isNearbyVisibilityEnabled.asStateFlow()

    fun setNearbyVisibility(enabled: Boolean) {
        _isNearbyVisibilityEnabled.value = enabled
        prefs.edit().putBoolean("nearby_visibility", enabled).apply()
        if (enabled && _isMeshActive.value) {
            bleManager.startAdvertising(myNodeId, myNickname, getBatteryLevel())
        } else {
            bleManager.stopAdvertising()
        }
    }

    // 3. Bluetooth Low Energy Discovery
    private val _isBleDiscoveryEnabled = MutableStateFlow(prefs.getBoolean("ble_discovery", true))
    val isBleDiscoveryEnabled: StateFlow<Boolean> = _isBleDiscoveryEnabled.asStateFlow()

    fun setBleDiscovery(enabled: Boolean) {
        _isBleDiscoveryEnabled.value = enabled
        prefs.edit().putBoolean("ble_discovery", enabled).apply()
        if (enabled && _isMeshActive.value) {
            bleManager.startScanning()
        } else {
            bleManager.stopScanning()
        }
    }

    // 4. Wi-Fi Direct Discovery
    private val _isWifiDirectEnabled = MutableStateFlow(prefs.getBoolean("wifi_direct_discovery", true))
    val isWifiDirectEnabled: StateFlow<Boolean> = _isWifiDirectEnabled.asStateFlow()

    fun setWifiDirectEnabled(enabled: Boolean) {
        _isWifiDirectEnabled.value = enabled
        prefs.edit().putBoolean("wifi_direct_discovery", enabled).apply()
        if (enabled && _isMeshActive.value) {
            try { wifiP2pHelper.discoverPeers() } catch (_: Throwable) {}
        }
    }

    // 5. Mesh Relay Mode (Forward packets for other peers)
    private val _isMeshRelayEnabled = MutableStateFlow(prefs.getBoolean("mesh_relay_enabled", true))
    val isMeshRelayEnabled: StateFlow<Boolean> = _isMeshRelayEnabled.asStateFlow()

    fun setMeshRelayEnabled(enabled: Boolean) {
        _isMeshRelayEnabled.value = enabled
        prefs.edit().putBoolean("mesh_relay_enabled", enabled).apply()
        meshRouter.isRelayEnabled = enabled
    }

    // 6. Max Relay Hops (TTL)
    private val _maxRelayHops = MutableStateFlow(prefs.getInt("max_relay_hops", 5))
    val maxRelayHops: StateFlow<Int> = _maxRelayHops.asStateFlow()

    fun setMaxRelayHops(hops: Int) {
        val clamped = hops.coerceIn(1, 10)
        _maxRelayHops.value = clamped
        prefs.edit().putInt("max_relay_hops", clamped).apply()
    }

    // 7. Read Receipts (Send delivery tick color status)
    private val _isReadReceiptsEnabled = MutableStateFlow(prefs.getBoolean("read_receipts", true))
    val isReadReceiptsEnabled: StateFlow<Boolean> = _isReadReceiptsEnabled.asStateFlow()

    fun setReadReceiptsEnabled(enabled: Boolean) {
        _isReadReceiptsEnabled.value = enabled
        prefs.edit().putBoolean("read_receipts", enabled).apply()
    }

    // 8. In-App Floating Quick Reply Alerts
    private val _isInAppAlertsEnabled = MutableStateFlow(prefs.getBoolean("in_app_alerts", true))
    val isInAppAlertsEnabled: StateFlow<Boolean> = _isInAppAlertsEnabled.asStateFlow()

    fun setInAppAlertsEnabled(enabled: Boolean) {
        _isInAppAlertsEnabled.value = enabled
        prefs.edit().putBoolean("in_app_alerts", enabled).apply()
    }

    // 9. Vibration on Incoming Alert
    private val _isVibrationEnabled = MutableStateFlow(prefs.getBoolean("vibration_enabled", true))
    val isVibrationEnabled: StateFlow<Boolean> = _isVibrationEnabled.asStateFlow()

    fun setVibrationEnabled(enabled: Boolean) {
        _isVibrationEnabled.value = enabled
        prefs.edit().putBoolean("vibration_enabled", enabled).apply()
    }

    // 10. Accent Color
    private val _accentColorName = MutableStateFlow(prefs.getString("accent_color", "Green") ?: "Green")
    val accentColorName: StateFlow<String> = _accentColorName.asStateFlow()

    fun setAccentColor(name: String) {
        _accentColorName.value = name
        prefs.edit().putString("accent_color", name).apply()
    }

    // 11. Real Storage Calculations & Cleanup
    fun getStorageUsageFormatted(): Triple<String, String, String> {
        val cacheSizeBytes = context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val voiceNotesBytes = File(context.cacheDir, "voice_notes").walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val filesSizeBytes = context.filesDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        return Triple(formatBytes(filesSizeBytes), formatBytes(voiceNotesBytes), formatBytes(cacheSizeBytes))
    }

    fun clearChatHistory() {
        _richMessages.value = emptyList()
        _messages.value = emptyList()
    }

    fun clearLocalCacheAndMedia() {
        try {
            context.cacheDir.deleteRecursively()
            context.cacheDir.mkdirs()
            File(context.cacheDir, "voice_notes").mkdirs()
        } catch (_: Exception) {}
    }

    private fun formatBytes(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> "%.1f MB".format(mb)
            kb >= 1.0 -> "%.1f KB".format(kb)
            else -> "$bytes B"
        }
    }

    // 12. Font & Layout Preferences
    private val _fontSizeScale = MutableStateFlow(prefs.getFloat("font_size_scale", 1.0f))
    val fontSizeScale: StateFlow<Float> = _fontSizeScale.asStateFlow()

    fun setFontSizeScale(scale: Float) {
        _fontSizeScale.value = scale
        prefs.edit().putFloat("font_size_scale", scale).apply()
    }

    private val _isCompactBubbles = MutableStateFlow(prefs.getBoolean("compact_bubbles", false))
    val isCompactBubbles: StateFlow<Boolean> = _isCompactBubbles.asStateFlow()

    fun setCompactBubbles(compact: Boolean) {
        _isCompactBubbles.value = compact
        prefs.edit().putBoolean("compact_bubbles", compact).apply()
    }

    private val _isSmoothAnimations = MutableStateFlow(prefs.getBoolean("smooth_animations", true))
    val isSmoothAnimations: StateFlow<Boolean> = _isSmoothAnimations.asStateFlow()

    fun setSmoothAnimations(enabled: Boolean) {
        _isSmoothAnimations.value = enabled
        prefs.edit().putBoolean("smooth_animations", enabled).apply()
    }

    // 13. Experimental Mesh Preferences
    private val _isMultiPathRoutingEnabled = MutableStateFlow(prefs.getBoolean("multipath_routing", true))
    val isMultiPathRoutingEnabled: StateFlow<Boolean> = _isMultiPathRoutingEnabled.asStateFlow()

    fun setMultiPathRoutingEnabled(enabled: Boolean) {
        _isMultiPathRoutingEnabled.value = enabled
        prefs.edit().putBoolean("multipath_routing", enabled).apply()
    }

    private val _isLowPowerDutyCycleEnabled = MutableStateFlow(prefs.getBoolean("low_power_duty_cycle", false))
    val isLowPowerDutyCycleEnabled: StateFlow<Boolean> = _isLowPowerDutyCycleEnabled.asStateFlow()

    fun setLowPowerDutyCycleEnabled(enabled: Boolean) {
        _isLowPowerDutyCycleEnabled.value = enabled
        prefs.edit().putBoolean("low_power_duty_cycle", enabled).apply()
    }

    // StateFlows for UI (Zero fake data: strictly empty until real NearbyMesh devices connect)
    private val _peers = MutableStateFlow<Map<String, MeshPeer>>(emptyMap())
    val peers: StateFlow<Map<String, MeshPeer>> = _peers.asStateFlow()

    private val _richMessages = MutableStateFlow<List<RichChatMessage>>(emptyList())
    val richMessages: StateFlow<List<RichChatMessage>> = _richMessages.asStateFlow()

    private val _incomingAlert = MutableStateFlow<IncomingMessageAlert?>(null)
    val incomingAlert: StateFlow<IncomingMessageAlert?> = _incomingAlert.asStateFlow()

    fun dismissAlert() {
        _incomingAlert.value = null
    }

    // Real selected files from phone picker
    private val _selectedFiles = MutableStateFlow<List<RealFileItem>>(emptyList())
    val selectedFiles: StateFlow<List<RealFileItem>> = _selectedFiles.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _stats = MutableStateFlow(MeshStats())
    val stats: StateFlow<MeshStats> = _stats.asStateFlow()

    private val _isMeshActive = MutableStateFlow(false)
    val isMeshActive: StateFlow<Boolean> = _isMeshActive.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    init {
        setupEngines()
    }

    private fun setupEngines() {
        meshRouter.isRelayEnabled = _isMeshRelayEnabled.value

        // Higher-level engines
        fileTransferManager = FileTransferManager(context) { packet ->
            meshRouter.sendPacket(packet)
        }

        emergencyManager = EmergencyManager(context) { packet ->
            meshRouter.sendPacket(packet)
        }

        virtualSimulator = VirtualMeshSimulator(myNodeId) { packet ->
            meshRouter.processIncomingPacket(packet, "VIRTUAL_SIM")
        }

        // Wi-Fi P2P Setup
        try {
            wifiP2pHelper = WifiP2pManagerHelper(
                context = context,
                onConnectionEstablished = { isGroupOwner, goAddress ->
                    handleWifiP2pConnected(isGroupOwner, goAddress)
                },
                onDisconnected = {
                    handleWifiP2pDisconnected()
                }
            )
            wifiP2pHelper.init()
        } catch (t: Throwable) {
            Log.e(TAG, "Wi-Fi P2P init error: ${t.message}")
        }

        // Socket server for receiving high speed P2P packets
        try {
            socketServer = P2PSocketServer { packet ->
                meshRouter.processIncomingPacket(packet, "WIFI_P2P_SOCKET")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Socket server init error: ${t.message}")
        }

        // Start BLE GATT Server
        try {
            bleGattManager.start()
        } catch (t: Throwable) {
            Log.e(TAG, "BLE GATT init error: ${t.message}")
        }

        // Start Bluetooth RFCOMM Server
        try {
            bluetoothRfcommManager.start()
        } catch (t: Throwable) {
            Log.e(TAG, "Bluetooth RFCOMM init error: ${t.message}")
        }

        // Connect MeshRouter outbound dispatcher to all transports
        meshRouter.outboundDispatcher = { packet, excludeSenderId ->
            dispatchOutboundPacket(packet, excludeSenderId)
        }

        // Listen for packets delivered to this local node
        scope.launch {
            meshRouter.deliveredPackets.collect { packet ->
                handleDeliveredPacket(packet)
            }
        }

        // Listen for relayed packets to update stats
        scope.launch {
            meshRouter.relayedPackets.collect {
                _stats.value = _stats.value.copy(
                    packetsRouted = _stats.value.packetsRouted + 1
                )
            }
        }

        // Listen for dropped duplicates
        scope.launch {
            meshRouter.droppedPackets.collect {
                _stats.value = _stats.value.copy(
                    duplicatesDropped = _stats.value.duplicatesDropped + 1
                )
            }
        }

        // Wire Connectionless Instant BLE Blaster (<50ms delivery)
        bleManager.onInstantPacketReceived = { packet, rssi ->
            meshRouter.processIncomingPacket(packet, "BLE_INSTANT", rssi)
        }

        // Collect BLE discovered devices into MeshPeer list
        scope.launch {
            bleManager.discoveredDevices.collect { bleDevices ->
                updatePeersFromBle(bleDevices)
            }
        }
    }

    fun startMesh() {
        if (_isMeshActive.value) return
        _isMeshActive.value = true

        scope.launch(Dispatchers.IO) {
            val batteryLevel = getBatteryLevel()

            // 1. Start zero-pairing BLE GATT Server
            bleGattManager.start()

            // 2. Start Bluetooth RFCOMM Server for reliable streaming
            bluetoothRfcommManager.start()

            // 3. Start BLE Advertising & Scanning (Respect user settings)
            _isScanning.value = true
            if (_isNearbyVisibilityEnabled.value) {
                bleManager.startAdvertising(myNodeId, myNickname, batteryLevel)
            }
            if (_isBleDiscoveryEnabled.value) {
                bleManager.startScanning()
            }

            // 4. Discover Wi-Fi Direct peers
            if (_isWifiDirectEnabled.value) {
                wifiP2pHelper.discoverPeers()
            }
            scope.launch {
                delay(12000L)
                _isScanning.value = false
            }

            // 5. Start local TCP server
            socketServer?.start()

            // 6. Start periodic announcement heartbeat
            startPeriodicAnnouncements()

            Log.d(TAG, "NearbyMesh started for node $myNodeId ($myNickname)")
        }
    }

    fun stopMesh() {
        _isMeshActive.value = false
        bleGattManager.stop()
        bluetoothRfcommManager.stop()
        bleManager.stop()
        socketServer?.stop()
        socketClient?.stop()
        wifiP2pHelper.release()
        virtualSimulator.stop()
    }

    private fun handleWifiP2pConnected(isGroupOwner: Boolean, groupOwnerAddress: String?) {
        if (isGroupOwner) {
            socketServer?.start()
        } else if (groupOwnerAddress != null) {
            socketClient = P2PSocketClient(
                hostAddress = groupOwnerAddress,
                onPacketReceived = { packet ->
                    meshRouter.processIncomingPacket(packet, "WIFI_P2P_CLIENT")
                },
                onConnectionStateChanged = { connected ->
                    Log.d(TAG, "P2P Socket client connection state: $connected")
                }
            )
            socketClient?.connect()
        }
    }

    private fun handleWifiP2pDisconnected() {
        socketClient?.disconnect()
    }

    private fun dispatchOutboundPacket(packet: MeshPacket, excludeSenderId: String?) {
        // If targeted to a specific node, ensure direct connection is initiated
        if (!packet.isBroadcast) {
            val peer = _peers.value[packet.targetNodeId]
            if (peer?.bluetoothDevice != null) {
                bleGattManager.connectToDevice(packet.targetNodeId, peer.bluetoothDevice)
                bluetoothRfcommManager.connectToDevice(peer.bluetoothDevice)
            }
        }

        // 0. Connectionless Instant BLE Blaster (<50ms delivery for Text, ACKs, SOS, PING)
        if (packet.payloadType == PayloadType.TEXT_MESSAGE ||
            packet.payloadType == PayloadType.MESSAGE_ACK ||
            packet.payloadType == PayloadType.EMERGENCY_SOS ||
            packet.payloadType == PayloadType.PING ||
            packet.payloadType == PayloadType.PONG) {
            bleManager.blastInstantPacket(packet)
        }

        // 1. Send via BLE GATT (Zero-pairing primary channel: works immediately between any 2 Android phones)
        bleGattManager.sendPacket(packet)

        // 2. Broadcast across all active Bluetooth RFCOMM streams (High throughput for bonded/paired phones)
        bluetoothRfcommManager.broadcastPacket(packet, excludeSenderId)

        if (!packet.isBroadcast) {
            bluetoothRfcommManager.sendToNode(packet.targetNodeId, packet)
        }

        // 3. Forward through Wi-Fi socket server if active
        socketServer?.broadcastPacket(packet, excludeSenderId)

        // 4. Forward through Wi-Fi socket client if connected
        socketClient?.sendPacket(packet)

        // 5. If virtual simulator is running, dispatch into virtual mesh topology
        if (virtualSimulator.isSimulationActive.value) {
            virtualSimulator.dispatchFromLocalNode(packet)
        }

        _stats.value = _stats.value.copy(
            totalBytesSent = _stats.value.totalBytesSent + packet.payload.length
        )
    }

    private fun handleDeliveredPacket(packet: MeshPacket) {
        _stats.value = _stats.value.copy(
            totalBytesReceived = _stats.value.totalBytesReceived + packet.payload.length
        )

        when (packet.payloadType) {
            PayloadType.TEXT_MESSAGE -> {
                handleIncomingTextMessage(packet)
            }
            PayloadType.MESSAGE_ACK -> {
                handleIncomingMessageAck(packet)
            }
            PayloadType.FILE_METADATA -> {
                fileTransferManager.handleIncomingMetadata(packet)
            }
            PayloadType.FILE_CHUNK -> {
                fileTransferManager.handleIncomingChunk(packet, myNodeId)
            }
            PayloadType.FILE_CHUNK_ACK -> {
                fileTransferManager.handleChunkAck(packet)
            }
            PayloadType.EMERGENCY_SOS -> {
                emergencyManager.handleIncomingSos(packet)
            }
            PayloadType.PEER_ANNOUNCE -> {
                handlePeerAnnounce(packet)
            }
            PayloadType.PING -> {
                // Send Pong reply
                val pong = MeshPacket(
                    sourceNodeId = myNodeId,
                    senderNodeId = myNodeId,
                    targetNodeId = packet.sourceNodeId,
                    payloadType = PayloadType.PONG,
                    payload = "PONG"
                )
                meshRouter.sendPacket(pong)
            }
            PayloadType.PONG -> {}
            PayloadType.CALL_REQUEST,
            PayloadType.CALL_ACCEPT,
            PayloadType.CALL_REJECT,
            PayloadType.CALL_HANGUP,
            PayloadType.CALL_AUDIO_FRAME -> {
                voiceCallManager.handleIncomingCallPacket(packet)
            }
        }
    }

    private fun handleIncomingTextMessage(packet: MeshPacket) {
        var textContent = packet.payload
        var isDecrypted = false

        // Attempt E2EE Decryption if marked encrypted
        if (packet.isEncrypted) {
            val peer = _peers.value[packet.sourceNodeId]
            val peerPubKey = peer?.publicKeyBase64
            if (peerPubKey != null) {
                try {
                    textContent = cryptoEngine.decrypt(packet.payload, peerPubKey)
                    isDecrypted = true
                } catch (e: Exception) {
                    Log.e(TAG, "Decryption error: ${e.message}")
                    textContent = "[Encrypted Message - Key Mismatch]"
                }
            } else {
                textContent = "[Encrypted Message - Peer Key Required]"
            }
        }

        val chatPayload = try {
            gson.fromJson(textContent, TextMessagePayload::class.java)
        } catch (_: Exception) {
            TextMessagePayload(UUID.randomUUID().toString(), textContent)
        }

        val senderNickname = _peers.value[packet.sourceNodeId]?.nickname ?: "Node ${packet.sourceNodeId}"
        val chatMessage = ChatMessage(
            id = chatPayload.messageId,
            senderNodeId = packet.sourceNodeId,
            senderName = senderNickname,
            recipientNodeId = if (packet.isBroadcast) "mesh-broadcast" else myNodeId,
            text = chatPayload.text,
            isFromMe = false,
            timestamp = chatPayload.timestamp,
            hopCount = packet.hopCount,
            hopPath = packet.hopPath,
            status = MessageStatus.READ,
            isEncrypted = packet.isEncrypted
        )

        addMessage(chatMessage)

        // CRITICAL FIX: Also add to _richMessages with hopPath and MessageStatus
        val richMsg = RichChatMessage(
            id = chatMessage.id,
            senderNodeId = packet.sourceNodeId,
            senderName = senderNickname,
            recipientNodeId = if (packet.isBroadcast) "mesh-broadcast" else myNodeId,
            text = chatPayload.text,
            isFromMe = false,
            contentType = MessageContentType.TEXT,
            timestamp = chatPayload.timestamp,
            hopCount = packet.hopCount,
            hopPath = packet.hopPath,
            status = MessageStatus.READ
        )
        val currentRich = _richMessages.value.toMutableList()
        currentRich.add(richMsg)
        _richMessages.value = currentRich

        // Trigger Android Heads-Up Notification with Direct Reply (RemoteInput)
        try {
            com.nearbymesh.app.service.NotificationHelper.showIncomingMessageNotification(
                context = context,
                peerId = packet.sourceNodeId,
                senderName = senderNickname,
                messageText = chatPayload.text
            )
        } catch (e: Exception) {
            Log.e(TAG, "Notification error: ${e.message}")
        }

        // Trigger In-App Pop-up Quick Reply Banner if enabled
        if (_isInAppAlertsEnabled.value) {
            _incomingAlert.value = IncomingMessageAlert(
                messageId = chatMessage.id,
                senderNodeId = packet.sourceNodeId,
                senderName = senderNickname,
                text = chatPayload.text,
                timestamp = chatPayload.timestamp
            )
        }

        // Send ACK back to sender
        val ackPayload = MessageAckPayload(
            originalPacketId = packet.packetId,
            originalMessageId = chatMessage.id,
            deliveredViaHops = packet.hopCount
        )
        val ackPacket = MeshPacket(
            sourceNodeId = myNodeId,
            senderNodeId = myNodeId,
            targetNodeId = packet.sourceNodeId,
            payloadType = PayloadType.MESSAGE_ACK,
            payload = gson.toJson(ackPayload)
        )
        meshRouter.sendPacket(ackPacket)
    }

    private fun handleIncomingMessageAck(packet: MeshPacket) {
        val ack = try {
            gson.fromJson(packet.payload, MessageAckPayload::class.java)
        } catch (_: Exception) {
            return
        }

        val isReadReceipt = ack.originalMessageId == "READ_RECEIPT"
        val targetStatus = if (isReadReceipt) MessageStatus.READ else MessageStatus.DELIVERED_VIA_RELAY

        val updated = _messages.value.map { msg ->
            if (msg.id == ack.originalMessageId || (msg.recipientNodeId == packet.sourceNodeId && msg.isFromMe)) {
                msg.copy(status = targetStatus)
            } else msg
        }
        _messages.value = updated

        val updatedRich = _richMessages.value.map { msg ->
            if (msg.id == ack.originalMessageId || (msg.recipientNodeId == packet.sourceNodeId && msg.isFromMe)) {
                msg.copy(status = targetStatus, isDelivered = true)
            } else msg
        }
        _richMessages.value = updatedRich
    }

    private fun handlePeerAnnounce(packet: MeshPacket) {
        val announce = try {
            gson.fromJson(packet.payload, PeerAnnouncePayload::class.java)
        } catch (_: Exception) {
            return
        }

        val peer = MeshPeer(
            nodeId = packet.sourceNodeId,
            nickname = announce.nickname,
            batteryPercent = announce.batteryLevel,
            publicKeyBase64 = announce.publicKeyBase64,
            hopsAway = packet.hopCount.coerceAtLeast(1),
            lastSeen = System.currentTimeMillis()
        )

        val current = _peers.value.toMutableMap()
        current[peer.nodeId] = peer
        _peers.value = current

        meshRouter.routingTable.updateRoute(
            RouteEntry(
                destinationNodeId = peer.nodeId,
                nextHopNodeId = packet.senderNodeId,
                hops = packet.hopCount,
                nickname = announce.nickname,
                batteryLevel = announce.batteryLevel,
                publicKeyBase64 = announce.publicKeyBase64
            )
        )
    }

    /**
     * Sends a 1-to-1 or mesh broadcast chat message.
     */
    fun sendChatMessage(recipientNodeId: String, text: String): ChatMessage {
        val msgId = UUID.randomUUID().toString()
        val plainPayload = gson.toJson(TextMessagePayload(messageId = msgId, text = text))

        var finalPayload = plainPayload
        var isEncrypted = false

        // Check if recipient has public key for E2EE
        val peer = _peers.value[recipientNodeId]
        if (recipientNodeId != MeshPacket.BROADCAST_ID && peer?.publicKeyBase64 != null) {
            try {
                finalPayload = cryptoEngine.encrypt(plainPayload, peer.publicKeyBase64)
                isEncrypted = true
            } catch (e: Exception) {
                Log.e(TAG, "Encryption failed, sending clear: ${e.message}")
            }
        }

        val packet = MeshPacket(
            sourceNodeId = myNodeId,
            senderNodeId = myNodeId,
            targetNodeId = recipientNodeId,
            ttl = _maxRelayHops.value,
            hopCount = 0,
            hopPath = mutableListOf(myNodeId),
            payloadType = PayloadType.TEXT_MESSAGE,
            payload = finalPayload,
            isEncrypted = isEncrypted
        )

        meshRouter.sendPacket(packet)

        val chatMessage = ChatMessage(
            id = msgId,
            senderNodeId = myNodeId,
            senderName = "$myNickname (You)",
            recipientNodeId = recipientNodeId,
            text = text,
            isFromMe = true,
            status = MessageStatus.SENT,
            isEncrypted = isEncrypted
        )

        addMessage(chatMessage)
        return chatMessage
    }

    private fun addMessage(msg: ChatMessage) {
        val list = _messages.value.toMutableList()
        list.add(msg)
        _messages.value = list
    }

    private fun updatePeersFromBle(bleDevices: Map<String, BleDiscoveredDevice>) {
        val current = _peers.value.toMutableMap()
        bleDevices.values.forEach { ble ->
            val existing = current[ble.nodeId]
            val peer = MeshPeer(
                nodeId = ble.nodeId,
                nickname = if (existing?.nickname?.isNotEmpty() == true) existing.nickname else ble.deviceName,
                batteryPercent = if (ble.batteryPercent != -1) ble.batteryPercent else existing?.batteryPercent ?: -1,
                estimatedDistance = ble.estimatedDistanceMeters,
                rssi = ble.rssi,
                connectionType = "Bluetooth",
                publicKeyBase64 = existing?.publicKeyBase64,
                hopsAway = 1,
                lastSeen = ble.lastSeenTimestamp,
                bluetoothAddress = ble.deviceAddress,
                bluetoothDevice = ble.bluetoothDevice ?: existing?.bluetoothDevice
            )
            current[ble.nodeId] = peer

            // Register MAC mapping for reliable RFCOMM packet routing
            bluetoothRfcommManager.registerNodeMac(ble.nodeId, ble.deviceAddress)

            // Connect zero-pairing BLE GATT immediately for messaging and voice calls
            ble.bluetoothDevice?.let { dev ->
                bleGattManager.connectToDevice(ble.nodeId, dev)
                bluetoothRfcommManager.connectToDevice(dev)
            }
        }
        _peers.value = current
    }

    fun registerPeerFromHandshake(nodeId: String, nickname: String, pubKey: String?) {
        val current = _peers.value.toMutableMap()
        val existing = current[nodeId]
        val updated = (existing ?: MeshPeer(nodeId = nodeId, nickname = nickname)).copy(
            nickname = if (nickname.isNotBlank()) nickname else existing?.nickname ?: "Device $nodeId",
            publicKeyBase64 = pubKey ?: existing?.publicKeyBase64,
            lastSeen = System.currentTimeMillis()
        )
        current[nodeId] = updated
        _peers.value = current
        Log.d(TAG, "Registered peer via GATT Handshake: $nodeId ($nickname), pubKey=${pubKey != null}")
    }

    private fun startPeriodicAnnouncements() {
        scope.launch {
            while (_isMeshActive.value && isActive) {
                val announce = PeerAnnouncePayload(
                    nickname = myNickname,
                    batteryLevel = getBatteryLevel(),
                    publicKeyBase64 = cryptoEngine.publicKeyBase64,
                    isRelayEnabled = true
                )
                val packet = MeshPacket(
                    sourceNodeId = myNodeId,
                    senderNodeId = myNodeId,
                    targetNodeId = MeshPacket.BROADCAST_ID,
                    ttl = 3, // Announce 3 hops away
                    payloadType = PayloadType.PEER_ANNOUNCE,
                    payload = gson.toJson(announce)
                )
                meshRouter.sendPacket(packet)
                delay(15_000) // Every 15 seconds
            }
        }
    }

    fun getBatteryLevel(): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        return bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 85
    }

    fun triggerScan() {
        scope.launch(Dispatchers.IO) {
            try {
                _isScanning.value = true
                val battery = getBatteryLevel()
                bleManager.stop()
                delay(200L) // Allow Bluetooth HAL controller to cycle down cleanly
                bleGattManager.start()
                bleManager.startAdvertising(myNodeId, myNickname, battery)
                bleManager.startScanning()
                bluetoothRfcommManager.autoConnectBondedDevices()
                wifiP2pHelper.discoverPeers()
                Log.d(TAG, "Triggered fresh mesh discovery scan")
                delay(10000L)
                _isScanning.value = false
            } catch (t: Throwable) {
                Log.e(TAG, "Error triggering scan: ${t.message}")
                _isScanning.value = false
            }
        }
    }

    fun sendRichMessage(
        recipientNodeId: String,
        text: String,
        contentType: MessageContentType = MessageContentType.TEXT,
        mediaUri: String? = null,
        audioDurationSeconds: Int = 0,
        fileName: String? = null,
        fileSizeFormatted: String? = null
    ) {
        val newMsg = RichChatMessage(
            senderNodeId = myNodeId,
            senderName = "You",
            recipientNodeId = recipientNodeId,
            text = text,
            isFromMe = true,
            contentType = contentType,
            mediaUri = mediaUri,
            audioDurationSeconds = audioDurationSeconds,
            fileName = fileName,
            fileSizeFormatted = fileSizeFormatted,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENT,
            isDelivered = false
        )
        val current = _richMessages.value.toMutableList()
        current.add(newMsg)
        _richMessages.value = current

        // Broadcast over mesh router and Bluetooth RFCOMM
        sendChatMessage(recipientNodeId, text)
    }

    fun markChatAsRead(peerId: String) {
        com.nearbymesh.app.service.NotificationHelper.cancelNotification(context, peerId)
        if (_incomingAlert.value?.senderNodeId == peerId) {
            _incomingAlert.value = null
        }
        if (_isReadReceiptsEnabled.value) {
            val ackPayload = MessageAckPayload(
                originalPacketId = UUID.randomUUID().toString(),
                originalMessageId = "READ_RECEIPT",
                deliveredViaHops = 1
            )
            val ackPacket = MeshPacket(
                sourceNodeId = myNodeId,
                senderNodeId = myNodeId,
                targetNodeId = peerId,
                payloadType = PayloadType.MESSAGE_ACK,
                payload = gson.toJson(ackPayload)
            )
            meshRouter.sendPacket(ackPacket)
        }
    }

    // Voice Calling Operations
    fun startVoiceCall(targetNodeId: String, targetName: String) = voiceCallManager.startCall(targetNodeId, targetName)
    fun acceptVoiceCall() = voiceCallManager.acceptCall()
    fun rejectVoiceCall() = voiceCallManager.rejectCall()
    fun hangupVoiceCall() = voiceCallManager.hangupCall()
    fun toggleCallMute() = voiceCallManager.toggleMute()
    fun toggleCallSpeaker() = voiceCallManager.toggleSpeaker()

    fun updateUserProfile(profile: UserProfile) {
        _userProfile.value = profile
        myNickname = profile.displayName
    }

    fun addSelectedFile(item: RealFileItem) {
        val current = _selectedFiles.value.toMutableList()
        current.add(item)
        _selectedFiles.value = current
    }

    fun removeSelectedFile(fileId: String) {
        _selectedFiles.value = _selectedFiles.value.filter { it.id != fileId }
    }

    fun clearSelectedFiles() {
        _selectedFiles.value = emptyList()
    }
}
