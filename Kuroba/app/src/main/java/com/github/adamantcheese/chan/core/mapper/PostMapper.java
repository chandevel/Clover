package com.github.adamantcheese.chan.core.mapper;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.save.SerializablePost;

import java.util.ArrayList;
import java.util.List;

public class PostMapper {

    public static SerializablePost toSerializablePost(Post post) {
        return new SerializablePost(
                post.boardId,
                BoardMapper.toSerializableBoard(post.board),
                post.no,
                post.isOP,
                post.name,
                post.comment,
                post.subject,
                post.time,
                PostImageMapper.toSerializablePostImageList(post.images),
                post.tripcode,
                post.id,
                post.opId,
                post.capcode,
                post.isSavedReply,
                post.repliesTo,
                post.subjectSpan,
                post.nameTripcodeIdCapcodeSpan,
                post.deleted.get(),
                post.repliesFrom,
                post.isSticky(),
                post.isClosed(),
                post.isArchived(),
                post.getReplies(),
                post.getImagesCount(),
                post.getUniqueIps(),
                post.getLastModified(),
                post.getTitle()
        );
    }

    public static List<SerializablePost> toSerializablePostList(List<Post> postList) {
        List<SerializablePost> serializablePostList = new ArrayList<>(postList.size());

        for (Post post : postList) {
            serializablePostList.add(toSerializablePost(post));
        }

        return serializablePostList;
    }
}
