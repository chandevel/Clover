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

public class PostImage {
    public enum Type {
        STATIC, GIF, MOVIE
    }

    public String originalName;
    public String thumbnailUrl;
    public String imageUrl;
    public String filename;
    public String extension;
    public int imageWidth;
    public int imageHeight;
    public boolean spoiler;
    public long size;
    public String MD5;

    public Type type;

    public PostImage(String originalName, String thumbnailUrl, String imageUrl, String filename, String extension, int imageWidth, int imageHeight, boolean spoiler, long size, String md5) {
        this.originalName = originalName;
        this.thumbnailUrl = thumbnailUrl;
        this.imageUrl = imageUrl;
        this.filename = filename;
        this.extension = extension;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.spoiler = spoiler;
        this.size = size;
        this.MD5 = md5;

        switch (extension) {
            case "gif":
                type = Type.GIF;
                break;
            case "webm":
                type = Type.MOVIE;
                break;
            default:
                type = Type.STATIC;
                break;
        }
    }
}
