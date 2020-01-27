package com.github.adamantcheese.chan.core.cache.downloader

import org.junit.Assert.assertEquals
import org.junit.Test

class ChunkerKtTest {

    @Test
    fun `test split 5 into five chunks`() {
        val chunks = chunkLong(5, 5, 1)

        assertEquals(5, chunks.size)
        assertEquals(0, chunks[0].start)
        assertEquals(1, chunks[0].realEnd)
        assertEquals(1, chunks[1].start)
        assertEquals(2, chunks[1].realEnd)
        assertEquals(2, chunks[2].start)
        assertEquals(3, chunks[2].realEnd)
        assertEquals(3, chunks[3].start)
        assertEquals(4, chunks[3].realEnd)
        assertEquals(4, chunks[4].start)
        assertEquals(5, chunks[4].realEnd)
    }

    @Test
    fun `test split even amount of chunks`() {
        val chunks = chunkLong(10, 5, 1)

        assertEquals(5, chunks.size)
        assertEquals(0, chunks[0].start)
        assertEquals(2, chunks[0].realEnd)
        assertEquals(2, chunks[1].start)
        assertEquals(4, chunks[1].realEnd)
        assertEquals(4, chunks[2].start)
        assertEquals(6, chunks[2].realEnd)
        assertEquals(6, chunks[3].start)
        assertEquals(8, chunks[3].realEnd)
        assertEquals(8, chunks[4].start)
        assertEquals(10, chunks[4].realEnd)
    }

    @Test
    fun `test split odd amount of chunks`() {
        val chunks = chunkLong(10, 3, 1)

        assertEquals(3, chunks.size)
        assertEquals(0, chunks[0].start)
        assertEquals(3, chunks[0].realEnd)
        assertEquals(3, chunks[1].start)
        assertEquals(6, chunks[1].realEnd)
        assertEquals(6, chunks[2].start)
        assertEquals(10, chunks[2].realEnd)
    }

    @Test
    fun `try split value less than min chunk size`() {
        val chunks = chunkLong(10, 5, 20)

        assertEquals(1, chunks.size)
        assertEquals(0, chunks[0].start)
        assertEquals(10, chunks[0].realEnd)
    }
}