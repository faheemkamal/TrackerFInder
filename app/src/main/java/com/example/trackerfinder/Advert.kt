package com.example.trackerfinder

/**
 * What kind of device an advert looks like, judged only from the advert bytes.
 *
 * Hoco sells the E96/E96A as a Google Find Hub ("Find My Device") tracker. Find Hub
 * trackers that are set up advertise Eddystone service data (UUID 0xFEAA) whose first
 * byte is 0x40 (normal) or 0x41 (separated-from-owner / unwanted-tracking mode).
 * Before setup they advertise Google Fast Pair (UUID 0xFE2C). Some trackers are Apple
 * Find My instead, which advertise Apple manufacturer data starting with 0x12.
 * GUESS: the exact frames your E96 sends are not confirmed on real hardware; the
 * "All devices" list is always available as a fallback.
 */
enum class TrackerKind(val label: String, val isTracker: Boolean) {
    GOOGLE_FIND_HUB("Google Find Hub tracker", true),
    GOOGLE_FIND_HUB_SEPARATED("Google Find Hub tracker (away from owner)", true),
    FAST_PAIR("Fast Pair device (setup mode)", true),
    APPLE_FIND_MY("Apple Find My tracker", true),
    NAMED_HOCO("Hoco device", true),
    OTHER("Bluetooth device", false),
}

object AdvertParser {
    const val EDDYSTONE_UUID16 = 0xFEAA
    const val FAST_PAIR_UUID16 = 0xFE2C
    const val APPLE_COMPANY_ID = 0x004C

    /**
     * Pure function so it can be unit tested without Android classes.
     * @param serviceData16 service data keyed by 16-bit UUID
     * @param manufacturerData manufacturer data keyed by company id
     */
    fun classify(
        name: String?,
        serviceData16: Map<Int, ByteArray>,
        manufacturerData: Map<Int, ByteArray>,
    ): TrackerKind {
        serviceData16[EDDYSTONE_UUID16]?.firstOrNull()?.let { frame ->
            when (frame.toInt() and 0xFF) {
                0x40 -> return TrackerKind.GOOGLE_FIND_HUB
                0x41 -> return TrackerKind.GOOGLE_FIND_HUB_SEPARATED
            }
        }
        if (serviceData16.containsKey(FAST_PAIR_UUID16)) return TrackerKind.FAST_PAIR
        manufacturerData[APPLE_COMPANY_ID]?.firstOrNull()?.let { type ->
            if ((type.toInt() and 0xFF) == 0x12) return TrackerKind.APPLE_FIND_MY
        }
        if (name != null && (name.contains("hoco", ignoreCase = true) || name.contains("E96", ignoreCase = true))) {
            return TrackerKind.NAMED_HOCO
        }
        return TrackerKind.OTHER
    }

    /**
     * A "type signature" for re-finding a saved tracker. Trackers deliberately rotate their
     * address and IDs, so this cannot single out one tracker: it only says "a tracker of the
     * same make/format". Two E96s side by side would share it.
     */
    fun fingerprint(
        kind: TrackerKind,
        name: String?,
        serviceData16: Map<Int, ByteArray>,
        manufacturerData: Map<Int, ByteArray>,
    ): String {
        val body = when (kind) {
            // 0x40 vs 0x41 flips when the tracker leaves its owner, so leave the frame byte out.
            TrackerKind.GOOGLE_FIND_HUB, TrackerKind.GOOGLE_FIND_HUB_SEPARATED ->
                "findhub:" + (serviceData16[EDDYSTONE_UUID16]?.size ?: 0)
            TrackerKind.FAST_PAIR -> "fastpair:" + (serviceData16[FAST_PAIR_UUID16]?.size ?: 0)
            TrackerKind.APPLE_FIND_MY -> "findmy:" + (manufacturerData[APPLE_COMPANY_ID]?.size ?: 0)
            TrackerKind.NAMED_HOCO, TrackerKind.OTHER -> "named"
        }
        // Names often arrive in only some adverts, so they're used only when nothing better exists.
        val useName = name != null && (kind == TrackerKind.NAMED_HOCO || kind == TrackerKind.OTHER)
        return if (useName) "$body|$name" else body
    }
}
