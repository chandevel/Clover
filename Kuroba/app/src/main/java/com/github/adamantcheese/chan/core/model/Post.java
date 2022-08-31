/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.core.model;

import android.graphics.Color;
import android.text.*;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.features.embedding.Embeddable;
import com.github.adamantcheese.chan.ui.text.post_linkables.PostLinkable;
import com.github.adamantcheese.chan.ui.text.post_linkables.QuoteLinkable;
import com.vdurmont.emoji.EmojiParser;

import org.jsoup.parser.Parser;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import okhttp3.Call;

public class Post
        implements Comparable<Post>, Cloneable, Embeddable {
    public final String boardCode;

    public final Board board;

    public final int no;

    public final boolean isOP;

    public final String name;

    public final Spannable subject;

    /**
     * Unix timestamp, in seconds.
     */
    public final long time;

    public final List<PostImage> images = new CopyOnWriteArrayList<>();

    public final String tripcode;

    public final String id;

    public final int opId;

    public final String capcode;

    public final List<PostHttpIcon> httpIcons;

    public final boolean isSavedReply;

    public final int filterHighlightedColor;

    public final boolean filterStub;

    public final boolean filterRemove;

    public final boolean filterWatch;

    public final boolean filterReplies;

    public final boolean filterOnlyOP;

    public final boolean filterSaved;

    /**
     * This post replies to the these post numbers.
     */
    public final Set<Integer> repliesTo;

    public final CharSequence subjectSpan;

    public final CharSequence nameTripcodeIdCapcodeSpan;

    /**
     * These post numbers replied to this post.
     */
    public final List<Integer> repliesFrom = new CopyOnWriteArrayList<>();

    public Spanned comment;

    // These members may only mutate on the main thread.
    public boolean sticky;
    public boolean closed;
    public boolean archived;
    public boolean deleted;
    public int replies;
    public int imagesCount;
    public int uniqueIps;
    public long lastModified;
    public String title = "";

    private final List<Call> embedCalls = new ArrayList<>();

    public int compareTo(Post p) {
        return -Long.compare(this.time, p.time);
    }

    private Post(Builder builder) {
        board = builder.board;
        boardCode = builder.board.code;
        no = builder.no;

        isOP = builder.op;
        replies = builder.replies;
        imagesCount = builder.imagesCount;
        uniqueIps = builder.uniqueIps;
        lastModified = builder.lastModified;
        sticky = builder.sticky;
        closed = builder.closed;
        archived = builder.archived;

        subject = builder.subject;
        name = builder.name;
        comment = new SpannableString(builder.comment);
        tripcode = builder.tripcode;

        time = builder.unixTimestampSeconds;
        if (builder.images != null) {
            images.addAll(builder.images);
        }

        if (builder.httpIcons != null) {
            httpIcons = Collections.unmodifiableList(builder.httpIcons);
        } else {
            httpIcons = null;
        }

        id = builder.posterId;
        opId = builder.opId;
        capcode = builder.moderatorCapcode;

        filterHighlightedColor = builder.filterHighlightedColor;
        filterStub = builder.filterStub;
        filterRemove = builder.filterRemove;
        filterWatch = builder.filterWatch;
        filterReplies = builder.filterReplies;
        filterOnlyOP = builder.filterOnlyOP;
        filterSaved = builder.filterSaved;

        isSavedReply = builder.isSavedReply;
        subjectSpan = builder.subjectSpan;
        nameTripcodeIdCapcodeSpan = builder.nameTripcodeIdCapcodeSpan;

        repliesTo = Collections.unmodifiableSet(builder.repliesToNos);
    }

    /**
     * Return the first image, or {@code null} if post has no images.
     *
     * @return the first image, or {@code null}
     */
    public PostImage image() {
        return images.isEmpty() ? null : images.get(0);
    }

    public boolean hasFilterParameters() {
        return filterRemove || filterHighlightedColor != 0 || filterReplies || filterStub;
    }

    public PostLinkable<?>[] getLinkables() {
        return comment.getSpans(0, comment.length(), PostLinkable.class);
    }

    public QuoteLinkable[] getQuoteLinkables() {
        return comment.getSpans(0, comment.length(), QuoteLinkable.class);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Post post = (Post) o;
        //@formatter:off
        return no == post.no
                && isOP == post.isOP
                && time == post.time
                && opId == post.opId
                && isSavedReply == post.isSavedReply
                && filterHighlightedColor == post.filterHighlightedColor
                && filterStub == post.filterStub
                && filterRemove == post.filterRemove
                && filterWatch == post.filterWatch
                && filterReplies == post.filterReplies
                && filterOnlyOP == post.filterOnlyOP
                && filterSaved == post.filterSaved
                && sticky == post.sticky
                && closed == post.closed
                && archived == post.archived
                && lastModified == post.lastModified
                && Objects.equals(boardCode, post.boardCode)
                && Objects.equals(board, post.board)
                && Objects.equals(name, post.name)
                && Objects.equals(comment, post.comment)
                && Objects.equals(subject, post.subject)
                && Objects.equals(images, post.images)
                && Objects.equals(tripcode, post.tripcode)
                && Objects.equals(id, post.id)
                && Objects.equals(capcode, post.capcode)
                && Objects.equals(httpIcons, post.httpIcons)
                && Objects.equals(repliesTo, post.repliesTo)
                && Objects.equals(deleted, post.deleted)
                && Objects.equals(repliesFrom, post.repliesFrom)
                && Objects.equals(title, post.title);
        //@formatter:on
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                boardCode,
                board,
                no,
                isOP,
                name,
                comment,
                subject,
                time,
                images,
                tripcode,
                id,
                opId,
                capcode,
                httpIcons,
                isSavedReply,
                filterHighlightedColor,
                filterStub,
                filterRemove,
                filterWatch,
                filterReplies,
                filterOnlyOP,
                filterSaved,
                repliesTo,
                deleted,
                repliesFrom,
                sticky,
                closed,
                archived,
                lastModified,
                title
        );
    }

    @Override
    @NonNull
    public String toString() {
        return "[no = "
                + no
                + ", boardCode = "
                + board.code
                + ", siteId = "
                + board.siteId
                + ", comment = "
                + comment
                + "]";
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @NonNull
    @Override
    public Post clone() {
        Post clone = new Builder()
                .board(board)
                .no(no)
                .opId(opId)
                .op(isOP)
                .replies(replies)
                .images(imagesCount)
                .uniqueIps(uniqueIps)
                .sticky(sticky)
                .archived(archived)
                .lastModified(lastModified)
                .closed(closed)
                .subject(subject)
                .name(name)
                .comment(comment)
                .tripcode(tripcode)
                .setUnixTimestampSeconds(time)
                .images(images)
                .posterId(id)
                .moderatorCapcode(capcode)
                .setHttpIcons(httpIcons)
                .filter(
                        filterHighlightedColor,
                        filterStub,
                        filterRemove,
                        filterWatch,
                        filterReplies,
                        filterOnlyOP,
                        filterSaved
                )
                .isSavedReply(isSavedReply)
                .spans(subjectSpan, nameTripcodeIdCapcodeSpan)
                .repliesTo(repliesTo)
                .build();
        clone.repliesFrom.addAll(repliesFrom);
        clone.title = title;
        clone.deleted = deleted;
        return clone;
    }

    @Override
    public Spanned getEmbeddableText() {
        return comment;
    }

    @Override
    public void setEmbeddableText(Spanned text) {
        comment = text;
    }

    @Override
    public void addEmbedCall(Call call) {
        embedCalls.add(call);
    }

    @Override
    public List<Call> getEmbedCalls() {
        return embedCalls;
    }

    @Override
    public void addImageObjects(List<PostImage> images) {
        for (PostImage p : images) {
            if (this.images.contains(p)) continue;
            if (images.size() >= 5) return;
            this.images.add(p);
        }
    }

    @SuppressWarnings("unused")
    public String threadUrl() {
        return board.site.resolvable().desktopUrl(Loadable.forThread(board, opId, title, false), no);
    }

    @SuppressWarnings("UnusedReturnValue")
    public static final class Builder
            implements Cloneable {
        public Board board;
        public int no = -1;
        public int opId = -1;

        public boolean op;
        public int replies = -1;
        public int imagesCount = -1;
        public int uniqueIps = -1;
        public boolean sticky;
        public boolean closed;
        public boolean archived;
        public long lastModified = -1L;

        private Spannable subject = new SpannableString("");
        private String name = "";
        public Spannable comment = new SpannableString("");
        public String tripcode = "";

        public long unixTimestampSeconds = -1L;
        public List<PostImage> images = new CopyOnWriteArrayList<>();

        public List<PostHttpIcon> httpIcons;

        public String posterId = "";
        public String moderatorCapcode = "";

        public int idColor;

        public int filterHighlightedColor;
        public boolean filterStub;
        public boolean filterRemove;
        public boolean filterWatch;
        public boolean filterReplies;
        public boolean filterOnlyOP;
        public boolean filterSaved;
        public boolean isSavedReply;

        public CharSequence subjectSpan;
        public CharSequence nameTripcodeIdCapcodeSpan;

        public final Set<Integer> repliesToNos = new HashSet<>();

        public Builder() {
        }

        public Builder board(Board board) {
            this.board = board;
            return this;
        }

        public Builder no(int no) {
            this.no = no;
            return this;
        }

        public Builder idColor(int idColor) {
            this.idColor = idColor;
            return this;
        }

        public Builder opId(int opId) {
            this.opId = opId;
            return this;
        }

        public Builder op(boolean op) {
            this.op = op;
            return this;
        }

        public Builder replies(int replies) {
            this.replies = replies;
            return this;
        }

        public Builder images(int images) {
            this.imagesCount = images;
            return this;
        }

        public Builder uniqueIps(int uniqueIps) {
            this.uniqueIps = uniqueIps;
            return this;
        }

        public Builder sticky(boolean sticky) {
            this.sticky = sticky;
            return this;
        }

        public Builder archived(boolean archived) {
            this.archived = archived;
            return this;
        }

        public Builder lastModified(long lastModified) {
            this.lastModified = lastModified;
            return this;
        }

        public Builder closed(boolean closed) {
            this.closed = closed;
            return this;
        }

        public Builder subject(CharSequence subject) {
            this.subject = new SpannableString(Parser.unescapeEntities(subject.toString(), false));
            return this;
        }

        public Spannable getSubject() {
            return subject;
        }

        public Builder name(String name) {
            if (ChanSettings.enableEmoji.get()) {
                this.name = EmojiParser.parseToUnicode(name);
            } else {
                this.name = name;
            }
            this.name = Parser.unescapeEntities(this.name, false);
            return this;
        }

        public String getName() {
            return name;
        }

        public Builder comment(CharSequence comment) {
            this.comment = new SpannableString(comment);
            return this;
        }

        public Builder tripcode(String tripcode) {
            this.tripcode = tripcode;
            return this;
        }

        public Builder setUnixTimestampSeconds(long unixTimestampSeconds) {
            this.unixTimestampSeconds = unixTimestampSeconds;
            return this;
        }

        /**
         * Add images to this post
         *
         * @param images A list of images to add
         * @return This builder
         */
        public Builder images(List<PostImage> images) {
            this.images.addAll(images);

            return this;
        }

        public Builder posterId(String posterId) {
            this.posterId = posterId;

            // Stolen from the 4chan extension
            int hash = 0;
            for (int i = 0; i < posterId.length(); i++) {
                hash = (hash << 5) - hash + posterId.charAt(i);
            }
            hash = hash >>> 8;

            this.idColor = Color.BLACK | hash;

            return this;
        }

        public Builder moderatorCapcode(String moderatorCapcode) {
            this.moderatorCapcode = moderatorCapcode;
            return this;
        }

        public Builder addHttpIcon(PostHttpIcon httpIcon) {
            if (httpIcons == null) {
                httpIcons = new ArrayList<>();
            }
            httpIcons.add(httpIcon);

            return this;
        }

        public Builder setHttpIcons(List<PostHttpIcon> httpIcons) {
            this.httpIcons = httpIcons;
            return this;
        }

        public Builder filter(
                int highlightedColor,
                boolean stub,
                boolean remove,
                boolean watch,
                boolean filterReplies,
                boolean onlyOnOp,
                boolean filterSaved
        ) {
            // for any filter effect, OR it with any previous filters; the highlighted color will be the last one in the list
            filterHighlightedColor = highlightedColor;
            filterStub = filterStub | stub;
            filterRemove = filterRemove | remove;
            filterWatch = filterWatch | watch;
            this.filterReplies = this.filterReplies | filterReplies;
            filterOnlyOP = filterOnlyOP | onlyOnOp;
            this.filterSaved = this.filterSaved | filterSaved;
            return this;
        }

        public Builder isSavedReply(boolean isSavedReply) {
            this.isSavedReply = isSavedReply;
            return this;
        }

        public Builder spans(CharSequence subjectSpan, CharSequence nameTripcodeIdCapcodeSpan) {
            this.subjectSpan = subjectSpan;
            this.nameTripcodeIdCapcodeSpan = nameTripcodeIdCapcodeSpan;
            return this;
        }

        /**
         * Specify that this post replies to these post number
         *
         * @param repliesToNos The post numbers that are being replied to
         */
        public Builder repliesTo(Set<Integer> repliesToNos) {
            this.repliesToNos.addAll(repliesToNos);
            return this;
        }

        public List<QuoteLinkable> getQuoteLinkables() {
            List<QuoteLinkable> linkables = new ArrayList<>();
            Collections.addAll(linkables, comment.getSpans(0, comment.length(), QuoteLinkable.class));
            return linkables;
        }

        public String threadUrl() {
            return board.site.resolvable().desktopUrl(Loadable.forThread(board, opId, "", false), no);
        }

        @SuppressWarnings("MethodDoesntCallSuperMethod")
        @NonNull
        @Override
        public Post.Builder clone() {
            return new Builder()
                    .board(board)
                    .no(no)
                    .opId(opId)
                    .op(op)
                    .replies(replies)
                    .images(imagesCount)
                    .uniqueIps(uniqueIps)
                    .sticky(sticky)
                    .closed(closed)
                    .archived(archived)
                    .lastModified(lastModified)
                    .subject(subject)
                    .name(name)
                    .comment(comment)
                    .tripcode(tripcode)
                    .setUnixTimestampSeconds(unixTimestampSeconds)
                    .images(images)
                    .posterId(posterId)
                    .moderatorCapcode(moderatorCapcode)
                    .setHttpIcons(httpIcons)
                    .filter(
                            filterHighlightedColor,
                            filterStub,
                            filterRemove,
                            filterWatch,
                            filterReplies,
                            filterOnlyOP,
                            filterSaved
                    )
                    .isSavedReply(isSavedReply)
                    .spans(subjectSpan, nameTripcodeIdCapcodeSpan)
                    .repliesTo(repliesToNos);
        }

        public Post build() {
            if (board == null || no < 0 || opId < 0 || unixTimestampSeconds < 0) {
                throw new IllegalArgumentException("Post data not complete");
            }

            return new Post(this);
        }
    }
}
