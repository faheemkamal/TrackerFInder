package com.example.trackerfinder

import org.junit.Assert.assertEquals
import org.junit.Test

class LogicTest {
    private fun b(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test fun findHubFrame() {
        assertEquals(TrackerKind.GOOGLE_FIND_HUB,
            AdvertParser.classify(null, mapOf(0xFEAA to b(0x40, 1, 2)), emptyMap()))
        assertEquals(TrackerKind.GOOGLE_FIND_HUB_SEPARATED,
            AdvertParser.classify(null, mapOf(0xFEAA to b(0x41, 1, 2)), emptyMap()))
    }

    @Test fun eddystoneUrlIsNotTracker() {
        assertEquals(TrackerKind.OTHER, AdvertParser.classify(null, mapOf(0xFEAA to b(0x10)), emptyMap()))
    }

    @Test fun appleAndFastPairAndName() {
        assertEquals(TrackerKind.APPLE_FIND_MY, AdvertParser.classify(null, emptyMap(), mapOf(0x004C to b(0x12, 0x19))))
        assertEquals(TrackerKind.FAST_PAIR, AdvertParser.classify(null, mapOf(0xFE2C to b(1, 2, 3)), emptyMap()))
        assertEquals(TrackerKind.NAMED_HOCO, AdvertParser.classify("hoco E96", emptyMap(), emptyMap()))
    }

    @Test fun filterDropsSpike() {
        val f = SignalFilter()
        repeat(5) { f.add(-70) }
        val after = f.add(-40)
        assertEquals(-70.0, after, 0.01)
    }

    @Test fun zonesAndDistance() {
        assertEquals(Zone.VERY_CLOSE, Proximity.zone(-60.0, -60))
        assertEquals(Zone.FAR, Proximity.zone(-95.0, -60))
        assertEquals(1.0, Proximity.estimateMeters(-60.0, -60), 0.001)
        assertEquals(Trend.WARMER, Proximity.trend(listOf(-80f, -80f, -80f, -80f, -80f, -80f, -70f, -70f)))
    }

    @Test fun fingerprintIgnoresSeparatedFlagAndName() {
        val a = AdvertParser.fingerprint(TrackerKind.GOOGLE_FIND_HUB, null, mapOf(0xFEAA to ByteArray(21)), emptyMap())
        val b = AdvertParser.fingerprint(TrackerKind.GOOGLE_FIND_HUB_SEPARATED, "x", mapOf(0xFEAA to ByteArray(21)), emptyMap())
        assertEquals(a, b)
    }

    private fun dev(addr: String, fp: String, rssi: Int = -60, kind: TrackerKind = TrackerKind.GOOGLE_FIND_HUB) =
        SeenDevice(addr, null, kind, fp, rssi, 0, 0)

    @Test fun favoriteMatchesByAddressOrType() {
        val fav = Favorite(nickname = "Keys", fingerprint = "findhub:21", kind = TrackerKind.GOOGLE_FIND_HUB, name = null, lastAddress = "AA", lastSeenAt = 0)
        assertEquals(true, fav.matches(dev("BB", "findhub:21")))
        assertEquals(false, fav.matches(dev("BB", "findmy:25")))
        val generic = fav.copy(fingerprint = "named", kind = TrackerKind.OTHER)
        assertEquals(false, generic.matches(dev("BB", "named", kind = TrackerKind.OTHER)))
        assertEquals(true, generic.matches(dev("AA", "named", kind = TrackerKind.OTHER)))
    }

    @Test fun bestMatchPrefersExactAddressThenStrongest() {
        val fav = Favorite(nickname = "Keys", fingerprint = "findhub:21", kind = TrackerKind.GOOGLE_FIND_HUB, name = null, lastAddress = "AA", lastSeenAt = 0)
        val list = listOf(dev("BB", "findhub:21", -50), dev("AA", "findhub:21", -80), dev("CC", "findhub:21", -40))
        assertEquals("AA", fav.bestMatch(list)?.address)
        assertEquals("CC", fav.bestMatch(list.filter { it.address != "AA" })?.address)
        assertEquals(FavoriteStatus.Away, fav.status(emptyList()))
        assertEquals(2, (fav.status(list.drop(0).filter { it.address != "AA" }) as FavoriteStatus.Maybe).count)
    }
}
