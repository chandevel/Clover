package com.github.adamantcheese.chan.ui.text;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.style.LineBackgroundSpan;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.ui.theme.Theme;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;

public class CodeBackgroundSpan
        implements LineBackgroundSpan {
    private final int color;

    public CodeBackgroundSpan(Theme theme) {
        this.color = getAttrColor(theme.resValue, R.attr.backcolor_secondary);
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
        final int paintColor = paint.getColor();
        paint.setColor(color);
        canvas.drawRect(new Rect(left, top, right, bottom), paint);
        paint.setColor(paintColor);
    }
}
