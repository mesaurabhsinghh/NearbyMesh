package com.nearbymesh.app.core.transport.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.google.gson.Gson
import com.nearbymesh.app.core.mesh.MeshPacket
import com.nearbymesh.app.core.mesh.PayloadType
import com.nearbymesh.app.core.mesh.TextMessagePayload
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.util.*
import kotlin.math.pow
import kotlin.math.roundToInt

data class BleDiscoveredDevice(
    val deviceAddress: String,
    val deviceName: String,
    val nodeId: String,
    val batteryPercent: Int,
    val rssi: Int,
    val estimatedDistanceMeters: Double,
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val bluetoothDevice: BluetoothDevice? = null
)

class BleManager(private val context: Context) {

    companion object {
        private const val TAG = "BleManager"
        val MESH_SERVICE_UUID: UUID = UUID.fromString("0000FE60-0000-1000-8000-00805F9B34FB")
        val PARCEL_SERVICE_UUID = ParcelUuid(MESH_SERVICE_UUID)
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val gson = Gson()
    var onInstantPacketReceived: ((packet: MeshPacket, rssi: Int) -> Unit)? = null

    private val blastQueue = kotlinx.coroutines.channels.Channel<MeshPacket>(capacity = 64)
    private val reassemblyCache = Collections.synchronizedMap(object : LinkedHashMap<String, Array<ByteArray?>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Array<ByteArray?>>?): Boolean {
            return size > 50
        }
    })

    init {
        scope.launch {
            for (packet in blastQueue) {
                try {
                    executeBlast(packet)
                } catch (e: Throwable) {
                    Log.e(TAG, "Error executing instant blast: ${e.message}")
                }
            }
        }
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null

    private val _discoveredDevices = MutableStateFlow<Map<String, BleDiscoveredDevice>>(emptyMap())
    val discoveredDevices: StateFlow<Map<String, BleDiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private var isScanning = false
    private var isAdvertising = false

    private var lastNodeId: String = ""
    private var lastNickname: String = ""
    private var lastBattery: Int = 85

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d(TAG, "BLE Advertising started successfully")
            isAdvertising = true
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "BLE Advertising failed with error code: $errorCode, attempting fallback advertising...")
            isAdvertising = false
            tryFallbackAdvertising()
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let { handleScanResult(it) }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { handleScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE Scan failed: $errorCode")
            isScanning = false
        }
    }

    @SuppressLint("MissingPermission")
    fun startAdvertising(nodeId: String, nickname: String, batteryLevel: Int) {
        lastNodeId = nodeId
        lastNickname = nickname
        lastBattery = batteryLevel

        try {
            if (bluetoothAdapter?.isEnabled != true) {
                Log.w(TAG, "Bluetooth not enabled, skipping advertising")
                return
            }
            advertiser = bluetoothAdapter.bluetoothLeAdvertiser
            if (advertiser == null) {
                Log.w(TAG, "BluetoothLeAdvertiser unavailable on this device")
                return
            }

            // High Tx Power + Balanced Mode: Delivers maximum range across all Android hardware
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .setTimeout(0)
                .build()

            // Encode [NodeId (8 bytes)] + [Battery (1 byte)]
            val dataBytes = ByteArray(9)
            val nodeIdClean = nodeId.replace("-", "").take(8).padEnd(8, '0')
            System.arraycopy(nodeIdClean.toByteArray(Charsets.US_ASCII), 0, dataBytes, 0, 8)
            dataBytes[8] = batteryLevel.coerceIn(0, 100).toByte()

            // 1. Primary Advertisement: Broadcasts Service UUID so all scanners immediately match
            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(PARCEL_SERVICE_UUID)
                .build()

            // 2. Scan Response: Broadcasts Service Data + Manufacturer Data
            val scanResponse = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addServiceData(PARCEL_SERVICE_UUID, dataBytes)
                .addManufacturerData(0xFE60, dataBytes)
                .build()

            advertiser?.startAdvertising(settings, data, scanResponse, advertiseCallback)
        } catch (e: Throwable) {
            Log.e(TAG, "Error starting BLE advertisement: ${e.message}")
            tryFallbackAdvertising()
        }
    }

    @SuppressLint("MissingPermission")
    private fun tryFallbackAdvertising() {
        try {
            if (bluetoothAdapter?.isEnabled != true) return
            advertiser = bluetoothAdapter.bluetoothLeAdvertiser ?: return

            // Stop previous to release controller
            try { advertiser?.stopAdvertising(advertiseCallback) } catch (_: Throwable) {}

            val fallbackSettings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
                .setConnectable(true)
                .setTimeout(0)
                .build()

            val fallbackData = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(PARCEL_SERVICE_UUID)
                .build()

            advertiser?.startAdvertising(fallbackSettings, fallbackData, null, object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    Log.d(TAG, "Fallback BLE advertising active")
                    isAdvertising = true
                }
                override fun onStartFailure(errorCode: Int) {
                    Log.e(TAG, "Fallback BLE advertising also failed: $errorCode")
                }
            })
        } catch (t: Throwable) {
            Log.e(TAG, "Error in tryFallbackAdvertising: ${t.message}")
        }
    }

    val isCodedPhySupported: Boolean
        get() = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && bluetoothAdapter?.isLeCodedPhySupported == true

    @SuppressLint("MissingPermission")
    fun startScanning() {
        try {
            if (bluetoothAdapter?.isEnabled != true) {
                Log.w(TAG, "Cannot start scan: Bluetooth not enabled")
                return
            }
            if (isScanning) {
                Log.d(TAG, "BLE Scanner already running")
                return
            }
            scanner = bluetoothAdapter.bluetoothLeScanner
            if (scanner == null) {
                Log.w(TAG, "BluetoothLeScanner unavailable")
                return
            }

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0)
                .build()

            scanner?.startScan(emptyList(), settings, scanCallback)
            isScanning = true
            Log.d(TAG, "BLE Scan active in low-latency mode")
        } catch (e: Throwable) {
            Log.e(TAG, "Error starting BLE scan: ${e.message}")
            isScanning = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleScanResult(result: ScanResult) {
        val record = result.scanRecord ?: return

        // 0. Connectionless Instant BLE Blaster Packet Check (Magic Byte 0x9B)
        val mfgData = record.getManufacturerSpecificData(0xFE60)
        if (mfgData != null && mfgData.isNotEmpty() && mfgData[0] == 0x9B.toByte()) {
            handleInstantBlastData(mfgData, result.rssi)
            return
        }

        // Also check raw bytes for Instant Blaster (0x60, 0xFE, 0x9B)
        val rawBytes = record.bytes
        if (rawBytes != null && rawBytes.isNotEmpty()) {
            for (idx in 0 until rawBytes.size - 2) {
                val b0 = rawBytes[idx].toInt() and 0xFF
                val b1 = rawBytes[idx + 1].toInt() and 0xFF
                val b2 = rawBytes[idx + 2].toInt() and 0xFF
                if (b0 == 0x60 && b1 == 0xFE && b2 == 0x9B) {
                    val slice = rawBytes.sliceArray((idx + 2) until rawBytes.size)
                    handleInstantBlastData(slice, result.rssi)
                    return
                }
            }
        }

        val serviceUuids = record.serviceUuids

        // 1. Service UUID Check (Full UUID or 16-bit FE60)
        val hasMeshUuid = serviceUuids?.any {
            it.uuid == MESH_SERVICE_UUID ||
            it.uuid.toString().uppercase().contains("FE60")
        } == true

        // 2. Service Data Check
        val serviceData = record.getServiceData(PARCEL_SERVICE_UUID)

        // 3. Manufacturer Data Check (0xFE60)

        // 4. Raw Bytes Signature Scan (0x60, 0xFE anywhere in packet)
        var foundInRawBytes = false
        var rawDiscoveredNodeId: String? = null
        var rawBattery = -1
        if (rawBytes != null && rawBytes.isNotEmpty()) {
            for (idx in 0 until rawBytes.size - 1) {
                val b0 = rawBytes[idx].toInt() and 0xFF
                val b1 = rawBytes[idx + 1].toInt() and 0xFF
                if (b0 == 0x60 && b1 == 0xFE) {
                    foundInRawBytes = true
                    // Check if 8 bytes ASCII follow
                    if (idx + 2 + 8 <= rawBytes.size) {
                        try {
                            val candidate = String(rawBytes.sliceArray((idx + 2) until (idx + 10)), Charsets.US_ASCII)
                            if (candidate.all { it.isLetterOrDigit() }) {
                                rawDiscoveredNodeId = candidate
                                if (idx + 10 < rawBytes.size) {
                                    rawBattery = rawBytes[idx + 10].toInt() and 0xFF
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    break
                }
            }
        }

        // 5. Device Name Signature Check
        val devName = try { record.deviceName ?: result.device.name } catch (_: Throwable) { null } ?: ""
        val nameMatches = devName.startsWith("NM-") || devName.contains("NearbyMesh", ignoreCase = true)

        // Peer Identification: If any signature matches, this is our NearbyMesh peer!
        val isOurPeer = hasMeshUuid || serviceData != null || mfgData != null || foundInRawBytes || nameMatches
        if (!isOurPeer) {
            return
        }

        var discoveredNodeId: String? = null
        var battery = -1

        if (serviceData != null && serviceData.size >= 8) {
            discoveredNodeId = String(serviceData.sliceArray(0 until 8), Charsets.US_ASCII)
            if (serviceData.size >= 9) battery = serviceData[8].toInt() and 0xFF
        } else if (mfgData != null && mfgData.size >= 8) {
            discoveredNodeId = String(mfgData.sliceArray(0 until 8), Charsets.US_ASCII)
            if (mfgData.size >= 9) battery = mfgData[8].toInt() and 0xFF
        } else if (rawDiscoveredNodeId != null) {
            discoveredNodeId = rawDiscoveredNodeId
            battery = rawBattery
        }

        // Generate stable fallback from hardware MAC address if not parsed yet
        if (discoveredNodeId.isNullOrBlank() || discoveredNodeId == "UNKNOWN") {
            val cleanMac = result.device.address.replace(":", "").takeLast(8).padEnd(8, '0')
            discoveredNodeId = "${cleanMac.take(4)}-${cleanMac.drop(4)}"
        } else if (discoveredNodeId.length == 8 && !discoveredNodeId.contains("-")) {
            discoveredNodeId = "${discoveredNodeId.take(4)}-${discoveredNodeId.drop(4)}"
        }

        val deviceName = if (devName.isNotBlank() && !devName.startsWith("NM-")) devName else "Peer $discoveredNodeId"
        val rssi = result.rssi
        val distance = calculateDistance(rssi, record.txPowerLevel)

        val device = BleDiscoveredDevice(
            deviceAddress = result.device.address,
            deviceName = deviceName,
            nodeId = discoveredNodeId,
            batteryPercent = battery,
            rssi = rssi,
            estimatedDistanceMeters = distance,
            bluetoothDevice = result.device
        )

        val current = _discoveredDevices.value.toMutableMap()
        current[result.device.address] = device
        _discoveredDevices.value = current
        Log.d(TAG, "Discovered NearbyMesh Peer: $discoveredNodeId ($deviceName) at ${distance}m, RSSI=$rssi")
    }

    /**
     * Estimates distance from RSSI using log-distance path loss model:
     * Distance = 10 ^ ((Measured Power - RSSI) / (10 * N))
     */
    private fun calculateDistance(rssi: Int, txPower: Int): Double {
        val measuredPower = if (txPower != Int.MIN_VALUE && txPower != 0) txPower else -59
        val pathLossExponent = 2.5 // Indoor / near ground attenuation
        val ratio = (measuredPower - rssi) / (10.0 * pathLossExponent)
        val distance = 10.0.pow(ratio)
        return (distance * 10).roundToInt() / 10.0 // 1 decimal place, e.g. 3.2m
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        if (isAdvertising) {
            try {
                advertiser?.stopAdvertising(advertiseCallback)
            } catch (_: Exception) {}
            isAdvertising = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (isScanning) {
            try {
                scanner?.stopScan(scanCallback)
            } catch (_: Exception) {}
            isScanning = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        stopAdvertising()
        stopScanning()
    }

    // =========================================================================
    // CONNECTIONLESS INSTANT BLE BLASTER (<50ms High-Power Multi-Hop Transmission)
    // =========================================================================

    fun blastInstantPacket(packet: MeshPacket) {
        blastQueue.trySend(packet)
    }

    @SuppressLint("MissingPermission")
    private suspend fun executeBlast(packet: MeshPacket) {
        if (bluetoothAdapter?.isEnabled != true) return
        val adv = advertiser ?: bluetoothAdapter.bluetoothLeAdvertiser ?: return

        // 1. Extract payload bytes based on packet type
        val payloadBytes = when (packet.payloadType) {
            PayloadType.TEXT_MESSAGE -> {
                try {
                    val msgObj = gson.fromJson(packet.payload, TextMessagePayload::class.java)
                    (msgObj.text ?: packet.payload).toByteArray(Charsets.UTF_8)
                } catch (_: Exception) {
                    packet.payload.toByteArray(Charsets.UTF_8)
                }
            }
            PayloadType.PING -> "PING".toByteArray(Charsets.UTF_8)
            PayloadType.PONG -> "PONG".toByteArray(Charsets.UTF_8)
            else -> packet.payload.toByteArray(Charsets.UTF_8)
        }

        val maxChunk = 10
        val totalFrames = if (payloadBytes.isEmpty()) 1 else ((payloadBytes.size + maxChunk - 1) / maxChunk).coerceIn(1, 15)

        for (frameIdx in 0 until totalFrames) {
            val start = frameIdx * maxChunk
            val end = (start + maxChunk).coerceAtMost(payloadBytes.size)
            val chunk = if (start < payloadBytes.size) payloadBytes.sliceArray(start until end) else ByteArray(0)

            val frame = ByteArray(16 + chunk.size)
            frame[0] = 0x9B.toByte() // Magic signature: NearbyMesh Instant Blaster
            frame[1] = when (packet.payloadType) {
                PayloadType.TEXT_MESSAGE -> 0x01.toByte()
                PayloadType.MESSAGE_ACK -> 0x02.toByte()
                PayloadType.EMERGENCY_SOS -> 0x03.toByte()
                PayloadType.PING -> 0x04.toByte()
                PayloadType.PONG -> 0x05.toByte()
                PayloadType.PEER_ANNOUNCE -> 0x06.toByte()
                else -> 0x01.toByte()
            }
            frame[2] = packet.ttl.coerceIn(0, 15).toByte()
            frame[3] = packet.hopCount.coerceIn(0, 15).toByte()

            val srcBytes = nodeIdToBytes(packet.sourceNodeId)
            System.arraycopy(srcBytes, 0, frame, 4, 4)

            val tgtBytes = nodeIdToBytes(packet.targetNodeId)
            System.arraycopy(tgtBytes, 0, frame, 8, 4)

            val seq = (packet.packetId.hashCode() and 0xFFFF)
            frame[12] = ((seq shr 8) and 0xFF).toByte()
            frame[13] = (seq and 0xFF).toByte()

            frame[14] = (((frameIdx and 0x0F) shl 4) or (totalFrames and 0x0F)).toByte()
            frame[15] = chunk.size.toByte()
            if (chunk.isNotEmpty()) {
                System.arraycopy(chunk, 0, frame, 16, chunk.size)
            }

            val blastData = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addManufacturerData(0xFE60, frame)
                .build()

            val blastSettings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .setTimeout(0)
                .build()

            val blastCallback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    Log.d(TAG, "Instant Blast frame $frameIdx/$totalFrames sent (High Power, Low Latency)")
                }
                override fun onStartFailure(errorCode: Int) {
                    Log.w(TAG, "Instant Blast frame $frameIdx failed: $errorCode")
                }
            }

            try {
                adv.startAdvertising(blastSettings, blastData, null, blastCallback)
                delay(200L) // Broadcast ~10 advertisement frames
                adv.stopAdvertising(blastCallback)
            } catch (t: Throwable) {
                Log.e(TAG, "Blast frame error: ${t.message}")
            }
            if (frameIdx < totalFrames - 1) {
                delay(25L)
            }
        }
    }

    private fun handleInstantBlastData(data: ByteArray, rssi: Int) {
        if (data.size < 16) return
        if (data[0] != 0x9B.toByte()) return

        val typeByte = data[1]
        val payloadType = when (typeByte) {
            0x01.toByte() -> PayloadType.TEXT_MESSAGE
            0x02.toByte() -> PayloadType.MESSAGE_ACK
            0x03.toByte() -> PayloadType.EMERGENCY_SOS
            0x04.toByte() -> PayloadType.PING
            0x05.toByte() -> PayloadType.PONG
            0x06.toByte() -> PayloadType.PEER_ANNOUNCE
            else -> PayloadType.TEXT_MESSAGE
        }

        val ttl = data[2].toInt() and 0xFF
        val hopCount = data[3].toInt() and 0xFF
        val srcNodeId = bytesToNodeId(data, 4)
        val tgtNodeId = bytesToNodeId(data, 8)

        val seqHigh = data[12].toInt() and 0xFF
        val seqLow = data[13].toInt() and 0xFF
        val packetSeq = (seqHigh shl 8) or seqLow

        val frameHdr = data[14].toInt() and 0xFF
        val frameIdx = (frameHdr shr 4) and 0x0F
        val totalFrames = (frameHdr and 0x0F).coerceAtLeast(1)

        val chunkLen = (data[15].toInt() and 0xFF).coerceAtMost(data.size - 16)
        val chunk = if (chunkLen > 0) data.sliceArray(16 until 16 + chunkLen) else ByteArray(0)

        val packetKey = "$srcNodeId-$packetSeq"

        if (totalFrames <= 1) {
            val payloadStr = String(chunk, Charsets.UTF_8)
            val packet = MeshPacket(
                packetId = "INSTANT-$packetKey",
                sourceNodeId = srcNodeId,
                senderNodeId = srcNodeId,
                targetNodeId = tgtNodeId,
                ttl = ttl,
                hopCount = hopCount,
                hopPath = mutableListOf(srcNodeId),
                payloadType = payloadType,
                payload = payloadStr,
                isEncrypted = false
            )
            Log.d(TAG, "Instant Packet received via BLE Blaster: $srcNodeId -> $tgtNodeId, type=$payloadType, hops=$hopCount, RSSI=$rssi")
            onInstantPacketReceived?.invoke(packet, rssi)
        } else {
            val frameArray = reassemblyCache.getOrPut(packetKey) { arrayOfNulls(totalFrames) }
            if (frameIdx < frameArray.size) {
                frameArray[frameIdx] = chunk
            }
            if (frameArray.all { it != null }) {
                reassemblyCache.remove(packetKey)
                val totalSize = frameArray.filterNotNull().sumOf { it.size }
                val fullBytes = ByteArray(totalSize)
                var offset = 0
                for (f in frameArray.filterNotNull()) {
                    System.arraycopy(f, 0, fullBytes, offset, f.size)
                    offset += f.size
                }
                val fullPayload = String(fullBytes, Charsets.UTF_8)
                val packet = MeshPacket(
                    packetId = "INSTANT-$packetKey",
                    sourceNodeId = srcNodeId,
                    senderNodeId = srcNodeId,
                    targetNodeId = tgtNodeId,
                    ttl = ttl,
                    hopCount = hopCount,
                    hopPath = mutableListOf(srcNodeId),
                    payloadType = payloadType,
                    payload = fullPayload,
                    isEncrypted = false
                )
                Log.d(TAG, "Reassembled multi-frame Instant Packet: $srcNodeId -> $tgtNodeId ($totalFrames frames), RSSI=$rssi")
                onInstantPacketReceived?.invoke(packet, rssi)
            }
        }
    }

    private fun nodeIdToBytes(nodeId: String): ByteArray {
        val clean = nodeId.replace("-", "").trim().uppercase()
        val bytes = ByteArray(4)
        if (clean == "BROADCAST" || clean == "MESH-BROADCAST" || clean == "ALL" || nodeId == MeshPacket.BROADCAST_ID) {
            bytes.fill(0xFF.toByte())
            return bytes
        }
        try {
            if (clean.length >= 8) {
                for (i in 0 until 4) {
                    val hexByte = clean.substring(i * 2, i * 2 + 2)
                    bytes[i] = hexByte.toInt(16).toByte()
                }
                return bytes
            }
        } catch (_: Exception) {}
        val ascii = clean.padEnd(4, '0').take(4).toByteArray(Charsets.US_ASCII)
        System.arraycopy(ascii, 0, bytes, 0, 4)
        return bytes
    }

    private fun bytesToNodeId(bytes: ByteArray, offset: Int): String {
        val b0 = bytes[offset].toInt() and 0xFF
        val b1 = bytes[offset + 1].toInt() and 0xFF
        val b2 = bytes[offset + 2].toInt() and 0xFF
        val b3 = bytes[offset + 3].toInt() and 0xFF
        if (b0 == 0xFF && b1 == 0xFF && b2 == 0xFF && b3 == 0xFF) {
            return MeshPacket.BROADCAST_ID
        }
        return "%02X%02X-%02X%02X".format(b0, b1, b2, b3)
    }
}
