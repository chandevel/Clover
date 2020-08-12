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

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
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

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.image.ImageLoaderV2;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.utils.NetUtils;

import okhttp3.Call;
import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.sp;

public abstract class ThumbnailView
        extends View
        implements NetUtils.BitmapResult {
    private Call bitmapCall;
    private boolean circular = false;
    private int rounding = 0;

    private boolean calculate;
    private Bitmap bitmap;
    private RectF bitmapRect = new RectF();
    private RectF drawRect = new RectF();
    private RectF outputRect = new RectF();

    private Matrix matrix = new Matrix();
    BitmapShader bitmapShader;
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    private boolean foregroundCalculate = false;
    private Drawable foreground;

    protected boolean error = false;
    private String errorText;
    private Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Rect tmpTextRect = new Rect();

    private ValueAnimator fadeIn;

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

        // for Android Studio to display some sort of bitmap in preview windows
        if (isInEditMode()) {
            setImageBitmap(BitmapFactory.decodeResource(context.getResources(), android.R.drawable.ic_menu_gallery));
        }
    }

    public void setUrl(HttpUrl url, int maxWidth, int maxHeight) {
        error = false;
        setImageBitmap(null);
        if (fadeIn != null) {
            fadeIn.end();
            fadeIn = null;
        }

        if (bitmapCall != null) {
            bitmapCall.cancel();
            bitmapCall = null;
        }

        if (url == null) {
            return;
        }

        bitmapCall = NetUtils.makeBitmapRequest(url, this, maxWidth, maxHeight);
    }

    public void setUrl(HttpUrl url) {
        setUrl(url, 0, 0);
    }

    public void setUrlFromDisk(Loadable loadable, String filename, boolean isSpoiler, int width, int height) {
        if (fadeIn != null) {
            fadeIn.end();
            fadeIn = null;
        }
        setImageBitmap(null);
        try {
            bitmapCall = ImageLoaderV2.getFromDisk(loadable, filename, isSpoiler, this, width, height, null);
        } catch (Exception ignored) { }
    }

    public void setCircular(boolean circular) {
        this.circular = circular;
    }

    public void setRounding(int rounding) {
        this.rounding = rounding;
    }

    @Override
    public void setClickable(boolean clickable) {
        if (clickable != isClickable()) {
            super.setClickable(clickable);

            foregroundCalculate = clickable;
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
        invalidate();

        return true;
    }

    public void setGreyscale(boolean grey) {
        ColorMatrix greyMatrix = new ColorMatrix();
        greyMatrix.setSaturation(0);
        paint.setColorFilter(grey ? new ColorMatrixColorFilter(greyMatrix) : null);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        calculate = true;
        foregroundCalculate = true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (getAlpha() == 0f) {
            return;
        }

        int width = getWidth() - getPaddingLeft() - getPaddingRight();
        int height = getHeight() - getPaddingTop() - getPaddingBottom();

        //noinspection IfStatementWithIdenticalBranches
        if (error) {
            canvas.save();

            textPaint.getTextBounds(errorText, 0, errorText.length(), tmpTextRect);
            float x = width / 2f - tmpTextRect.exactCenterX();
            float y = height / 2f - tmpTextRect.exactCenterY();
            canvas.drawText(errorText, x + getPaddingLeft(), y + getPaddingTop(), textPaint);

            canvas.restore();
        } else {
            if (bitmap == null) {
                return;
            }

            if (calculate) {
                calculate = false;
                bitmapRect.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
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

                bitmapShader.setLocalMatrix(matrix);
                paint.setShader(bitmapShader);
            }

            canvas.save();
            canvas.clipRect(outputRect);

            if (circular) {
                canvas.drawRoundRect(outputRect, width / 2f, height / 2f, paint);
            } else {
                canvas.drawRoundRect(outputRect, rounding, rounding, paint);
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

    @Override
    public void drawableHotspotChanged(float x, float y) {
        super.drawableHotspotChanged(x, y);

        if (foreground != null) {
            foreground.setHotspot(x, y);
        }
    }

    private void onImageSet(boolean isImmediate) {
        clearAnimation();
        if (!isImmediate) {
            setAlpha(0f);
            fadeIn = ObjectAnimator.ofFloat(this, View.ALPHA, 0f, 1f).setDuration(200);
            fadeIn.start();
        } else {
            if (fadeIn != null) {
                fadeIn.cancel();
                fadeIn = null;
            }
            setAlpha(1f);
        }
    }

    private void setImageBitmap(Bitmap bitmap) {
        bitmapShader = null;
        paint.setShader(null);

        this.bitmap = bitmap;
        if (bitmap != null) {
            calculate = true;
            bitmapShader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        }
        invalidate();
    }

    @Override
    public void onBitmapFailure(Bitmap errormap, Exception e) {
        if (e instanceof NetUtils.HttpCodeException) {
            errorText = String.valueOf(((NetUtils.HttpCodeException) e).code);
        } else {
            errorText = getString(R.string.thumbnail_load_failed_network);
        }
        error = true;

        onImageSet(true);
        invalidate();
        bitmapCall = null;
    }

    @Override
    public void onBitmapSuccess(@NonNull Bitmap bitmap, boolean fromCache) {
        setImageBitmap(bitmap);
        onImageSet(fromCache);
        bitmapCall = null;
    }
}
