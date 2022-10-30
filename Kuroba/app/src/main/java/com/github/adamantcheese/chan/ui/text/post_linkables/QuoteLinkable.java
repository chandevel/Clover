package com.github.adamantcheese.chan.ui.text.post_linkables;

import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;

import android.graphics.*;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.LineBackgroundSpan;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.ui.theme.Theme;

public class QuoteLinkable
        extends PostLinkable<Integer>
        implements LineBackgroundSpan {

    private int markedNo = -1;
    private PostLinkableCallback callback;

    private final Paint dashPaint = new Paint();
    private final Path dashPath = new Path();

    private static final float DASH_SPACING = dp(3); // arbitrary, but looks good
    private static final float UNDERLINE_THICKNESS = dp(2.392578125f); // same as getUnderlineThickness in API 29+
    private static final float BASELINE_OFFSET = dp(1.025390625f); // same as getUnderlinePosition in API 29+

    public QuoteLinkable(
            @NonNull Theme theme, Integer value
    ) {
        super(theme, value);

        // internal dash paint setup
        dashPaint.setStyle(Paint.Style.STROKE);
        dashPaint.setPathEffect(new DashPathEffect(new float[]{DASH_SPACING, DASH_SPACING}, 0));
        // only one side of the stroke needs to be this thick, it is doubled automatically
        // this appears to look the best when compared to other underlines as well?
        dashPaint.setStrokeWidth(UNDERLINE_THICKNESS / 3);
    }

    public void setMarkedNo(int markedNo) {
        this.markedNo = markedNo;
    }

    public void setCallback(PostLinkableCallback callback) {
        this.callback = callback;
    }

    @Override
    public void updateDrawState(@NonNull TextPaint textPaint) {
        textPaint.setColor(quoteColor);
        textPaint.setUnderlineText(!shouldDrawDashedUnderline());
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
            if (paint instanceof TextPaint) {
                workPaint.set((TextPaint) paint);
            }
            for (CharacterStyle span : spanned.getSpans(0, spanned.length(), CharacterStyle.class)) {
                span.updateDrawState(workPaint);
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
        return callback != null && callback.allowsDashedUnderlines() && value == markedNo;
    }

    public interface PostLinkableCallback {
        boolean allowsDashedUnderlines();
    }
}
