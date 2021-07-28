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
package com.github.adamantcheese.chan.ui.theme;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.StringUtils.getShortString;

public class ArrowMenuDrawable
        extends Drawable {
    private final Paint mPaint = new Paint();

    // The angle in degrees that the arrow head is inclined at.
    private static final float ARROW_HEAD_ANGLE = (float) Math.toRadians(45);
    // The thickness of the bars
    private final float mBarThickness = dp(2f);
    // The length of top and bottom bars when they merge into an arrow
    private final float mTopBottomArrowSize = dp(11.31f);
    // The length of middle bar
    private final float mBarSize = dp(18f);
    // The length of the middle bar when arrow is shaped
    private final float mMiddleArrowSize = dp(16f);
    // The space between bars when they are parallel
    private final float mBarGap = dp(3f);
    // Use Path instead of canvas operations so that if color has transparency, overlapping sections
    // wont look different
    private final Path mPath = new Path();
    // The reported intrinsic size of the drawable.
    private final float mSize = dp(24f);
    // Whether we should mirror animation when animation is reversed.
    private boolean mVerticalMirror = false;
    // The interpolated version of the original progress
    private float mProgress = 0.0f;

    private String badgeText;
    private boolean badgeRed = false;
    private final Paint badgePaint = new Paint();
    private final Rect badgeTextBounds = new Rect();

    public ArrowMenuDrawable() {
        mPaint.setColor(Color.WHITE);
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeJoin(Paint.Join.MITER);
        mPaint.setStrokeCap(Paint.Cap.SQUARE);
        mPaint.setStrokeWidth(mBarThickness);
        badgePaint.setAntiAlias(true);
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        // Interpolated widths of arrow bars
        final float arrowSize = lerp(mBarSize, mTopBottomArrowSize, mProgress);
        final float middleBarSize = lerp(mBarSize, mMiddleArrowSize, mProgress);
        // Interpolated size of middle bar
        final float middleBarCut = lerp(0, mBarThickness / 2, mProgress);
        // The rotation of the top and bottom bars (that make the arrow head)
        final float rotation = lerp(0, ARROW_HEAD_ANGLE, mProgress);

        // The whole canvas rotates as the transition happens
        final float canvasRotate = lerp(-180, 0, mProgress);
        final float topBottomBarOffset = lerp(mBarGap + mBarThickness, 0, mProgress);
        mPath.rewind();

        final float arrowEdge = -middleBarSize / 2;
        // draw middle bar
        mPath.moveTo(arrowEdge + middleBarCut, 0);
        mPath.rLineTo(middleBarSize - middleBarCut, 0);

        float arrowWidth = arrowSize * (float) Math.cos(rotation);
        float arrowHeight = arrowSize * (float) Math.sin(rotation);

        if (Float.compare(mProgress, 0f) == 0 || Float.compare(mProgress, 1f) == 0) {
            arrowWidth = Math.round(arrowWidth);
            arrowHeight = Math.round(arrowHeight);
        }

        // top bar
        mPath.moveTo(arrowEdge, topBottomBarOffset);
        mPath.rLineTo(arrowWidth, arrowHeight);

        // bottom bar
        mPath.moveTo(arrowEdge, -topBottomBarOffset);
        mPath.rLineTo(arrowWidth, -arrowHeight);

        canvas.save();
        // Rotate the whole canvas if spinning.
        canvas.rotate(canvasRotate * ((mVerticalMirror) ? -1 : 1), bounds.centerX(), bounds.centerY());
        canvas.translate(bounds.centerX(), bounds.centerY());
        canvas.drawPath(mPath, mPaint);

        canvas.restore();

        // Draw a badge over the arrow/menu
        if (badgeText != null) {
            canvas.save();
            float badgeSize = mSize * 0.7f;
            float badgeX = mSize - badgeSize / 2f;
            float badgeY = badgeSize / 2f;

            if (badgeRed) {
                badgePaint.setColor(0xddf44336);
            } else {
                badgePaint.setColor(0x89000000);
            }

            canvas.drawCircle(badgeX, badgeY, badgeSize / 2f, badgePaint);

            float textSize;
            if (badgeText.length() == 1) {
                textSize = badgeSize * 0.7f;
            } else if (badgeText.length() == 2) {
                textSize = badgeSize * 0.6f;
            } else {
                textSize = badgeSize * 0.5f;
            }

            badgePaint.setColor(Color.WHITE);
            badgePaint.setTextSize(textSize);
            badgePaint.getTextBounds(badgeText, 0, badgeText.length(), badgeTextBounds);
            canvas.drawText(badgeText,
                    badgeX - badgeTextBounds.right / 2f,
                    badgeY - badgeTextBounds.top / 2f,
                    badgePaint
            );
            canvas.restore();
        }
    }

    @Override
    public void setAlpha(int i) {
        mPaint.setAlpha(i);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getIntrinsicHeight() {
        return (int) mSize;
    }

    @Override
    public int getIntrinsicWidth() {
        return (int) mSize;
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    public float getProgress() {
        return mProgress;
    }

    public void setProgress(float progress) {
        if (progress != mProgress) {
            if (Float.compare(progress, 1f) == 0) {
                mVerticalMirror = true;
            } else if (Float.compare(progress, 0f) == 0) {
                mVerticalMirror = false;
            }
            mProgress = progress;
            invalidateSelf();
        }
    }

    public void setBadge(int count, boolean red) {
        String text = count == 0 ? null : (getShortString(count));
        if (badgeRed != red || !TextUtils.equals(text, badgeText)) {
            badgeText = text;
            badgeRed = red;
            invalidateSelf();
        }
    }

    /**
     * Linear interpolate between a and b with parameter t.
     */
    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
