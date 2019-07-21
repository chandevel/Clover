/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
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
package org.floens.chan.ui.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkError;
import com.android.volley.ParseError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;

import org.floens.chan.R;

import static org.floens.chan.Chan.injector;
import static org.floens.chan.utils.AndroidUtils.getString;
import static org.floens.chan.utils.AndroidUtils.sp;

public class ThumbnailView extends View implements ImageLoader.ImageListener {
    private ImageLoader.ImageContainer container;
    private int fadeTime = 200;
    private ValueAnimator fadeAnimation;
    private boolean hidden = false;

    private boolean circular = false;
    private int rounding = 0;
    private boolean clickable = false;

    private boolean calculate;
    private Bitmap bitmap;
    private RectF bitmapRect = new RectF();
    private RectF drawRect = new RectF();
    private RectF outputRect = new RectF();

    private Matrix matrix = new Matrix();
    BitmapShader bitmapShader;
    private Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    private boolean foregroundCalculate = false;
    private Drawable foreground;

    protected boolean error = false;
    private String errorText;
    private Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Rect tmpTextRect = new Rect();

    public ThumbnailView(Context context) {
        super(context);
        init();
    }

    public ThumbnailView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ThumbnailView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        textPaint.setColor(0xff000000);
        textPaint.setTextSize(sp(14));
        backgroundPaint.setColor(0x22000000);
        endAnimations();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        endAnimations();
    }

    public void setUrl(String url, int width, int height) {
        if (container != null && container.getRequestUrl().equals(url)) {
            return;
        }

        if (container != null) {
            container.cancelRequest();
            container = null;
            error = false;
            setImageBitmap(null);
        }

        if (!TextUtils.isEmpty(url)) {
            container = injector().instance(ImageLoader.class).get(url, this, width, height);
        }
    }

    public void setCircular(boolean circular) {
        this.circular = circular;
    }

    public void setRounding(int rounding) {
        this.rounding = rounding;
    }

    public int getRounding() {
        return rounding;
    }

    @SuppressWarnings({"deprecation", "ConstantConditions"})
    @Override
    public void setClickable(boolean clickable) {
        super.setClickable(clickable);

        if (clickable != this.clickable) {
            this.clickable = clickable;

            foregroundCalculate = clickable;
            if (clickable) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    foreground = getContext().getDrawable(R.drawable.item_background);
                } else {
                    foreground = getResources().getDrawable(R.drawable.item_background);
                }
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
        }
    }

    public void setFadeTime(int fadeTime) {
        this.fadeTime = fadeTime;
    }

    public Bitmap getBitmap() {
        return bitmap;
    }

    public void hide(boolean animateIfNeeded) {
        hidden = true;
        if (getAlpha() == 0f) return;

        if (animateIfNeeded && fadeAnimation == null && bitmap != null && !calculate) {
            animate().alpha(0f).setDuration(150);
        } else {
            setAlpha(0f);
        }
        endAnimations();
    }

    public void show(boolean animateIfNeeded) {
        hidden = false;
        if (getAlpha() == 1f) return;

        if (animateIfNeeded && fadeAnimation == null && bitmap != null && !calculate) {
            animate().alpha(1f).setDuration(150);
        } else {
            setAlpha(1f);
        }
        endAnimations();
    }

    @Override
    public void onResponse(ImageLoader.ImageContainer response, boolean isImmediate) {
        if (response.getBitmap() != null) {
            setImageBitmap(response.getBitmap());
            onImageSet(isImmediate);
        }
    }

    @Override
    public void onErrorResponse(VolleyError e) {
        error = true;

        if (e instanceof NetworkError || e instanceof TimeoutError || e instanceof ParseError || e instanceof AuthFailureError) {
            errorText = getString(R.string.thumbnail_load_failed_network);
        } else {
            errorText = getString(R.string.thumbnail_load_failed_server);
        }

        onImageSet(false);
        invalidate();
    }

    @Override
    protected boolean onSetAlpha(int alpha) {
        if (error) {
            textPaint.setAlpha(alpha);
        } else {
            bitmapPaint.setAlpha(alpha);
        }
        invalidate();

        return true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        calculate = true;
        foregroundCalculate = true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getWidth() - getPaddingLeft() - getPaddingRight();
        int height = getHeight() - getPaddingTop() - getPaddingBottom();

        if (error) {
            // Render a simple text if there was an error.
            canvas.save();

            textPaint.getTextBounds(errorText, 0, errorText.length(), tmpTextRect);
            float x = width / 2f - tmpTextRect.exactCenterX();
            float y = height / 2f - tmpTextRect.exactCenterY();
            canvas.drawText(errorText, x + getPaddingLeft(), y + getPaddingTop(), textPaint);

            canvas.restore();
        } else {
            outputRect.set(getPaddingLeft(), getPaddingTop(),
                    getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());

            // Gray background if thumbnail is not yet loaded.
            if (bitmap == null || fadeAnimation != null) {
                if (circular) {
                    canvas.drawRoundRect(outputRect, width / 2.0f, height / 2.0f, backgroundPaint);
                } else {
                    canvas.drawRoundRect(outputRect, rounding, rounding, backgroundPaint);
                }
            }

            if (bitmap == null) {
                return;
            }

            // If needed, calculate positions.
            if (calculate) {
                calculate = false;
                bitmapRect.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
                float scale = Math.max(
                        (float) width / (float) bitmap.getWidth(),
                        (float) height / (float) bitmap.getHeight());
                float scaledX = bitmap.getWidth() * scale;
                float scaledY = bitmap.getHeight() * scale;
                float offsetX = (scaledX - width) * 0.5f;
                float offsetY = (scaledY - height) * 0.5f;

                drawRect.set(-offsetX, -offsetY, scaledX - offsetX, scaledY - offsetY);
                drawRect.offset(getPaddingLeft(), getPaddingTop());

                matrix.setRectToRect(bitmapRect, drawRect, Matrix.ScaleToFit.FILL);

                bitmapShader.setLocalMatrix(matrix);
                bitmapPaint.setShader(bitmapShader);
            }

            canvas.save();
            canvas.clipRect(outputRect);

            // Draw rounded bitmap.
            if (circular) {
                canvas.drawRoundRect(outputRect, width / 2.0f, height / 2.0f, bitmapPaint);
            } else {
                canvas.drawRoundRect(outputRect, rounding, rounding, bitmapPaint);
            }

            canvas.restore();
            canvas.save();

            if (foreground != null) {
                if (foregroundCalculate) {
                    foregroundCalculate = false;
                    foreground.setBounds(0, 0, getRight(), getBottom());
                }

                foreground.draw(canvas);
            }

            canvas.restore();
        }
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
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

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void drawableHotspotChanged(float x, float y) {
        super.drawableHotspotChanged(x, y);

        if (foreground != null) {
            foreground.setHotspot(x, y);
        }
    }

    private void onImageSet(boolean isImmediate) {
        clearAnimation();
        if (fadeTime > 0 && !isImmediate && !hidden) {
            runFadeInAnimation();
        } else {
            setAlpha(1f);
        }
    }

    private void setImageBitmap(Bitmap bitmap) {
        bitmapShader = null;
        bitmapPaint.setShader(null);

        this.bitmap = bitmap;
        if (bitmap != null) {
            calculate = true;
            bitmapShader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        }
        endAnimations();
        invalidate();
    }

    private void runFadeInAnimation() {
        fadeAnimation = ValueAnimator.ofFloat(0.0f, 1.0f);
        fadeAnimation.setDuration(fadeTime);
        fadeAnimation.addUpdateListener(v -> {
            bitmapPaint.setAlpha((int) (((float) v.getAnimatedValue()) * 255));
            invalidate();
        });
        fadeAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                fadeAnimation = null;
                if (bitmapPaint.getAlpha() != ((int) getAlpha() * 255)) {
                    bitmapPaint.setAlpha((int) (getAlpha() * 255));
                    invalidate();
                }
            }
        });
        fadeAnimation.start();
    }

    private void endAnimations() {
        if (fadeAnimation != null) {
            fadeAnimation.end();
            fadeAnimation = null;
        }
    }
}
