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
package com.github.adamantcheese.chan.core.site.parser.comment_action;

import static com.github.adamantcheese.chan.features.html_styling.impl.CommonStyleActions.CHOMP;
import static com.github.adamantcheese.chan.features.html_styling.impl.CommonStyleActions.MONOSPACE;
import static com.github.adamantcheese.chan.features.html_styling.impl.CommonStyleActions.NULLIFY;
import static com.github.adamantcheese.chan.features.html_styling.impl.CommonStyleActions.SRC_ATTR;
import static com.github.adamantcheese.chan.features.html_styling.impl.CommonThemedStyleActions.CODE;
import static com.github.adamantcheese.chan.features.html_styling.impl.CommonThemedStyleActions.INLINE_QUOTE_COLOR;
import static com.github.adamantcheese.chan.features.html_styling.impl.CommonThemedStyleActions.SPOILER;
import static com.github.adamantcheese.chan.features.html_styling.impl.PostThemedStyleActions.*;

import androidx.annotation.CallSuper;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.site.parser.PostParser;
import com.github.adamantcheese.chan.features.html_styling.impl.HtmlTagAction;
import com.github.adamantcheese.chan.features.theme.Theme;

/**
 * This has specific rules for chan boards (see the PostThemedStyleAction classes) that make display a bit nicer or functional,
 * as well a bunch of default styling actions that are applied.
 */
public class ChanCommentAction
        extends HtmlTagAction {
    public ChanCommentAction() {
        addDefaultRules();

        // text modifying
        mapTagToRule("span", "abbr", NULLIFY);
        mapTagToRule("iframe", SRC_ATTR);

        // styled text
        mapTagToRule("pre", MONOSPACE);
    }

    @CallSuper
    public HtmlTagAction addSpecificActions(
            Theme theme, Post.Builder post, PostParser.PostParserCallback callback
    ) {
        HtmlTagAction newAction = new HtmlTagAction();

        // themed text
        newAction.mapTagToRule("span", "quote", INLINE_QUOTE_COLOR.with(theme));
        newAction.mapTagToRule("pre", "prettyprint", CODE.with(theme), CHOMP);
        newAction.mapTagToRule("code", CODE.with(theme));
        newAction.mapTagToRule("span", "spoiler", SPOILER.with(theme));
        newAction.mapTagToRule("s", SPOILER.with(theme));

        // functional text
        newAction.mapTagToRule("a", CHAN_ANCHOR.with(theme, post, callback));
        newAction.mapTagToRule("span", "deadlink", DEADLINK.with(theme, post, callback));
        newAction.mapTagToRule("span", "sjis", SJIS.with(theme, post, callback));
        newAction.mapTagToRule("table", TABLE.with(theme, post, callback));
        newAction.mapTagToRule("img", IMAGE.with(theme, post, callback));
        return mergeWith(newAction);
    }
}
