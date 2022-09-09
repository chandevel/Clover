package com.github.adamantcheese.chan.ui.text;

import android.content.Context;
import android.graphics.*;
import android.text.style.ImageSpan;

import androidx.annotation.NonNull;

public class CenteringImageSpan
        extends ImageSpan {

    public CenteringImageSpan(
            @NonNull Context context, @NonNull Bitmap bitmap
    ) {
        super(context, bitmap);
    }

    @Override
    public void draw(
            @NonNull Canvas canvas,
            CharSequence text,
            int start,
            int end,
            float x,
            int top,
            int y,
            int bottom,
            @NonNull Paint paint
    ) {
        Paint.FontMetricsInt metrics = paint.getFontMetricsInt();
        canvas.save();
        canvas.translate(
                x,
                bottom - getDrawable().getBounds().bottom - metrics.bottom
                        + metrics.descent / 2f
                        + metrics.ascent / 2f
                        + getDrawable().getBounds().height() / 2f
        );
        getDrawable().draw(canvas);
        canvas.restore();
    }
}
