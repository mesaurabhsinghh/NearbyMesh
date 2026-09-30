package com.nearbymesh.app.core.transport.socket

import android.util.Log
import com.nearbymesh.app.core.mesh.MeshPacket
import kotlinx.coroutines.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * P2P Socket Client that connects to a Group Owner or peer device.
 */
class P2PSocketClient(
    private val hostAddress: String,
    private val port: Int = 8988,
    private val onPacketReceived: (packet: MeshPacket) -> Unit,
    private val onConnectionStateChanged: (isConnected: Boolean) -> Unit
) {
    companion object {
        private const val TAG = "P2PSocketClient"
    }

    private var socket: Socket? = null
    private var inputStream: DataInputStream? = null
    private var outputStream: DataOutputStream? = null
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun connect() {
        if (isRunning) return
        isRunning = true

        scope.launch {
            try {
                Log.d(TAG, "Connecting to P2P server at $hostAddress:$port...")
                socket = Socket()
                socket?.connect(InetSocketAddress(hostAddress, port), 10_000)

                inputStream = DataInputStream(socket?.getInputStream())
                outputStream = DataOutputStream(socket?.getOutputStream())
                onConnectionStateChanged(true)
                Log.d(TAG, "Connected to $hostAddress:$port")

                while (isRunning && isActive && socket?.isClosed == false) {
                    val length = inputStream?.readInt() ?: break
                    if (length <= 0 || length > 10 * 1024 * 1024) break

                    val buffer = ByteArray(length)
                    inputStream?.readFully(buffer)
                    val json = String(buffer, Charsets.UTF_8)
                    val packet = MeshPacket.fromJson(json)

                    if (packet != null) {
                        onPacketReceived(packet)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Socket client error: ${e.message}")
            } finally {
                disconnect()
            }
        }
    }

    fun sendPacket(packet: MeshPacket) {
        scope.launch {
            try {
                val data = packet.toJson().toByteArray(Charsets.UTF_8)
                outputStream?.let { os ->
                    synchronized(os) {
                        os.writeInt(data.size)
                        os.write(data)
                        os.flush()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Send packet error: ${e.message}")
                disconnect()
            }
        }
    }

    fun isConnected(): Boolean = socket?.isConnected == true && socket?.isClosed == false

    fun disconnect() {
        isRunning = false
        try {
            socket?.close()
        } catch (_: Exception) {}
        onConnectionStateChanged(false)
    }

    fun stop() {
        disconnect()
        scope.cancel()
    }
}
