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
public class Post {
    public final String boardId;

    public final Board board;

    public final int no;

    public final boolean isOP;

//    public final String date;

    public final String name;

    public final CharSequence comment;

    public final String subject;

    public final PostImage image;

    public final String tripcode;

    public final String id;

    public final String capcode;

    public final String country;

    public final String countryName;

    /**
     * Unix timestamp, in seconds.
     */
    public final long time;

    public final String countryUrl;

    public final boolean isSavedReply;

    public final int filterHighlightedColor;

    public final boolean filterStub;

    public final boolean filterRemove;

    /**
     * This post replies to the these ids.
     */
    public final Set<Integer> repliesTo;

    public final List<PostLinkable> linkables;

    public final CharSequence subjectSpan;

    public final CharSequence nameTripcodeIdCapcodeSpan;

    // These members may only mutate on the main thread.
    public boolean sticky = false;
    public boolean closed = false;
    public boolean archived = false;
    public int replies = -1;
    public int images = -1;
    public int uniqueIps = 1;
    public String title = "";

    // Atomic, any thread.
    public final AtomicBoolean deleted = new AtomicBoolean(false);

    /**
     * These ids replied to this post.<br>
     * <b>synchronize on this when accessing.</b>
     */
    public final List<Integer> repliesFrom = new ArrayList<>();

    private Post(Builder builder) {
        board = builder.board;
        boardId = builder.board.code;
        no = builder.id;

        isOP = builder.op;
        replies = builder.replies;
        images = builder.images;
        uniqueIps = builder.uniqueIps;
        sticky = builder.sticky;
        closed = builder.closed;
        archived = builder.archived;

        subject = builder.subject;
        name = builder.name;
        comment = builder.comment;
        tripcode = builder.tripcode;

        time = builder.unixTimestampSeconds;
        image = builder.image;

        country = builder.countryCode;
        countryName = builder.countryName;
        countryUrl = builder.countryUrl;

        id = builder.posterId;
        capcode = builder.moderatorCapcode;

        filterHighlightedColor = builder.filterHighlightedColor;
        filterStub = builder.filterStub;
        filterRemove = builder.filterRemove;

        isSavedReply = builder.isSavedReply;

        subjectSpan = builder.subjectSpan;
        nameTripcodeIdCapcodeSpan = builder.nameTripcodeIdCapcodeSpan;

        linkables = Collections.unmodifiableList(builder.linkables);
        repliesTo = Collections.unmodifiableSet(builder.repliesToIds);
    }

    public static final class Builder {
        public Board board;
        public int id = -1;
        public int opId = -1;

        public boolean op;
        public int replies;
        public int images;
        public int uniqueIps;
        public boolean sticky;
        public boolean closed;
        public boolean archived;

        public String subject = "";
        public String name = "";
        public CharSequence comment = "";
        public String tripcode = "";

        public long unixTimestampSeconds = -1;
        public PostImage image;

        public String countryCode;
        public String countryName;
        public String countryUrl;

        public String posterId = "";
        public String moderatorCapcode = "";

        public int filterHighlightedColor;
        public boolean filterStub;
        public boolean filterRemove;

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
            this.images = images;
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

        public Builder image(PostImage image) {
            this.image = image;
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

        public Builder country(String countryCode, String countryName, String countryUrl) {
            this.countryCode = countryCode;
            this.countryName = countryName;
            this.countryUrl = countryUrl;
            return this;
        }

        public Builder filter(int highlightedColor, boolean stub, boolean remove) {
            filterHighlightedColor = highlightedColor;
            filterStub = stub;
            filterRemove = remove;
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
