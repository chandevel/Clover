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

import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import okhttp3.Call;
import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.ui.cell.PostIcons.FlagType.ARCHIVED_FLAG;
import static com.github.adamantcheese.chan.ui.cell.PostIcons.FlagType.CLOSED_FLAG;
import static com.github.adamantcheese.chan.ui.cell.PostIcons.FlagType.DELETED_FLAG;
import static com.github.adamantcheese.chan.ui.cell.PostIcons.FlagType.HTTP_ICONS_FLAG_NO_TEXT;
import static com.github.adamantcheese.chan.ui.cell.PostIcons.FlagType.HTTP_ICONS_FLAG_TEXT;
import static com.github.adamantcheese.chan.ui.cell.PostIcons.FlagType.STICKY_FLAG;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;

public class PostIcons
        extends View {
    enum FlagType {
        STICKY_FLAG,
        CLOSED_FLAG,
        DELETED_FLAG,
        ARCHIVED_FLAG,
        HTTP_ICONS_FLAG_TEXT,
        HTTP_ICONS_FLAG_NO_TEXT
    }

    private final BitSet iconFlags = new BitSet(FlagType.values().length);

    private final float spacing;
    private final RectF drawRect = new RectF();

    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect textRect = new Rect();

    private final Set<PostIconsHttpIcon> httpIcons = new HashSet<>();

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
        textPaint.setColor(getAttrColor(context, R.attr.post_details_color));
        setVisibility(GONE);
    }

    public void set(Post post, boolean displayText) {
        setForFlag(STICKY_FLAG, post.isSticky());
        setForFlag(CLOSED_FLAG, post.isClosed());
        setForFlag(DELETED_FLAG, post.deleted.get());
        setForFlag(ARCHIVED_FLAG, post.isArchived());
        setHttpIcons(post.httpIcons, displayText);
        setVisibility(hasAnyContent() ? VISIBLE : GONE);
        requestLayout();
    }

    private void setForFlag(FlagType icon, boolean enable) {
        iconFlags.set(icon.ordinal(), enable);
    }

    private void setHttpIcons(List<PostHttpIcon> icons, boolean displayText) {
        boolean hasIcons = icons != null && !icons.isEmpty();
        setForFlag(displayText ? HTTP_ICONS_FLAG_TEXT : HTTP_ICONS_FLAG_NO_TEXT, hasIcons);
        if (!hasIcons) return;
        // de-dupe based on description and request those
        for (PostHttpIcon icon : icons) {
            httpIcons.add(new PostIconsHttpIcon(icon));
        }
        for (PostIconsHttpIcon icon : httpIcons) {
            icon.doRequest();
        }
    }

    private boolean hasAnyContent() {
        return iconFlags.cardinality() != 0;
    }

    public void clear() {
        for (PostIconsHttpIcon httpIcon : httpIcons) {
            if (httpIcon.request != null) {
                httpIcon.request.cancel();
                httpIcon.request = null;
            }
            httpIcon.bitmap = null;
        }
        httpIcons.clear();
        iconFlags.clear();
        setVisibility(GONE);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int returnedWidth = drawOrMeasure(null);
        int returnedHeight = hasAnyContent() ? getLayoutParams().height : 0;

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

    /**
     * Draw to the given canvas, if provided, and return the EXACT width of this view, excluding padding
     * @param canvas An optional canvas to draw to
     * @return The exact width of this view, excluding padding
     */
    private int drawOrMeasure(Canvas canvas) {
        int width = 0;
        if (hasAnyContent()) {
            if (canvas != null) {
                canvas.save();
                canvas.translate(getPaddingLeft(), getPaddingTop());
            }

            if (getFlagEnabled(STICKY_FLAG)) {
                width += drawBitmap(canvas, BitmapRepository.stickyIcon, width);
            }

            if (getFlagEnabled(CLOSED_FLAG)) {
                width += drawBitmap(canvas, BitmapRepository.closedIcon, width);
            }

            if (getFlagEnabled(DELETED_FLAG)) {
                width += drawBitmap(canvas, BitmapRepository.trashIcon, width);
            }

            if (getFlagEnabled(ARCHIVED_FLAG)) {
                width += drawBitmap(canvas, BitmapRepository.archivedIcon, width);
            }

            if (getFlagEnabled(HTTP_ICONS_FLAG_TEXT) || getFlagEnabled(HTTP_ICONS_FLAG_NO_TEXT)) {
                for (PostIconsHttpIcon httpIcon : httpIcons) {
                    if (httpIcon.bitmap != null) {
                        width += drawBitmap(canvas, httpIcon.bitmap, width);

                        if (getFlagEnabled(HTTP_ICONS_FLAG_TEXT)) {
                            textPaint.setTextSize(getLayoutParams().height);
                            textPaint.getTextBounds(httpIcon.icon.description,
                                    0,
                                    httpIcon.icon.description.length(),
                                    textRect
                            );
                            if (canvas != null) {
                                float y = getLayoutParams().height / 2f - textRect.exactCenterY();
                                canvas.drawText(httpIcon.icon.description, width, y, textPaint);
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

    private boolean getFlagEnabled(FlagType icon) {
        return iconFlags.get(icon.ordinal());
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
        protected final PostHttpIcon icon;
        protected Bitmap bitmap;

        protected Call request;

        PostIconsHttpIcon(PostHttpIcon icon) {
            this.icon = icon;
        }

        public void doRequest() {
            if (bitmap != null) {
                invalidate();
                return;
            }
            if (request != null) return;
            request = NetUtils.makeBitmapRequest(icon.url,
                    icon.bitmapResult.setPassthrough(new NetUtilsClasses.BitmapResult() {
                        @Override
                        public void onBitmapFailure(@NonNull HttpUrl source, Exception e) {
                            request = null;
                            bitmap = BitmapRepository.error;
                            requestLayout();
                        }

                        @Override
                        public void onBitmapSuccess(
                                @NonNull HttpUrl source, @NonNull Bitmap bitmap, boolean fromCache
                        ) {
                            request = null;
                            PostIconsHttpIcon.this.bitmap = bitmap;
                            requestLayout();
                        }
                    })
            );
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PostIconsHttpIcon that = (PostIconsHttpIcon) o;
            return Objects.equals(icon.description, that.icon.description);
        }

        @Override
        public int hashCode() {
            return Objects.hash(icon.description);
        }
    }
}
