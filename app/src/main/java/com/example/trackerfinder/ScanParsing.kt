package com.example.trackerfinder

import android.bluetooth.le.ScanResult

/** Turns a raw scan result into a [SeenDevice]. Shared by the app screen and the background monitor. */
fun parseScan(r: ScanResult, old: SeenDevice?, now: Long): SeenDevice {
    val record = r.scanRecord
    val name = record?.deviceName ?: old?.name
    val serviceData = buildMap {
        record?.serviceData?.forEach { (uuid, bytes) ->
            // 16-bit UUIDs live in bits 32..47 of the 128-bit Bluetooth base UUID.
            put(((uuid.uuid.mostSignificantBits ushr 32) and 0xFFFF).toInt(), bytes)
        }
    }
    val mfr = buildMap {
        val sparse = record?.manufacturerSpecificData
        if (sparse != null) for (i in 0 until sparse.size()) put(sparse.keyAt(i), sparse.valueAt(i))
    }
    val kind = AdvertParser.classify(name, serviceData, mfr)
    return SeenDevice(
        address = r.device.address,
        name = name,
        kind = kind,
        fingerprint = AdvertParser.fingerprint(kind, name, serviceData, mfr),
        rssi = r.rssi,
        firstSeen = old?.firstSeen ?: now,
        lastSeen = now,
    )
}

/** 16-bit service data of an advert (e.g. 0xFEAA → Eddystone/Find Hub frame). */
fun serviceData16(r: ScanResult): Map<Int, ByteArray> = buildMap {
    r.scanRecord?.serviceData?.forEach { (uuid, bytes) -> put(((uuid.uuid.mostSignificantBits ushr 32) and 0xFFFF).toInt(), bytes) }
}

/**
 * Coarse "what kind of signal is this" labels used to learn a tag's button press (Find my phone).
 * They ignore rotating IDs so the same kind of signal gives the same label every time.
 */
fun advertTokens(r: ScanResult): Set<String> {
    val out = HashSet<String>()
    serviceData16(r).forEach { (uuid, bytes) ->
        out += if (uuid == AdvertParser.EDDYSTONE_UUID16 && bytes.isNotEmpty()) "svc:feaa:%02x".format(bytes[0])
        else "svc:%04x".format(uuid)
    }
    r.scanRecord?.serviceUuids?.forEach { u -> out += "uuid:" + u.uuid.toString() }
    r.scanRecord?.manufacturerSpecificData?.let { m ->
        for (i in 0 until m.size()) out += "mfr:%04x".format(m.keyAt(i)) + (m.valueAt(i)?.firstOrNull()?.let { ":%02x".format(it) } ?: "")
    }
    r.scanRecord?.deviceName?.takeIf { it.isNotBlank() }?.let { out += "name:$it" }
    return out
}
