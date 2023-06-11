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
package com.github.adamantcheese.chan.core.site.common.vichan;

import static com.github.adamantcheese.chan.features.html_styling.impl.CommonThemedStyleActions.INLINE_QUOTE_COLOR;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.site.parser.PostParser;
import com.github.adamantcheese.chan.core.site.parser.comment_action.ChanCommentAction;
import com.github.adamantcheese.chan.features.html_styling.impl.HtmlTagAction;
import com.github.adamantcheese.chan.features.theme.Theme;

public class VichanCommentAction
        extends ChanCommentAction {

    @Override
    public HtmlTagAction addSpecificActions(
            Theme theme, Post.Builder post, PostParser.PostParserCallback callback
    ) {
        HtmlTagAction base = super.addSpecificActions(theme, post, callback);
        HtmlTagAction newAction = new HtmlTagAction();
        newAction.mapTagToRule("p", "quote", INLINE_QUOTE_COLOR.with(theme));
        return base.mergeWith(newAction);
    }
}
