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

import android.text.SpannableString;

import org.floens.chan.core.model.Post;
import org.floens.chan.core.site.parser.CommentParser;
import org.floens.chan.core.site.parser.CommentParserHelper;
import org.floens.chan.ui.span.ForegroundColorSpanHashed;
import org.floens.chan.ui.theme.Theme;
import org.jsoup.nodes.Element;

import java.util.regex.Pattern;

public class ViChanCommentParser extends CommentParser {
    public ViChanCommentParser() {
        setQuotePattern(Pattern.compile(".*#(\\d+)"));
        setFullQuotePattern(Pattern.compile("/(\\w+)/\\w+/(\\d+)\\.html#(\\d+)"));
    }

    @Override
    public CharSequence handleParagraph(Theme theme, Post.Builder post, CharSequence text, Element element) {
        if (element.hasClass("quote")) {
            SpannableString res = span(text, new ForegroundColorSpanHashed(theme.inlineQuoteColor));
            CommentParserHelper.detectLinks(theme, post, res.toString(), res);
            return res;
        } else {
            return text;
        }
    }
}
