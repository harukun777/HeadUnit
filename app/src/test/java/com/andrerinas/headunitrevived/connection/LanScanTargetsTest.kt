package com.andrerinas.headunitrevived.connection

import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress

class LanScanTargetsTest {
    private fun hosts(ip: String, prefix: Int) = LanScanTargets.hosts(InetAddress.getByName(ip).address, prefix)

    @Test fun excludesSelfNetworkAndBroadcast() {
        val targets = hosts("192.168.1.20", 24)
        assertEquals(253, targets.size)
        assertTrue("192.168.1.254" in targets)
        assertFalse("192.168.1.20" in targets)
        assertFalse("192.168.1.0" in targets)
        assertFalse("192.168.1.255" in targets)
    }

    @Test fun respectsSmallSubnet() {
        assertEquals(listOf("10.0.0.1", "10.0.0.3", "10.0.0.4", "10.0.0.5", "10.0.0.6"), hosts("10.0.0.2", 29))
    }

    @Test fun searchesAcrossOctetsForWiderSubnet() {
        val targets = hosts("192.168.3.20", 23)
        assertEquals(509, targets.size)
        assertTrue("192.168.2.1" in targets)
        assertTrue("192.168.3.254" in targets)
    }

    @Test fun boundsLargeNetworksAroundCurrentAddress() {
        val targets = hosts("10.1.7.20", 8)
        assertEquals(1021, targets.size)
        assertEquals("10.1.4.1", targets.first())
        assertEquals("10.1.7.254", targets.last())
    }

    @Test fun rejectsInvalidOrPointToPointSubnets() {
        assertTrue(hosts("10.0.0.1", 31).isEmpty())
        assertTrue(hosts("10.0.0.1", 32).isEmpty())
        assertTrue(hosts("10.0.0.1", -1).isEmpty())
        assertTrue(LanScanTargets.hosts(ByteArray(16), 24).isEmpty())
    }
}
