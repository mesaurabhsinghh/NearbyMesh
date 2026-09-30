package com.nearbymesh.app.core.mesh

import java.util.concurrent.ConcurrentHashMap

data class RouteEntry(
    val destinationNodeId: String,
    val nextHopNodeId: String,
    val hops: Int,
    val nickname: String,
    val batteryLevel: Int = -1,
    val publicKeyBase64: String? = null,
    val linkType: LinkType = LinkType.DIRECT_WIFI,
    val rssi: Int = -60,
    var lastSeen: Long = System.currentTimeMillis()
)

enum class LinkType {
    DIRECT_WIFI,
    DIRECT_BLE,
    RELAY_MESH,
    VIRTUAL_SIMULATOR
}

class RoutingTable {

    // Destination Node ID -> RouteEntry
    private val routes = ConcurrentHashMap<String, RouteEntry>()

    fun updateRoute(entry: RouteEntry) {
        val existing = routes[entry.destinationNodeId]
        if (existing == null) {
            routes[entry.destinationNodeId] = entry
        } else {
            // If new route has fewer hops or is more recent direct route, prefer it
            if (entry.hops <= existing.hops || (System.currentTimeMillis() - existing.lastSeen > 30_000)) {
                routes[entry.destinationNodeId] = entry
            } else {
                existing.lastSeen = System.currentTimeMillis()
            }
        }
    }

    fun getRoute(destinationNodeId: String): RouteEntry? {
        return routes[destinationNodeId]
    }

    fun getAllRoutes(): List<RouteEntry> {
        return routes.values.toList()
    }

    fun removeStaleRoutes(timeoutMs: Long = 120_000) {
        val now = System.currentTimeMillis()
        routes.entries.removeIf { now - it.value.lastSeen > timeoutMs }
    }

    fun clear() {
        routes.clear()
    }
}
