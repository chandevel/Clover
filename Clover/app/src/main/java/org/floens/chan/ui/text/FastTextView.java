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
package org.floens.chan.ui.text;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.Spannable;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.LruCache;
import android.view.MotionEvent;
import android.view.View;

import org.floens.chan.R;
import org.floens.chan.ui.cell.PostCell;
import org.floens.chan.utils.Logger;

import static org.floens.chan.utils.AndroidUtils.sp;

/**
 * A simple implementation of a TextView that caches the used StaticLayouts for performance.<br>
 * This view was made for {@link PostCell} and may have untested behaviour with other layouts.
 */
public class FastTextView extends View {
    private static final String TAG = "FastTextView";
    private static LruCache<Long, StaticLayout> textCache = new LruCache<>(1000);

    private TextPaint paint;
    private boolean singleLine;

    private CharSequence text;

    private boolean update = false;
    private StaticLayout layout;
    private int width;
    private FastTextViewMovementMethod movementMethod;

    public FastTextView(Context context) {
        this(context, null);
    }

    public FastTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FastTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.FastTextView);
        setTextColor(a.getColor(R.styleable.FastTextView_textColor, 0xff000000));
        setTextSize(a.getDimensionPixelSize(R.styleable.FastTextView_textSize, 15));
        singleLine = a.getBoolean(R.styleable.FastTextView_singleLine, false);
        a.recycle();
    }

    public void setText(CharSequence text) {
        if (!TextUtils.equals(this.text, text)) {
            this.text = text;

            update = true;
            invalidate();
            requestLayout();
        }
    }

    public void setTextSize(float size) {
        int sizeSp = sp(size);
        if (paint.getTextSize() != sizeSp) {
            paint.setTextSize(sizeSp);
            update = true;
            invalidate();
        }
    }

    public void setTextColor(int color) {
        if (paint.getColor() != color) {
            paint.setColor(color);
            update = true;
            invalidate();
        }
    }

    public void setMovementMethod(FastTextViewMovementMethod movementMethod) {
        if (this.movementMethod != movementMethod) {
            this.movementMethod = movementMethod;

            if (movementMethod != null) {
                setFocusable(true);
                setClickable(true);
                setLongClickable(true);
            } else {
                setFocusable(false);
                setClickable(false);
                setLongClickable(false);
            }

            update = true;
            invalidate();
        }
    }

    public StaticLayout getLayout() {
        return layout;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean handled = false;

        if (movementMethod != null && text instanceof Spannable && layout != null && isEnabled()) {
            handled |= movementMethod.onTouchEvent(this, (Spannable) layout.getText(), event);
        }

        return handled || super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        updateLayout();

        if (layout != null) {
            canvas.save();
            canvas.translate(getPaddingLeft(), getPaddingTop());
            layout.draw(canvas);
            canvas.restore();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

//        Logger.test("%X %s %s", System.identityHashCode(this), MeasureSpec.toString(widthMeasureSpec), MeasureSpec.toString(heightMeasureSpec));

        if ((widthMode == MeasureSpec.AT_MOST || widthMode == MeasureSpec.UNSPECIFIED) && !singleLine) {
            throw new IllegalArgumentException("FasTextView only supports wrapping widths on a single line");
        }

        int width = 0;
        if (widthMode == MeasureSpec.EXACTLY) {
            width = widthSize;
        } else if ((widthMode == MeasureSpec.AT_MOST || widthMode == MeasureSpec.UNSPECIFIED) && !TextUtils.isEmpty(text)) {
            width = (int) Layout.getDesiredWidth(text, paint) + getPaddingLeft() + getPaddingRight();
            if (widthMode == MeasureSpec.AT_MOST) {
                width = Math.min(width, widthSize);
            }
        }

        if (width > 0) {
            if (this.width != width) {
                this.width = width;
                update = true;
            }

            updateLayout();

            if (layout != null) {
                int height;
                if (heightMode == MeasureSpec.EXACTLY) {
                    height = heightSize;
                } else {
                    height = layout.getHeight() + getPaddingTop() + getPaddingBottom();
                    if (heightMode == MeasureSpec.AT_MOST) {
                        height = Math.min(height, heightSize);
                    }
                }

                setMeasuredDimension(width, height);
            } else {
                int height;
                if (heightMode == MeasureSpec.EXACTLY) {
                    height = heightSize;
                } else {
                    height = 0;
                }

                setMeasuredDimension(width, height);
            }
        } else {
            // Width is 0, ignore
            Logger.w(TAG, "Width = 0");
            setMeasuredDimension(0, 0);
        }
    }

    private void updateLayout() {
        if (!TextUtils.isEmpty(text)) {
            if (update) {
                int layoutWidth = width - getPaddingLeft() - getPaddingRight();
                if (layoutWidth > 0) {
//                    long start = Time.startTiming();

                    // The StaticLayouts are cached with the static textCache LRU map

                    // Use .toString() to make sure we take the hashcode from the string representation
                    // and not from any spannables that are in it
                    long cacheKey = text.toString().hashCode();
                    cacheKey = 31 * cacheKey + paint.getColor();
                    cacheKey = 31 * cacheKey + Float.floatToIntBits(paint.getTextSize());
                    cacheKey = 31 * cacheKey + layoutWidth;

                    StaticLayout cached = textCache.get(cacheKey);
                    if (cached == null) {
//                        Logger.test("staticlayout cache miss: text = %s", text);
                        cached = getStaticLayout(layoutWidth);
                        textCache.put(cacheKey, cached);
                    } else {
//                        Logger.test("staticlayout cache hit");
                        // Make sure the layout has the actual text, color, size and width, hashcodes almost never collide
                        Paint cachedPaint = cached.getPaint();
                        if (!text.toString().equals(cached.getText().toString()) ||
                                cachedPaint.getColor() != paint.getColor() ||
                                cachedPaint.getTextSize() != paint.getTextSize() ||
                                cached.getWidth() != layoutWidth) {
                            Logger.w(TAG, "Cache miss with the same hashcode %x: \"%s\" \"%s\"!", cacheKey, text.toString(), cached.getText().toString());
                            cached = getStaticLayout(layoutWidth);
                        }
                    }

                    layout = cached;
//                    Time.endTiming(Integer.toHexString(System.identityHashCode(this)) + " staticlayout for width = " + layoutWidth + "\t", start);
                } else {
                    layout = null;
                }
            }
        } else {
            layout = null;
        }
        update = false;
    }

    private StaticLayout getStaticLayout(int layoutWidth) {
//        Logger.test("new staticlayout width=%d", layoutWidth);
        return new StaticLayout(text, paint, layoutWidth, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
    }
}
