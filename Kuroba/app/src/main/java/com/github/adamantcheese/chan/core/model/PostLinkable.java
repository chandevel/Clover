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

import static com.github.adamantcheese.chan.core.model.PostLinkable.Type.LINK;
import static com.github.adamantcheese.chan.core.model.PostLinkable.Type.QUOTE;
import static com.github.adamantcheese.chan.core.model.PostLinkable.Type.SPOILER;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getContrastColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.resolveColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.resolveFloat;

/**
 * A Clickable span that handles post clicks. These are created in PostParser for post quotes, spoilers etc.<br>
 * PostCell has a {@link PostCell.PostViewMovementMethod}, that searches spans at the location the TextView was tapped,
 * and handled if it was a PostLinkable.
 */
public class PostLinkable
        extends ClickableSpan {
    public enum Type {
        QUOTE,
        LINK,
        SPOILER,
        THREAD,
        BOARD,
        SEARCH
    }

    private final float blendRatio;
    private final int quoteColor;
    private final int spoilerColor;
    public final CharSequence key;
    public final Object value;
    public final Type type;

    private boolean spoilerVisible = ChanSettings.revealTextSpoilers.get();
    private int markedNo = -1;

    public PostLinkable(Theme theme, CharSequence key, Object value, Type type) {
        blendRatio = resolveFloat(theme.resValue, R.attr.highlight_linkable_blend);
        quoteColor = resolveColor(theme.resValue, R.attr.post_quote_color);
        spoilerColor = resolveColor(theme.resValue, R.attr.post_spoiler_color);
        this.key = key;
        this.value = value;
        this.type = type;
    }

    @Override
    public void onClick(View widget) {
        spoilerVisible = !spoilerVisible;
    }

    public void setMarkedNo(int markedNo) {
        this.markedNo = markedNo;
    }

    @Override
    public void updateDrawState(@NonNull TextPaint ds) {
        if (type != SPOILER) {
            ds.setColor(type == LINK ? ds.linkColor : quoteColor);
            ds.setUnderlineText(true);
            ds.setFakeBoldText(false);
            if (type == QUOTE && value instanceof Integer && ((int) value) == markedNo) {
                float[] HSV = new float[3];
                Color.colorToHSV(quoteColor, HSV);
                HSV[1] = Math.min(HSV[1] * blendRatio, 1.0f);
                HSV[2] = Math.min(HSV[2] * blendRatio, 1.0f);
                int ARGB = Color.HSVToColor(Color.alpha(quoteColor), HSV);
                ds.setColor(ARGB);
                ds.setFakeBoldText(true);
            }
        } else {
            ds.bgColor = spoilerColor;
            ds.setUnderlineText(false);
            ds.setFakeBoldText(false);
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
        int result = 0;
        for (char c : key.toString().toCharArray()) {
            result += c;
        }
        result = 31 * result;
        for (char c : value.toString().toCharArray()) {
            result += c;
        }
        result = 31 * result + type.ordinal();
        return result;
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
