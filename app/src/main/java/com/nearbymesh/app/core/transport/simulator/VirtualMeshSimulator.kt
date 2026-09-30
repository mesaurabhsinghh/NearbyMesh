package com.nearbymesh.app.core.transport.simulator

import android.util.Log
import com.nearbymesh.app.core.mesh.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SimulatedHopLog(
    val timestamp: Long = System.currentTimeMillis(),
    val stepDescription: String,
    val fromNode: String,
    val toNode: String,
    val packetId: String,
    val hopNumber: Int,
    val remainingTtl: Int,
    val isDuplicate: Boolean = false,
    val isEncrypted: Boolean = true
)

/**
 * Virtual Multi-Node Mesh Simulator.
 * Simulates a multi-hop ad-hoc wireless topology (A -> B -> C -> D) in-memory,
 * allowing full validation of TTL decrementing, routing paths, duplicate rejection,
 * and end-to-end encryption without requiring multiple physical devices.
 */
class VirtualMeshSimulator(
    private val myNodeId: String,
    private val onPacketReceivedByLocalNode: (MeshPacket) -> Unit
) {
    companion object {
        private const val TAG = "VirtualMeshSimulator"
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Simulated virtual nodes in the mesh
    val simulatedNodeB = "B921-NODE" // Relay 1 (e.g. Rahul's Phone)
    val simulatedNodeC = "C442-NODE" // Relay 2 (e.g. Aman's Phone)
    val simulatedNodeD = "D810-NODE" // Destination (e.g. Pooja's Phone)

    private val _logs = MutableStateFlow<List<SimulatedHopLog>>(emptyList())
    val logs: StateFlow<List<SimulatedHopLog>> = _logs.asStateFlow()

    private val _isSimulationActive = MutableStateFlow(false)
    val isSimulationActive: StateFlow<Boolean> = _isSimulationActive.asStateFlow()

    private val simulatedNodeDSeenCache = SeenPacketCache()

    fun toggleSimulation(enable: Boolean) {
        _isSimulationActive.value = enable
        if (enable) {
            addLog("Simulation started: Topology [My Phone $myNodeId] <--> [$simulatedNodeB] <--> [$simulatedNodeC] <--> [$simulatedNodeD]", myNodeId, simulatedNodeB, "INIT", 0, 7)
        } else {
            addLog("Simulation stopped", myNodeId, myNodeId, "STOP", 0, 0)
        }
    }

    /**
     * Called when our local node transmits a packet while simulation is active.
     */
    fun dispatchFromLocalNode(packet: MeshPacket) {
        if (!_isSimulationActive.value) return

        scope.launch {
            // Step 1: Transmit from My Node to Node B (First Hop)
            delay(400)
            addLog(
                "Local phone ($myNodeId) transmitted packet to Relay Node B",
                myNodeId,
                simulatedNodeB,
                packet.packetId,
                1,
                packet.ttl
            )

            // Step 2: Node B inspects packet
            delay(500)
            if (packet.targetNodeId == simulatedNodeB) {
                addLog("Relay Node B reached final destination", simulatedNodeB, simulatedNodeB, packet.packetId, 1, packet.ttl)
                return@launch
            }

            // Node B forwards to Node C
            val hopBtoC = packet.createForwardPacket(simulatedNodeB)
            addLog(
                "Relay Node B forwarded packet towards ${packet.targetNodeId} to Relay Node C (TTL decremented to ${hopBtoC.ttl})",
                simulatedNodeB,
                simulatedNodeC,
                packet.packetId,
                hopBtoC.hopCount,
                hopBtoC.ttl
            )

            // Step 3: Node C forwards to Node D
            delay(500)
            if (packet.targetNodeId == simulatedNodeC) {
                addLog("Relay Node C reached final destination", simulatedNodeC, simulatedNodeC, packet.packetId, 2, hopBtoC.ttl)
                return@launch
            }

            val hopCtoD = hopBtoC.createForwardPacket(simulatedNodeC)
            addLog(
                "Relay Node C forwarded packet to Destination Node D (TTL decremented to ${hopCtoD.ttl})",
                simulatedNodeC,
                simulatedNodeD,
                packet.packetId,
                hopCtoD.hopCount,
                hopCtoD.ttl
            )

            // Step 4: Destination Node D receives packet
            delay(400)
            if (simulatedNodeDSeenCache.isDuplicateAndMark(packet.packetId)) {
                addLog(
                    "Node D detected duplicate packet ID ${packet.packetId} -> DISCARDED!",
                    simulatedNodeC,
                    simulatedNodeD,
                    packet.packetId,
                    hopCtoD.hopCount,
                    hopCtoD.ttl,
                    isDuplicate = true
                )
                return@launch
            }

            addLog(
                "Destination Node D successfully received packet! Hops traversed: ${hopCtoD.hopPath.joinToString(" -> ")}",
                simulatedNodeC,
                simulatedNodeD,
                packet.packetId,
                hopCtoD.hopCount,
                hopCtoD.ttl
            )

            // Step 5: Node D sends back an ACK or reply along the reverse route D -> C -> B -> A
            delay(600)
            val ackPacket = MeshPacket(
                sourceNodeId = simulatedNodeD,
                senderNodeId = simulatedNodeB,
                targetNodeId = myNodeId,
                ttl = 7,
                hopCount = 3,
                hopPath = mutableListOf(simulatedNodeD, simulatedNodeC, simulatedNodeB),
                payloadType = PayloadType.MESSAGE_ACK,
                payload = "{\"status\":\"DELIVERED\",\"hops\":3,\"route\":\"$simulatedNodeD -> $simulatedNodeC -> $simulatedNodeB -> $myNodeId\"}"
            )

            addLog(
                "Destination Node D sent ACK back to My Phone via reverse mesh route!",
                simulatedNodeD,
                myNodeId,
                ackPacket.packetId,
                3,
                ackPacket.ttl
            )

            onPacketReceivedByLocalNode(ackPacket)
        }
    }

    private fun addLog(
        desc: String,
        from: String,
        to: String,
        packetId: String,
        hop: Int,
        ttl: Int,
        isDuplicate: Boolean = false
    ) {
        val entry = SimulatedHopLog(
            stepDescription = desc,
            fromNode = from,
            toNode = to,
            packetId = packetId,
            hopNumber = hop,
            remainingTtl = ttl,
            isDuplicate = isDuplicate
        )
        val current = _logs.value.toMutableList()
        current.add(0, entry) // Newest first
        if (current.size > 200) current.removeAt(current.size - 1)
        _logs.value = current
        Log.d(TAG, desc)
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun stop() {
        _isSimulationActive.value = false
        scope.cancel()
    }
}
