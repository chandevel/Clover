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

import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.LineBackgroundSpan;
import android.text.style.UpdateAppearance;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.cell.PostViewMovementMethod;
import com.github.adamantcheese.chan.ui.theme.Theme;

import java.util.Objects;

import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getContrastColor;

/**
 * A Clickable span that handles post clicks. These are created in PostParser for post quotes, spoilers etc.<br>
 * PostCell has a {@link PostViewMovementMethod}, that searches spans at the location the TextView was tapped,
 * and handled if it was a PostLinkable.
 */
public class PostLinkable
        extends ClickableSpan
        implements LineBackgroundSpan {
    public enum Type {
        QUOTE,              // value: Integer, post num in text
        LINK,               // value: String, the link text url
        EMBED_AUTO_LINK,    // same as LINK, but used for embedding engine auto links
        EMBED_REPLACE_LINK, // same as LINK, but used for embedding engine link replacements (ie youtube titles)
        EMBED_TEMP,         // same as LINK, but only temporarily added, to be removed later in the embedding process
        SPOILER,            // value: CharSequence, the spoilered text
        THREAD,             // value: ThreadLink, matching the board, opNo, and postNo
        BOARD,              // value: String, the board code
        SEARCH,             // value: SearchLink, matchinng the board and search query text
        ARCHIVE,            // value: ThreadLink OR ResolveLink, matching the board, opNo, and postNo or board and postNo, respectively
        JAVASCRIPT,         // value: String, the javascript that needs to be run on the source webpage
        OTHER               // value: Object, a catch-all for any other linkables; value is always new Object()
    }

    private final int quoteColor;
    private final int spoilerColor;
    public final Object value; // the value associated with the text, see enum above
    public final Type type;
    public PostLinkableCallback callback;

    private boolean spoilerVisible = ChanSettings.revealTextSpoilers.get();
    private int markedNo = -1;

    private final Paint dashPaint = new Paint();
    private final Path dashPath = new Path();

    private static final float DASH_SPACING = dp(3); // arbitrary, but looks good
    private static final float UNDERLINE_THICKNESS = dp(2.392578125f); // same as getUnderlineThickness in API 29+
    private static final float BASELINE_OFFSET = dp(1.025390625f); // same as getUnderlinePosition in API 29+

    public PostLinkable(@NonNull Theme theme, Object value, Type type) {
        quoteColor = getAttrColor(theme.resValue, R.attr.post_quote_color);
        spoilerColor = getAttrColor(theme.resValue, R.attr.post_spoiler_color);
        this.value = value;
        this.type = type;

        // internal dash paint setup
        dashPaint.setStyle(Paint.Style.STROKE);
        dashPaint.setPathEffect(new DashPathEffect(new float[]{DASH_SPACING, DASH_SPACING}, 0));
        // only one side of the stroke needs to be this thick, it is doubled automatically
        dashPaint.setStrokeWidth(UNDERLINE_THICKNESS / 2);
    }

    public void onClick(@NonNull View widget) {
        spoilerVisible = !spoilerVisible;
    }

    public void setMarkedNo(int markedNo) {
        this.markedNo = markedNo;
    }

    public void setCallback(PostLinkableCallback callback) {
        this.callback = callback;
    }

    public boolean isSpoilerVisible() {
        return spoilerVisible;
    }

    @Override
    public void updateDrawState(@NonNull TextPaint textPaint) {
        switch (type) {
            // regular links (external to the application)
            case LINK:
            case JAVASCRIPT:
            case EMBED_AUTO_LINK:
            case EMBED_REPLACE_LINK:
            case EMBED_TEMP:
                textPaint.setColor(textPaint.linkColor);
                //noinspection fallthrough
            case OTHER:
                textPaint.setUnderlineText(true);
                break;
            // special postlinkable links (internal to the application)
            case QUOTE:
            case SEARCH:
            case THREAD:
            case ARCHIVE:
            case BOARD:
                textPaint.setColor(quoteColor);
                textPaint.setUnderlineText(!shouldDrawDashedUnderline());
                break;
            // spoiler specific
            case SPOILER:
                textPaint.bgColor = spoilerColor;
                textPaint.setColor(spoilerVisible ? getContrastColor(spoilerColor) : spoilerColor);
                break;
        }
    }

    @Override
    public void drawBackground(
            @NonNull Canvas canvas,
            @NonNull Paint paint,
            int left,
            int right,
            int top,
            int baseline,
            int bottom,
            @NonNull CharSequence text,
            int start,
            int end,
            int lineNumber
    ) {
        if (shouldDrawDashedUnderline()) {
            // calculate starting position of this span on the line
            Spanned lineText = (Spanned) text.subSequence(start, end); // the text on this line being rendered
            int spanStart = lineText.getSpanStart(this); // where this span starts on this line
            int spanEnd = lineText.getSpanEnd(this); // where this span ends on this line
            Spanned preText = (Spanned) lineText.subSequence(0, spanStart); // the text that is before this span
            Spanned spanned = (Spanned) lineText.subSequence(spanStart, spanEnd); // the text spanned in this line
            float preSpannedWidth = paint.measureText(preText, 0, preText.length()); // with previous paint attributes
            float spannedWidth = paint.measureText(spanned, 0, spanned.length());

            float newLeft = left + preSpannedWidth;
            float newBottom = bottom - BASELINE_OFFSET * 2;

            // update colors in case of overlapping spans
            TextPaint workPaint = new TextPaint();
            for (CharacterStyle span : spanned.getSpans(0, spanned.length(), CharacterStyle.class)) {
                if (span instanceof UpdateAppearance) {
                    span.updateDrawState(workPaint);
                }
            }
            dashPaint.setColor(workPaint.getColor());

            // draw dashed line
            dashPath.rewind();
            dashPath.moveTo(newLeft, newBottom);
            dashPath.lineTo(newLeft + spannedWidth, newBottom);
            canvas.drawPath(dashPath, this.dashPaint);
        }
    }

    private boolean shouldDrawDashedUnderline() {
        return callback != null && callback.allowsDashedUnderlines() && type == Type.QUOTE && value instanceof Integer
                && ((int) value) == markedNo;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value.toString(), type.ordinal());
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj == null) return false;
        if (!(obj instanceof PostLinkable)) return false;
        PostLinkable linkable = (PostLinkable) obj;
        return linkable.value.equals(this.value) && linkable.type.equals(this.type);
    }

    public interface PostLinkableCallback {
        boolean allowsDashedUnderlines();
    }
}
