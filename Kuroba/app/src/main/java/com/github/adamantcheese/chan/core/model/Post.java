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

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;

import com.github.adamantcheese.chan.core.model.orm.Board;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Contains all data needed to represent a single post.<br>
 * All {@code final} fields are thread-safe.
 */
public class Post
        implements Comparable<Post> {
    public final String boardId;

    public final Board board;

    public final int no;

    public final boolean isOP;

    public final String name;

    public final CharSequence comment;

    public final String subject;

    /**
     * Unix timestamp, in seconds.
     */
    public final long time;

    public final List<PostImage> images;

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
     * This post replies to the these ids.
     */
    public final Set<Integer> repliesTo;

    public final List<PostLinkable> linkables;

    public final CharSequence subjectSpan;

    public final CharSequence nameTripcodeIdCapcodeSpan;

    /**
     * This post has been deleted (the server isn't sending it anymore).
     * <p><b>This boolean is modified in worker threads, use {@code .get()} to access it.</b>
     */
    public final AtomicBoolean deleted = new AtomicBoolean(false);

    /**
     * These ids replied to this post.
     * <p><b>Manual synchronization is needed, since this list can be modified from any thread.
     * Wrap all accesses in a {@code synchronized} block.</b>
     */
    public final List<Integer> repliesFrom = new ArrayList<>();

    // These members may only mutate on the main thread.
    private boolean sticky;
    private boolean closed;
    private boolean archived;
    private int replies;
    private int imagesCount;
    private int uniqueIps;
    private long lastModified;
    private String title = "";

    public int compareTo(Post p) {
        return -Long.compare(this.time, p.time);
    }

    private Post(Builder builder) {
        board = builder.board;
        boardId = builder.board.code;
        no = builder.id;

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
        comment = builder.comment;
        tripcode = builder.tripcode;

        time = builder.unixTimestampSeconds;
        if (builder.images == null) {
            images = Collections.emptyList();
        } else {
            images = Collections.unmodifiableList(builder.images);
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

        linkables = Collections.unmodifiableList(new ArrayList<>(builder.linkables));
        repliesTo = Collections.unmodifiableSet(builder.repliesToIds);
    }

    @AnyThread
    public boolean isSticky() {
        return sticky;
    }

    @MainThread
    public void setSticky(boolean sticky) {
        this.sticky = sticky;
    }

    @MainThread
    public boolean isClosed() {
        return closed;
    }

    @MainThread
    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    @MainThread
    public boolean isArchived() {
        return archived;
    }

    @MainThread
    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    @MainThread
    public int getReplies() {
        return replies;
    }

    @MainThread
    public void setReplies(int replies) {
        this.replies = replies;
    }

    @MainThread
    public int getImagesCount() {
        return imagesCount;
    }

    @MainThread
    public void setImagesCount(int imagesCount) {
        this.imagesCount = imagesCount;
    }

    @MainThread
    public int getUniqueIps() {
        return uniqueIps;
    }

    @MainThread
    public void setUniqueIps(int uniqueIps) {
        this.uniqueIps = uniqueIps;
    }

    @MainThread
    public long getLastModified() {
        return lastModified;
    }

    @MainThread
    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    @MainThread
    public String getTitle() {
        return title;
    }

    @MainThread
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Return the first image, or {@code null} if post has no images.
     *
     * @return the first image, or {@code null}
     */
    @MainThread
    public PostImage image() {
        return images.isEmpty() ? null : images.get(0);
    }

    @MainThread
    public boolean hasFilterParameters() {
        return filterRemove || filterHighlightedColor != 0 || filterReplies || filterStub;
    }

    @Override
    public int hashCode() {
        int commentTotal = 0;
        for (char c : comment.toString().toCharArray()) {
            commentTotal += c;
        }
        return 31 * no + 31 * board.code.hashCode() + 31 * board.siteId + 31 * (deleted.get() ? 1 : 0)
                + 31 * commentTotal;
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }

        if (other == this) {
            return true;
        }

        if (this.getClass() != other.getClass()) {
            return false;
        }

        Post otherPost = (Post) other;

        //@formatter:off
        return this.no == otherPost.no
                && this.board.code.equals(otherPost.board.code)
                && this.board.siteId == otherPost.board.siteId
                && this.deleted.get() == otherPost.deleted.get()
                && this.comment.toString().equals(otherPost.comment.toString());
        //@formatter:on
    }

    @Override
    public String toString() {
        return "[no = " + no + ", boardCode = " + board.code + ", siteId = " + board.siteId + ", comment = " + comment
                + "]";
    }

    public static final class Builder {
        public Board board;
        public int id = -1;
        public int opId = -1;

        public boolean op;
        public int replies = -1;
        public int imagesCount = -1;
        public int uniqueIps = -1;
        public boolean sticky;
        public boolean closed;
        public boolean archived;
        public long lastModified = -1L;

        public String subject = "";
        public String name = "";
        public CharSequence comment = "";
        public String tripcode = "";

        public long unixTimestampSeconds = -1L;
        public List<PostImage> images;

        public List<PostHttpIcon> httpIcons;

        public String posterId = "";
        public String moderatorCapcode = "";

        public int idColor;
        public boolean isLightColor;

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

        private Set<PostLinkable> linkables = new HashSet<>();
        private Set<Integer> repliesToIds = new HashSet<>();

        public Builder() {
        }

        public Builder board(Board board) {
            this.board = board;
            return this;
        }

        public Builder id(int id) {
            this.id = id;
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

        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder comment(CharSequence comment) {
            this.comment = comment;
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

        public Builder images(List<PostImage> images) {
            synchronized (this) {
                if (this.images == null) {
                    this.images = new ArrayList<>(images.size());
                }

                this.images.addAll(images);
            }

            return this;
        }

        public Builder posterId(String posterId) {
            this.posterId = posterId;

            // Stolen from the 4chan extension
            int hash = this.posterId.hashCode();

            int r = (hash >> 24) & 0xff;
            int g = (hash >> 16) & 0xff;
            int b = (hash >> 8) & 0xff;

            this.idColor = (0xff << 24) + (r << 16) + (g << 8) + b;
            this.isLightColor = (r * 0.299f) + (g * 0.587f) + (b * 0.114f) > 125f;

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
            filterHighlightedColor = highlightedColor;
            filterStub = stub;
            filterRemove = remove;
            filterWatch = watch;
            this.filterReplies = filterReplies;
            filterOnlyOP = onlyOnOp;
            this.filterSaved = filterSaved;
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

        public Builder addLinkable(PostLinkable linkable) {
            synchronized (this) {
                linkables.add(linkable);
                return this;
            }
        }

        public Builder linkables(List<PostLinkable> linkables) {
            synchronized (this) {
                this.linkables = new HashSet<>(linkables);
                return this;
            }
        }

        public List<PostLinkable> getLinkables() {
            synchronized (this) {
                List<PostLinkable> result = new ArrayList<>();
                if (linkables != null) {
                    result.addAll(linkables);
                }
                return result;
            }
        }

        public Builder addReplyTo(int postId) {
            repliesToIds.add(postId);
            return this;
        }

        public Builder repliesTo(Set<Integer> repliesToIds) {
            this.repliesToIds = repliesToIds;
            return this;
        }

        public Post build() {
            if (board == null || id < 0 || opId < 0 || unixTimestampSeconds < 0 || comment == null) {
                throw new IllegalArgumentException("Post data not complete");
            }

            return new Post(this);
        }
    }
}
