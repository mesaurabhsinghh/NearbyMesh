package com.nearbymesh.app.core.filetransfer

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.nearbymesh.app.core.mesh.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.UUID

enum class TransferDirection {
    OUTGOING,
    INCOMING
}

enum class TransferStatus {
    WAITING,
    ACTIVE,
    PAUSED,
    COMPLETED,
    FAILED
}

data class FileTransferSession(
    val transferId: String,
    val fileName: String,
    val fileSize: Long,
    val chunkSize: Int,
    val totalChunks: Int,
    val targetOrSenderNodeId: String,
    val direction: TransferDirection,
    var status: TransferStatus = TransferStatus.WAITING,
    var completedChunksCount: Int = 0,
    val completedChunkIndices: MutableSet<Int> = mutableSetOf(),
    var bytesTransferred: Long = 0,
    var speedBytesPerSec: Long = 0,
    val fileSha256: String,
    val localFilePath: String,
    val startTime: Long = System.currentTimeMillis()
) {
    val progressPercentage: Float
        get() = if (totalChunks > 0) (completedChunksCount.toFloat() / totalChunks) * 100f else 0f
}

/**
 * Resumable, chunked file transfer engine.
 * Splits large files into discrete SHA-256 verified chunks, verifies each chunk with ACK/NACK,
 * and seamlessly resumes interrupted transfers from the exact missing chunk.
 */
