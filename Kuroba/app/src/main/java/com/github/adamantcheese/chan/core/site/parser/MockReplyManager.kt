package com.github.adamantcheese.chan.core.site.parser

import androidx.annotation.GuardedBy
import com.github.adamantcheese.chan.utils.Logger
import java.util.*

/**
 * This class is used to add mock replies to posts that were made by you or marked as yours. The
 * main point of this is to tests things related to replies to your posts (like notifications
 * showing up on (You)s and such). This is only should be used for development purposes.
 *
 * If you want to add a mock reply to a post that was not made by you then you should mark that
 * post as yours beforehand (in case you want to test (You) notification show up) because this class
 * DOES NOT do that automatically for you.
 *
 * The new mock reply will also be stored in the database and in the local thread and it's
 * impossible to remove it.
 *
 * Also, the replies are not persisted across application lifecycle, so once the app dies all
 * replies in the queue will be gone and you will have to add them again.
 *
 * ThreadSafe.
 * */
class MockReplyManager {
    @GuardedBy("this")
    private val mockReplyMultiMap = mutableMapOf<ThreadDescriptor, LinkedList<Int>>()

    fun addMockReply(siteId: Int, boardCode: String, opNo: Int, postNo: Int) {
        synchronized(this) {
            val threadDescriptor = ThreadDescriptor(siteId, boardCode, opNo)

            if (!mockReplyMultiMap.containsKey(threadDescriptor)) {
                mockReplyMultiMap[threadDescriptor] = LinkedList()
            }

            mockReplyMultiMap[threadDescriptor]!!.addFirst(postNo)
            Logger.d(TAG, "addMockReply() mock replies count = ${mockReplyMultiMap.size}")
        }
    }

    fun getLastMockReply(siteId: Int, boardCode: String, opNo: Int): Int {
        return synchronized(this) {
            val threadDescriptor = ThreadDescriptor(siteId, boardCode, opNo)

            val repliesQueue = mockReplyMultiMap[threadDescriptor]
                    ?: return@synchronized -1

            if (repliesQueue.isEmpty()) {
                mockReplyMultiMap.remove(threadDescriptor)
                return@synchronized -1
            }

            val lastReply = repliesQueue.removeLast()
            Logger.d(TAG, "getLastMockReplyOrNull() mock replies " +
                    "count = ${mockReplyMultiMap.values.sumBy { queue -> queue.size }}")

            if (repliesQueue.isEmpty()) {
                mockReplyMultiMap.remove(threadDescriptor)
            }

            return@synchronized lastReply
        }
    }

    data class ThreadDescriptor(
            val siteId: Int,
            val boardCode: String,
            val opNo: Int
    ) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ThreadDescriptor

            if (siteId != other.siteId) return false
            if (boardCode != other.boardCode) return false
            if (opNo != other.opNo) return false

            return true
        }

        override fun hashCode(): Int {
            var result = siteId
            result = 31 * result + boardCode.hashCode()
            result = 31 * result + opNo
            return result
        }
    }

    companion object {
        private const val TAG = "MockReplyManager"
    }
}