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
package com.github.adamantcheese.chan.core.site.parser.style;

import android.text.Spanned;
import android.text.SpannedString;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.site.parser.PostParser.Callback;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.utils.JavaUtils;
import com.github.adamantcheese.chan.utils.Logger;

import org.jsoup.nodes.Element;

import java.util.Collections;
import java.util.List;

/**
 * A style action that applies a chain of actions to a given set of text.
 * The order in which you put the actions is the order they will execute.
 */
public class ChainStyleAction
        implements StyleAction {
    public final List<StyleAction> actions = new JavaUtils.NoDeleteArrayList<>();

    public ChainStyleAction(StyleAction... actions) {
        Collections.addAll(this.actions, actions);
    }

    public ChainStyleAction(List<StyleAction> actions) {
        this.actions.addAll(actions);
    }

    @NonNull
    @Override
    public SpannedString style(
            @NonNull Element element,
            @NonNull Spanned text,
            @NonNull Theme theme,
            @NonNull Post.Builder post,
            @NonNull Callback callback
    ) {
        SpannedString result = new SpannedString(text);
        for (StyleAction styleAction : actions) {
            try {
                result = styleAction.style(element, result, theme, post, callback);
            } catch (Exception e) {
                Logger.v(this, "Failed style action", e);
            }
        }
        return result;
    }
}
