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

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostHttpIcon;
import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import okhttp3.Call;
import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.ui.cell.PostIcons.FLAGS.ARCHIVED_FLAG;
import static com.github.adamantcheese.chan.ui.cell.PostIcons.FLAGS.CLOSED_FLAG;
import static com.github.adamantcheese.chan.ui.cell.PostIcons.FLAGS.DELETED_FLAG;
import static com.github.adamantcheese.chan.ui.cell.PostIcons.FLAGS.HTTP_ICONS_FLAG_NO_TEXT;
import static com.github.adamantcheese.chan.ui.cell.PostIcons.FLAGS.HTTP_ICONS_FLAG_TEXT;
import static com.github.adamantcheese.chan.ui.cell.PostIcons.FLAGS.STICKY_FLAG;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;

public class PostIcons
        extends View {
    enum FLAGS {
        STICKY_FLAG,
        CLOSED_FLAG,
        DELETED_FLAG,
        ARCHIVED_FLAG,
        HTTP_ICONS_FLAG_TEXT,
        HTTP_ICONS_FLAG_NO_TEXT
    }

    private final BitSet iconFlags = new BitSet(FLAGS.values().length);

    private final float spacing;
    private final RectF drawRect = new RectF();

    private int httpIconTextColor;
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect textRect = new Rect();

    private final List<PostIconsHttpIcon> httpIcons = new ArrayList<>();

    public PostIcons(Context context) {
        this(context, null);
    }

    public PostIcons(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PostIcons(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        spacing = dp(context, 4);
        textPaint.setTypeface(Typeface.create((String) null, Typeface.ITALIC));
        setVisibility(GONE);
    }

    public void set(Post post, boolean displayText) {
        edit();
        setForFlag(STICKY_FLAG, post.isSticky());
        setForFlag(CLOSED_FLAG, post.isClosed());
        setForFlag(DELETED_FLAG, post.deleted.get());
        setForFlag(ARCHIVED_FLAG, post.isArchived());
        setHttpIcons(post.httpIcons, displayText);
        apply();
    }

    public void clear() {
        edit();
        iconFlags.clear();
        apply();
    }

    private void edit() {
        for (PostIconsHttpIcon httpIcon : httpIcons) {
            if (httpIcon.request != null) {
                httpIcon.request.cancel();
                httpIcon.request = null;
            }
        }
        httpIcons.clear();
    }

    private void setForFlag(FLAGS icon, boolean enable) {
        if (enable) {
            iconFlags.set(icon.ordinal());
        } else {
            iconFlags.clear(icon.ordinal());
        }
    }

    private void setHttpIcons(List<PostHttpIcon> icons, boolean displayText) {
        if (icons == null) return;
        setForFlag(displayText ? HTTP_ICONS_FLAG_TEXT : HTTP_ICONS_FLAG_NO_TEXT, true);
        httpIconTextColor = getAttrColor(getContext(), R.attr.post_details_color);
        for (PostHttpIcon icon : icons) {
            httpIcons.add(new PostIconsHttpIcon(icon));
        }
    }

    private void apply() {
        setVisibility(hasAnyContent() ? VISIBLE : GONE);
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int returnedWidth = drawOrMeasure(null);
        int returnedHeight = hasAnyContent() ? getLayoutParams().height + getPaddingTop() + getPaddingBottom() : 0;

        switch (widthMode) {
            case MeasureSpec.EXACTLY:
                returnedWidth = widthSize;
                break;
            case MeasureSpec.UNSPECIFIED:
                break;
            case MeasureSpec.AT_MOST:
                returnedWidth = Math.min(widthSize, returnedWidth);
                break;
        }

        switch (heightMode) {
            case MeasureSpec.EXACTLY:
                returnedHeight = heightSize;
                break;
            case MeasureSpec.UNSPECIFIED:
                break;
            case MeasureSpec.AT_MOST:
                returnedHeight = Math.min(heightSize, returnedHeight);
                break;
        }

        setMeasuredDimension(returnedWidth, returnedHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        drawOrMeasure(canvas);
    }

    private int drawOrMeasure(Canvas canvas) {
        int width = 0;
        if (hasAnyContent()) {
            if (canvas != null) {
                canvas.save();
                canvas.translate(getPaddingLeft(), getPaddingTop());
            }

            if (get(STICKY_FLAG)) {
                width += drawBitmap(canvas, BitmapRepository.stickyIcon, width);
            }

            if (get(CLOSED_FLAG)) {
                width += drawBitmap(canvas, BitmapRepository.closedIcon, width);
            }

            if (get(DELETED_FLAG)) {
                width += drawBitmap(canvas, BitmapRepository.trashIcon, width);
            }

            if (get(ARCHIVED_FLAG)) {
                width += drawBitmap(canvas, BitmapRepository.archivedIcon, width);
            }

            if (get(HTTP_ICONS_FLAG_TEXT) || get(HTTP_ICONS_FLAG_NO_TEXT)) {
                for (PostIconsHttpIcon httpIcon : httpIcons) {
                    if (httpIcon.bitmap != null) {
                        width += drawBitmap(canvas, httpIcon.bitmap, width);

                        if (get(HTTP_ICONS_FLAG_TEXT)) {
                            textPaint.setColor(httpIconTextColor);
                            textPaint.setTextSize(getLayoutParams().height);
                            textPaint.getTextBounds(httpIcon.name, 0, httpIcon.name.length(), textRect);
                            if (canvas != null) {
                                float y = getLayoutParams().height / 2f - textRect.exactCenterY();
                                canvas.drawText(httpIcon.name, width, y, textPaint);
                            }
                            width += textRect.width() + spacing;
                        }
                    }
                }
            }

            // adjust for final spacing if anything was drawn
            if (width != 0) {
                width -= spacing;
            }

            if (canvas != null) {
                canvas.restore();
            }
        }
        return width;
    }

    private boolean get(FLAGS icon) {
        return iconFlags.get(icon.ordinal());
    }

    private boolean hasAnyContent() {
        return iconFlags.cardinality() != 0;
    }

    private float drawBitmap(Canvas canvas, Bitmap bitmap, int offset) {
        float scaledWidth = (getLayoutParams().height / (float) bitmap.getHeight()) * bitmap.getWidth();
        if (canvas != null) {
            drawRect.set(offset, 0f, offset + scaledWidth, getLayoutParams().height);
            canvas.drawBitmap(bitmap, null, drawRect, null);
        }
        return scaledWidth + spacing;
    }

    class PostIconsHttpIcon {
        protected final String name;
        protected Call request;
        protected Bitmap bitmap;

        PostIconsHttpIcon(PostHttpIcon icon) {
            name = icon.description;

            icon.bitmapResult.setPassthrough(new NetUtilsClasses.BitmapResult() {
                @Override
                public void onBitmapFailure(@NonNull HttpUrl source, Exception e) {
                    bitmap = BitmapRepository.error;
                    requestLayout();
                }

                @Override
                public void onBitmapSuccess(@NonNull HttpUrl source, @NonNull Bitmap bitmap, boolean fromCache) {
                    PostIconsHttpIcon.this.bitmap = bitmap;
                    requestLayout();
                }
            });

            // don't cache stuff in memory for icons since they could be sprite-mapped (ie 4chan)
            if (icon.url != null) {
                request = NetUtils.makeBitmapRequest(icon.url, icon.bitmapResult);
            } else {
                icon.bitmapResult.onBitmapSuccess(null, bitmap, true);
            }
        }
    }
}
