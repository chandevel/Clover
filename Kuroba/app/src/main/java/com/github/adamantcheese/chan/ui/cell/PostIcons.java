package com.github.adamantcheese.chan.ui.cell;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.PostHttpIcon;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.ui.cell.PostCell.PostIconsHttpIcon;

import java.util.ArrayList;
import java.util.List;

import static android.view.View.MeasureSpec.EXACTLY;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;

public class PostIcons
        extends View {
    protected static final int STICKY_FLAG = 0x1;
    protected static final int CLOSED_FLAG = 0x2;
    protected static final int DELETED_FLAG = 0x4;
    protected static final int ARCHIVED_FLAG = 0x8;
    protected static final int HTTP_ICONS_FLAG = 0x10;

    private int height;
    private int spacing;
    private int iconFlags;
    private int previousIcons;
    private final RectF drawRect = new RectF();

    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect textRect = new Rect();

    private int httpIconTextColor;
    private int httpIconTextSize;

    private List<PostIconsHttpIcon> httpIcons;

    public PostIcons(Context context) {
        this(context, null);
    }

    public PostIcons(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PostIcons(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        textPaint.setTypeface(Typeface.create((String) null, Typeface.ITALIC));
        setVisibility(GONE);
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public void setSpacing(int spacing) {
        this.spacing = spacing;
    }

    public void edit() {
        previousIcons = iconFlags;
        httpIcons = null;
    }

    public void apply() {
        if (previousIcons != iconFlags) {
            // Require a layout only if the height changed
            if (previousIcons == 0 || iconFlags == 0) {
                setVisibility(iconFlags == 0 ? GONE : VISIBLE);
                requestLayout();
            }

            invalidate();
        }
    }

    public void setHttpIcons(List<PostHttpIcon> icons, int size) {
        if (icons == null) return;
        httpIconTextColor = getAttrColor(getContext(), R.attr.post_details_color);
        httpIconTextSize = size;
        httpIcons = new ArrayList<>(icons.size());
        for (PostHttpIcon icon : icons) {
            httpIcons.add(new PostIconsHttpIcon(this, icon));
        }
    }

    public void cancelRequests() {
        if (httpIcons != null) {
            for (PostIconsHttpIcon httpIcon : httpIcons) {
                httpIcon.cancel();
            }
        }
    }

    public void set(int icon, boolean enable) {
        if (enable) {
            iconFlags |= icon;
        } else {
            iconFlags &= ~icon;
        }
    }

    public boolean get(int icon) {
        return (iconFlags & icon) == icon;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int measureHeight = iconFlags == 0 ? 0 : (height + getPaddingTop() + getPaddingBottom());

        setMeasuredDimension(widthMeasureSpec, MeasureSpec.makeMeasureSpec(measureHeight, EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (iconFlags != 0) {
            canvas.save();
            canvas.translate(getPaddingLeft(), getPaddingTop());

            int offset = 0;

            if (get(STICKY_FLAG)) {
                offset += drawBitmap(canvas, BitmapRepository.stickyIcon, offset);
            }

            if (get(CLOSED_FLAG)) {
                offset += drawBitmap(canvas, BitmapRepository.closedIcon, offset);
            }

            if (get(DELETED_FLAG)) {
                offset += drawBitmap(canvas, BitmapRepository.trashIcon, offset);
            }

            if (get(ARCHIVED_FLAG)) {
                offset += drawBitmap(canvas, BitmapRepository.archivedIcon, offset);
            }

            if (get(HTTP_ICONS_FLAG)) {
                for (PostIconsHttpIcon httpIcon : httpIcons) {
                    if (httpIcon.bitmap != null) {
                        offset += drawBitmap(canvas, httpIcon.bitmap, offset);

                        textPaint.setColor(httpIconTextColor);
                        textPaint.setTextSize(httpIconTextSize);
                        textPaint.getTextBounds(httpIcon.name, 0, httpIcon.name.length(), textRect);
                        float y = height / 2f - textRect.exactCenterY();
                        canvas.drawText(httpIcon.name, offset, y, textPaint);
                        offset += textRect.width() + spacing;
                    }
                }
            }

            canvas.restore();
        }
    }

    private int drawBitmap(Canvas canvas, Bitmap bitmap, int offset) {
        int width = (int) (((float) height / bitmap.getHeight()) * bitmap.getWidth());
        drawRect.set(offset, 0f, offset + width, height);
        canvas.drawBitmap(bitmap, null, drawRect, null);
        return width + spacing;
    }
}
