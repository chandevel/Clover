/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
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
package org.floens.chan.core.model;

import android.support.annotation.MainThread;

import org.floens.chan.core.model.orm.Board;

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
public class Post implements Comparable<Post> {
    public final String boardId;

    public final Board board;

    public final int no;

    public final boolean isOP;

//    public final String date;

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

    public final String capcode;

    public final List<PostHttpIcon> httpIcons;

    public final boolean isSavedReply;

    public final int filterHighlightedColor;

    public final boolean filterStub;

    public final boolean filterRemove;

    public final boolean filterWatch;

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
        if (this.time < p.time) {
            return 1;
        } else if (this.time > p.time) {
            return -1;
        } else {
            return 0;
        }
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
        capcode = builder.moderatorCapcode;

        filterHighlightedColor = builder.filterHighlightedColor;
        filterStub = builder.filterStub;
        filterRemove = builder.filterRemove;
        filterWatch = builder.filterWatch;

        isSavedReply = builder.isSavedReply;

        subjectSpan = builder.subjectSpan;
        nameTripcodeIdCapcodeSpan = builder.nameTripcodeIdCapcodeSpan;

        linkables = Collections.unmodifiableList(builder.linkables);
        repliesTo = Collections.unmodifiableSet(builder.repliesToIds);
    }

    @MainThread
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

        public int filterHighlightedColor;
        public boolean filterStub;
        public boolean filterRemove;
        public boolean filterWatch;

        public boolean isSavedReply;

        public CharSequence subjectSpan;
        public CharSequence nameTripcodeIdCapcodeSpan;

        private List<PostLinkable> linkables = new ArrayList<>();
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

        public Builder comment(String comment) {
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
            if (this.images == null) {
                this.images = new ArrayList<>(images.size());
            }

            this.images.addAll(images);

            return this;
        }

        public Builder posterId(String posterId) {
            this.posterId = posterId;
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

        public Builder filter(int highlightedColor, boolean stub, boolean remove, boolean watch) {
            filterHighlightedColor = highlightedColor;
            filterStub = stub;
            filterRemove = remove;
            filterWatch = watch;
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
            linkables.add(linkable);
            return this;
        }

        public Builder addReplyTo(int postId) {
            repliesToIds.add(postId);
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
