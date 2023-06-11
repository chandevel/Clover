package com.github.adamantcheese.chan.ui.text.spans;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.text.*;
import android.text.style.LineBackgroundSpan;

import androidx.annotation.*;

@RequiresApi(api = Build.VERSION_CODES.M)
public class RoundedBackgroundSpan
        implements LineBackgroundSpan {
    private final int color;

    public RoundedBackgroundSpan(@ColorInt int color) {
        this.color = color;
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
        // work paint for calculations; typeface isn't set when this is called, so we need to set it here
        final TextPaint workPaint = new TextPaint(paint);
        workPaint.setColor(color);

        // calculate starting position of this span on the line
        Spanned lineText = (Spanned) text.subSequence(start, end); // the text on this line being rendered
        int spanStart = lineText.getSpanStart(this); // where this span starts on this line
        int spanEnd = lineText.getSpanEnd(this); // where this span ends on this line
        CharSequence preText = lineText.subSequence(0, spanStart); // the text that is before this span
        CharSequence spanned = lineText.subSequence(spanStart, spanEnd); // the text spanned in this line
        // calculate the pre-text width
        StaticLayout preLayout =
                StaticLayout.Builder.obtain(preText, 0, preText.length(), workPaint, right - left).build();
        float preSpannedWidth = preLayout.getLineWidth(0);
        // calculate the span width
        StaticLayout spanLayout =
                StaticLayout.Builder.obtain(spanned, 0, spanned.length(), workPaint, right - left).build();
        float spannedWidth = spanLayout.getLineWidth(0);
        float radius = (bottom - top) / 2f;
        float newLeft = left + preSpannedWidth;

        // draw background
        canvas.drawRoundRect(newLeft, top, newLeft + spannedWidth, bottom, radius, radius, workPaint);
    }
}
