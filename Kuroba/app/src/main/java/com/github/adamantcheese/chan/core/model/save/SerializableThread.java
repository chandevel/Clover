package com.github.adamantcheese.chan.core.model.save;

import com.github.adamantcheese.chan.core.mapper.PostMapper;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
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

    public SerializableThread(List<SerializablePost> postList) {
        this.postList = postList;
    }

    public List<SerializablePost> getPostList() {
        return postList;
    }

    /**
     * Merge old posts with new posts avoiding duplicates and then sort merged list
     */
    public SerializableThread merge(List<Post> posts) {
        if (BackgroundUtils.isMainThread()) {
            throw new RuntimeException("Cannot be executed on the main thread!");
        }

        Set<SerializablePost> postsSet = new HashSet<>(posts.size() + postList.size());
        postsSet.addAll(postList);

        for (Post post : posts) {
            postsSet.add(PostMapper.toSerializablePost(post));
        }

        List<SerializablePost> filteredPosts = new ArrayList<>(postsSet.size());
        filteredPosts.addAll(postsSet);
        postsSet.clear();

        Collections.sort(filteredPosts, postComparator);

        postList.clear();
        postList.addAll(filteredPosts);
        return this;
    }

    private static final Comparator<SerializablePost> postComparator
            = (o1, o2) -> Integer.compare(o1.getNo(), o2.getNo());
}
