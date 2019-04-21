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
package org.floens.chan.core.model.orm;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.floens.chan.core.database.DatabaseHelper;
import org.floens.chan.core.model.Post;

@DatabaseTable(tableName = DatabaseHelper.POST_HIDE_TABLE_NAME)
public class PostHide {
    @DatabaseField(generatedId = true)
    public int id;

    @DatabaseField(columnName = SITE_COLUMN_NAME)
    public int site;

    @DatabaseField(columnName = BOARD_COLUMN_NAME)
    public String board;

    @DatabaseField(columnName = NO_COLUMN_NAME)
    public int no;

    /**
     * Indicates whether we should hide the whole thread or just a single post (when hiding OP post)
     */
    @DatabaseField(columnName = WHOLE_THREAD_COLUMN_NAME)
    public boolean wholeThread;

    /**
     * Indicates whether we should just hide (grey out) or completely remove this post
     */
    @DatabaseField(columnName = HIDE_COLUMN)
    public boolean hide;

    @DatabaseField(columnName = HIDE_REPLIES_TO_THIS_POST_COLUMN)
    public boolean hideRepliesToThisPost;

    public static final String SITE_COLUMN_NAME = "site";
    public static final String BOARD_COLUMN_NAME = "board";
    public static final String NO_COLUMN_NAME = "no";
    public static final String WHOLE_THREAD_COLUMN_NAME = "whole_thread";
    public static final String HIDE_COLUMN = "hide";
    public static final String HIDE_REPLIES_TO_THIS_POST_COLUMN = "hide_replies_to_this_post";

    public PostHide() {
    }

    public static PostHide fromPost(Post post, Boolean wholeThread, Boolean hide, Boolean hideRepliesToThisPost) {
        PostHide postHide = new PostHide();
        postHide.board = post.board.code;
        postHide.no = post.no;
        postHide.site = post.board.siteId;
        postHide.wholeThread = wholeThread;
        postHide.hide = hide;
        postHide.hideRepliesToThisPost = hideRepliesToThisPost;
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
        int result = board.hashCode();
        result = 31 * result + no;
        result = 31 * result + site;
        return result;
    }

    public boolean equalsPost(Post post) {
        return post.no == no && board.equals(post.board.code) && post.board.siteId == site;
    }
}
