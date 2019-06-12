package com.github.adamantcheese.chan.core.model.save;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class SerializableThread {
    @SerializedName("post_list")
    private List<SerializablePost> postList;
    @SerializedName("closed")
    private boolean closed;
    @SerializedName("archived")
    private boolean archived;

    public SerializableThread(List<SerializablePost> postList, boolean closed, boolean archived) {
        this.postList = postList;
        this.closed = closed;
        this.archived = archived;
    }

    public List<SerializablePost> getPostList() {
        return postList;
    }

    public boolean isClosed() {
        return closed;
    }

    public boolean isArchived() {
        return archived;
    }
}
