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
package org.floens.chan.core.site.sites.vichan;

import org.floens.chan.core.site.parser.CommentParser;
import org.floens.chan.core.site.parser.StyleRule;

import java.util.regex.Pattern;

import static org.floens.chan.core.site.parser.StyleRule.tagRule;

public class ViChanCommentParser extends CommentParser {
    public ViChanCommentParser() {
        setQuotePattern(Pattern.compile(".*#(\\d+)"));
        setFullQuotePattern(Pattern.compile("/(\\w+)/\\w+/(\\d+)\\.html#(\\d+)"));

        rule(tagRule("p").cssClass("quote").color(StyleRule.Color.INLINE_QUOTE).linkify());
    }
}
