package com.github.adamantcheese.chan.core.model.save;

import com.github.adamantcheese.chan.core.mapper.PostMapper;
import com.github.adamantcheese.chan.core.model.Post;
import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class SerializableThread {
    @SerializedName("post_list")
    private List<SerializablePost> postList;
//    @SerializedName("closed")
//    private boolean closed;
//    @SerializedName("archived")
//    private boolean archived;

    public SerializableThread(List<SerializablePost> postList/*, boolean closed, boolean archived*/) {
        this.postList = postList;
//        this.closed = closed;
//        this.archived = archived;
    }

    public List<SerializablePost> getPostList() {
        return postList;
    }

//    public boolean isClosed() {
//        return closed;
//    }
//
//    public boolean isArchived() {
//        return archived;
//    }

    public SerializableThread merge(List<Post> posts) {
        for (Post post : posts) {
            // TODO: check duplicates once more just in case
            postList.add(PostMapper.toSerializablePost(post));
        }

        Collections.sort(postList, postComparator);
        return this;
    }

    private static final Comparator<SerializablePost> postComparator = (o1, o2) -> {
        return Integer.compare(o1.getNo(), o2.getNo());
    };
}
