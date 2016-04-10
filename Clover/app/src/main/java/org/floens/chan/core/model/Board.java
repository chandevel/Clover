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

import android.text.TextUtils;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable
public class Board {
    public Board() {
    }

    public Board(String name, String code, boolean saved, boolean workSafe) {
        this.name = name;
        this.code = code;
        this.saved = saved;
        this.workSafe = workSafe;
    }

    @DatabaseField(generatedId = true)
    public int id;

    // named key for legacy support
    @DatabaseField(columnName = "key")
    public String name;

    // named value for legacy support
    @DatabaseField(columnName = "value")
    public String code;

    /**
     * True if this board appears in the dropdown, false otherwise.
     */
    @DatabaseField
    public boolean saved = false;

    @DatabaseField
    public int order;

    @DatabaseField
    public boolean workSafe = false;

    @DatabaseField
    public int perPage = -1;

    @DatabaseField
    public int pages = -1;

    @DatabaseField
    public int maxFileSize = -1;

    @DatabaseField
    public int maxWebmSize = -1;

    @DatabaseField
    public int maxCommentChars = -1;

    @DatabaseField
    public int bumpLimit = -1;

    @DatabaseField
    public int imageLimit = -1;

    @DatabaseField
    public int cooldownThreads = -1;

    @DatabaseField
    public int cooldownReplies = -1;

    @DatabaseField
    public int cooldownImages = -1;

    @DatabaseField
    public int cooldownRepliesIntra = -1;

    @DatabaseField
    public int cooldownImagesIntra = -1;

    @DatabaseField
    public boolean spoilers = false;

    @DatabaseField
    public int customSpoilers = -1;

    @DatabaseField
    public boolean userIds = false;

    @DatabaseField
    public boolean codeTags = false;

    @DatabaseField
    public boolean preuploadCaptcha = false;

    @DatabaseField
    public boolean countryFlags = false;

    @Deprecated
    @DatabaseField
    public boolean trollFlags = false;

    @DatabaseField
    public boolean mathTags = false;

    @DatabaseField
    public String description;

    public boolean finish() {
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(code) || perPage < 0 || pages < 0)
            return false;

        if (cooldownThreads < 0 || cooldownReplies < 0 || cooldownImages < 0 || cooldownRepliesIntra < 0 || cooldownImagesIntra < 0) {
            return false;
        }

        return true;
    }

    @Override
    public String toString() {
        return name;
    }


}
