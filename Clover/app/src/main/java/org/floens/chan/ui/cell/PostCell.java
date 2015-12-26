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
package org.floens.chan.ui.cell;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.support.annotation.NonNull;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.UnderlineSpan;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;

import org.floens.chan.Chan;
import org.floens.chan.R;
import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.PostImage;
import org.floens.chan.core.model.PostLinkable;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.span.AbsoluteSizeSpanHashed;
import org.floens.chan.ui.span.ForegroundColorSpanHashed;
import org.floens.chan.ui.text.FastTextView;
import org.floens.chan.ui.text.FastTextViewMovementMethod;
import org.floens.chan.ui.theme.Theme;
import org.floens.chan.ui.theme.ThemeHelper;
import org.floens.chan.ui.view.FloatingMenu;
import org.floens.chan.ui.view.FloatingMenuItem;
import org.floens.chan.ui.view.ThumbnailView;
import org.floens.chan.utils.AndroidUtils;
import org.floens.chan.utils.Time;

import java.util.ArrayList;
import java.util.List;

import static android.text.TextUtils.isEmpty;
import static org.floens.chan.utils.AndroidUtils.ROBOTO_CONDENSED_REGULAR;
import static org.floens.chan.utils.AndroidUtils.dp;
import static org.floens.chan.utils.AndroidUtils.getString;
import static org.floens.chan.utils.AndroidUtils.setRoundItemBackground;
import static org.floens.chan.utils.AndroidUtils.sp;

public class PostCell extends LinearLayout implements PostCellInterface, PostLinkable.Callback {
    private static final String TAG = "PostCell";
    private static final int COMMENT_MAX_LENGTH_BOARD = 350;

    private ThumbnailView thumbnailView;
    private FastTextView title;
    private PostIcons icons;
    private TextView comment;
    private FastTextView replies;
    private ImageView options;
    private View divider;
    private View filterMatchColor;

    private boolean commentClickable = false;
    private int detailsSizePx;
    private int countrySizePx;
    private int paddingPx;
    private boolean threadMode;
    private boolean ignoreNextOnClick;

    private boolean bound = false;
    private Theme theme;
    private Post post;
    private PostCellCallback callback;
    private boolean highlighted;
    private boolean selected;
    private int markedNo;
    private boolean showDivider;

