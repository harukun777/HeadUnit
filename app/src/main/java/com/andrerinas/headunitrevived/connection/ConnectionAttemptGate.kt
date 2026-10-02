package com.andrerinas.headunitrevived.connection

import java.util.concurrent.atomic.AtomicBoolean

/** Claim before opening a transport; an established session also owns the connection. */
internal class ConnectionAttemptGate {
    private val opening = AtomicBoolean(false)
    fun acquire(sessionBusy: () -> Boolean): Boolean {
        if (!opening.compareAndSet(false, true)) return false
        if (sessionBusy()) {
            opening.set(false)
            return false
        }
        return true
    }
    fun release() { opening.set(false) }
}
