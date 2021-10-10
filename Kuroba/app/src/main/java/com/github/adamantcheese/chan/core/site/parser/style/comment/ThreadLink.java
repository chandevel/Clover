package com.github.adamantcheese.chan.core.site.parser.style.comment;

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
}
