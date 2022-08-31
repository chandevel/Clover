package com.github.adamantcheese.chan.ui.text;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.style.ImageSpan;

import androidx.annotation.NonNull;

public class CenteringImageSpan
        extends ImageSpan {

    public CenteringImageSpan(
            @NonNull Context context, @NonNull Bitmap bitmap
    ) {
        super(context, bitmap);
    }

    public CenteringImageSpan(@NonNull Context context, @NonNull Bitmap bitmap, int verticalAlignment) {
        super(context, bitmap, verticalAlignment);
    }

    public CenteringImageSpan(@NonNull Drawable drawable) {
        super(drawable);
    }

    public CenteringImageSpan(@NonNull Drawable drawable, int verticalAlignment) {
        super(drawable, verticalAlignment);
    }

    public CenteringImageSpan(@NonNull Drawable drawable, @NonNull String source) {
        super(drawable, source);
    }

    public CenteringImageSpan(@NonNull Drawable drawable, @NonNull String source, int verticalAlignment) {
        super(drawable, source, verticalAlignment);
    }

    public CenteringImageSpan(@NonNull Context context, @NonNull Uri uri) {
        super(context, uri);
    }

    public CenteringImageSpan(@NonNull Context context, @NonNull Uri uri, int verticalAlignment) {
        super(context, uri, verticalAlignment);
    }

    public CenteringImageSpan(@NonNull Context context, int resourceId) {
        super(context, resourceId);
    }

    public CenteringImageSpan(@NonNull Context context, int resourceId, int verticalAlignment) {
        super(context, resourceId, verticalAlignment);
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
