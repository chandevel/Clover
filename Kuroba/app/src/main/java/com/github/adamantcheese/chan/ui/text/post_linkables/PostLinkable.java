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
package com.github.adamantcheese.chan.ui.text.post_linkables;

import android.text.style.ClickableSpan;
import android.view.View;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.ui.cell.PostViewMovementMethod;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.utils.AndroidUtils;

import java.util.Objects;

/**
 * A Clickable span that handles post clicks. These are created in PostParser for post quotes, spoilers etc.<br>
 * PostCell has a {@link PostViewMovementMethod}, that searches spans at the location the TextView was tapped,
 * and handled if it was a PostLinkable.
 */
public abstract class PostLinkable<T>
        extends ClickableSpan {

    protected final int quoteColor;
    public final T value; // the value associated with the text; see implementations

    public PostLinkable(@NonNull Theme theme, T value) {
        quoteColor = AndroidUtils.getThemeAttrColor(theme, R.attr.post_quote_color);
        this.value = value;
    }

    @Override
    public void onClick(@NonNull View widget) {
        // by default this doesn't do anything; PostViewMovementMethod takes care of the actual clicks
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PostLinkable)) return false;
        PostLinkable<?> that = (PostLinkable<?>) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
