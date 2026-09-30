package com.nearbymesh.app.core.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.nearbymesh.app.core.mesh.CallAudioPayload
import com.nearbymesh.app.core.mesh.CallSignalPayload
import com.nearbymesh.app.core.mesh.MeshPacket
import com.nearbymesh.app.core.mesh.PayloadType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

enum class CallState {
    IDLE,
    OUTGOING_CALLING,
    INCOMING_RINGING,
    CONNECTED,
    ENDED
}

data class ActiveCallInfo(
    val callId: String,
    val peerNodeId: String,
    val peerName: String,
    val isIncoming: Boolean,
    val state: CallState,
    val durationSeconds: Long = 0,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = true
)

/**
 * Zero-internet offline voice calling engine.
 * Streams real-time 16kHz PCM audio bidirectionally over the mesh network
 * using AudioRecord and AudioTrack.
 */
class VoiceCallManager(
    private val context: Context,
    private val myNodeId: String,
    private val myNickname: String,
    private val sendPacket: (MeshPacket) -> Unit
) {
    companion object {
        private const val TAG = "VoiceCallManager"
        private const val SAMPLE_RATE = 8000
        private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val FRAME_SIZE_BYTES = 320 // 20ms of audio at 8kHz 16-bit Mono (single BLE GATT packet)
        private val gson = Gson()
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _activeCall = MutableStateFlow<ActiveCallInfo?>(null)
    val activeCall: StateFlow<ActiveCallInfo?> = _activeCall.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private var recordingJob: Job? = null
    private var durationTimerJob: Job? = null
    private var sequenceNumber: Long = 0L

    /**
     * Initiates an outgoing call to a peer.
     */
    fun startCall(targetNodeId: String, targetName: String) {
        if (_activeCall.value != null && _activeCall.value?.state != CallState.ENDED && _activeCall.value?.state != CallState.IDLE) {
            Log.w(TAG, "Already in a call")
            return
        }

        val callId = UUID.randomUUID().toString()
        _activeCall.value = ActiveCallInfo(
            callId = callId,
            peerNodeId = targetNodeId,
            peerName = targetName,
            isIncoming = false,
            state = CallState.OUTGOING_CALLING
        )

        val signal = CallSignalPayload(
            callId = callId,
            callerNodeId = myNodeId,
            callerName = myNickname
        )

        val packet = MeshPacket(
            sourceNodeId = myNodeId,
            senderNodeId = myNodeId,
            targetNodeId = targetNodeId,
            ttl = 4,
            payloadType = PayloadType.CALL_REQUEST,
            payload = gson.toJson(signal)
        )

        sendPacket(packet)
        Log.d(TAG, "Initiated call $callId to $targetNodeId ($targetName)")
    }

    /**
     * Accepts an incoming call.
     */
    fun acceptCall() {
        val current = _activeCall.value ?: return
        if (current.state != CallState.INCOMING_RINGING) return

        val signal = CallSignalPayload(
            callId = current.callId,
            callerNodeId = myNodeId,
            callerName = myNickname
        )

        val packet = MeshPacket(
            sourceNodeId = myNodeId,
            senderNodeId = myNodeId,
            targetNodeId = current.peerNodeId,
            ttl = 4,
            payloadType = PayloadType.CALL_ACCEPT,
            payload = gson.toJson(signal)
        )

        sendPacket(packet)
        startAudioStream(current.callId, current.peerNodeId, current.peerName, current.isIncoming)
    }

    /**
     * Rejects an incoming call.
     */
    fun rejectCall() {
        val current = _activeCall.value ?: return
        val signal = CallSignalPayload(
            callId = current.callId,
            callerNodeId = myNodeId,
            callerName = myNickname
        )

        val packet = MeshPacket(
            sourceNodeId = myNodeId,
            senderNodeId = myNodeId,
            targetNodeId = current.peerNodeId,
            ttl = 4,
            payloadType = PayloadType.CALL_REJECT,
            payload = gson.toJson(signal)
        )

        sendPacket(packet)
        endCallLocal()
    }

    /**
     * Hangs up the active call.
     */
    fun hangupCall() {
        val current = _activeCall.value ?: return
        val signal = CallSignalPayload(
            callId = current.callId,
            callerNodeId = myNodeId,
            callerName = myNickname
        )

        val packet = MeshPacket(
            sourceNodeId = myNodeId,
            senderNodeId = myNodeId,
            targetNodeId = current.peerNodeId,
            ttl = 4,
            payloadType = PayloadType.CALL_HANGUP,
            payload = gson.toJson(signal)
        )

        sendPacket(packet)
        endCallLocal()
    }

    fun toggleMute() {
        val current = _activeCall.value ?: return
        _activeCall.value = current.copy(isMuted = !current.isMuted)
    }

    fun toggleSpeaker() {
        val current = _activeCall.value ?: return
        val newSpeakerState = !current.isSpeakerOn
        audioManager?.isSpeakerphoneOn = newSpeakerState
        _activeCall.value = current.copy(isSpeakerOn = newSpeakerState)
    }

    /**
     * Handles incoming mesh packets related to calling.
     */
    fun handleIncomingCallPacket(packet: MeshPacket) {
        when (packet.payloadType) {
            PayloadType.CALL_REQUEST -> {
                val signal = try {
                    gson.fromJson(packet.payload, CallSignalPayload::class.java)
                } catch (_: Exception) { null } ?: return

                // If already in a call, auto-reject
                val current = _activeCall.value
                if (current != null && current.state == CallState.CONNECTED) {
                    val reject = CallSignalPayload(signal.callId, myNodeId, myNickname)
                    val rejectPacket = MeshPacket(
                        sourceNodeId = myNodeId,
                        senderNodeId = myNodeId,
                        targetNodeId = packet.sourceNodeId,
                        payloadType = PayloadType.CALL_REJECT,
                        payload = gson.toJson(reject)
                    )
                    sendPacket(rejectPacket)
                    return
                }

                _activeCall.value = ActiveCallInfo(
                    callId = signal.callId,
                    peerNodeId = packet.sourceNodeId,
                    peerName = signal.callerName.ifEmpty { "Node ${packet.sourceNodeId.take(4)}" },
                    isIncoming = true,
                    state = CallState.INCOMING_RINGING
                )
                Log.d(TAG, "Incoming call received from ${packet.sourceNodeId}")
            }

            PayloadType.CALL_ACCEPT -> {
                val current = _activeCall.value ?: return
                if (current.state == CallState.OUTGOING_CALLING) {
                    startAudioStream(current.callId, current.peerNodeId, current.peerName, current.isIncoming)
                }
            }

            PayloadType.CALL_REJECT -> {
                Log.d(TAG, "Call rejected by peer")
                endCallLocal()
            }

            PayloadType.CALL_HANGUP -> {
                Log.d(TAG, "Call hung up by peer")
                endCallLocal()
            }

            PayloadType.CALL_AUDIO_FRAME -> {
                val current = _activeCall.value ?: return
                if (current.state == CallState.CONNECTED) {
                    handleAudioFrame(packet.payload)
                }
            }

            else -> {}
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAudioStream(callId: String, peerNodeId: String, peerName: String, isIncoming: Boolean) {
        _activeCall.value = ActiveCallInfo(
            callId = callId,
            peerNodeId = peerNodeId,
            peerName = peerName,
            isIncoming = isIncoming,
            state = CallState.CONNECTED,
            durationSeconds = 0,
            isSpeakerOn = true
        )

        try {
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager?.isSpeakerphoneOn = true

            // Set up AudioTrack for playback
            val minPlayBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, ENCODING)
            val playBufSize = minPlayBuf.coerceAtLeast(FRAME_SIZE_BYTES * 4)

            audioTrack = AudioTrack.Builder()
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(ENCODING)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_OUT)
                        .build()
                )
                .setBufferSizeInBytes(playBufSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()

            // Set up AudioRecord for recording
            val minRecBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING)
            val recBufSize = minRecBuf.coerceAtLeast(FRAME_SIZE_BYTES * 4)

            val rec = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_IN,
                ENCODING,
                recBufSize
            )

            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                rec.release()
                return
            }

            audioRecord = rec
            rec.startRecording()
            sequenceNumber = 0L

            // Background recording loop
            recordingJob?.cancel()
            recordingJob = scope.launch {
                val frameBuffer = ByteArray(FRAME_SIZE_BYTES)
                while (isActive && _activeCall.value?.state == CallState.CONNECTED) {
                    val readBytes = audioRecord?.read(frameBuffer, 0, FRAME_SIZE_BYTES) ?: -1
                    if (readBytes > 0) {
                        val currentCall = _activeCall.value
                        if (currentCall != null && !currentCall.isMuted) {
                            val base64 = Base64.encodeToString(frameBuffer, 0, readBytes, Base64.NO_WRAP)
                            val audioPayload = CallAudioPayload(
                                callId = callId,
                                sequenceNumber = sequenceNumber++,
                                audioBase64 = base64
                            )
                            val packet = MeshPacket(
                                sourceNodeId = myNodeId,
                                senderNodeId = myNodeId,
                                targetNodeId = peerNodeId,
                                ttl = 2, // Low TTL for real-time low latency
                                payloadType = PayloadType.CALL_AUDIO_FRAME,
                                payload = gson.toJson(audioPayload)
                            )
                            sendPacket(packet)
                        }
                    }
                    delay(18) // Cadence match 20ms audio frame
                }
            }

            // Duration timer ticker
            durationTimerJob?.cancel()
            durationTimerJob = scope.launch {
                while (isActive && _activeCall.value?.state == CallState.CONNECTED) {
                    delay(1000)
                    _activeCall.value?.let { current ->
                        _activeCall.value = current.copy(durationSeconds = current.durationSeconds + 1)
                    }
                }
            }

            Log.d(TAG, "Audio stream active for call $callId")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting audio stream: ${e.message}")
            endCallLocal()
        }
    }

    private fun handleAudioFrame(jsonPayload: String) {
        try {
            val audioPayload = gson.fromJson(jsonPayload, CallAudioPayload::class.java) ?: return
            val pcmBytes = Base64.decode(audioPayload.audioBase64, Base64.NO_WRAP)
            audioTrack?.write(pcmBytes, 0, pcmBytes.size)
        } catch (e: Exception) {
            Log.w(TAG, "Error writing audio frame to track: ${e.message}")
        }
    }

    private fun endCallLocal() {
        recordingJob?.cancel()
        recordingJob = null
        durationTimerJob?.cancel()
        durationTimerJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null

        try {
            audioManager?.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {}

        _activeCall.value?.let { current ->
            _activeCall.value = current.copy(state = CallState.ENDED)
        }

        scope.launch {
            delay(1500)
            _activeCall.value = null
        }
    }
}
