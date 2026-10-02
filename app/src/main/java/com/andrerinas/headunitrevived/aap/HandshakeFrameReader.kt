package com.andrerinas.headunitrevived.aap

import java.io.EOFException
import java.io.IOException
import java.net.SocketTimeoutException

/** TCP reads can split or combine protocol messages. Consume exactly one frame. */
internal class HandshakeFrameReader(
    private val receive: (ByteArray, Int, Int) -> Int,
    private val now: () -> Long
) {
    fun read(buffer: ByteArray, deadline: Long): Int {
        fun exact(offset: Int, length: Int) {
            var copied = 0
            val chunk = ByteArray(length)
            while (copied < length) {
                val remaining = deadline - now()
                if (remaining <= 0) throw SocketTimeoutException("Version response timed out ($copied/$length bytes)")
                val count = receive(chunk, length - copied, remaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                if (count < 0) throw EOFException("Phone closed connection during version response")
                if (count == 0) throw SocketTimeoutException("Phone did not complete version response ($copied/$length bytes)")
                if (count > length - copied) throw IOException("Invalid TCP read length")
                chunk.copyInto(buffer, offset + copied, 0, count)
                copied += count
            }
        }
        exact(0, 4)
        val size = ((buffer[2].toInt() and 255) shl 8) or (buffer[3].toInt() and 255)
        if (size < 2 || size > buffer.size - 4) throw IOException("Invalid version frame length: $size")
        exact(4, size)
        return size + 4
    }
}
