package com.nearbymesh.app.core.transport.socket

import android.util.Log
import com.nearbymesh.app.core.mesh.MeshPacket
import kotlinx.coroutines.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

/**
 * High-speed TCP Socket Server running over Wi-Fi Direct / Local Wi-Fi link.
 * Listens on port 8988, receives framed packets and broadcasts to connected clients.
 */
class P2PSocketServer(
    private val port: Int = 8988,
    private val onPacketReceived: (packet: MeshPacket) -> Unit
) {
    companion object {
        private const val TAG = "P2PSocketServer"
    }

    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val connectedClients = CopyOnWriteArrayList<SocketHandler>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start() {
        if (isRunning) return
        isRunning = true
        scope.launch {
            try {
                serverSocket = ServerSocket(port)
                Log.d(TAG, "P2P Server listening on port $port")

                while (isRunning && isActive) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        val handler = SocketHandler(clientSocket)
                        connectedClients.add(handler)
                        handler.start()
                    } catch (e: Exception) {
                        if (!isRunning) break
                        Log.e(TAG, "Error accepting client: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server socket error: ${e.message}")
            }
        }
    }

    fun broadcastPacket(packet: MeshPacket, excludeSenderId: String? = null) {
        val jsonBytes = packet.toJson().toByteArray(Charsets.UTF_8)
        val iterator = connectedClients.iterator()
        while (iterator.hasNext()) {
            val handler = iterator.next()
            if (handler.isAlive() && handler.remoteNodeId != excludeSenderId) {
                handler.sendPacket(jsonBytes)
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        connectedClients.forEach { it.close() }
        connectedClients.clear()
        scope.cancel()
    }

    inner class SocketHandler(private val socket: Socket) {
        var remoteNodeId: String? = null
        private var inputStream: DataInputStream? = null
        private var outputStream: DataOutputStream? = null
        private var clientJob: Job? = null

        fun start() {
            clientJob = scope.launch {
                try {
                    inputStream = DataInputStream(socket.getInputStream())
                    outputStream = DataOutputStream(socket.getOutputStream())

                    while (isRunning && isActive && !socket.isClosed) {
                        val length = inputStream?.readInt() ?: break
                        if (length <= 0 || length > 10 * 1024 * 1024) break // Max 10MB packet safety limit

                        val buffer = ByteArray(length)
                        inputStream?.readFully(buffer)
                        val json = String(buffer, Charsets.UTF_8)
                        val packet = MeshPacket.fromJson(json)

                        if (packet != null) {
                            remoteNodeId = packet.senderNodeId
                            onPacketReceived(packet)
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Client disconnected: ${socket.inetAddress} (${e.message})")
                } finally {
                    close()
                    connectedClients.remove(this@SocketHandler)
                }
            }
        }

        fun sendPacket(data: ByteArray) {
            scope.launch {
                try {
                    outputStream?.let { os ->
                        synchronized(os) {
                            os.writeInt(data.size)
                            os.write(data)
                            os.flush()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send packet to client: ${e.message}")
                    close()
                }
            }
        }

        fun isAlive(): Boolean = !socket.isClosed && socket.isConnected

        fun close() {
            try {
                socket.close()
            } catch (_: Exception) {}
            clientJob?.cancel()
        }
    }
}
