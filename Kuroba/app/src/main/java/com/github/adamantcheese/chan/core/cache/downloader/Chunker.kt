package com.github.adamantcheese.chan.core.cache.downloader


fun chunkLong(value: Long, chunksCount: Int, minChunkSize: Long): List<Chunk> {
    require(chunksCount > 0) { "ChunksCount ($chunksCount) must be greater than zero!" }
    require(value >= chunksCount) { "Value ($value) must be greater or equal to chunksCount ($chunksCount)" }

    if (value < minChunkSize) {
        return listOf(Chunk(0, value))
    }

    val chunks = mutableListOf<Chunk>()
    val chunkSize = value / chunksCount
    var current = 0L

    for (i in 0 until chunksCount) {
        chunks += Chunk(current, (current + chunkSize).coerceAtMost(value))
        current += chunkSize
    }

    if (current < value) {
        val lastChunk = chunks.removeAt(chunks.lastIndex)
        chunks += Chunk(lastChunk.start, value)
    }

    return chunks
}

/**
 * [realEnd] is only being used in tests.
 * */
data class Chunk(val start: Long, val realEnd: Long) {
    // Must be 1 less than actual _end
    val end: Long
        get() = realEnd - 1

    fun isWholeFile(): Boolean {
        return start == 0L && realEnd == Long.MAX_VALUE
    }

    fun chunkSize(): Long = realEnd - start

    override fun toString(): String {
        return "Chunk(start=$start, end=$end)"
    }

    companion object {
        fun wholeFile(): Chunk = Chunk(0, Long.MAX_VALUE)
    }
}