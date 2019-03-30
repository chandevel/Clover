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

import org.floens.chan.core.model.Post;

@DatabaseTable(tableName = "posthide")
public class PostHide {
    @DatabaseField(generatedId = true)
    public int id;

    @DatabaseField(columnName = "site")
    public int site;

    @DatabaseField
    public String board;

    @DatabaseField
    public int no;

    /**
     * Indicates whether we should hide the whole thread or just a single post (when hiding OP post)
     * */
    @DatabaseField(columnName = "whole_thread")
    public boolean wholeThread;

    public PostHide() {
    }

    public static PostHide fromPost(Post post, Boolean wholeThread) {
        PostHide hide = new PostHide();
        hide.board = post.board.code;
        hide.no = post.no;
        hide.site = post.board.siteId;
        hide.wholeThread = wholeThread;
        return hide;
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
        return result;
    }

    public boolean equalsPost(Post post) {
        return post.no == no && board.equals(post.board.code) && post.board.siteId == site;
    }
}
