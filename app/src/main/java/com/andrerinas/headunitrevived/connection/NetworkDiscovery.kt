package com.andrerinas.headunitrevived.connection

import android.content.Context
import android.os.Build
import com.andrerinas.headunitrevived.utils.AppLog
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.InetSocketAddress
import java.net.Socket

class NetworkDiscovery(
    private val context: Context,
    private val listener: Listener,
    private val discoverHelper: Boolean = true
) {
    interface Listener {
        fun onServiceFound(ip: String, port: Int, socket: Socket? = null)
        fun onScanFinished()
    }
    private var scanJob: Job? = null

    fun startScan() {
        if (scanJob?.isActive == true) return
        scanJob = CoroutineScope(Dispatchers.IO).launch {
            val lan = WifiLan(context)
            val found = java.util.Collections.synchronizedSet(mutableSetOf<String>())
            try {
                val gateways = listOfNotNull(lan.gateway(), if (isEmulator()) "10.0.2.2" else null).distinct()
                for (ip in gateways) {
                    ensureActive()
                    if (checkAndReport(lan, ip, found)) {
                        withContext(Dispatchers.Main) { listener.onScanFinished() }
                        return@launch
                    }
                }
                // Children belong to the scan job: cancellation prevents further callbacks.
                val limit = Semaphore(24)
                coroutineScope {
                    lan.targets().filterNot { it in gateways }.map { ip ->
                        async { limit.withPermit { ensureActive(); checkAndReport(lan, ip, found) } }
                    }.awaitAll()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("NetworkDiscovery: WiFi scan failed", e)
            }
            withContext(Dispatchers.Main) { listener.onScanFinished() }
        }
    }

    private suspend fun checkAndReport(lan: WifiLan, ip: String, found: MutableSet<String>): Boolean {
        val ports = if (discoverHelper) listOf(5277, 5289) else listOf(5277)
        for (port in ports) {
            currentCoroutineContext().ensureActive()
            val socket = Socket()
            var handedOff = false
            try {
                lan.bind(socket)
                socket.connect(InetSocketAddress(ip, port), 500)
                if (!found.add(ip)) return true
                withContext(Dispatchers.Main) {
                    if (port == 5277) {
                        listener.onServiceFound(ip, port, socket)
                        handedOff = true
                    } else listener.onServiceFound(ip, port)
                }
                return true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Closed below, including failed and cancelled probes.
            } finally {
                if (!handedOff) try { socket.close() } catch (_: Exception) {}
            }
        }
        return false
    }

    fun stop() {
        scanJob?.cancel()
        scanJob = null
    }

    private fun isEmulator(): Boolean {
        return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.PRODUCT.contains("sdk_google")
                || Build.PRODUCT.contains("google_sdk")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("sdk_x86")
                || Build.PRODUCT.contains("vbox86p")
                || Build.PRODUCT.contains("emulator")
                || Build.PRODUCT.contains("simulator")
    }
}
