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
package com.github.adamantcheese.chan.core.model.orm;

import com.github.adamantcheese.chan.core.model.Post;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import java.util.Objects;

@DatabaseTable(tableName = "posthide")
public class PostHide {
    @SuppressWarnings("unused")
    @DatabaseField(generatedId = true)
    public int id;

    @DatabaseField(columnName = "site")
    public int site;

    @DatabaseField(columnName = "board")
    public String board;

    @DatabaseField(columnName = "no")
    public int no;

    /**
     * Indicates whether we should hide the whole thread or just a single post (when hiding OP post)
     */
    @DatabaseField(columnName = "whole_thread")
    public boolean wholeThread;

    /**
     * Indicates whether we should just hide (grey out) or completely remove this post
     */
    @DatabaseField(columnName = "hide")
    public boolean hide;

    /**
     * Indicates whether we also should hide/remove all the current and future replies to this post
     */
    @DatabaseField(columnName = "hide_replies_to_this_post")
    public boolean hideRepliesToThisPost;

    /**
     * Thread where this post is hidden. It's being used to show the user all posts the posts that he
     * has hid/removed in a thread (so he can unhide some of them)
     */
    @DatabaseField(columnName = "thread_no")
    public int threadNo;

    private PostHide() {
    }

    public PostHide(int siteId, String boardCode, int no) {
        site = siteId;
        board = boardCode;
        this.no = no;
    }

    public static PostHide hidePost(
            Post post, Boolean wholeThread, Boolean hide, Boolean hideRepliesToThisPost
    ) {
        PostHide postHide = new PostHide();
        postHide.board = post.board.code;
        postHide.no = post.no;
        postHide.threadNo = post.opId;
        postHide.site = post.board.siteId;
        postHide.wholeThread = wholeThread;
        postHide.hide = hide;
        postHide.hideRepliesToThisPost = hideRepliesToThisPost;
        return postHide;
    }

    public static PostHide unhidePost(Post post) {
        PostHide postHide = new PostHide();

        postHide.board = post.board.code;
        postHide.no = post.no;
        postHide.site = post.board.siteId;

        return postHide;
    }

    public static PostHide unhidePost(int siteId, String boardCode, int postNo) {
        PostHide postHide = new PostHide();

        postHide.site = siteId;
        postHide.board = boardCode;
        postHide.no = postNo;

        return postHide;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PostHide that = (PostHide) o;

        return no == that.no && board.equals(that.board) && site == that.site;
    }

    @Override
    public int hashCode() {
        return Objects.hash(board, no, site);
    }
}
