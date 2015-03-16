package org.floens.chan.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.widget.ImageView;

public class ClippingImageView extends ImageView {
    private Rect clipRect = new Rect();

    public ClippingImageView(Context context) {
        super(context);
    }

    public ClippingImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ClippingImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!clipRect.isEmpty() && (clipRect.width() < getWidth() || clipRect.height() < getHeight())) {
            canvas.clipRect(clipRect);
        }

        super.onDraw(canvas);
    }

    public void clip(Rect rect) {
        if (rect == null) {
            clipRect.setEmpty();
        } else {
            clipRect.set(rect);
        }
        invalidate();
    }
}
