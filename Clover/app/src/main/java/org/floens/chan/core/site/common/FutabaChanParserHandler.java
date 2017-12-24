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
 */package org.floens.chan.core.site.common;

import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.PostLinkable;
import org.floens.chan.ui.theme.Theme;
import org.jsoup.nodes.Element;

public interface FutabaChanParserHandler {
    CharSequence handleParagraph(FutabaChanParser parser, Theme theme, Post.Builder post, CharSequence text, Element element);

    CharSequence handleSpan(FutabaChanParser parser, Theme theme, Post.Builder post, Element span);

    CharSequence handleTable(FutabaChanParser parser, Theme theme, Post.Builder post, Element table);

    CharSequence handleStrong(FutabaChanParser parser, Theme theme, Post.Builder post, Element strong);

    CharSequence handlePre(FutabaChanParser parser, Theme theme, Post.Builder post, Element pre);

    CharSequence handleStrike(FutabaChanParser parser, Theme theme, Post.Builder post, Element strike);

    Link handleAnchor(FutabaChanParser parser, Theme theme, Post.Builder post, Element anchor);

    class Link {
        public PostLinkable.Type type;
        public String key;
        public Object value;
    }
}
