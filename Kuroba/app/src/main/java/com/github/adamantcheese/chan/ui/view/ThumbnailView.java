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
package com.github.adamantcheese.chan.ui.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;

import okhttp3.Call;
import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.sp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.waitForLayout;

public abstract class ThumbnailView
        extends View {
    private HttpUrl source;
    private Call bitmapCall;
    private ViewTreeObserver.OnPreDrawListener drawListener;
    private final boolean circular;
    private int rounding = 0;

    // animate() for ALPHA doesn't call setAlpha but instead some internal function
    // we need setAlpha though, so this class uses a ValueAnimator to take care of that for us
    // the actual alpha set is in onSetAlpha, which setAlpha calls internally
    private final ValueAnimator fadeIn = ValueAnimator.ofFloat(0f, 1f);

    private Bitmap bitmap;
    private final RectF bitmapRect = new RectF();
    private final RectF drawRect = new RectF();
    private final RectF outputRect = new RectF();

    private final Matrix matrix = new Matrix();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    private Drawable foreground;

    protected boolean error = false;
    private String errorText;
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect tmpTextRect = new Rect();

    public ThumbnailView(Context context) {
        this(context, null);
    }

    public ThumbnailView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ThumbnailView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        textPaint.setColor(getAttrColor(context, android.R.attr.textColorPrimary));
        textPaint.setTextSize(sp(context, 14));

        fadeIn.addUpdateListener(animation -> setAlpha((Float) animation.getAnimatedValue()));
        fadeIn.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // basically a final invalidate for any animation, just to ensure everything's finished
                invalidate();
            }
        });

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ThumbnailView);
        try {
            circular = a.getBoolean(R.styleable.ThumbnailView_circular, false);
        } finally {
            a.recycle();
        }

        // for Android Studio to display some sort of bitmap in preview windows
        if (isInEditMode()) {
            setImageBitmap(BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_stat_notify), false);
        } else {
            setImageBitmap(BitmapRepository.empty, false);
        }
    }

    /**
     * Set the URL for this thumbnail view. Since thumbnails are generally square, this only takes one dimension parameter.
     *
     * @param url          The image to set
     * @param maxDimension <0 for this view's width, 0 for exact bitmap dimension, >0 for scaled dimension
     */
    public void setUrl(HttpUrl url, int maxDimension) {
        if (source != null && source.equals(url)) return; // no-op if already set
        source = url;

        setImageBitmap(BitmapRepository.empty, false);

        NetUtilsClasses.BitmapResult result = new NetUtilsClasses.BitmapResult() {
            @Override
            public void onBitmapFailure(@NonNull HttpUrl source, Exception e) {
                if (e instanceof NetUtilsClasses.HttpCodeException) {
                    errorText = String.valueOf(((NetUtilsClasses.HttpCodeException) e).code);
                } else {
                    errorText = getString(R.string.thumbnail_load_failed_network);
                }
                error = true;
                bitmapCall = null;

                fadeIn.end();
            }

            @Override
            public void onBitmapSuccess(@NonNull HttpUrl source, @NonNull Bitmap bitmap, boolean fromCache) {
                setImageBitmap(bitmap, !fromCache);
            }
        };

        if (maxDimension < 0) {
            drawListener = waitForLayout(this, view -> {
                int dim = Math.max(getWidth(), getHeight());
                bitmapCall = NetUtils.makeBitmapRequest(url, result, dim, dim);
                return true;
            });
        } else {
            bitmapCall = NetUtils.makeBitmapRequest(url, result, maxDimension, maxDimension);
        }
    }

    public void setRounding(int rounding) {
        this.rounding = rounding;
    }

    @Override
    public void setClickable(boolean clickable) {
        if (clickable != isClickable()) {
            super.setClickable(clickable);

            if (clickable) {
                TypedValue rippleAttrForThemeValue = new TypedValue();
                getContext().getTheme().resolveAttribute(R.attr.colorControlHighlight, rippleAttrForThemeValue, true);
                foreground = new RippleDrawable(ColorStateList.valueOf(rippleAttrForThemeValue.data),
                        null,
                        new ColorDrawable(Color.WHITE)
                );
                foreground.setCallback(this);
                if (foreground.isStateful()) {
                    foreground.setState(getDrawableState());
                }
            } else {
                unscheduleDrawable(foreground);
                foreground = null;
            }
            requestLayout();
            invalidate();
        } else {
            super.setClickable(clickable);
        }
    }

    public Bitmap getBitmap() {
        return bitmap;
    }

    @Override
    protected boolean onSetAlpha(int alpha) {
        if (error) {
            textPaint.setAlpha(alpha);
        } else {
            paint.setAlpha(alpha);
        }
        invalidate(); // in order to draw the new alpha

        return true;
    }

    public void setGreyscale(boolean grey) {
        ColorMatrix greyMatrix = new ColorMatrix();
        greyMatrix.setSaturation(0);
        paint.setColorFilter(grey ? new ColorMatrixColorFilter(greyMatrix) : null);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getWidth() - getPaddingLeft() - getPaddingRight();
        int height = getHeight() - getPaddingTop() - getPaddingBottom();

        if (error) {
            textPaint.getTextBounds(errorText, 0, errorText.length(), tmpTextRect);
            float x = width / 2f - tmpTextRect.exactCenterX();
            float y = height / 2f - tmpTextRect.exactCenterY();
            canvas.drawText(errorText, x + getPaddingLeft(), y + getPaddingTop(), textPaint);
        } else {
            float scale = Math.max(width / (float) bitmap.getWidth(), height / (float) bitmap.getHeight());
            float scaledX = bitmap.getWidth() * scale;
            float scaledY = bitmap.getHeight() * scale;
            float offsetX = (scaledX - width) * 0.5f;
            float offsetY = (scaledY - height) * 0.5f;

            drawRect.set(-offsetX, -offsetY, scaledX - offsetX, scaledY - offsetY);
            drawRect.offset(getPaddingLeft(), getPaddingTop());

            outputRect.set(getPaddingLeft(),
                    getPaddingTop(),
                    getWidth() - getPaddingRight(),
                    getHeight() - getPaddingBottom()
            );

            matrix.setRectToRect(bitmapRect, drawRect, Matrix.ScaleToFit.FILL);

            paint.getShader().setLocalMatrix(matrix);

            canvas.save();
            canvas.clipRect(outputRect);

            if (circular) {
                canvas.drawRoundRect(outputRect, width / 2f, height / 2f, paint);
            } else {
                canvas.drawRoundRect(outputRect, rounding, rounding, paint);
            }

            canvas.restore();

            if (foreground != null) {
                foreground.setBounds(0, 0, getRight(), getBottom());
                foreground.draw(canvas);
            }
        }
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return super.verifyDrawable(who) || (who == foreground);
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();

        if (foreground != null) {
            foreground.jumpToCurrentState();
        }
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        if (foreground != null && foreground.isStateful()) {
            foreground.setState(getDrawableState());
        }
    }

    @Override
    public void drawableHotspotChanged(float x, float y) {
        super.drawableHotspotChanged(x, y);

        if (foreground != null) {
            foreground.setHotspot(x, y);
        }
    }

    protected void setImageBitmap(Bitmap bitmap, boolean animate) {
        error = false;

        if (bitmapCall != null) { // clear out any calls
            bitmapCall.cancel();
            bitmapCall = null;
        }

        if (drawListener != null) { // clear out any pending on-draws
            if (getViewTreeObserver().isAlive()) {
                getViewTreeObserver().removeOnPreDrawListener(drawListener);
            }
            drawListener = null;
        }

        // set the bitmap and fields for drawing
        this.bitmap = bitmap;
        bitmapRect.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
        paint.setShader(new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));

        // if animated, start, otherwise call end to set the alpha to the end value
        if (animate) {
            fadeIn.start();
        } else {
            fadeIn.end();
        }
    }
}
