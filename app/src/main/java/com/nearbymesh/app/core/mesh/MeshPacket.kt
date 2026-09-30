package com.nearbymesh.app.core.mesh

import com.google.gson.Gson
import java.util.UUID

/**
 * Standard Mesh Routing Envelope.
 * Intermediate relay nodes inspect only the envelope headers (source, target, TTL, hopCount)
 * and route the packet without having access to decrypted payload data.
 */
data class MeshPacket(
    val packetId: String = UUID.randomUUID().toString(),
    val sourceNodeId: String,
    val senderNodeId: String,
    val targetNodeId: String,
    var ttl: Int = DEFAULT_TTL,
    var hopCount: Int = 0,
    val hopPath: MutableList<String> = mutableListOf(),
    val payloadType: PayloadType,
    val payload: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isEncrypted: Boolean = false,
    val signature: String? = null
) {
    companion object {
        const val BROADCAST_ID = "mesh-broadcast"
        const val LEGACY_BROADCAST_ID = "BROADCAST"
        const val DEFAULT_TTL = 7
        const val MAX_TTL = 15

        private val gson = Gson()

        fun fromJson(json: String): MeshPacket? {
            return try {
                gson.fromJson(json, MeshPacket::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }

    fun toJson(): String {
        return gson.toJson(this)
    }

    val isBroadcast: Boolean
        get() = targetNodeId == BROADCAST_ID || targetNodeId == LEGACY_BROADCAST_ID || targetNodeId.equals("mesh-broadcast", ignoreCase = true)

    /**
     * Prepares packet for forwarding across the next hop:
     * Decrements TTL, increments hop count, and records local node ID in hopPath.
     */
    fun createForwardPacket(currentNodeId: String): MeshPacket {
        val nextHops = ArrayList(hopPath)
        if (!nextHops.contains(currentNodeId)) {
            nextHops.add(currentNodeId)
        }
        return this.copy(
            senderNodeId = currentNodeId,
            ttl = this.ttl - 1,
            hopCount = this.hopCount + 1,
            hopPath = nextHops
        )
    }
}
