package com.andrerinas.headunitrevived.connection

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.TimeUnit

class ConnectionAttemptGateTest {
    @Test fun concurrentAttemptsOpenOnlyOneTransport() {
        val gate = ConnectionAttemptGate()
        val start = CountDownLatch(1)
        val finished = CountDownLatch(12)
        val opened = AtomicInteger()
        val pool = Executors.newFixedThreadPool(12)
        try {
            repeat(12) { pool.submit {
                start.await()
                if (gate.acquire { false }) opened.incrementAndGet()
                finished.countDown()
            } }
            start.countDown()
            assertTrue(finished.await(5, TimeUnit.SECONDS))
            assertEquals(1, opened.get())
            gate.release()
            assertTrue(gate.acquire { false })
        } finally { pool.shutdownNow() }
    }
    @Test fun preservesEstablishedSessionAndAllowsRetryAfterDisconnect() {
        val gate = ConnectionAttemptGate()
        assertFalse(gate.acquire { true })
        assertTrue(gate.acquire { false })
        gate.release()
        assertFalse(gate.acquire { true })
        assertTrue(gate.acquire { false })
    }
}
