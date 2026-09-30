package com.nearbymesh.app.core.mesh

/**
 * Supported payload types inside a MeshPacket envelope.
 */
enum class PayloadType {
    TEXT_MESSAGE,
    MESSAGE_ACK,
    FILE_METADATA,
    FILE_CHUNK,
    FILE_CHUNK_ACK,
    PEER_ANNOUNCE,
    EMERGENCY_SOS,
    PING,
    PONG,
    CALL_REQUEST,
    CALL_ACCEPT,
    CALL_REJECT,
    CALL_HANGUP,
    CALL_AUDIO_FRAME
}

/**
 * Plaintext chat message payload (encrypted inside packet.payload if E2EE).
 */
data class TextMessagePayload(
    val messageId: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Message delivery acknowledgement
 */
data class MessageAckPayload(
    val originalPacketId: String,
    val originalMessageId: String,
    val deliveredViaHops: Int,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * File transfer metadata header
 */
data class FileMetadataPayload(
    val transferId: String,
    val fileName: String,
    val fileSize: Long,
    val totalChunks: Int,
    val chunkSize: Int,
    val sha256: String,
    val mimeType: String
)

/**
 * Individual chunk of a file
 */
data class FileChunkPayload(
    val transferId: String,
    val chunkIndex: Int,
    val totalChunks: Int,
    val dataBase64: String,
    val chunkSha256: String
)

/**
 * ACK for a received chunk (enables selective retransmission and resume)
 */
data class FileChunkAckPayload(
    val transferId: String,
    val chunkIndex: Int,
    val success: Boolean
)

/**
 * High-priority Emergency SOS alert payload
 */
data class EmergencySosPayload(
    val senderNickname: String,
    val distressMessage: String,
    val batteryLevel: Int,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Broadcast periodic announcement for mesh topology discovery
 */
data class PeerAnnouncePayload(
    val nickname: String,
    val batteryLevel: Int,
    val publicKeyBase64: String,
    val isRelayEnabled: Boolean = true
)

/**
 * Offline Voice Call signaling payload (Request, Accept, Reject, Hangup)
 */
data class CallSignalPayload(
    val callId: String,
    val callerNodeId: String,
    val callerName: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Live Audio frame streaming payload for zero-internet voice calls
 */
data class CallAudioPayload(
    val callId: String,
    val sequenceNumber: Long,
    val audioBase64: String
)

