package com.github.adamantcheese.chan.core.site.parser.comment_action.linkdata;

import java.util.Objects;

/**
 * A board, thread, and postId combination to identify a thread.
 * Used for ExternalSiteArchives.
 */
public class ThreadLink {
    public String boardCode;
    public int threadId;
    public int postId;

    public ThreadLink(String boardCode, int threadId, int postId) {
        this.boardCode = boardCode;
        this.threadId = threadId;
        this.postId = postId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ThreadLink)) return false;
        ThreadLink that = (ThreadLink) o;
        return threadId == that.threadId && postId == that.postId && boardCode.equals(that.boardCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(boardCode, threadId, postId);
    }
}
