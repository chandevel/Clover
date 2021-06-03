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
import java.util.List;

import okhttp3.Call;
import okhttp3.HttpUrl;

import static android.view.View.MeasureSpec.EXACTLY;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;

public class PostIcons
        extends View {
    protected static final int STICKY_FLAG = 0x1;
    protected static final int CLOSED_FLAG = 0x2;
    protected static final int DELETED_FLAG = 0x4;
    protected static final int ARCHIVED_FLAG = 0x8;
    protected static final int HTTP_ICONS_FLAG_TEXT = 0x10;
    protected static final int HTTP_ICONS_FLAG_NO_TEXT = 0x20;

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

    private void edit() {
        previousIcons = iconFlags;
        if (httpIcons != null) {
            for (PostIconsHttpIcon httpIcon : httpIcons) {
                httpIcon.cancel();
            }
        }
        httpIcons = null;
    }

    private void apply() {
        if (previousIcons != iconFlags) {
            // Require a layout only if the height changed
            if (previousIcons == 0 || iconFlags == 0) {
                setVisibility(iconFlags == 0 ? GONE : VISIBLE);
                requestLayout();
            }

            invalidate();
        }
    }

    private void setHttpIcons(List<PostHttpIcon> icons, boolean displayText, int size) {
        if (icons == null) return;
        set(displayText ? PostIcons.HTTP_ICONS_FLAG_TEXT : PostIcons.HTTP_ICONS_FLAG_NO_TEXT, true);
        httpIconTextColor = getAttrColor(getContext(), R.attr.post_details_color);
        httpIconTextSize = size;
        httpIcons = new ArrayList<>(icons.size());
        for (PostHttpIcon icon : icons) {
            httpIcons.add(new PostIconsHttpIcon(this, icon));
        }
    }

    private void set(int icon, boolean enable) {
        if (enable) {
            iconFlags |= icon;
        } else {
            iconFlags &= ~icon;
        }
    }

    public void setWithText(Post post, int iconSizePx) {
        edit();
        set(PostIcons.STICKY_FLAG, post.isSticky());
        set(PostIcons.CLOSED_FLAG, post.isClosed());
        set(PostIcons.DELETED_FLAG, post.deleted.get());
        set(PostIcons.ARCHIVED_FLAG, post.isArchived());
        setHttpIcons(post.httpIcons, true, iconSizePx);
        apply();
    }

    public void setWithoutText(Post post, int iconSizePx) {
        edit();
        set(PostIcons.STICKY_FLAG, post.isSticky());
        set(PostIcons.CLOSED_FLAG, post.isClosed());
        set(PostIcons.DELETED_FLAG, post.deleted.get());
        set(PostIcons.ARCHIVED_FLAG, post.isArchived());
        setHttpIcons(post.httpIcons, false, iconSizePx);
        apply();
    }

    public void clear() {
        edit();
        iconFlags = 0;
        apply();
    }

    private boolean get(int icon) {
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

            if (get(HTTP_ICONS_FLAG_TEXT) || get(HTTP_ICONS_FLAG_NO_TEXT)) {
                for (PostIconsHttpIcon httpIcon : httpIcons) {
                    if (httpIcon.bitmap != null) {
                        offset += drawBitmap(canvas, httpIcon.bitmap, offset);

                        if (get(HTTP_ICONS_FLAG_TEXT)) {
                            textPaint.setColor(httpIconTextColor);
                            textPaint.setTextSize(httpIconTextSize);
                            textPaint.getTextBounds(httpIcon.name, 0, httpIcon.name.length(), textRect);
                            float y = height / 2f - textRect.exactCenterY();
                            canvas.drawText(httpIcon.name, offset, y, textPaint);
                            offset += textRect.width() + spacing;
                        }
                    }
                }
            }

            canvas.restore();
        }
    }

    private int drawBitmap(Canvas canvas, Bitmap bitmap, int offset) {
        int width = getScaledWidth(bitmap);
        drawRect.set(offset, 0f, offset + width, height);
        canvas.drawBitmap(bitmap, null, drawRect, null);
        return width + spacing;
    }

    private int getScaledWidth(Bitmap bitmap) {
        return (int) (((float) height / bitmap.getHeight()) * bitmap.getWidth());
    }

    static class PostIconsHttpIcon {
        protected final String name;
        protected Call request;
        protected Bitmap bitmap;

        PostIconsHttpIcon(final PostIcons postIcons, PostHttpIcon icon) {
            name = icon.description;

            icon.bitmapResult.setPassthrough(new NetUtilsClasses.BitmapResult() {
                @Override
                public void onBitmapFailure(@NonNull HttpUrl source, Exception e) {
                    bitmap = BitmapRepository.error;
                    postIcons.invalidate();
                }

                @Override
                public void onBitmapSuccess(@NonNull HttpUrl source, @NonNull Bitmap bitmap, boolean fromCache) {
                    PostIconsHttpIcon.this.bitmap = bitmap;
                    postIcons.invalidate();
                }
            });

            // don't cache stuff in memory for icons since they could be sprite-mapped (ie 4chan)
            if (icon.url != null) {
                request = NetUtils.makeBitmapRequest(icon.url, icon.bitmapResult);
            } else {
                icon.bitmapResult.onBitmapSuccess(null, bitmap, true);
            }
        }

        void cancel() {
            if (request != null) {
                request.cancel();
                request = null;
            }
        }
    }
}
