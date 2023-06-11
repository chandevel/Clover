package com.github.adamantcheese.chan.ui.text.spans;

import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;

import android.content.Context;
import android.graphics.*;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.ImageSpan;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.ui.text.spans.post_linkables.SpoilerLinkable;

public class CenteringImageSpan
        extends ImageSpan {

    private final Paint underlinePaint = new Paint();
    private final Path underlinePath = new Path();

    private static final float UNDERLINE_THICKNESS = dp(2.392578125f); // same as getUnderlineThickness in API 29+
    private static final float BASELINE_OFFSET = dp(1.025390625f); // same as getUnderlinePosition in API 29+

    public CenteringImageSpan(
            @NonNull Context context, @NonNull Bitmap bitmap
    ) {
        super(context, bitmap);

        // internal dash paint setup
        underlinePaint.setStyle(Paint.Style.STROKE);
        // only one side of the stroke needs to be this thick, it is doubled automatically
        // this appears to look the best when compared to other underlines as well?
        underlinePaint.setStrokeWidth(UNDERLINE_THICKNESS / 3);
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
        // respect spoilers
        if (text instanceof Spanned) {
            SpoilerLinkable[] spoilers = ((Spanned) text).getSpans(start, end, SpoilerLinkable.class);
            if (spoilers.length > 0) {
                SpoilerLinkable firstSpoiler = spoilers[0];
                TextPaint temp = new TextPaint();
                firstSpoiler.updateDrawState(temp);
                temp.setColor(temp.bgColor);
                canvas.drawRect(x, top, getDrawable().getBounds().width(), bottom, temp);
                if (!firstSpoiler.isSpoilerVisible()) return; // spoilered, don't draw anything else
            }
        }

        // respect underlines
        if (text instanceof Spanned) {
            TextPaint temp = new TextPaint();
            if (paint instanceof TextPaint) {
                temp.set((TextPaint) paint);
            }
            for (CharacterStyle style : ((Spanned) text).getSpans(start, end, CharacterStyle.class)) {
                style.updateDrawState(temp);
            }
            if (temp.isUnderlineText()) {
                underlinePaint.setColor(temp.getColor());
                float underlinePosition = bottom - BASELINE_OFFSET * 2;
                underlinePath.rewind();
                underlinePath.moveTo(x, underlinePosition);
                underlinePath.lineTo(x + getDrawable().getBounds().width(), underlinePosition);
                canvas.drawPath(underlinePath, underlinePaint);
            }
        }

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
