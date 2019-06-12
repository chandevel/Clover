package com.github.adamantcheese.chan.core.mapper;

import com.github.adamantcheese.chan.core.model.ChanThread;
import com.github.adamantcheese.chan.core.model.save.SerializableThread;

public class ThreadMapper {

    public static SerializableThread toSerializableThread(ChanThread chanThread) {
        return new SerializableThread(
                PostMapper.toSerializablePostList(chanThread.posts),
                chanThread.closed,
                chanThread.archived
        );
    }

}
