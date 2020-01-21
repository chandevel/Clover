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
package com.github.adamantcheese.chan.core.site.common.taimaba;

import com.github.adamantcheese.chan.core.model.PostLinkable;
import com.github.adamantcheese.chan.core.site.parser.CommentParser;
import com.github.adamantcheese.chan.core.site.parser.StyleRule;

import java.util.regex.Pattern;

import static com.github.adamantcheese.chan.utils.AndroidUtils.sp;

public class TaimabaCommentParser extends CommentParser {
    public TaimabaCommentParser() {
        //addDefaultRules();
        //not sure if the quote patterns are even correct
        setQuotePattern(Pattern.compile(".*(\\d+)"));
        setFullQuotePattern(Pattern.compile("/(\\w+)/\\w+/(\\w+)/(\\d+)#(\\d+)"));
        //from default
        rule(StyleRule.tagRule("span").cssClass("spoiler").link(PostLinkable.Type.SPOILER));
        rule(StyleRule.tagRule("i").italic());
        rule(StyleRule.tagRule("b").bold());
        //custom
        rule(StyleRule.tagRule("s").strikeThrough());
        rule(StyleRule.tagRule("pre").monospace().size(sp(12f)));
        rule(StyleRule.tagRule("blockquote").cssClass("unkfunc").foregroundColor(StyleRule.ForegroundColor.INLINE_QUOTE).linkify());
    }
}