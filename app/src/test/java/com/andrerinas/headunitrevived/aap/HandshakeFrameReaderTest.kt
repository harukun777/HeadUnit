package com.andrerinas.headunitrevived.aap

import org.junit.Assert.*
import org.junit.Test
import java.net.SocketTimeoutException
import java.io.EOFException

class HandshakeFrameReaderTest {
    private val frame = byteArrayOf(0, 3, 0, 4, 0, 2, 0, 1)
    private fun reader(data: ByteArray, chunkSize: Int): HandshakeFrameReader {
        var offset = 0
        return HandshakeFrameReader({ buffer, length, _ ->
            if (offset == data.size) -1 else {
                val count = minOf(length, chunkSize, data.size - offset)
                data.copyInto(buffer, 0, offset, offset + count)
                offset += count
                count
            }
        }, { 0L })
    }

    @Test fun reconstructsResponseSplitIntoSingleBytes() {
        val buffer = ByteArray(32)
        assertEquals(8, reader(frame, 1).read(buffer, 2000))
        assertArrayEquals(frame, buffer.copyOf(8))
    }

    @Test fun leavesNextFrameForNextReadWhenPacketsAreCombined() {
        val reader = reader(frame + frame, 32)
        val buffer = ByteArray(32)
        repeat(2) {
            assertEquals(8, reader.read(buffer, 2000))
            assertArrayEquals(frame, buffer.copyOf(8))
        }
    }

    @Test(expected = EOFException::class) fun rejectsTruncatedResponse() {
        reader(frame.copyOf(6), 2).read(ByteArray(32), 2000)
    }

    @Test(expected = SocketTimeoutException::class) fun doesNotDiscardPartialHeaderOnTimeout() {
        var reads = 0
        HandshakeFrameReader({ buffer, _, _ ->
            if (reads++ == 0) { buffer[0] = 0; 1 } else 0
        }, { 0L }).read(ByteArray(32), 2000)
    }
}
