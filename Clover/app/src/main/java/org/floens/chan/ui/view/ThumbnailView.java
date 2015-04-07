package org.floens.chan.ui.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;

import org.floens.chan.ChanApplication;

public class ThumbnailView extends View implements ImageLoader.ImageListener {
    private ImageLoader.ImageContainer container;
    private int fadeTime = 200;

    private boolean circular = false;

    private boolean calculate;
    private Bitmap bitmap;
    private RectF bitmapRect = new RectF();
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private RectF drawRect = new RectF();
    private RectF outputRect = new RectF();

    private Matrix matrix = new Matrix();
    BitmapShader bitmapShader;
    private Paint roundPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

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
    }

    public void setUrl(String url, int width, int height) {
        if (container != null && container.getRequestUrl().equals(url)) {
            return;
        }

        if (container != null) {
            container.cancelRequest();
            container = null;
            setImageBitmap(null);
        }

        if (!TextUtils.isEmpty(url)) {
            container = ChanApplication.getVolleyImageLoader().get(url, this, width, height);
        }
    }

    public void setCircular(boolean circular) {
        this.circular = circular;
    }

    public void setFadeTime(int fadeTime) {
        this.fadeTime = fadeTime;
    }

    public Bitmap getBitmap() {
        return bitmap;
    }

    @Override
    public void onResponse(ImageLoader.ImageContainer response, boolean isImmediate) {
        if (response.getBitmap() != null) {
            setImageBitmap(response.getBitmap());

            clearAnimation();
            if (fadeTime > 0 && !isImmediate) {
                setAlpha(0f);
                animate().alpha(1f).setDuration(fadeTime);
            } else {
                setAlpha(1f);
            }
        }
    }

    @Override
    public void onErrorResponse(VolleyError error) {
        error.printStackTrace();
    }

    @Override
    protected boolean onSetAlpha(int alpha) {
        if (circular) {
            roundPaint.setAlpha(alpha);
        } else {
            paint.setAlpha(alpha);
        }
        invalidate();

        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (bitmap == null || getAlpha() == 0f) {
            return;
        }

        int width = getWidth() - getPaddingLeft() - getPaddingRight();
        int height = getHeight() - getPaddingTop() - getPaddingBottom();

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

            if (circular) {
                bitmapShader.setLocalMatrix(matrix);
                roundPaint.setShader(bitmapShader);
            }
        }

        canvas.save();
        canvas.clipRect(outputRect);
        if (circular) {
            canvas.drawRoundRect(outputRect, width / 2, height / 2, roundPaint);
        } else {
            canvas.drawBitmap(bitmap, matrix, paint);
        }
        canvas.restore();
    }

    private void setImageBitmap(Bitmap bitmap) {
        bitmapShader = null;
        roundPaint.setShader(null);

        this.bitmap = bitmap;
        if (bitmap != null) {
            calculate = true;
            if (circular) {
                bitmapShader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            }
        }
        invalidate();
    }
}
