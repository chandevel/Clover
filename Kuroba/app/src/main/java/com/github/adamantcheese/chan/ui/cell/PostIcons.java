package com.github.adamantcheese.chan.ui.cell;

import static com.github.adamantcheese.chan.ui.cell.PostIcons.FlagType.*;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostHttpIcon;
import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;

import java.util.*;

import okhttp3.Call;
import okhttp3.HttpUrl;

public class PostIcons
        extends View {

    enum FlagType {
        STICKY_FLAG(BitmapRepository.stickyIcon),
        CLOSED_FLAG(BitmapRepository.closedIcon),
        DELETED_FLAG(BitmapRepository.trashIcon),
        ARCHIVED_FLAG(BitmapRepository.archivedIcon),
        HTTP_ICONS_FLAG_TEXT(null),
        HTTP_ICONS_FLAG_NO_TEXT(null);

        Bitmap forFlag;

        FlagType(Bitmap bitmap) {
            forFlag = bitmap;
        }
    }

    private final BitSet iconFlags = new BitSet(FlagType.values().length);

    private final float spacing;
    private final RectF drawRect = new RectF();

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
        textPaint.setColor(getAttrColor(context, R.attr.post_details_color));
        setVisibility(GONE);
    }

    public void set(Post post, boolean displayText) {
        setForFlag(STICKY_FLAG, post.sticky);
        setForFlag(CLOSED_FLAG, post.closed);
        setForFlag(DELETED_FLAG, post.deleted);
        setForFlag(ARCHIVED_FLAG, post.archived);
        setHttpIcons(post.httpIcons, displayText);
        setVisibility(iconFlags.cardinality() != 0 ? VISIBLE : GONE);
        requestLayout();
    }

    private void setForFlag(FlagType icon, boolean enable) {
        iconFlags.set(icon.ordinal(), enable);
    }

    private void setHttpIcons(List<PostHttpIcon> newIcons, boolean displayText) {
        boolean hasNewIcons = !newIcons.isEmpty();
        setForFlag(displayText ? HTTP_ICONS_FLAG_TEXT : HTTP_ICONS_FLAG_NO_TEXT, hasNewIcons);
        if (!hasNewIcons) {
            clearHttpRequests();
            return;
        }
        // if already set, don't re-request stuff; use clear() first
        if (!httpIcons.isEmpty()) return;
        for (PostHttpIcon icon : newIcons) {
            httpIcons.add(new PostIconsHttpIcon(icon));
        }
    }

    public void clear() {
        clearHttpRequests();
        iconFlags.clear();
        setVisibility(GONE);
    }

    private void clearHttpRequests() {
        for (PostIconsHttpIcon httpIcon : httpIcons) {
            if (httpIcon.request != null) {
                httpIcon.request.cancel();
                httpIcon.request = null;
            }
            httpIcon.bitmap = null;
        }
        httpIcons.clear();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int returnedWidth = drawOrMeasure(null);
        int returnedHeight = iconFlags.cardinality() != 0 ? getLayoutParams().height : 0;

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
     *
     * @param canvas An optional canvas to draw to
     * @return The exact width of this view, excluding padding
     */
    private int drawOrMeasure(Canvas canvas) {
        int width = 0;
        if (iconFlags.cardinality() != 0) {
            if (canvas != null) {
                canvas.save();
                canvas.translate(getPaddingLeft(), getPaddingTop());
            }

            for (FlagType f : FlagType.values()) {
                if (!iconFlags.get(f.ordinal())) continue;
                switch (f) {
                    case STICKY_FLAG:
                    case CLOSED_FLAG:
                    case DELETED_FLAG:
                    case ARCHIVED_FLAG:
                        width += drawBitmap(canvas, f.forFlag, width);
                        break;
                    case HTTP_ICONS_FLAG_TEXT:
                    case HTTP_ICONS_FLAG_NO_TEXT:
                        for (PostIconsHttpIcon httpIcon : httpIcons) {
                            width += drawBitmap(canvas, httpIcon.bitmap, width);

                            if (f == HTTP_ICONS_FLAG_TEXT) {
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
                        break;
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

    private float drawBitmap(Canvas canvas, Bitmap bitmap, int offset) {
        if (bitmap == null) return 0;
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
            NetUtilsClasses.BitmapResult resultPassthrough = new NetUtilsClasses.BitmapResult() {
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
            };
            NetUtilsClasses.BitmapResult withPassthrough = icon.bitmapResult.setPassthrough(resultPassthrough);
            if (icon.url == null) {
                withPassthrough.onBitmapSuccess(null, null, true);
            } else {
                request = NetUtils.makeBitmapRequest(icon.url, withPassthrough);
            }
        }
    }
}
