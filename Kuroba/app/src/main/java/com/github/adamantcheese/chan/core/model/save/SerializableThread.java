package com.github.adamantcheese.chan.core.model.save;

import com.github.adamantcheese.chan.core.mapper.PostMapper;
import com.github.adamantcheese.chan.core.model.Post;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        Set<SerializablePost> postsSet = new HashSet<>(posts.size() + postList.size());
        postsSet.addAll(postList);

        for (Post post : posts) {
            postsSet.add(PostMapper.toSerializablePost(post));
        }

        List<SerializablePost> filteredPosts = new ArrayList<>(postsSet.size());
        filteredPosts.addAll(postsSet);

        Collections.sort(filteredPosts, postComparator);

        postList.clear();
        postList.addAll(filteredPosts);
        return this;
    }

    private static final Comparator<SerializablePost> postComparator = (o1, o2) -> {
        return Integer.compare(o1.getNo(), o2.getNo());
    };
}
