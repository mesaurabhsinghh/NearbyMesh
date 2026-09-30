package com.nearbymesh.app.core.emergency

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.google.gson.Gson
import com.nearbymesh.app.core.mesh.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ActiveEmergencyAlert(
    val alertId: String,
    val senderNodeId: String,
    val senderNickname: String,
    val distressMessage: String,
    val batteryPercent: Int,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val hopCount: Int,
    val hopPath: List<String>,
    val timestamp: Long = System.currentTimeMillis()
)

class EmergencyManager(
    private val context: Context,
    private val onBroadcastPacket: (MeshPacket) -> Unit
) {
    companion object {
        private const val TAG = "EmergencyManager"
        private val gson = Gson()
    }

    private val _activeAlerts = MutableStateFlow<List<ActiveEmergencyAlert>>(emptyList())
    val activeAlerts: StateFlow<List<ActiveEmergencyAlert>> = _activeAlerts.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /**
     * Broadcasts an Emergency SOS packet to the entire mesh with maximum TTL.
     */
    fun broadcastEmergencySos(myNodeId: String, nickname: String, message: String, batteryLevel: Int) {
        val payload = EmergencySosPayload(
            senderNickname = nickname,
            distressMessage = message,
            batteryLevel = batteryLevel
        )

        val packet = MeshPacket(
            sourceNodeId = myNodeId,
            senderNodeId = myNodeId,
            targetNodeId = MeshPacket.BROADCAST_ID,
            ttl = MeshPacket.MAX_TTL, // Floods maximum hops across entire mesh network
            hopCount = 0,
            hopPath = mutableListOf(myNodeId),
            payloadType = PayloadType.EMERGENCY_SOS,
            payload = gson.toJson(payload)
        )

        onBroadcastPacket(packet)

        // Also add to local active alerts
        val localAlert = ActiveEmergencyAlert(
            alertId = packet.packetId,
            senderNodeId = myNodeId,
            senderNickname = "$nickname (You)",
            distressMessage = message,
            batteryPercent = batteryLevel,
            hopCount = 0,
            hopPath = listOf(myNodeId)
        )
        addAlert(localAlert)
    }

    /**
     * Handles incoming SOS packet from another node in the mesh.
     */
    fun handleIncomingSos(packet: MeshPacket) {
        val sos = try {
            gson.fromJson(packet.payload, EmergencySosPayload::class.java)
        } catch (e: Exception) {
            return
        }

        val alert = ActiveEmergencyAlert(
            alertId = packet.packetId,
            senderNodeId = packet.sourceNodeId,
            senderNickname = sos.senderNickname,
            distressMessage = sos.distressMessage,
            batteryPercent = sos.batteryLevel,
            latitude = sos.latitude,
            longitude = sos.longitude,
            hopCount = packet.hopCount,
            hopPath = packet.hopPath,
            timestamp = sos.timestamp
        )

        addAlert(alert)
        triggerDistressAlarm()
    }

    private fun addAlert(alert: ActiveEmergencyAlert) {
        val current = _activeAlerts.value.toMutableList()
        if (current.none { it.alertId == alert.alertId }) {
            current.add(0, alert)
            _activeAlerts.value = current
        }
    }

    fun dismissAlert(alertId: String) {
        _activeAlerts.value = _activeAlerts.value.filter { it.alertId != alertId }
    }

    private fun triggerDistressAlarm() {
        scope.launch {
            try {
                // Morse code SOS vibration: ... --- ...
                val pattern = longArrayOf(0, 150, 100, 150, 100, 150, 300, 400, 100, 400, 100, 400, 300, 150, 100, 150, 100, 150)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, -1)
                }

                // Play high-pitch emergency alert tone
                val tone = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                tone.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 1500)
                delay(1500)
                tone.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error playing alarm: ${e.message}")
            }
        }
    }

    fun stop() {
        scope.cancel()
    }
}
