package com.nearbymesh.app.core.transport.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.nearbymesh.app.core.mesh.MeshPacket
import kotlinx.coroutines.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance, zero-internet Bluetooth RFCOMM Socket Manager.
 * Establishes real phone-to-phone bidirectional communication streams
 * for instant text messages, file transfers, and real-time offline voice calls.
 */
class BluetoothRfcommManager(
    private val context: Context,
    private val onPacketReceived: (MeshPacket) -> Unit
) {
    companion object {
        private const val TAG = "BtRfcommManager"
        // Dedicated NearbyMesh RFCOMM UUID for peer-to-peer data streaming
        val MESH_RFCOMM_UUID: UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66")
        private const val SERVICE_NAME = "NearbyMeshRfcomm"
        private val gson = Gson()
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var isRunning = false

    private var serverSocket: BluetoothServerSocket? = null
    private var serverJob: Job? = null

    // Active sockets keyed by Bluetooth MAC address
    private val activeConnections = ConcurrentHashMap<String, ConnectedPeer>()

    // Mapping from Mesh NodeId to Bluetooth MAC address
    private val nodeToMacMap = ConcurrentHashMap<String, String>()

    class ConnectedPeer(
        val device: BluetoothDevice,
        val socket: BluetoothSocket,
        val outputStream: DataOutputStream,
        val inputStream: DataInputStream,
        var readJob: Job? = null
    ) {
        val isConnected: Boolean
            get() = socket.isConnected
    }

    /**
     * Starts listening for incoming Bluetooth connections from other NearbyMesh phones.
     */
    @SuppressLint("MissingPermission")
    fun start() {
        if (isRunning) return
        isRunning = true

        startServer()
        autoConnectBondedDevices()
        Log.d(TAG, "Bluetooth RFCOMM Manager started")
    }

    @SuppressLint("MissingPermission")
    fun autoConnectBondedDevices() {
        if (bluetoothAdapter?.isEnabled != true) return
        scope.launch {
            try {
                val bonded = bluetoothAdapter.bondedDevices ?: return@launch
                for (device in bonded) {
                    val mac = device.address
                    if (!activeConnections.containsKey(mac)) {
                        Log.d(TAG, "Auto-connecting RFCOMM to bonded device: ${device.name ?: "Peer"} ($mac)")
                        connectToDevice(device)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error checking bonded devices: ${e.message}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startServer() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.w(TAG, "Bluetooth adapter not enabled or unavailable")
            return
        }

        serverJob?.cancel()
        serverJob = scope.launch {
            while (isRunning && isActive) {
                try {
                    Log.d(TAG, "Opening RFCOMM Insecure Server Socket...")
                    serverSocket = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(
                        SERVICE_NAME,
                        MESH_RFCOMM_UUID
                    )

                    while (isRunning && isActive) {
                        val socket = try {
                            serverSocket?.accept()
                        } catch (e: IOException) {
                            if (isRunning) {
                                Log.w(TAG, "Server socket accept IOException: ${e.message}")
                            }
                            null
                        }

                        if (socket != null) {
                            val peerDevice = socket.remoteDevice
                            Log.d(TAG, "Incoming Bluetooth connection accepted from: ${peerDevice.name} (${peerDevice.address})")
                            handleNewConnection(peerDevice, socket)
                        }
                    }
                } catch (t: Throwable) {
                    if (isRunning) {
                        Log.e(TAG, "RFCOMM server loop error: ${t.message}. Retrying in 3s...")
                        delay(3000)
                    }
                } finally {
                    try {
                        serverSocket?.close()
                    } catch (_: Exception) {}
                    serverSocket = null
                }
            }
        }
    }

    /**
     * Registers a known node's Bluetooth MAC address for targeted routing.
     */
    fun registerNodeMac(nodeId: String, macAddress: String) {
        nodeToMacMap[nodeId] = macAddress
    }

    /**
     * Connects to a remote Bluetooth device if not already connected.
     */
    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        val mac = device.address
        val existing = activeConnections[mac]
        if (existing != null && existing.isConnected) {
            return
        }

        scope.launch {
            try {
                Log.d(TAG, "Initiating RFCOMM connection to ${device.name ?: "Peer"} ($mac)...")
                // Cancel discovery to avoid huge latency and connection failures
                try {
                    bluetoothAdapter?.cancelDiscovery()
                } catch (_: Exception) {}

                val socket = device.createInsecureRfcommSocketToServiceRecord(MESH_RFCOMM_UUID)
                socket.connect()

                Log.d(TAG, "Successfully connected to ${device.name ?: "Peer"} ($mac)")
                handleNewConnection(device, socket)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to connect to ${device.address}: ${e.message}")
            }
        }
    }

    /**
     * Sets up IO streams and starts reading packets from an established socket.
     */
    private fun handleNewConnection(device: BluetoothDevice, socket: BluetoothSocket) {
        val mac = device.address

        // Close any prior socket for this MAC
        activeConnections[mac]?.let { old ->
            try {
                old.readJob?.cancel()
                old.socket.close()
            } catch (_: Exception) {}
        }

        try {
            val out = DataOutputStream(socket.outputStream)
            val `in` = DataInputStream(socket.inputStream)

            val peer = ConnectedPeer(device, socket, out, `in`)
            val job = scope.launch {
                readLoop(peer)
            }
            peer.readJob = job
            activeConnections[mac] = peer
            Log.d(TAG, "Active RFCOMM connections count: ${activeConnections.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up streams for $mac: ${e.message}")
            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Continuously reads binary-framed JSON MeshPackets from the Bluetooth socket stream.
     */
    private suspend fun readLoop(peer: ConnectedPeer) {
        val mac = peer.device.address
        val inStream = peer.inputStream

        try {
            while (isRunning && peer.isConnected) {
                // Read 4-byte packet length prefix
                val length = withContext(Dispatchers.IO) {
                    inStream.readInt()
                }

                if (length <= 0 || length > 10_000_000) {
                    Log.w(TAG, "Invalid packet length received: $length from $mac")
                    break
                }

                val buffer = ByteArray(length)
                withContext(Dispatchers.IO) {
                    inStream.readFully(buffer)
                }

                val json = String(buffer, Charsets.UTF_8)
                val packet = gson.fromJson(json, MeshPacket::class.java)

                if (packet != null) {
                    // Cache sender node ID to MAC mapping
                    nodeToMacMap[packet.senderNodeId] = mac
                    nodeToMacMap[packet.sourceNodeId] = mac

                    // Forward to MeshCoordinator / MeshRouter
                    onPacketReceived(packet)
                }
            }
        } catch (e: Exception) {
            if (isRunning) {
                Log.d(TAG, "Connection closed with $mac: ${e.message}")
            }
        } finally {
            closePeer(mac)
        }
    }

    /**
     * Sends a packet to a specific peer by MAC address.
     */
    fun sendToMac(macAddress: String, packet: MeshPacket): Boolean {
        val peer = activeConnections[macAddress]
        if (peer == null || !peer.isConnected) {
            // Attempt to connect if device known
            try {
                val dev = bluetoothAdapter?.getRemoteDevice(macAddress)
                if (dev != null) {
                    connectToDevice(dev)
                }
            } catch (_: Exception) {}
            return false
        }

        return sendToPeer(peer, packet)
    }

    /**
     * Sends a packet to a specific peer by Mesh NodeId.
     */
    fun sendToNode(nodeId: String, packet: MeshPacket): Boolean {
        val mac = nodeToMacMap[nodeId]
        if (mac != null) {
            return sendToMac(mac, packet)
        }
        return false
    }

    /**
     * Broadcasts a packet across ALL active Bluetooth RFCOMM sockets.
     */
    fun broadcastPacket(packet: MeshPacket, excludeSenderId: String? = null) {
        val excludeMac = if (excludeSenderId != null) nodeToMacMap[excludeSenderId] else null

        activeConnections.forEach { (mac, peer) ->
            if (mac != excludeMac && peer.isConnected) {
                scope.launch {
                    sendToPeer(peer, packet)
                }
            }
        }
    }

    private fun sendToPeer(peer: ConnectedPeer, packet: MeshPacket): Boolean {
        return try {
            val jsonBytes = gson.toJson(packet).toByteArray(Charsets.UTF_8)
            synchronized(peer.outputStream) {
                peer.outputStream.writeInt(jsonBytes.size)
                peer.outputStream.write(jsonBytes)
                peer.outputStream.flush()
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Error writing packet to ${peer.device.address}: ${e.message}")
            closePeer(peer.device.address)
            false
        }
    }

    private fun closePeer(mac: String) {
        activeConnections.remove(mac)?.let { peer ->
            try {
                peer.readJob?.cancel()
                peer.socket.close()
            } catch (_: Exception) {}
        }
        Log.d(TAG, "Closed peer $mac. Remaining active: ${activeConnections.size}")
    }

    /**
     * Stops the RFCOMM server and closes all active connections.
     */
    fun stop() {
        isRunning = false
        serverJob?.cancel()

        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null

        activeConnections.forEach { (_, peer) ->
            try {
                peer.readJob?.cancel()
                peer.socket.close()
            } catch (_: Exception) {}
        }
        activeConnections.clear()
        Log.d(TAG, "Bluetooth RFCOMM Manager stopped")
    }

    fun hasActiveConnections(): Boolean = activeConnections.any { it.value.isConnected }
    fun getConnectedDeviceCount(): Int = activeConnections.count { it.value.isConnected }
}