    private OnClickListener selfClicked = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (ignoreNextOnClick) {
                ignoreNextOnClick = false;
            } else {
                callback.onPostClicked(post);
            }
        }
    };
    private PostViewMovementMethod commentMovementMethod = new PostViewMovementMethod();
    private PostViewFastMovementMethod titleMovementMethod = new PostViewFastMovementMethod();

    public PostCell(Context context) {
        super(context);
    }

    public PostCell(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PostCell(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        thumbnailView = (ThumbnailView) findViewById(R.id.thumbnail_view);
        title = (FastTextView) findViewById(R.id.title);
        icons = (PostIcons) findViewById(R.id.icons);
        comment = (TextView) findViewById(R.id.comment);
        replies = (FastTextView) findViewById(R.id.replies);
        options = (ImageView) findViewById(R.id.options);
        divider = findViewById(R.id.divider);
        filterMatchColor = findViewById(R.id.filter_match_color);

        int textSizeSp = Integer.parseInt(ChanSettings.fontSize.get());
        paddingPx = dp(textSizeSp - 6);
        detailsSizePx = sp(textSizeSp - 4);
        title.setTextSize(textSizeSp);
        title.setPadding(paddingPx, paddingPx, dp(52), 0);

        countrySizePx = sp(textSizeSp - 3);
        icons.setHeight(sp(textSizeSp));
        icons.setSpacing(dp(4));
        icons.setPadding(paddingPx, dp(4), paddingPx, 0);

        comment.setTextSize(textSizeSp);
        comment.setPadding(paddingPx, paddingPx, paddingPx, 0);

        if (ChanSettings.fontCondensed.get()) {
            comment.setTypeface(ROBOTO_CONDENSED_REGULAR);
        }

        replies.setTextSize(textSizeSp);
        replies.setPadding(paddingPx, 0, paddingPx, paddingPx);

        setRoundItemBackground(replies);
        setRoundItemBackground(options);

        RelativeLayout.LayoutParams dividerParams = (RelativeLayout.LayoutParams) divider.getLayoutParams();
        dividerParams.leftMargin = paddingPx;
        dividerParams.rightMargin = paddingPx;
        divider.setLayoutParams(dividerParams);

        thumbnailView.setClickable(true);
        thumbnailView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                callback.onThumbnailClicked(post, thumbnailView);
            }
        });
        thumbnailView.setRounding(dp(2));

        replies.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (threadMode) {
                    int repliesFromSize;
                    synchronized (post.repliesFrom) {
                        repliesFromSize = post.repliesFrom.size();
                    }

                    if (repliesFromSize > 0) {
                        callback.onShowPostReplies(post);
                    }
                }
            }
        });

        options.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ThemeHelper.getInstance().getTheme().isLightTheme) {
                    options.setImageResource(R.drawable.ic_overflow_black);
                }

                List<FloatingMenuItem> items = new ArrayList<>();

                callback.onPopulatePostOptions(post, items);

                FloatingMenu menu = new FloatingMenu(getContext(), v, items);
                menu.setCallback(new FloatingMenu.FloatingMenuCallback() {
                    @Override
                    public void onFloatingMenuItemClicked(FloatingMenu menu, FloatingMenuItem item) {
                        callback.onPostOptionClicked(post, item.getId());
                    }

                    @Override
                    public void onFloatingMenuDismissed(FloatingMenu menu) {
                        options.setImageResource(R.drawable.ic_overflow);
                    }
                });
                menu.show();
            }
        });

        setOnClickListener(selfClicked);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (post != null && bound) {
            unbindPost(post);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (post != null && !bound) {
            bindPost(theme, post);
        }
    }

    public void setPost(Theme theme, final Post post, PostCellInterface.PostCellCallback callback,
                        boolean highlighted, boolean selected, int markedNo, boolean showDivider, PostCellInterface.PostViewMode postViewMode) {
        if (this.post == post && this.highlighted == highlighted && this.selected == selected && this.markedNo == markedNo && this.showDivider == showDivider) {
            return;
        }

        if (theme == null) {
            theme = ThemeHelper.theme();
        }

        if (this.post != null && bound) {
            unbindPost(this.post);
            this.post = null;
        }

        this.theme = theme;
        this.post = post;
        this.callback = callback;
        this.highlighted = highlighted;
        this.selected = selected;
        this.markedNo = markedNo;
        this.showDivider = showDivider;

        bindPost(theme, post);
    }

    public Post getPost() {
        return post;
    }

    public ThumbnailView getThumbnailView() {
        return thumbnailView;
    }

    @Override
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public boolean hasOverlappingRendering() {
        return false;
    }

    private void bindPost(Theme theme, Post post) {
        bound = true;

        threadMode = callback.getLoadable().isThreadMode();

        setPostLinkableListener(post, this);

        replies.setClickable(threadMode);

        if (!threadMode) {
            replies.setBackgroundResource(0);
        }

        if (highlighted) {
            setBackgroundColor(theme.highlightedColor);
        } else if (post.isSavedReply) {
            setBackgroundColor(theme.savedReplyColor);
        } else if (selected) {
            setBackgroundColor(theme.selectedColor);
        } else if (threadMode) {
            setBackgroundResource(0);
        } else {
            setBackgroundResource(R.drawable.item_background);
        }

        if (post.filterHighlightedColor != 0) {
            filterMatchColor.setVisibility(View.VISIBLE);
            filterMatchColor.setBackgroundColor(post.filterHighlightedColor);
        } else {
            filterMatchColor.setVisibility(View.GONE);
        }

        if (post.hasImage) {
            thumbnailView.setVisibility(View.VISIBLE);
            thumbnailView.setUrl(post.thumbnailUrl, thumbnailView.getLayoutParams().width, thumbnailView.getLayoutParams().height);
        } else {
            thumbnailView.setVisibility(View.GONE);
            thumbnailView.setUrl(null, 0, 0);
        }

        List<CharSequence> titleParts = new ArrayList<>(5);

        if (post.subjectSpan != null) {
            titleParts.add(post.subjectSpan);
            titleParts.add("\n");
        }

        titleParts.add(post.nameTripcodeIdCapcodeSpan);

        CharSequence time;
        if (ChanSettings.postFullDate.get()) {
            time = post.date;
        } else {
            // Disabled for performance reasons
            // Force the relative date to use the english locale, and restore the previous one.
            /*Configuration c = Resources.getSystem().getConfiguration();
            Locale previousLocale = c.locale;
            c.locale = Locale.ENGLISH;
            Resources.getSystem().updateConfiguration(c, null);
            time = DateUtils.getRelativeTimeSpanString(post.time * 1000L, Time.get(), DateUtils.SECOND_IN_MILLIS, 0);
            c.locale = previousLocale;
            Resources.getSystem().updateConfiguration(c, null);*/
            time = DateUtils.getRelativeTimeSpanString(post.time * 1000L, Time.get(), DateUtils.SECOND_IN_MILLIS, 0);
        }

        String noText = "No." + post.no;
        SpannableString date = new SpannableString(noText + " " + time);
        date.setSpan(new ForegroundColorSpanHashed(theme.detailsColor), 0, date.length(), 0);
        date.setSpan(new AbsoluteSizeSpanHashed(detailsSizePx), 0, date.length(), 0);

        boolean noClickable = ChanSettings.tapNoReply.get();
        if (noClickable) {
            date.setSpan(new NoClickableSpan(), 0, noText.length(), 0);
        }

        titleParts.add(date);

        if (post.hasImage) {
            PostImage image = post.image;

            boolean postFileName = ChanSettings.postFilename.get();
            if (postFileName) {
                String filename = image.spoiler ? getString(R.string.image_spoiler_filename) : image.filename + "." + image.extension;
                SpannableString fileInfo = new SpannableString("\n" + filename);
                fileInfo.setSpan(new ForegroundColorSpanHashed(theme.detailsColor), 0, fileInfo.length(), 0);
                fileInfo.setSpan(new AbsoluteSizeSpanHashed(detailsSizePx), 0, fileInfo.length(), 0);
                fileInfo.setSpan(new UnderlineSpan(), 0, fileInfo.length(), 0);
                titleParts.add(fileInfo);
            }

            if (ChanSettings.postFileInfo.get()) {
                SpannableString fileInfo = new SpannableString((postFileName ? " " : "\n") + image.extension.toUpperCase() + " " +
                        AndroidUtils.getReadableFileSize(image.size, false) + " " +
                        image.imageWidth + "x" + image.imageHeight);
                fileInfo.setSpan(new ForegroundColorSpanHashed(theme.detailsColor), 0, fileInfo.length(), 0);
                fileInfo.setSpan(new AbsoluteSizeSpanHashed(detailsSizePx), 0, fileInfo.length(), 0);
                titleParts.add(fileInfo);
            }
        }

        title.setText(TextUtils.concat(titleParts.toArray(new CharSequence[titleParts.size()])));

        icons.edit();
        icons.set(PostIcons.STICKY, post.sticky);
        icons.set(PostIcons.CLOSED, post.closed);
        icons.set(PostIcons.DELETED, post.deleted.get());
        icons.set(PostIcons.ARCHIVED, post.archived);

        if (!isEmpty(post.country)) {
            icons.set(PostIcons.COUNTRY, true);
            icons.showCountry(post, theme, countrySizePx);
        } else {
            icons.set(PostIcons.COUNTRY, false);
        }

        icons.apply();

        CharSequence commentText;
        if (post.comment.length() > COMMENT_MAX_LENGTH_BOARD && !threadMode) {
            commentText = post.comment.subSequence(0, COMMENT_MAX_LENGTH_BOARD);
        } else {
            commentText = post.comment;
        }

        comment.setText(commentText);
        comment.setVisibility(isEmpty(commentText) && !post.hasImage ? GONE : VISIBLE);

        if (commentClickable != threadMode) {
            commentClickable = threadMode;
            if (commentClickable) {
                comment.setMovementMethod(commentMovementMethod);
                comment.setOnClickListener(selfClicked);

                if (noClickable) {
                    title.setMovementMethod(titleMovementMethod);
                }
            } else {
                comment.setOnClickListener(null);
                comment.setClickable(false);
                comment.setMovementMethod(null);
                title.setMovementMethod(null);
            }
        }

        int repliesFromSize;
        synchronized (post.repliesFrom) {
            repliesFromSize = post.repliesFrom.size();
        }

        if ((!threadMode && post.replies > 0) || (repliesFromSize > 0)) {
            replies.setVisibility(View.VISIBLE);

            int replyCount = threadMode ? repliesFromSize : post.replies;
            String text = getResources().getQuantityString(R.plurals.reply, replyCount, replyCount);

            if (!threadMode && post.images > 0) {
                text += ", " + getResources().getQuantityString(R.plurals.image, post.images, post.images);
            }

            replies.setText(text);
            comment.setPadding(comment.getPaddingLeft(), comment.getPaddingTop(), comment.getPaddingRight(), 0);
            replies.setPadding(replies.getPaddingLeft(), paddingPx, replies.getPaddingRight(), replies.getPaddingBottom());
        } else {
            replies.setVisibility(View.GONE);
            comment.setPadding(comment.getPaddingLeft(), comment.getPaddingTop(), comment.getPaddingRight(), paddingPx);
            replies.setPadding(replies.getPaddingLeft(), 0, replies.getPaddingRight(), replies.getPaddingBottom());
        }

        divider.setVisibility(showDivider ? VISIBLE : GONE);
    }

    private void unbindPost(Post post) {
        bound = false;

        icons.cancelCountryRequest();

        setPostLinkableListener(post, null);
    }

    private void setPostLinkableListener(Post post, PostLinkable.Callback callback) {
        if (post.comment instanceof Spanned) {
            Spanned commentSpanned = (Spanned) post.comment;
            PostLinkable[] linkables = commentSpanned.getSpans(0, commentSpanned.length(), PostLinkable.class);
            for (PostLinkable linkable : linkables) {
                if (callback == null) {
                    while (linkable.hasCallback(this)) {
                        linkable.removeCallback(this);
                    }
                } else {
                    if (!linkable.hasCallback(this)) {
                        linkable.addCallback(callback);
                    }
                }
            }

            if (callback == null) {
                if (commentSpanned instanceof Spannable) {
                    Spannable commentSpannable = (Spannable) commentSpanned;
                    commentSpannable.removeSpan(BACKGROUND_SPAN);
                }
            }
        }
    }

    @Override
    public void onLinkableClick(PostLinkable postLinkable) {
        callback.onPostLinkableClicked(postLinkable);
    }

    @Override
    public int getMarkedNo(PostLinkable postLinkable) {
        return markedNo;
    }

    private static BackgroundColorSpan BACKGROUND_SPAN = new BackgroundColorSpan(0x6633B5E5);

    /**
     * A MovementMethod that searches for PostLinkables.<br>
     * See {@link PostLinkable} for more information.
     */
    private class PostViewMovementMethod extends LinkMovementMethod {
        @Override
        public boolean onTouchEvent(@NonNull TextView widget, @NonNull Spannable buffer, @NonNull MotionEvent event) {
            int action = event.getActionMasked();

            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_DOWN) {
                int x = (int) event.getX();
                int y = (int) event.getY();

                x -= widget.getTotalPaddingLeft();
                y -= widget.getTotalPaddingTop();

                x += widget.getScrollX();
                y += widget.getScrollY();

                Layout layout = widget.getLayout();
                int line = layout.getLineForVertical(y);
                int off = layout.getOffsetForHorizontal(line, x);

                ClickableSpan[] link = buffer.getSpans(off, off, ClickableSpan.class);

                if (link.length != 0) {
                    if (action == MotionEvent.ACTION_UP) {
                        ignoreNextOnClick = true;
                        link[0].onClick(widget);
                        buffer.removeSpan(BACKGROUND_SPAN);
                    } else if (action == MotionEvent.ACTION_DOWN && link[0] instanceof PostLinkable) {
                        buffer.setSpan(BACKGROUND_SPAN, buffer.getSpanStart(link[0]), buffer.getSpanEnd(link[0]), 0);
                    } else if (action == MotionEvent.ACTION_CANCEL) {
                        buffer.removeSpan(BACKGROUND_SPAN);
                    }

                    return true;
                } else {
                    buffer.removeSpan(BACKGROUND_SPAN);
                }
            }

            return true;
        }
    }

    /**
     * A MovementMethod that searches for PostLinkables.<br>
     * This version is for the {@link FastTextView}.<br>
     * See {@link PostLinkable} for more information.
     */
    private class PostViewFastMovementMethod implements FastTextViewMovementMethod {
        @Override
        public boolean onTouchEvent(@NonNull FastTextView widget, @NonNull Spanned buffer, @NonNull MotionEvent event) {
            int action = event.getActionMasked();

            if (action == MotionEvent.ACTION_UP) {
                int x = (int) event.getX();
                int y = (int) event.getY();

                x -= widget.getPaddingLeft();
                y -= widget.getPaddingTop();

                x += widget.getScrollX();
                y += widget.getScrollY();

                Layout layout = widget.getLayout();
                int line = layout.getLineForVertical(y);
                int off = layout.getOffsetForHorizontal(line, x);

                ClickableSpan[] link = buffer.getSpans(off, off, ClickableSpan.class);

                if (link.length != 0) {
                    link[0].onClick(widget);
                    return true;
                }
            }

            return false;
        }
    }

    private class NoClickableSpan extends ClickableSpan {
        @Override
        public void onClick(View widget) {
            callback.onPostNoClicked(post);
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            ds.setUnderlineText(false);
        }
    }

    private static Bitmap stickyIcon;
    private static Bitmap closedIcon;
    private static Bitmap trashIcon;
    private static Bitmap archivedIcon;

    static {
        Resources res = AndroidUtils.getRes();
        stickyIcon = BitmapFactory.decodeResource(res, R.drawable.sticky_icon);
        closedIcon = BitmapFactory.decodeResource(res, R.drawable.closed_icon);
        trashIcon = BitmapFactory.decodeResource(res, R.drawable.trash_icon);
        archivedIcon = BitmapFactory.decodeResource(res, R.drawable.archived_icon);
    }

    public static class PostIcons extends View {
        private static final int STICKY = 0x1;
        private static final int CLOSED = 0x2;
        private static final int DELETED = 0x4;
        private static final int ARCHIVED = 0x8;
        private static final int COUNTRY = 0x10;

        private int height;
        private int spacing;
        private int icons;
        private int previousIcons;
        private RectF drawRect = new RectF();

        private Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Rect textRect = new Rect();
        private ImageLoader.ImageContainer countryIconRequest;
        private Bitmap countryIcon;
        private String countryName;
        private int countryTextColor;
        private int countryTextSize;

        public PostIcons(Context context) {
            super(context);
            init();
        }

        public PostIcons(Context context, AttributeSet attrs) {
            super(context, attrs);
            init();
        }

        public PostIcons(Context context, AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);
            init();
        }

        private void init() {
            textPaint.setTypeface(Typeface.create((String) null, Typeface.ITALIC));
            setVisibility(View.GONE);
        }

        public void setHeight(int height) {
            this.height = height;
        }

        public void setSpacing(int spacing) {
            this.spacing = spacing;
        }

        public void edit() {
            previousIcons = icons;
        }

        public void apply() {
            if (previousIcons != icons) {
                if (previousIcons == 0 || icons == 0) {
                    setVisibility(icons == 0 ? View.GONE : View.VISIBLE);
                    requestLayout();
                }

                invalidate();
            }
        }

        public void showCountry(final Post post, final Theme theme, int textSize) {
            countryName = post.countryName;
            countryTextColor = theme.detailsColor;
            countryTextSize = textSize;

            countryIconRequest = Chan.getVolleyImageLoader().get(post.countryUrl, new ImageLoader.ImageListener() {
                @Override
                public void onResponse(ImageLoader.ImageContainer response, boolean isImmediate) {
                    if (response.getBitmap() != null) {
                        countryIcon = response.getBitmap();

                        invalidate();
                    }
                }

                @Override
                public void onErrorResponse(VolleyError error) {
                }
            });
        }

        public void cancelCountryRequest() {
            if (countryIconRequest != null) {
                countryIconRequest.cancelRequest();
                countryIconRequest = null;
                countryIcon = null;
                countryName = null;
                countryTextColor = 0;
            }
        }

        public void set(int icon, boolean enable) {
            if (enable) {
                icons |= icon;
            } else {
                icons &= ~icon;
            }
        }

        public boolean get(int icon) {
            return (icons & icon) == icon;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int measureHeight = icons == 0 ? 0 : (height + getPaddingTop() + getPaddingBottom());

            setMeasuredDimension(widthMeasureSpec, MeasureSpec.makeMeasureSpec(measureHeight, MeasureSpec.EXACTLY));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (icons != 0) {
                canvas.save();
                canvas.translate(getPaddingLeft(), getPaddingTop());

                int offset = 0;

                if (get(STICKY)) {
                    offset += drawBitmap(canvas, stickyIcon, offset);
                }

                if (get(CLOSED)) {
                    offset += drawBitmap(canvas, closedIcon, offset);
                }

                if (get(DELETED)) {
                    offset += drawBitmap(canvas, trashIcon, offset);
                }

                if (get(ARCHIVED)) {
                    offset += drawBitmap(canvas, archivedIcon, offset);
                }

                if (get(COUNTRY) && countryIcon != null) {
                    offset += drawBitmap(canvas, countryIcon, offset);

                    textPaint.setColor(countryTextColor);
                    textPaint.setTextSize(countryTextSize);
                    textPaint.getTextBounds(countryName, 0, countryName.length(), textRect);
                    float y = height / 2f - textRect.exactCenterY();
                    canvas.drawText(countryName, offset, y, textPaint);
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
}
