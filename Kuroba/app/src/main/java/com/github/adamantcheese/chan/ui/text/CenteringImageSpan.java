package com.github.adamantcheese.chan.ui.text;

import android.content.Context;
import android.graphics.*;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ImageSpan;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.ui.text.post_linkables.SpoilerLinkable;

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
        SpoilerLinkable overlappingLinkable = null;

        // respect SpoilerLinkables
        if (text instanceof Spanned) {
            SpoilerLinkable[] spoilers = ((Spanned) text).getSpans(start, end, SpoilerLinkable.class);
            if (spoilers.length > 0) overlappingLinkable = spoilers[0];
        }

        if (overlappingLinkable != null) {
            TextPaint temp = new TextPaint();
            overlappingLinkable.updateDrawState(temp);
            temp.setColor(temp.bgColor);
            canvas.drawRect(x, top, getDrawable().getBounds().width(), bottom, temp);
        }

        // draw image
        if (overlappingLinkable != null && !overlappingLinkable.isSpoilerVisible()) return;
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
