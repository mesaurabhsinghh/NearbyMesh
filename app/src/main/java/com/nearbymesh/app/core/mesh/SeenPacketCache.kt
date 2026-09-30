package com.nearbymesh.app.core.mesh

import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance deduplication cache.
 * Tracks recently observed packet IDs and discards duplicates to prevent
 * broadcast storms, routing loops, and echo amplification in mesh topology.
 */
class SeenPacketCache(
    private val maxCapacity: Int = 10_000,
    private val retentionMs: Long = 300_000 // 5 minutes retention
) {
    private val seenMap = ConcurrentHashMap<String, Long>()

    /**
     * Checks if packet was already seen.
     * If not seen, records it and returns false.
     * If already seen, returns true (indicating duplicate).
     */
    fun isDuplicateAndMark(packetId: String): Boolean {
        cleanupExpired()
        val now = System.currentTimeMillis()
        val previous = seenMap.putIfAbsent(packetId, now)
        return previous != null
    }

    fun hasSeen(packetId: String): Boolean {
        return seenMap.containsKey(packetId)
    }

    private fun cleanupExpired() {
        if (seenMap.size > maxCapacity) {
            val now = System.currentTimeMillis()
            val iterator = seenMap.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (now - entry.value > retentionMs) {
                    iterator.remove()
                }
            }
        }
    }

    fun clear() {
        seenMap.clear()
    }
}
