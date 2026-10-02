package com.andrerinas.headunitrevived.connection

/** Bound automatic discovery to at most 1022 hosts; larger LANs also support manual IP entry. */
internal object LanScanTargets {
    fun hosts(address: ByteArray, prefixLength: Int): List<String> {
        if (address.size != 4 || prefixLength !in 0..32) return emptyList()
        val prefix = maxOf(prefixLength, 22)
        val local = address.fold(0L) { value, byte -> (value shl 8) or (byte.toLong() and 255) }
        val mask = (0xffffffffL shl (32 - prefix)) and 0xffffffffL
        val first = local and mask
        val last = first or (mask xor 0xffffffffL)
        if (prefix >= 31) return emptyList()
        return (first + 1 until last).filter { it != local }.map { ip ->
            (3 downTo 0).joinToString(".") { shift -> ((ip shr (shift * 8)) and 255).toString() }
        }
    }
}
