package com.github.adamantcheese.chan.core.mapper;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.model.ChanThread;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.model.save.SerializableThread;
import com.github.adamantcheese.chan.utils.Logger;
import com.google.gson.Gson;

import java.util.List;

public class ThreadMapper {
    private static final String TAG = "ThreadMapper";

    public static SerializableThread toSerializableThread(Gson gson, List<Post> posts) {
        return new SerializableThread(PostMapper.toSerializablePostList(gson, posts));
    }

    @Nullable
    public static ChanThread fromSerializedThread(Gson gson, Loadable loadable, SerializableThread serializableThread) {
        List<Post> posts = PostMapper.fromSerializedPostList(gson, loadable, serializableThread.getPostList());

        if (posts.isEmpty()) {
            Logger.w(TAG, "PostMapper.fromSerializedPostList returned empty list");
            return null;
        }

        ChanThread chanThread = new ChanThread(loadable, posts);

        chanThread.setArchived(true);

        return chanThread;
    }
}
