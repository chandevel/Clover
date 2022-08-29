package com.github.adamantcheese.chan.ui.text;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.LineBackgroundSpan;
import android.text.style.TypefaceSpan;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.utils.StringUtils;

public class CodeBackgroundSpan
        extends TypefaceSpan
        implements LineBackgroundSpan {
    private final int color;

    public CodeBackgroundSpan(int color) {
        super("monospace");
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
        final Paint workPaint = new Paint(paint);
        workPaint.setColor(color);
        workPaint.setTypeface(Typeface.MONOSPACE);

        // calculate starting position of this span on the line
        Spanned lineText = (Spanned) text.subSequence(start, end); // the text on this line being rendered
        int spanStart = lineText.getSpanStart(this); // where this span starts on this line
        int spanEnd = lineText.getSpanEnd(this); // where this span ends on this line
        CharSequence preText = lineText.subSequence(0, spanStart); // the text that is before this span
        CharSequence spanned = lineText.subSequence(spanStart, spanEnd); // the text spanned in this line
        float preSpannedWidth = paint.measureText(preText, 0, preText.length()); // the width of the text before the span
        float newLeft = left + preSpannedWidth; // the starting point of the span on the line
        boolean lineMatchesSpanned = TextUtils.equals(StringUtils.chomp(spanned), StringUtils.chomp(lineText)); // does the span fill the whole line?

        // draw background
        // if the text (minus any end newlines) matches exactly, set the background to the entire line
        // otherwise highlight the appropriate area, calculating the spanned width with proper attributes
        canvas.drawRect(
                lineMatchesSpanned ? left : newLeft,
                top,
                lineMatchesSpanned ? right : newLeft + workPaint.measureText(spanned, 0, spanned.length()),
                bottom,
                workPaint
        );
    }
}
