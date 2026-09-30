package com.nearbymesh.app.core.mesh

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Core Multi-Hop Mesh Router with Smart RSSI-Weighted Directed Forwarding.
 * Prioritizes long-range edge nodes for instantaneous forwarding (<15ms)
 * while delaying close nodes (200ms) to prevent radio storms and maximize hop distance.
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

    private val routerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

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
     * Ingests an incoming packet from any transport link (BLE Instant Blaster, BLE GATT, Wi-Fi, or RFCOMM).
     * Includes sender RSSI for Smart Directed Forwarding.
     */
    fun processIncomingPacket(
        packet: MeshPacket,
        receivedInterface: String = "WIRELESS",
        senderRssi: Int = -60
    ) {
        Log.d(TAG, "Incoming packet ${packet.packetId} from ${packet.sourceNodeId} (via ${packet.senderNodeId}) to ${packet.targetNodeId}, type=${packet.payloadType}, TTL=${packet.ttl}, Hops=${packet.hopCount}, RSSI=$senderRssi on $receivedInterface")

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

        // 3. Smart RSSI-Weighted Multi-Hop Forwarding
        // Far nodes (RSSI < -75 dBm) jump immediately (15ms delay) to maximize coverage.
        // Close nodes (RSSI > -65 dBm) wait 200ms to avoid air collisions.
        if (isRelayEnabled && (!isForMe || isBroadcast) && packet.ttl > 1) {
            val forwardDelayMs: Long = when {
                senderRssi < -78 -> 15L   // Far edge: forward immediately!
                senderRssi < -65 -> 80L   // Mid-range: short backoff
                else -> 200L              // Close node: backoff, yield to farther nodes
            }

            routerScope.launch {
                if (forwardDelayMs > 0) {
                    delay(forwardDelayMs)
                }

                val forwardPacket = packet.createForwardPacket(myNodeId)
                Log.d(TAG, "Smart RSSI Forwarding packet ${packet.packetId} towards ${packet.targetNodeId} via hop #${forwardPacket.hopCount} (delay=${forwardDelayMs}ms, RSSI=$senderRssi, new TTL=${forwardPacket.ttl})")
                _relayedPackets.tryEmit(forwardPacket)
                outboundDispatcher?.invoke(forwardPacket, packet.senderNodeId)
            }
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