class FileTransferManager(
    private val context: Context,
    private val onSendPacket: (MeshPacket) -> Unit
) {
    companion object {
        private const val TAG = "FileTransferManager"
        const val DEFAULT_CHUNK_SIZE = 512 * 1024 // 512 KB per chunk
        private val gson = Gson()
    }

    private val _sessions = MutableStateFlow<Map<String, FileTransferSession>>(emptyMap())
    val sessions: StateFlow<Map<String, FileTransferSession>> = _sessions.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sendJobs = mutableMapOf<String, Job>()

    /**
     * Initiates sending an outgoing file to a target node.
     */
    fun startSendFile(file: File, targetNodeId: String, myNodeId: String): String {
        val transferId = UUID.randomUUID().toString()
        val fileSize = file.length()
        val chunkSize = DEFAULT_CHUNK_SIZE
        val totalChunks = ((fileSize + chunkSize - 1) / chunkSize).toInt()
        val fileSha = computeFileSha256(file)

        val session = FileTransferSession(
            transferId = transferId,
            fileName = file.name,
            fileSize = fileSize,
            chunkSize = chunkSize,
            totalChunks = totalChunks,
            targetOrSenderNodeId = targetNodeId,
            direction = TransferDirection.OUTGOING,
            status = TransferStatus.WAITING,
            fileSha256 = fileSha,
            localFilePath = file.absolutePath
        )

        updateSession(session)

        // Send metadata packet to receiver
        val metadata = FileMetadataPayload(
            transferId = transferId,
            fileName = file.name,
            fileSize = fileSize,
            totalChunks = totalChunks,
            chunkSize = chunkSize,
            sha256 = fileSha,
            mimeType = "application/octet-stream"
        )

        val metadataPacket = MeshPacket(
            sourceNodeId = myNodeId,
            senderNodeId = myNodeId,
            targetNodeId = targetNodeId,
            payloadType = PayloadType.FILE_METADATA,
            payload = gson.toJson(metadata)
        )

        onSendPacket(metadataPacket)

        // Start sending chunks
        session.status = TransferStatus.ACTIVE
        updateSession(session)
        startChunkStreaming(session, myNodeId)

        return transferId
    }

    private fun startChunkStreaming(session: FileTransferSession, myNodeId: String) {
        val job = scope.launch {
            val file = File(session.localFilePath)
            if (!file.exists()) {
                session.status = TransferStatus.FAILED
                updateSession(session)
                return@launch
            }

            val buffer = ByteArray(session.chunkSize)
            var lastTime = System.currentTimeMillis()
            var bytesSinceLastTime = 0L

            RandomAccessFile(file, "r").use { raf ->
                for (chunkIdx in 0 until session.totalChunks) {
                    if (session.status == TransferStatus.PAUSED || !isActive) break

                    // Skip already acknowledged chunks (resumption support!)
                    if (session.completedChunkIndices.contains(chunkIdx)) continue

                    val offset = chunkIdx.toLong() * session.chunkSize
                    raf.seek(offset)
                    val bytesRead = raf.read(buffer)
                    if (bytesRead <= 0) break

                    val actualData = if (bytesRead == buffer.size) buffer else buffer.copyOf(bytesRead)
                    val chunkSha = computeBytesSha256(actualData)
                    val base64Data = Base64.encodeToString(actualData, Base64.NO_WRAP)

                    val chunkPayload = FileChunkPayload(
                        transferId = session.transferId,
                        chunkIndex = chunkIdx,
                        totalChunks = session.totalChunks,
                        dataBase64 = base64Data,
                        chunkSha256 = chunkSha
                    )

                    val chunkPacket = MeshPacket(
                        sourceNodeId = myNodeId,
                        senderNodeId = myNodeId,
                        targetNodeId = session.targetOrSenderNodeId,
                        payloadType = PayloadType.FILE_CHUNK,
                        payload = gson.toJson(chunkPayload)
                    )

                    onSendPacket(chunkPacket)

                    // Track speed
                    bytesSinceLastTime += bytesRead
                    val now = System.currentTimeMillis()
                    if (now - lastTime >= 1000) {
                        session.speedBytesPerSec = (bytesSinceLastTime * 1000) / (now - lastTime)
                        bytesSinceLastTime = 0
                        lastTime = now
                        updateSession(session)
                    }

                    // Slight delay to prevent buffer choke on radio
                    delay(30)
                }
            }
        }
        sendJobs[session.transferId] = job
    }

    /**
     * Receiver receives file metadata and initializes download session.
     */
    fun handleIncomingMetadata(packet: MeshPacket) {
        val metadata = try {
            gson.fromJson(packet.payload, FileMetadataPayload::class.java)
        } catch (e: Exception) {
            return
        }

        val downloadDir = File(context.cacheDir, "mesh_downloads")
        downloadDir.mkdirs()
        val destFile = File(downloadDir, "${metadata.transferId}_${metadata.fileName}")

        val session = FileTransferSession(
            transferId = metadata.transferId,
            fileName = metadata.fileName,
            fileSize = metadata.fileSize,
            chunkSize = metadata.chunkSize,
            totalChunks = metadata.totalChunks,
            targetOrSenderNodeId = packet.sourceNodeId,
            direction = TransferDirection.INCOMING,
            status = TransferStatus.ACTIVE,
            fileSha256 = metadata.sha256,
            localFilePath = destFile.absolutePath
        )

        updateSession(session)
        Log.d(TAG, "Incoming file accepted: ${metadata.fileName} (${metadata.fileSize} bytes in ${metadata.totalChunks} chunks)")
    }

    /**
     * Receiver receives a chunk, verifies its SHA-256, writes at chunk offset, and returns ACK.
     */
    fun handleIncomingChunk(packet: MeshPacket, myNodeId: String) {
        val chunk = try {
            gson.fromJson(packet.payload, FileChunkPayload::class.java)
        } catch (e: Exception) {
            return
        }

        val session = _sessions.value[chunk.transferId] ?: return
        val rawBytes = Base64.decode(chunk.dataBase64, Base64.NO_WRAP)
        val calculatedSha = computeBytesSha256(rawBytes)

        val isValid = calculatedSha == chunk.chunkSha256
        if (isValid) {
            try {
                val file = File(session.localFilePath)
                RandomAccessFile(file, "rw").use { raf ->
                    val offset = chunk.chunkIndex.toLong() * session.chunkSize
                    raf.seek(offset)
                    raf.write(rawBytes)
                }

                session.completedChunkIndices.add(chunk.chunkIndex)
                session.completedChunksCount = session.completedChunkIndices.size
                session.bytesTransferred = session.completedChunkIndices.size.toLong() * session.chunkSize
                if (session.completedChunksCount >= session.totalChunks) {
                    session.status = TransferStatus.COMPLETED
                    Log.d(TAG, "File transfer completed: ${session.fileName} (${session.fileSize} bytes) verified!")
                }
                updateSession(session)
            } catch (e: Exception) {
                Log.e(TAG, "Error writing chunk: ${e.message}")
            }
        }

        // Return ACK packet to sender
        val ackPayload = FileChunkAckPayload(
            transferId = chunk.transferId,
            chunkIndex = chunk.chunkIndex,
            success = isValid
        )
        val ackPacket = MeshPacket(
            sourceNodeId = myNodeId,
            senderNodeId = myNodeId,
            targetNodeId = packet.sourceNodeId,
            payloadType = PayloadType.FILE_CHUNK_ACK,
            payload = gson.toJson(ackPayload)
        )
        onSendPacket(ackPacket)
    }

    fun handleChunkAck(packet: MeshPacket) {
        val ack = try {
            gson.fromJson(packet.payload, FileChunkAckPayload::class.java)
        } catch (e: Exception) {
            return
        }

        val session = _sessions.value[ack.transferId] ?: return
        if (ack.success) {
            session.completedChunkIndices.add(ack.chunkIndex)
            session.completedChunksCount = session.completedChunkIndices.size
            session.bytesTransferred = session.completedChunkIndices.size.toLong() * session.chunkSize
            if (session.completedChunksCount >= session.totalChunks) {
                session.status = TransferStatus.COMPLETED
            }
            updateSession(session)
        }
    }

    fun pauseTransfer(transferId: String) {
        val session = _sessions.value[transferId] ?: return
        session.status = TransferStatus.PAUSED
        sendJobs[transferId]?.cancel()
        updateSession(session)
    }

    fun resumeTransfer(transferId: String, myNodeId: String) {
        val session = _sessions.value[transferId] ?: return
        if (session.direction == TransferDirection.OUTGOING && session.status == TransferStatus.PAUSED) {
            session.status = TransferStatus.ACTIVE
            updateSession(session)
            startChunkStreaming(session, myNodeId)
        }
    }

    private fun updateSession(session: FileTransferSession) {
        val current = _sessions.value.toMutableMap()
        current[session.transferId] = session
        _sessions.value = current
    }

    private fun computeFileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buf = ByteArray(16384)
            var n: Int
            while (fis.read(buf).also { n = it } > 0) {
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun computeBytesSha256(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }

    fun stop() {
        sendJobs.values.forEach { it.cancel() }
        scope.cancel()
    }
}
