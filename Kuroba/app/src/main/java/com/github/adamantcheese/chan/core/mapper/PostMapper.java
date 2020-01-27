package com.github.adamantcheese.chan.core.mapper;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.model.save.SerializablePost;
import com.github.adamantcheese.chan.utils.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class PostMapper {
    private static final String TAG = "PostMapper";
    private static final Comparator<Post> POST_COMPARATOR = (p1, p2) -> Integer.compare(p1.no, p2.no);

    public static SerializablePost toSerializablePost(Post post) {
        List<Integer> repliesFrom;

        synchronized (post.repliesFrom) {
            repliesFrom = new ArrayList<>(post.repliesFrom);
        }

        return new SerializablePost(
                post.boardId,
                BoardMapper.toSerializableBoard(post.board),
                post.no,
                post.isOP,
                post.name,
                SpannableStringMapper.serializeSpannableString(post.comment),
                SpannableStringMapper.serializeSpannableString(post.subject),
                post.time,
                PostImageMapper.toSerializablePostImageList(post.images),
                post.tripcode,
                post.id,
                post.opId,
                post.capcode,
                post.isSavedReply,
                post.filterHighlightedColor,
                post.filterStub,
                post.filterRemove,
                post.filterReplies,
                post.filterOnlyOP,
                post.filterSaved,
                post.repliesTo,
                SpannableStringMapper.serializeSpannableString(post.nameTripcodeIdCapcodeSpan),
                post.deleted.get(),
                repliesFrom,
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

    public static Post fromSerializedPost(Loadable loadable, SerializablePost serializablePost) {
        CharSequence subject = SpannableStringMapper.deserializeSpannableString(serializablePost.getSubject());
        CharSequence subjectSpans = subject.length() == 0 ? null : subject;

        Post.Builder postBuilder = new Post.Builder().board(loadable.board)
                .id(serializablePost.getNo())
                .op(serializablePost.isOP())
                .name(serializablePost.getName())
                .comment(SpannableStringMapper.deserializeSpannableString(serializablePost.getComment()))
                .subject(subject.toString())
                .setUnixTimestampSeconds(serializablePost.getTime())
                .images(PostImageMapper.fromSerializablePostImageList(serializablePost.getImages()))
                .tripcode(serializablePost.getTripcode())
                .opId(serializablePost.getOpId())
                .moderatorCapcode(serializablePost.getCapcode())
                .isSavedReply(serializablePost.isSavedReply())
                .filter(
                        serializablePost.getFilterHighlightedColor(),
                        serializablePost.isFilterStub(),
                        serializablePost.isFilterRemove(),
                        // always false, doesn't make sense and may break everything otherwise
                        false,
                        serializablePost.isFilterReplies(),
                        serializablePost.isFilterOnlyOP(),
                        serializablePost.isFilterSaved()
                )
                .repliesTo(serializablePost.getRepliesTo())
                .spans(
                        subjectSpans,
                        SpannableStringMapper.deserializeSpannableString(serializablePost.getNameTripcodeIdCapcodeSpan())
                )
                .sticky(serializablePost.isSticky())
                .archived(serializablePost.isArchived())
                .replies(serializablePost.getReplies())
                .images(serializablePost.getImagesCount())
                .uniqueIps(serializablePost.getUniqueIps())
                .lastModified(serializablePost.getLastModified());

        Post post = postBuilder.build();
        post.setTitle(serializablePost.getTitle());
        post.repliesFrom.addAll(serializablePost.getRepliesFrom());

        return post;
    }

    public static List<Post> fromSerializedPostList(Loadable loadable, List<SerializablePost> serializablePostList) {
        List<Post> posts = new ArrayList<>(serializablePostList.size());
        Throwable firstException = null;

        for (SerializablePost serializablePost : serializablePostList) {
            try {
                posts.add(fromSerializedPost(loadable, serializablePost));
            } catch (Throwable error) {
                // Skip post if could not deserialize
                if (firstException == null) {
                    // We will report only the first exception because there may be a lot of them
                    // and they may all be the same
                    firstException = error;
                }
            }
        }

        if (firstException != null) {
            Logger.e(TAG, "There were at least one exception thrown while trying to deserialize posts", firstException);
        }

        Collections.sort(posts, POST_COMPARATOR);
        return posts;
    }
}
