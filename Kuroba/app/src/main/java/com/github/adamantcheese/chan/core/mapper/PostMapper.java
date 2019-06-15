package com.github.adamantcheese.chan.core.mapper;

import android.text.Html;
import android.text.SpannableString;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.model.save.SerializablePost;
import com.github.adamantcheese.chan.utils.AndroidUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class PostMapper {
    private static final Comparator<Post> POST_COMPARATOR = (p1, p2) -> Integer.compare(p1.no, p2.no);

    // TODO: figure out how to serialize spans because right now only handful of them
    //  outlive deserialization (greentext and something else, links/quotes etc do not work
    //  after deserialization)
    public static SerializablePost toSerializablePost(Post post) {
        return new SerializablePost(
                post.boardId,
                // TODO: delete? We don't really need because we use loadable when
                //  deserializing boards, so we don't need to store it
                BoardMapper.toSerializableBoard(post.board),
                post.no,
                post.isOP,
                post.name,
                serializeSpannableString(post.comment),
                post.subject,
                post.time,
                PostImageMapper.toSerializablePostImageList(post.images),
                post.tripcode,
                post.id,
                post.opId,
                post.capcode,
                post.isSavedReply,
                post.repliesTo,
                serializeSpannableString(post.subjectSpan),
                serializeSpannableString(post.nameTripcodeIdCapcodeSpan),
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

    @Nullable
    private static String serializeSpannableString(@Nullable CharSequence charSequence) {
        if (charSequence == null) {
            return null;
        }

        if (AndroidUtils.isNougat()) {
            return Html.toHtml(new SpannableString(charSequence), Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE);
        } else {
            return Html.toHtml(new SpannableString(charSequence));
        }
    }

    public static List<SerializablePost> toSerializablePostList(List<Post> postList) {
        List<SerializablePost> serializablePostList = new ArrayList<>(postList.size());

        for (Post post : postList) {
            serializablePostList.add(toSerializablePost(post));
        }

        return serializablePostList;
    }

    public static Post fromSeriazliedPost(Loadable loadable, SerializablePost serializablePost) {
        Post.Builder postBuilder = new Post.Builder()
                .board(loadable.board)
                .id(serializablePost.getNo())
                .op(serializablePost.isOP())
                .name(serializablePost.getName())
                .comment(deserializeSpannableStringSpannableString(serializablePost.getComment()))
                .subject(serializablePost.getSubject())
                .setUnixTimestampSeconds(serializablePost.getTime())
                .images(PostImageMapper.fromSerializablePostImageList(serializablePost.getImages()))
                .tripcode(serializablePost.getTripcode())
                .opId(serializablePost.getOpId())
                .moderatorCapcode(serializablePost.getCapcode())
                .isSavedReply(serializablePost.isSavedReply())
                .repliesTo(serializablePost.getRepliesTo())
                .spans(
                        deserializeSpannableStringSpannableString(serializablePost.getSubjectSpan()),
                        deserializeSpannableStringSpannableString(serializablePost.getNameTripcodeIdCapcodeSpan()))
                .sticky(serializablePost.isSticky())
                .archived(serializablePost.isArchived())
                .replies(serializablePost.getReplies())
                .images(serializablePost.getImagesCount())
                .uniqueIps(serializablePost.getUniqueIps())
                .lastModified(serializablePost.getLastModified());

        for (Integer replyFrom : serializablePost.getRepliesFrom()) {
            postBuilder.addReplyTo(replyFrom); // TODO: may be wrong
        }

        Post post = postBuilder.build();
        post.setTitle(serializablePost.getTitle());


        return post;
    }

    @Nullable
    private static CharSequence deserializeSpannableStringSpannableString(@Nullable String string) {
        if (string == null) {
            return null;
        }

        if (AndroidUtils.isNougat()) {
            return Html.fromHtml(string, Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE);
        } else {
            return Html.fromHtml(string);
        }
    }

    public static List<Post> fromSerializedPostList(Loadable loadable, List<SerializablePost> serializablePostList) {
        List<Post> posts = new ArrayList<>(serializablePostList.size());

        for (SerializablePost serializablePost : serializablePostList) {
            posts.add(fromSeriazliedPost(loadable, serializablePost));
        }

        // TODO: double check, may be wrong order
        Collections.sort(posts, POST_COMPARATOR);
        return posts;
    }
}
