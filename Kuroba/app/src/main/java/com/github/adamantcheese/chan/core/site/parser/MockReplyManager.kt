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
    private val mockReplyQueue = LinkedList<MockReply>()

    fun addMockReply(siteId: Int, boardCode: String, opNo: Int, postNo: Int) {
        synchronized(this) {
            mockReplyQueue.addFirst(MockReply(siteId, boardCode, opNo, postNo))
            Logger.d(TAG, "addMockReply() mock replies count = ${mockReplyQueue.size}")
        }
    }

    fun hasMockReply(): Boolean {
        return synchronized(this) { mockReplyQueue.peekLast() != null }
    }

    fun getMockReply(): MockReply? {
        return synchronized(this) {
            val mockReply = mockReplyQueue.pollLast()
            Logger.d(TAG, "getMockReply() mock replies count = ${mockReplyQueue.size}")


            return@synchronized mockReply
        }
    }

    data class MockReply(
            val siteId: Int,
            val boardCode: String,
            val opNo: Int,
            val postNo: Int
    ) {
        fun isSuitablePost(siteId: Int, boardCode: String, opNo: Int): Boolean {
            return this.siteId == siteId && this.boardCode == boardCode && this.opNo == opNo
        }
    }

    companion object {
        private const val TAG = "MockReplyManager"
    }
}