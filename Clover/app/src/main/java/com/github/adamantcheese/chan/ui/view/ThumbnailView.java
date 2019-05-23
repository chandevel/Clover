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

import com.adamantcheese.github.chan.R;

import static com.github.adamantcheese.chan.Chan.injector;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.sp;

public class ThumbnailView extends View implements ImageLoader.ImageListener {
    private ImageLoader.ImageContainer container;

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
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

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

    @SuppressWarnings({"ConstantConditions"})
    @Override
    public void setClickable(boolean clickable) {
        super.setClickable(clickable);

        if (clickable != this.clickable) {
            this.clickable = clickable;

            foregroundCalculate = clickable;
            if (clickable) {
                foreground = getResources().getDrawable(R.drawable.item_background);
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

    public Bitmap getBitmap() {
        return bitmap;
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
            paint.setAlpha(alpha);
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
        if (getAlpha() == 0f) {
            return;
        }

        int width = getWidth() - getPaddingLeft() - getPaddingRight();
        int height = getHeight() - getPaddingTop() - getPaddingBottom();

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
                float scale = Math.max(
                        (float) width / (float) bitmap.getWidth(),
                        (float) height / (float) bitmap.getHeight());
                float scaledX = bitmap.getWidth() * scale;
                float scaledY = bitmap.getHeight() * scale;
                float offsetX = (scaledX - width) * 0.5f;
                float offsetY = (scaledY - height) * 0.5f;

                drawRect.set(-offsetX, -offsetY, scaledX - offsetX, scaledY - offsetY);
                drawRect.offset(getPaddingLeft(), getPaddingTop());

                outputRect.set(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());

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
            animate().alpha(1f).setDuration(200);
        } else {
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
}
