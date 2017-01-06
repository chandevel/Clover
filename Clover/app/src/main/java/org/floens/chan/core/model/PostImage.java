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

import org.floens.chan.core.settings.ChanSettings;

import okhttp3.HttpUrl;

public class PostImage {
    public enum Type {
        STATIC, GIF, MOVIE
    }

    public final String originalName;
    public final HttpUrl thumbnailUrl;
    public final HttpUrl imageUrl;
    public final String filename;
    public final String extension;
    public final int imageWidth;
    public final int imageHeight;
    public final boolean spoiler;
    public final long size;

    public final Type type;

    private PostImage(Builder builder) {
        this.originalName = builder.originalName;
        this.thumbnailUrl = builder.thumbnailUrl;
        this.imageUrl = builder.imageUrl;
        this.filename = builder.filename;
        this.extension = builder.extension;
        this.imageWidth = builder.imageWidth;
        this.imageHeight = builder.imageHeight;
        this.spoiler = builder.spoiler;
        this.size = builder.size;

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

    public static final class Builder {
        private String originalName;
        private HttpUrl thumbnailUrl;
        private HttpUrl imageUrl;
        private String filename;
        private String extension;
        private int imageWidth;
        private int imageHeight;
        private boolean spoiler;
        private long size;

        public Builder() {
        }

        public Builder originalName(String originalName) {
            this.originalName = originalName;
            return this;
        }

        public Builder thumbnailUrl(HttpUrl thumbnailUrl) {
            this.thumbnailUrl = thumbnailUrl;
            return this;
        }

        public Builder imageUrl(HttpUrl imageUrl) {
            this.imageUrl = imageUrl;
            return this;
        }

        public Builder filename(String filename) {
            this.filename = filename;
            return this;
        }

        public Builder extension(String extension) {
            this.extension = extension;
            return this;
        }

        public Builder imageWidth(int imageWidth) {
            this.imageWidth = imageWidth;
            return this;
        }

        public Builder imageHeight(int imageHeight) {
            this.imageHeight = imageHeight;
            return this;
        }

        public Builder spoiler(boolean spoiler) {
            this.spoiler = spoiler;
            return this;
        }

        public Builder size(long size) {
            this.size = size;
            return this;
        }

        public PostImage build() {
            if (ChanSettings.revealImageSpoilers.get()) {
                spoiler = false;
            }

            return new PostImage(this);
        }
    }
}
