package com.nearbymesh.app.core.transport.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.util.Log
import com.google.gson.Gson
import com.nearbymesh.app.core.mesh.MeshPacket
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

data class BleHandshakePayload(
    val nodeId: String,
    val nickname: String,
    val publicKeyBase64: String? = null
)

/**
 * True zero-pairing, 100% offline BLE GATT bidirectional transport.
 * Operates simultaneously as GATT Server (Peripheral) and GATT Client (Central)
 * with store-and-forward packet queuing, handshake-driven mutual authentication,
 * and sequential hardware-acknowledged chunk transmission.
 */
class BleGattManager(
    private val context: Context,
    private val onPacketReceived: (MeshPacket) -> Unit
) {
    companion object {
        private const val TAG = "BleGattManager"

        val MESH_SERVICE_UUID: UUID = UUID.fromString("0000FE60-0000-1000-8000-00805F9B34FB")
        val CHAR_MESH_INCOMING_UUID: UUID = UUID.fromString("0000FE61-0000-1000-8000-00805F9B34FB")
        val CHAR_MESH_NOTIFY_UUID: UUID = UUID.fromString("0000FE62-0000-1000-8000-00805F9B34FB")
        val CLIENT_CONFIG_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

        private const val FRAME_SINGLE: Byte = 0x01
        private const val FRAME_CHUNK: Byte = 0x02
        private const val FRAME_HANDSHAKE: Byte = 0x03
        private const val FRAME_HANDSHAKE_ACK: Byte = 0x04
        private const val HEADER_SIZE = 6 // [Type:1, Seq:2, ChunkIdx:1, TotalChunks:1, Reserved:1]
        private const val DEFAULT_CHUNK_SIZE = 240
        private val gson = Gson()
    }

    // Callbacks to acquire local identity for mutual handshakes
    var getMyNodeId: (() -> String)? = null
    var getMyNickname: (() -> String)? = null
    var getMyPublicKey: (() -> String)? = null
    var onPeerHandshakeReceived: ((nodeId: String, nickname: String, pubKey: String?) -> Unit)? = null

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var gattServer: BluetoothGattServer? = null
    private val activeGattClients = ConcurrentHashMap<String, BluetoothGatt>() // Mac -> BluetoothGatt
    val nodeToMacMap = ConcurrentHashMap<String, String>() // NodeId -> Mac
    val macToNodeMap = ConcurrentHashMap<String, String>() // Mac -> NodeId
    private val clientMtuMap = ConcurrentHashMap<String, Int>() // Mac -> MTU

    // Store known BluetoothDevice references for auto-connect
    val knownDevices = ConcurrentHashMap<String, BluetoothDevice>() // NodeId -> BluetoothDevice

    // Connected devices to our GATT Server
    private val serverConnectedDevices = ConcurrentHashMap<String, BluetoothDevice>()

    // Chunk assembler storage
    private val packetAssembler = ConcurrentHashMap<String, MutableMap<Int, ByteArray>>()
    private val packetTotalChunks = ConcurrentHashMap<String, Int>()
    private val packetTimestamp = ConcurrentHashMap<String, Long>()

    // Store-and-Forward Outgoing Queue for pending messages
    private val pendingOutboundPackets = ConcurrentHashMap<String, ConcurrentLinkedQueue<MeshPacket>>()

    // Hardware write synchronization mutex and completable
    private val writeLock = Mutex()
    @Volatile
    private var writeCompletable: CompletableDeferred<Boolean>? = null

    private val seqCounter = AtomicInteger(0)
    private var isRunning = false

    // GATT Server Callback
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            val address = device?.address ?: return
            Log.d(TAG, "GATT Server onConnectionStateChange: $address, status=$status, newState=$newState")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                serverConnectedDevices[address] = device
                Log.d(TAG, "GATT Server: Client device connected: $address")

                // Symmetrical Connection: Ensure reciprocal GATT Client is initiated
                val targetNode = macToNodeMap[address] ?: "Node-${address.replace(":", "").takeLast(8)}"
                connectToDevice(targetNode, device)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                serverConnectedDevices.remove(address)
                Log.d(TAG, "GATT Server: Client device disconnected: $address")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            val address = device?.address ?: "UNKNOWN"
            if (responseNeeded && gattServer != null && device != null) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }

            if (characteristic?.uuid == CHAR_MESH_INCOMING_UUID && value != null && value.isNotEmpty()) {
                handleIncomingBytes(address, value)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            descriptor: BluetoothGattDescriptor?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            if (descriptor != null && value != null) {
                @Suppress("DEPRECATION")
                descriptor.value = value
            }
            if (responseNeeded && gattServer != null && device != null) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }
    }

    /**
     * Starts the local BLE GATT Server so nearby phones can connect and write packets.
     */
    @SuppressLint("MissingPermission")
    fun start() {
        if (isRunning) return
        isRunning = true

        startGattServer()
        startAssemblerCleaner()
        Log.d(TAG, "BleGattManager started successfully")
    }

    @SuppressLint("MissingPermission")
    private fun startGattServer() {
        if (bluetoothManager == null || bluetoothAdapter?.isEnabled != true) {
            Log.w(TAG, "Cannot start GATT server: Bluetooth not ready")
            return
        }

        try {
            gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
            if (gattServer == null) {
                Log.e(TAG, "Failed to open GATT Server")
                return
            }

            val meshService = BluetoothGattService(
                MESH_SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )

            // Characteristic for remote phones to write incoming packets
            val incomingChar = BluetoothGattCharacteristic(
                CHAR_MESH_INCOMING_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE or
                        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )

            // Characteristic for notifications / confirmations
            val notifyChar = BluetoothGattCharacteristic(
                CHAR_MESH_NOTIFY_UUID,
                BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                        BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ
            )
            val cccd = BluetoothGattDescriptor(
                CLIENT_CONFIG_DESCRIPTOR_UUID,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
            )
            notifyChar.addDescriptor(cccd)

            meshService.addCharacteristic(incomingChar)
            meshService.addCharacteristic(notifyChar)

            val added = gattServer?.addService(meshService)
            Log.d(TAG, "GATT Mesh Service added: $added")
        } catch (t: Throwable) {
            Log.e(TAG, "Error starting GATT Server: ${t.message}")
        }
    }

    /**
     * Connects as a GATT Client to a discovered remote phone's BluetoothDevice.
     */
    @SuppressLint("MissingPermission")
    fun connectToDevice(nodeId: String, device: BluetoothDevice) {
        val mac = device.address
        nodeToMacMap[nodeId] = mac
        macToNodeMap[mac] = nodeId
        knownDevices[nodeId] = device

        val existing = activeGattClients[mac]
        if (existing != null) {
            // Already connected or in progress
            return
        }

        scope.launch {
            try {
                Log.d(TAG, "Connecting GATT Client to $mac (Node: $nodeId)...")
                val clientCallback = createClientCallback(mac, nodeId)
                val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    device.connectGatt(context, false, clientCallback, BluetoothDevice.TRANSPORT_LE)
                } else {
                    device.connectGatt(context, false, clientCallback)
                }
                if (gatt != null) {
                    activeGattClients[mac] = gatt
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error connecting GATT client to $mac: ${t.message}")
            }
        }
    }

    private fun createClientCallback(mac: String, initialNodeId: String): BluetoothGattCallback {
        return object : BluetoothGattCallback() {
            @SuppressLint("MissingPermission")
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                Log.d(TAG, "GATT Client onConnectionStateChange: $mac, status=$status, newState=$newState")
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    activeGattClients[mac] = gatt
                    Log.d(TAG, "GATT Client connected to $mac! Requesting MTU 512...")
                    val mtuRequested = gatt.requestMtu(512)
                    if (!mtuRequested) {
                        gatt.discoverServices()
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d(TAG, "GATT Client disconnected from $mac")
                    activeGattClients.remove(mac)
                    clientMtuMap.remove(mac)
                    try {
                        gatt.close()
                    } catch (_: Exception) {}
                }
            }

            @SuppressLint("MissingPermission")
            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                Log.d(TAG, "GATT Client MTU changed for $mac: $mtu (status=$status)")
                clientMtuMap[mac] = mtu
                gatt.discoverServices()
            }

            @SuppressLint("MissingPermission")
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = gatt.getService(MESH_SERVICE_UUID)
                    if (service != null) {
                        Log.d(TAG, "Discovered NearbyMesh Service on remote device $mac!")

                        // Subscribe to remote device notifications
                        val notifyChar = service.getCharacteristic(CHAR_MESH_NOTIFY_UUID)
                        if (notifyChar != null) {
                            gatt.setCharacteristicNotification(notifyChar, true)
                            val cccd = notifyChar.getDescriptor(CLIENT_CONFIG_DESCRIPTOR_UUID)
                            if (cccd != null) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                                } else {
                                    @Suppress("DEPRECATION")
                                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    @Suppress("DEPRECATION")
                                    gatt.writeDescriptor(cccd)
                                }
                            }
                        }

                        // Immediately initiate mutual Handshake
                        scope.launch {
                            delay(180) // Allow descriptor write to complete in Android BLE queue
                            sendHandshake(gatt, mac, isAck = false)

                            // Flush any queued outbound messages for this node
                            val node = macToNodeMap[mac] ?: initialNodeId
                            drainPendingQueue(node)
                        }
                    } else {
                        Log.w(TAG, "Mesh Service not found on device $mac")
                    }
                } else {
                    Log.w(TAG, "onServicesDiscovered failed for $mac: status $status")
                }
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {
                Log.d(TAG, "GATT Client onDescriptorWrite for $mac: status=$status")
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                val success = (status == BluetoothGatt.GATT_SUCCESS)
                if (!success) {
                    Log.w(TAG, "Characteristic write returned status $status for $mac")
                }
                writeCompletable?.complete(success)
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                @Suppress("DEPRECATION")
                val value = characteristic.value ?: return
                handleIncomingBytes(mac, value)
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                handleIncomingBytes(mac, value)
            }
        }
    }

    /**
     * Sends mutual handshake frame to exchange NodeId, Display Name, and E2EE Public Key
     */
    private suspend fun sendHandshake(gatt: BluetoothGatt, mac: String, isAck: Boolean) {
        val myId = getMyNodeId?.invoke() ?: return
        val myName = getMyNickname?.invoke() ?: "Device $myId"
        val myKey = getMyPublicKey?.invoke()
        val payload = BleHandshakePayload(nodeId = myId, nickname = myName, publicKeyBase64 = myKey)
        val jsonBytes = gson.toJson(payload).toByteArray(Charsets.UTF_8)

        val frame = ByteArray(HEADER_SIZE + jsonBytes.size)
        val buffer = ByteBuffer.wrap(frame)
        buffer.put(if (isAck) FRAME_HANDSHAKE_ACK else FRAME_HANDSHAKE)
        buffer.putShort(0)
        buffer.put(0.toByte())
        buffer.put(1.toByte())
        buffer.put(0.toByte())
        buffer.put(jsonBytes)

        val service = gatt.getService(MESH_SERVICE_UUID) ?: return
        val charIncoming = service.getCharacteristic(CHAR_MESH_INCOMING_UUID) ?: return
        writeFrameSynchronous(gatt, charIncoming, frame)
        Log.d(TAG, "Sent Handshake (isAck=$isAck) to $mac for node $myId ($myName)")
    }

    /**
     * Sends a MeshPacket to a specific target node or broadcasts to all connected peers.
     * If the target is not yet connected, enqueues the packet and connects automatically.
     */
    fun sendPacket(packet: MeshPacket): Boolean {
        val targetNodeId = packet.targetNodeId
        val isBroadcast = packet.isBroadcast

        if (!isBroadcast) {
            val mac = nodeToMacMap[targetNodeId]
            val gatt = if (mac != null) activeGattClients[mac] else null
            val isClientReady = gatt != null && gatt.getService(MESH_SERVICE_UUID) != null
            val serverDevice = if (mac != null) serverConnectedDevices[mac] else null

            if (isClientReady || serverDevice != null) {
                return sendPacketDirect(packet)
            } else {
                // Enqueue packet in store-and-forward queue
                val queue = pendingOutboundPackets.computeIfAbsent(targetNodeId) { ConcurrentLinkedQueue() }
                queue.add(packet)
                Log.d(TAG, "Enqueued packet ${packet.packetId} for target $targetNodeId (waiting for connection)")

                // Trigger auto-connect if device reference exists
                val dev = knownDevices[targetNodeId]
                if (dev != null) {
                    connectToDevice(targetNodeId, dev)
                }
                return true
            }
        } else {
            // Broadcast packet across all connected channels
            return sendPacketDirect(packet)
        }
    }

    /**
     * Transmits a packet immediately over established BLE GATT client or server channels.
     */
    private fun sendPacketDirect(packet: MeshPacket): Boolean {
        val targetNodeId = packet.targetNodeId
        val isBroadcast = packet.isBroadcast
        val json = gson.toJson(packet)
        val dataBytes = json.toByteArray(Charsets.UTF_8)

        if (!isBroadcast) {
            val mac = nodeToMacMap[targetNodeId]
            if (mac != null) {
                val gatt = activeGattClients[mac]
                if (gatt != null && gatt.getService(MESH_SERVICE_UUID) != null) {
                    return sendBytesToGatt(gatt, mac, dataBytes)
                }

                // If not connected as client, send via Server notification if connected as server
                val serverDevice = serverConnectedDevices[mac]
                if (serverDevice != null) {
                    notifyServerConnectedDevice(serverDevice, dataBytes)
                    return true
                }
            }
            return false
        } else {
            var sentAny = false
            activeGattClients.forEach { (mac, gatt) ->
                if (gatt.getService(MESH_SERVICE_UUID) != null) {
                    if (sendBytesToGatt(gatt, mac, dataBytes)) {
                        sentAny = true
                    }
                }
            }

            serverConnectedDevices.values.forEach { device ->
                notifyServerConnectedDevice(device, dataBytes)
                sentAny = true
            }

            return sentAny
        }
    }

    /**
     * Drains any enqueued store-and-forward packets for a newly authenticated node.
     */
    fun drainPendingQueue(nodeId: String) {
        val queue = pendingOutboundPackets.remove(nodeId) ?: return
        if (queue.isEmpty()) return
        Log.d(TAG, "Draining ${queue.size} pending packets for $nodeId...")
        scope.launch {
            while (queue.isNotEmpty()) {
                val packet = queue.poll() ?: break
                val sent = sendPacketDirect(packet)
                if (!sent) {
                    queue.add(packet)
                    break
                }
                delay(40)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun notifyServerConnectedDevice(device: BluetoothDevice, data: ByteArray) {
        val server = gattServer ?: return
        val service = server.getService(MESH_SERVICE_UUID) ?: return
        val notifyChar = service.getCharacteristic(CHAR_MESH_NOTIFY_UUID) ?: return

        scope.launch {
            try {
                val seq = (seqCounter.incrementAndGet() and 0xFFFF).toShort()
                val mtu = 20 // Standard safe BLE notification MTU
                val maxChunkSize = mtu - HEADER_SIZE

                if (data.size <= maxChunkSize) {
                    val frame = ByteArray(HEADER_SIZE + data.size)
                    val buffer = ByteBuffer.wrap(frame)
                    buffer.put(FRAME_SINGLE)
                    buffer.putShort(seq)
                    buffer.put(0.toByte())
                    buffer.put(1.toByte())
                    buffer.put(0.toByte())
                    buffer.put(data)

                    sendNotificationFrame(server, device, notifyChar, frame)
                } else {
                    val totalChunks = ((data.size + maxChunkSize - 1) / maxChunkSize).coerceAtMost(255)
                    for (i in 0 until totalChunks) {
                        val offset = i * maxChunkSize
                        val length = (data.size - offset).coerceAtMost(maxChunkSize)

                        val frame = ByteArray(HEADER_SIZE + length)
                        val buffer = ByteBuffer.wrap(frame)
                        buffer.put(FRAME_CHUNK)
                        buffer.putShort(seq)
                        buffer.put(i.toByte())
                        buffer.put(totalChunks.toByte())
                        buffer.put(0.toByte())
                        buffer.put(data, offset, length)

                        sendNotificationFrame(server, device, notifyChar, frame)
                        delay(25)
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error in notifyServerConnectedDevice: ${t.message}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendNotificationFrame(
        server: BluetoothGattServer,
        device: BluetoothDevice,
        notifyChar: BluetoothGattCharacteristic,
        frame: ByteArray
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                server.notifyCharacteristicChanged(device, notifyChar, false, frame)
            } else {
                @Suppress("DEPRECATION")
                notifyChar.value = frame
                @Suppress("DEPRECATION")
                server.notifyCharacteristicChanged(device, notifyChar, false)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error sending notification frame: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendBytesToGatt(gatt: BluetoothGatt, mac: String, data: ByteArray): Boolean {
        val service = gatt.getService(MESH_SERVICE_UUID) ?: return false
        val charIncoming = service.getCharacteristic(CHAR_MESH_INCOMING_UUID) ?: return false

        val mtu = clientMtuMap[mac] ?: DEFAULT_CHUNK_SIZE
        val maxChunkSize = (mtu - HEADER_SIZE - 5).coerceIn(20, 500)
        val seq = (seqCounter.incrementAndGet() and 0xFFFF).toShort()

        scope.launch {
            try {
                if (data.size <= maxChunkSize) {
                    val frame = ByteArray(HEADER_SIZE + data.size)
                    val buffer = ByteBuffer.wrap(frame)
                    buffer.put(FRAME_SINGLE)
                    buffer.putShort(seq)
                    buffer.put(0.toByte())
                    buffer.put(1.toByte())
                    buffer.put(0.toByte())
                    buffer.put(data)

                    writeFrameSynchronous(gatt, charIncoming, frame)
                } else {
                    val totalChunks = ((data.size + maxChunkSize - 1) / maxChunkSize).coerceAtMost(255)
                    for (i in 0 until totalChunks) {
                        val offset = i * maxChunkSize
                        val length = (data.size - offset).coerceAtMost(maxChunkSize)

                        val frame = ByteArray(HEADER_SIZE + length)
                        val buffer = ByteBuffer.wrap(frame)
                        buffer.put(FRAME_CHUNK)
                        buffer.putShort(seq)
                        buffer.put(i.toByte())
                        buffer.put(totalChunks.toByte())
                        buffer.put(0.toByte())
                        buffer.put(data, offset, length)

                        writeFrameSynchronous(gatt, charIncoming, frame)
                        delay(10)
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error writing frame to $mac: ${t.message}")
            }
        }
        return true
    }

    @SuppressLint("MissingPermission")
    private suspend fun writeFrameSynchronous(
        gatt: BluetoothGatt,
        char: BluetoothGattCharacteristic,
        bytes: ByteArray
    ): Boolean {
        return writeLock.withLock {
            val deferred = CompletableDeferred<Boolean>()
            writeCompletable = deferred

            val initiated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val res = gatt.writeCharacteristic(
                    char,
                    bytes,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                )
                res == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                char.value = bytes
                @Suppress("DEPRECATION")
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(char)
            }

            if (!initiated) {
                writeCompletable = null
                return false
            }

            val result = withTimeoutOrNull(1500) { deferred.await() } ?: false
            writeCompletable = null
            result
        }
    }

    private fun handleIncomingBytes(sourceMac: String, rawBytes: ByteArray) {
        if (rawBytes.size < HEADER_SIZE) return

        val buffer = ByteBuffer.wrap(rawBytes)
        val frameType = buffer.get()
        val seq = buffer.short
        val chunkIdx = buffer.get().toInt() and 0xFF
        val totalChunks = buffer.get().toInt() and 0xFF
        buffer.get() // reserved byte

        val payloadLength = rawBytes.size - HEADER_SIZE
        val payload = ByteArray(payloadLength)
        buffer.get(payload)

        when (frameType) {
            FRAME_HANDSHAKE -> {
                handleHandshakeFrame(sourceMac, payload, isAck = false)
            }
            FRAME_HANDSHAKE_ACK -> {
                handleHandshakeFrame(sourceMac, payload, isAck = true)
            }
            FRAME_SINGLE, FRAME_CHUNK -> {
                val packetKey = "$sourceMac-$seq"
                if (frameType == FRAME_SINGLE || totalChunks <= 1) {
                    parseAndDeliverPacket(payload, sourceMac)
                } else {
                    val map = packetAssembler.computeIfAbsent(packetKey) { ConcurrentHashMap() }
                    map[chunkIdx] = payload
                    packetTotalChunks[packetKey] = totalChunks
                    packetTimestamp[packetKey] = System.currentTimeMillis()

                    if (map.size >= totalChunks) {
                        var hasAll = true
                        for (i in 0 until totalChunks) {
                            if (!map.containsKey(i)) {
                                hasAll = false
                                break
                            }
                        }
                        if (hasAll) {
                            val totalBytes = (0 until totalChunks).sumOf { map[it]?.size ?: 0 }
                            val assembled = ByteArray(totalBytes)
                            var currentOffset = 0
                            for (i in 0 until totalChunks) {
                                val chunk = map[i] ?: break
                                System.arraycopy(chunk, 0, assembled, currentOffset, chunk.size)
                                currentOffset += chunk.size
                            }

                            packetAssembler.remove(packetKey)
                            packetTotalChunks.remove(packetKey)
                            packetTimestamp.remove(packetKey)

                            parseAndDeliverPacket(assembled, sourceMac)
                        }
                    }
                }
            }
        }
    }

    private fun handleHandshakeFrame(sourceMac: String, payload: ByteArray, isAck: Boolean) {
        try {
            val json = String(payload, Charsets.UTF_8)
            val handshake = gson.fromJson(json, BleHandshakePayload::class.java)
            if (handshake != null && handshake.nodeId.isNotBlank()) {
                nodeToMacMap[handshake.nodeId] = sourceMac
                macToNodeMap[sourceMac] = handshake.nodeId

                Log.d(TAG, "Received Handshake (isAck=$isAck) from $sourceMac: node=${handshake.nodeId}, name=${handshake.nickname}")
                onPeerHandshakeReceived?.invoke(handshake.nodeId, handshake.nickname, handshake.publicKeyBase64)

                // Symmetrical Connection: If this phone is not yet connected as a GATT Client, connect back immediately
                val remoteDev = knownDevices[handshake.nodeId] ?: serverConnectedDevices[sourceMac]
                if (remoteDev != null && activeGattClients[sourceMac] == null) {
                    Log.d(TAG, "Initiating reciprocal GATT Client connection to $sourceMac (${handshake.nodeId})")
                    connectToDevice(handshake.nodeId, remoteDev)
                }

                // If remote initiated handshake, reply with our own Handshake ACK
                if (!isAck) {
                    val clientGatt = activeGattClients[sourceMac]
                    if (clientGatt != null && clientGatt.getService(MESH_SERVICE_UUID) != null) {
                        scope.launch {
                            sendHandshake(clientGatt, sourceMac, isAck = true)
                        }
                    }
                }

                // Drain queued store-and-forward messages for this peer
                drainPendingQueue(handshake.nodeId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling handshake from $sourceMac: ${e.message}")
        }
    }

    private fun parseAndDeliverPacket(bytes: ByteArray, sourceMac: String) {
        try {
            val json = String(bytes, Charsets.UTF_8)
            val packet = gson.fromJson(json, MeshPacket::class.java)
            if (packet != null) {
                nodeToMacMap[packet.senderNodeId] = sourceMac
                nodeToMacMap[packet.sourceNodeId] = sourceMac
                macToNodeMap[sourceMac] = packet.sourceNodeId

                Log.d(TAG, "Delivered BLE GATT packet [Type: ${packet.payloadType}] from ${packet.sourceNodeId}")
                onPacketReceived(packet)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse incoming BLE packet from $sourceMac: ${e.message}")
        }
    }

    private fun startAssemblerCleaner() {
        scope.launch {
            while (isRunning && isActive) {
                delay(15_000)
                val now = System.currentTimeMillis()
                val keysToRemove = mutableListOf<String>()
                packetTimestamp.forEach { (key, timestamp) ->
                    if (now - timestamp > 30_000) {
                        keysToRemove.add(key)
                    }
                }
                keysToRemove.forEach { key ->
                    packetAssembler.remove(key)
                    packetTotalChunks.remove(key)
                    packetTimestamp.remove(key)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        isRunning = false
        try {
            gattServer?.close()
        } catch (_: Exception) {}
        gattServer = null

        activeGattClients.forEach { (_, gatt) ->
            try {
                gatt.close()
            } catch (_: Exception) {}
        }
        activeGattClients.clear()
        clientMtuMap.clear()
        serverConnectedDevices.clear()
        packetAssembler.clear()
        Log.d(TAG, "BleGattManager stopped")
    }

    fun hasActiveConnections(): Boolean = activeGattClients.isNotEmpty() || serverConnectedDevices.isNotEmpty()
    fun getConnectedClientCount(): Int = activeGattClients.size + serverConnectedDevices.size
}
