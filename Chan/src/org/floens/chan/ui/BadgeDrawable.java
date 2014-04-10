package org.floens.chan.ui;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

public class BadgeDrawable {
    public static Drawable get(Resources resources, int id, int count, boolean red) {
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inMutable = true;
        Bitmap bitmap = BitmapFactory.decodeResource(resources, id, opt);
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        Paint paint = new Paint();
        paint.setAntiAlias(true);

        Canvas canvas = new Canvas(bitmap);

        float badgeX = w * 0.3f;
        float badgeY = h * 0.3f;
        float badgeW = w * 0.6f;
        float badgeH = h * 0.6f;

        RectF rect = new RectF(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH);
        if (red) {
            paint.setColor(0xffff4444);
        } else {
            paint.setColor(0xaa000000);
        }
        canvas.drawRoundRect(rect, w * 0.1f, h * 0.1f, paint);

        String text = Integer.toString(count);
        if (count > 999) {
            text = "1k+";
        }

        paint.setColor(0xffffffff);

        float textHeight;
        float bottomOffset;
        if (text.length() <= 2) {
            textHeight = badgeH * 0.8f;
            bottomOffset = badgeH * 0.2f;
        } else {
            textHeight = badgeH * 0.5f;
            bottomOffset = badgeH * 0.3f;
        }

        paint.setTextSize(textHeight);

        Rect bounds = new Rect();
        paint.getTextBounds(text, 0, text.length(), bounds);

        canvas.drawText(text, badgeX + badgeW / 2f - bounds.right / 2f, badgeY + badgeH - bottomOffset, paint);

        return new BitmapDrawable(resources, bitmap);
    }
}
