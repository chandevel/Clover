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

import com.github.adamantcheese.chan.core.site.parser.CommentParser;
import com.github.adamantcheese.chan.core.site.parser.StyleRule;

import static com.github.adamantcheese.chan.core.site.parser.StyleRule.INLINE_QUOTE_COLOR;
import static com.github.adamantcheese.chan.core.site.parser.StyleRule.MONOSPACE;
import static com.github.adamantcheese.chan.core.site.parser.StyleRule.STRIKETHROUGH;
import static com.github.adamantcheese.chan.utils.AndroidUtils.sp;

public class TaimabaCommentParser
        extends CommentParser {
    public TaimabaCommentParser() {
        super();
        addDefaultRules();
        rule(new StyleRule("strike").style(STRIKETHROUGH));
        rule(new StyleRule("pre").style(MONOSPACE));
        rule(new StyleRule("blockquote").cssClass("unkfunc").style(INLINE_QUOTE_COLOR));
    }
}