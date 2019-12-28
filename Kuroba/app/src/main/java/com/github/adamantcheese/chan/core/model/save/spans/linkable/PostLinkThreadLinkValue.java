package com.github.adamantcheese.chan.core.model.save.spans.linkable;

import com.github.adamantcheese.chan.core.model.save.spans.SerializablePostLinkableSpan.PostLinkableType;
import com.google.gson.annotations.SerializedName;

public class PostLinkThreadLinkValue
        extends PostLinkableValue {
    @SerializedName("board")
    private String board;
    @SerializedName("thread_id")
    private int threadId;
    @SerializedName("post_id")
    private int postId;

    public PostLinkThreadLinkValue(PostLinkableType type, String board, int threadId, int postId) {
        super(type);

        this.board = board;
        this.threadId = threadId;
        this.postId = postId;
    }

    public String getBoard() {
        return board;
    }

    public int getThreadId() {
        return threadId;
    }

    public int getPostId() {
        return postId;
    }
}
