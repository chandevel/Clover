package com.github.adamantcheese.chan.core.cache

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer

class PartialContentOkHttpDispatcher : Dispatcher() {
    private fun error(msg: String): MockResponse = MockResponse()
            .setResponseCode(400)
            .setBody(msg)

    @Throws(InterruptedException::class)
    override fun dispatch(request: RecordedRequest): MockResponse {
        try {
            val rangeHeader = request.getHeader("Range")
                    ?: return error("No \"Range\" header")

            val rangeList = rangeHeader.substringAfter("bytes=")
                    .split('-')
                    .map { value -> value.toLongOrNull() }

            if (rangeList.size != 2 || rangeList.first() == null || rangeList.last() == null) {
                return error("Couldn't convert Range header \"$rangeHeader\"")
            }

            val imageName = request.path?.substringAfterLast('/')
                    ?: return error("Bad request path: ${request.path}")

            val (start, end) = rangeList.filterNotNull()

            val buffer = Buffer()
                    .readFrom(javaClass.classLoader.getResourceAsStream(imageName))

            val outputBuffer = buffer.use { buff ->
                if (start > 0) {
                    buff.skip(start)
                }

                val outputBuffer = Buffer()
                buff.read(outputBuffer, (end + 1) - start)

                return@use outputBuffer
            }

            return MockResponse()
                    .setBody(outputBuffer)
                    .setResponseCode(206)
        } catch (error: Throwable) {
            return error("Exception: ${error.javaClass.simpleName} message = ${error.message}")
        }
    }
}