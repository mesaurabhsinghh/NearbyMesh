package com.nearbymesh.app.core.mesh

import java.util.concurrent.ConcurrentLinkedQueue

data class QueuedPacket(
    val packet: MeshPacket,
    val queuedTimestamp: Long = System.currentTimeMillis(),
    val expiryMs: Long = 86_400_000, // 24 hours
    var retryCount: Int = 0
)

/**
 * Store-and-Forward Delay Tolerant Networking (DTN) queue.
 * Retains packets destined for unreachable nodes and flushes them when
 * new route paths or neighbors become available.
 */
class StoreAndForwardQueue {

    private val queue = ConcurrentLinkedQueue<QueuedPacket>()

    fun enqueue(packet: MeshPacket, expiryMs: Long = 86_400_000) {
        queue.add(QueuedPacket(packet = packet, expiryMs = expiryMs))
    }

    fun getPendingForNode(targetNodeId: String): List<MeshPacket> {
        val now = System.currentTimeMillis()
        val matching = mutableListOf<MeshPacket>()
        val iterator = queue.iterator()

        while (iterator.hasNext()) {
            val item = iterator.next()
            if (now - item.queuedTimestamp > item.expiryMs) {
                iterator.remove() // expired
            } else if (item.packet.targetNodeId == targetNodeId || item.packet.isBroadcast) {
                matching.add(item.packet)
                iterator.remove()
            }
        }
        return matching
    }

    fun getAllPending(): List<MeshPacket> {
        val now = System.currentTimeMillis()
        queue.removeIf { now - it.queuedTimestamp > it.expiryMs }
        return queue.map { it.packet }
    }

    fun size(): Int = queue.size

    fun clear() {
        queue.clear()
    }
}
