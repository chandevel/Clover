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
package com.github.adamantcheese.chan.core.model;

import android.graphics.Color;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.cell.PostCell;
import com.github.adamantcheese.chan.ui.theme.Theme;

import java.util.Objects;

import static com.github.adamantcheese.chan.core.model.PostLinkable.Type.EMBED;
import static com.github.adamantcheese.chan.core.model.PostLinkable.Type.JAVASCRIPT;
import static com.github.adamantcheese.chan.core.model.PostLinkable.Type.LINK;
import static com.github.adamantcheese.chan.core.model.PostLinkable.Type.QUOTE;
import static com.github.adamantcheese.chan.core.model.PostLinkable.Type.SPOILER;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrFloat;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getContrastColor;

/**
 * A Clickable span that handles post clicks. These are created in PostParser for post quotes, spoilers etc.<br>
 * PostCell has a {@link PostCell.PostViewMovementMethod}, that searches spans at the location the TextView was tapped,
 * and handled if it was a PostLinkable.
 */
public class PostLinkable
        extends ClickableSpan {
    public enum Type {
        QUOTE, //key: the quote text, value: Integer, post num in text
        LINK, //key: the link text to display, value: String, the link text url
        EMBED, // same as LINK, but used for embedding engine stuff
        EMBED_TEMP, // same as EMBED, but only temporarily added, to be removed later in the embedding process
        SPOILER, //key: "SPOILER", value: CharSequence, the spoilered text
        THREAD, //key: the thread link text, value: ThreadLink, matching the board, opNo, and postNo
        BOARD, //key: the board link text, value: String, the board code
        SEARCH, //key: the search link text, value: SearchLink, matchinng the board and search query text
        ARCHIVE, //key: the deadlink text or the `href` for the html tag, value: ThreadLink OR ResolveLink, matching the board, opNo, and postNo or board and postNo, respectively
        JAVASCRIPT //key: the link text, like "View" or something, value: the javascript that needs to be run on the source webpage
    }

    private final float blendRatio;
    private final int quoteColor;
    private final int spoilerColor;
    public final CharSequence key;
    public final Object value;
    public final Type type;

    private boolean spoilerVisible = ChanSettings.revealTextSpoilers.get();
    private int markedNo = -1;

    public PostLinkable(@NonNull Theme theme, CharSequence key, Object value, Type type) {
        blendRatio = getAttrFloat(theme.resValue, R.attr.highlight_linkable_blend);
        quoteColor = getAttrColor(theme.resValue, R.attr.post_quote_color);
        spoilerColor = getAttrColor(theme.resValue, R.attr.post_spoiler_color);
        this.key = key;
        this.value = value;
        this.type = type;
    }

    @Override
    public void onClick(@NonNull View widget) {
        spoilerVisible = !spoilerVisible;
    }

    public void setMarkedNo(int markedNo) {
        this.markedNo = markedNo;
    }

    @Override
    public void updateDrawState(@NonNull TextPaint ds) {
        if (type != SPOILER) {
            ds.setColor(type == LINK || type == EMBED || type == JAVASCRIPT ? ds.linkColor : quoteColor);
            ds.setUnderlineText(true);
            ds.setFakeBoldText(false);
            ds.setTextScaleX(1.0f);
            if (type == QUOTE && value instanceof Integer && ((int) value) == markedNo) {
                float[] HSV = new float[3];
                Color.colorToHSV(quoteColor, HSV);
                HSV[1] = Math.min(HSV[1] * blendRatio, 1.0f);
                HSV[2] = Math.min(HSV[2] * blendRatio, 1.0f);
                int ARGB = Color.HSVToColor(Color.alpha(quoteColor), HSV);
                ds.setColor(ARGB);
                ds.setFakeBoldText(true);
                ds.setTextScaleX(1.1f);
            }
        } else {
            ds.bgColor = spoilerColor;
            ds.setUnderlineText(false);
            ds.setFakeBoldText(false);
            ds.setTextScaleX(1.0f);
            if (!spoilerVisible) {
                ds.setColor(spoilerColor);
            } else {
                ds.setColor(getContrastColor(spoilerColor));
            }
        }
    }

    public boolean isSpoilerVisible() {
        return spoilerVisible;
    }

    @Override
    public int hashCode() {
        return Objects.hash(key.toString(), value.toString(), type.ordinal());
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj == null) return false;
        if (!(obj instanceof PostLinkable)) return false;
        PostLinkable linkable = (PostLinkable) obj;

        // We need to ignore the spans here when comparing
        return linkable.key.toString().equals(this.key.toString()) && linkable.value.equals(this.value) && linkable.type
                .equals(this.type);
    }
}
