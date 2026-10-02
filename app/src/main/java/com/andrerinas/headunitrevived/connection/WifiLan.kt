package com.andrerinas.headunitrevived.connection

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import java.net.Inet4Address
import java.net.InetAddress
import java.net.Socket

internal class WifiLan(context: Context) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun network(): Network? = if (Build.VERSION.SDK_INT >= 21) cm.allNetworks.firstOrNull {
        cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    } else null

    fun address(): InetAddress? {
        if (Build.VERSION.SDK_INT >= 21) network()?.let { net ->
            cm.getLinkProperties(net)?.linkAddresses?.firstOrNull { it.address is Inet4Address }?.let { return it.address }
        }
        @Suppress("DEPRECATION")
        val ip = wifi.connectionInfo.ipAddress
        return if (ip == 0) null else fromDhcp(ip)
    }

    fun gateway(): String? {
        if (Build.VERSION.SDK_INT >= 21) network()?.let { net ->
            cm.getLinkProperties(net)?.routes?.firstOrNull {
                it.isDefaultRoute && it.gateway is Inet4Address
            }?.gateway?.hostAddress?.let { return it }
        }
        @Suppress("DEPRECATION")
        val gateway = wifi.dhcpInfo?.gateway ?: 0
        return if (gateway == 0) null else fromDhcp(gateway).hostAddress
    }

    fun targets(): List<String> {
        if (Build.VERSION.SDK_INT >= 21) network()?.let { net ->
            cm.getLinkProperties(net)?.linkAddresses?.firstOrNull { it.address is Inet4Address }?.let {
                return LanScanTargets.hosts(it.address.address, it.prefixLength)
            }
        }
        return address()?.let { LanScanTargets.hosts(it.address, 24) } ?: emptyList()
    }

    fun bind(socket: Socket) {
        if (Build.VERSION.SDK_INT >= 22) network()?.bindSocket(socket)
    }

    private fun fromDhcp(ip: Int): InetAddress = InetAddress.getByAddress(
        ByteArray(4) { index -> (ip ushr (index * 8)).toByte() }
    )
}
