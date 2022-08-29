package com.github.adamantcheese.chan.core.site.parser.comment_action.linkdata;

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
