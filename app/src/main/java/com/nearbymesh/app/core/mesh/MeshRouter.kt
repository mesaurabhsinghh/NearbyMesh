package com.nearbymesh.app.core.mesh

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Core Multi-Hop Mesh Router.
 * Handles packet validation, loop prevention, deduplication, TTL decrement,
 * hop-path propagation, and forwarding across physical (Wi-Fi P2P / BLE) and simulated links.
 */
class MeshRouter(
    val myNodeId: String,
    val routingTable: RoutingTable = RoutingTable(),
    val seenCache: SeenPacketCache = SeenPacketCache(),
    val storeQueue: StoreAndForwardQueue = StoreAndForwardQueue()
) {
    companion object {
        private const val TAG = "MeshRouter"
    }

    private val _deliveredPackets = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 64)
    val deliveredPackets: SharedFlow<MeshPacket> = _deliveredPackets.asSharedFlow()

    private val _relayedPackets = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 64)
    val relayedPackets: SharedFlow<MeshPacket> = _relayedPackets.asSharedFlow()

    private val _droppedPackets = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val droppedPackets: SharedFlow<String> = _droppedPackets.asSharedFlow()

    // Outbound link dispatcher registered by TransportManager
    var outboundDispatcher: ((packet: MeshPacket, excludeSenderId: String?) -> Unit)? = null

    // Whether this node participates in forwarding packets for other peers
    var isRelayEnabled: Boolean = true

    /**
     * Ingests an incoming packet from any transport link (Wi-Fi, BLE, or Simulator).
     */
    fun processIncomingPacket(packet: MeshPacket, receivedInterface: String = "WIRELESS") {
        Log.d(TAG, "Incoming packet ${packet.packetId} from ${packet.sourceNodeId} (via ${packet.senderNodeId}) to ${packet.targetNodeId}, type=${packet.payloadType}, TTL=${packet.ttl}, Hops=${packet.hopCount}")

        // 1. Deduplication check: drop if already seen to prevent loops/storms
        if (seenCache.isDuplicateAndMark(packet.packetId)) {
            Log.d(TAG, "Duplicate packet ${packet.packetId} dropped (already processed)")
            _droppedPackets.tryEmit(packet.packetId)
            return
        }

        // 2. Deliver to local node if we are the destination or if it's broadcast
        val isForMe = packet.targetNodeId == myNodeId
        val isBroadcast = packet.isBroadcast

        if (isForMe || isBroadcast) {
            _deliveredPackets.tryEmit(packet)
            Log.d(TAG, "Packet ${packet.packetId} delivered to local node $myNodeId (hops=${packet.hopCount})")
        }

        // 3. Multi-hop relay forwarding
        // If it's broadcast or destined for another node, forward if TTL > 1 and relaying is enabled
        if (isRelayEnabled && !isForMe && packet.ttl > 1) {
            val forwardPacket = packet.createForwardPacket(myNodeId)
            Log.d(TAG, "Forwarding packet ${packet.packetId} towards ${packet.targetNodeId} via hop #${forwardPacket.hopCount} (new TTL=${forwardPacket.ttl})")
            _relayedPackets.tryEmit(forwardPacket)
            outboundDispatcher?.invoke(forwardPacket, packet.senderNodeId)
        } else if (isRelayEnabled && isBroadcast && packet.ttl > 1) {
            val forwardPacket = packet.createForwardPacket(myNodeId)
            Log.d(TAG, "Flooding broadcast packet ${packet.packetId} via hop #${forwardPacket.hopCount}")
            _relayedPackets.tryEmit(forwardPacket)
            outboundDispatcher?.invoke(forwardPacket, packet.senderNodeId)
        } else if (packet.ttl <= 1 && !isForMe) {
            Log.w(TAG, "Packet ${packet.packetId} dropped: TTL expired (${packet.ttl})")
            _droppedPackets.tryEmit(packet.packetId)
        }
    }

    /**
     * Originates a new packet from this local node.
     */
    fun sendPacket(packet: MeshPacket) {
        seenCache.isDuplicateAndMark(packet.packetId)
        outboundDispatcher?.invoke(packet, null)
    }
}
