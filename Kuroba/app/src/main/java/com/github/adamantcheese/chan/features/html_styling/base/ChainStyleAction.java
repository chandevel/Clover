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
package com.github.adamantcheese.chan.features.html_styling.base;

import static com.github.adamantcheese.chan.features.html_styling.impl.CommonStyleActions.NO_OP;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.utils.Logger;

import org.jsoup.nodes.Node;

/**
 * A style action that applies a chain of actions to a given set of text. The last element of item.chain().chain().chain()
 * is the one that will be processed first.
 */
public class ChainStyleAction
        implements StyleAction {
    private final StyleAction next;

    public ChainStyleAction(@NonNull StyleAction finalAction) {
        next = finalAction;
    }

    public ChainStyleAction chain(StyleAction intermediate) {
        return new ChainStyleAction((node, text) -> style(node, intermediate.style(node, text)));
    }

    @NonNull
    @Override
    public CharSequence style(
            @NonNull Node node, @Nullable CharSequence text
    ) {
        try {
            return next.style(node, text);
        } catch (Exception e) {
            Logger.v(this, "Failed style action", e);
        }
        return NO_OP.style(node, text);
    }
}
