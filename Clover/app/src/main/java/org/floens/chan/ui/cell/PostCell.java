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
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
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
import android.text.style.AbsoluteSizeSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
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
import org.floens.chan.core.model.PostLinkable;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.helper.PostHelper;
import org.floens.chan.ui.theme.Theme;
import org.floens.chan.ui.theme.ThemeHelper;
import org.floens.chan.ui.view.FloatingMenu;
import org.floens.chan.ui.view.FloatingMenuItem;
import org.floens.chan.ui.view.ThumbnailView;
import org.floens.chan.utils.Time;

import java.util.ArrayList;
import java.util.List;

import static org.floens.chan.utils.AndroidUtils.dp;
import static org.floens.chan.utils.AndroidUtils.getRes;
import static org.floens.chan.utils.AndroidUtils.setRoundItemBackground;
import static org.floens.chan.utils.AndroidUtils.sp;

public class PostCell extends LinearLayout implements PostCellInterface, PostLinkable.Callback {
    private static final int COMMENT_MAX_LENGTH_BOARD = 500;

    private ThumbnailView thumbnailView;
    private TextView title;
    private TextView icons;
    private TextView comment;
    private TextView replies;
    private ImageView options;
    private View divider;
    private View colorLeft;

    private boolean commentClickable = false;
    private CharSequence iconsSpannable;
    private int detailsSizePx;
    private int iconsTextSize;
    private int countrySizePx;
    private int paddingPx;
    private boolean threadMode;
    private boolean ignoreNextOnClick;

    private boolean bound = false;
    private Theme theme;
    private Post post;
    private PostCellCallback callback;
    private boolean highlighted;
    private int markedNo;

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
    private ImageLoader.ImageContainer countryIconRequest;

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
        title = (TextView) findViewById(R.id.title);
        icons = (TextView) findViewById(R.id.icons);
        comment = (TextView) findViewById(R.id.comment);
        replies = (TextView) findViewById(R.id.replies);
        options = (ImageView) findViewById(R.id.options);
        divider = findViewById(R.id.divider);
        colorLeft = findViewById(R.id.filter_match_color);

        int textSizeSp = Integer.parseInt(ChanSettings.fontSize.get());
        paddingPx = dp(textSizeSp - 6);
        detailsSizePx = sp(textSizeSp - 4);
        title.setTextSize(textSizeSp);
        title.setPadding(paddingPx, paddingPx, dp(52), 0);

        iconsTextSize = sp(textSizeSp);
        countrySizePx = sp(textSizeSp - 3);
        icons.setTextSize(textSizeSp);
        icons.setPadding(paddingPx, dp(4), paddingPx, 0);

        comment.setTextSize(textSizeSp);
        comment.setPadding(paddingPx, paddingPx, paddingPx, 0);

        replies.setTextSize(textSizeSp);
        replies.setPadding(paddingPx, 0, paddingPx, paddingPx);

        setRoundItemBackground(replies);
        setRoundItemBackground(options);

        RelativeLayout.LayoutParams dividerParams = (RelativeLayout.LayoutParams) divider.getLayoutParams();
        dividerParams.leftMargin = paddingPx;
        dividerParams.rightMargin = paddingPx;
        divider.setLayoutParams(dividerParams);

        thumbnailView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                callback.onThumbnailClicked(post, thumbnailView);
            }
        });

        replies.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (threadMode) {
                    if (post.repliesFrom.size() > 0) {
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

    public void setPost(Theme theme, final Post post, PostCellInterface.PostCellCallback callback, boolean highlighted, int markedNo) {
        if (this.post == post && this.highlighted == highlighted && this.markedNo == markedNo) {
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
        this.markedNo = markedNo;

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
        } else if (post.isSavedReply.get()) {
            setBackgroundColor(theme.savedReplyColor);
        } else if (threadMode) {
            setBackgroundResource(0);
        } else {
            setBackgroundResource(R.drawable.item_background);
        }

        if (post.filterHighlightedColor != 0) {
            colorLeft.setVisibility(View.VISIBLE);
            colorLeft.setBackgroundColor(post.filterHighlightedColor);
        } else {
            colorLeft.setVisibility(View.GONE);
        }

        if (post.hasImage) {
            thumbnailView.setVisibility(View.VISIBLE);
            thumbnailView.setUrl(post.thumbnailUrl, thumbnailView.getLayoutParams().width, thumbnailView.getLayoutParams().height);
        } else {
            thumbnailView.setVisibility(View.GONE);
            thumbnailView.setUrl(null, 0, 0);
        }

        CharSequence[] titleParts = new CharSequence[post.subjectSpan == null ? 2 : 4];
        int titlePartsCount = 0;

        if (post.subjectSpan != null) {
            titleParts[titlePartsCount++] = post.subjectSpan;
            titleParts[titlePartsCount++] = "\n";
        }

        titleParts[titlePartsCount++] = post.nameTripcodeIdCapcodeSpan;

        CharSequence relativeTime = DateUtils.getRelativeTimeSpanString(post.time * 1000L, Time.get(), DateUtils.SECOND_IN_MILLIS, 0);
        String noText = "No." + post.no;
        SpannableString date = new SpannableString(noText + " " + relativeTime);
        date.setSpan(new ForegroundColorSpan(theme.detailsColor), 0, date.length(), 0);
        date.setSpan(new AbsoluteSizeSpan(detailsSizePx), 0, date.length(), 0);
        date.setSpan(new NoClickableSpan(), 0, noText.length(), 0);

        titleParts[titlePartsCount] = date;

        title.setText(TextUtils.concat(titleParts));

        iconsSpannable = new SpannableString("");

        if (post.sticky) {
            iconsSpannable = PostHelper.addIcon(iconsSpannable, PostHelper.stickyIcon, iconsTextSize);
        }

        if (post.closed) {
            iconsSpannable = PostHelper.addIcon(iconsSpannable, PostHelper.closedIcon, iconsTextSize);
        }

        if (post.deleted.get()) {
            iconsSpannable = PostHelper.addIcon(iconsSpannable, PostHelper.trashIcon, iconsTextSize);
        }

        if (post.archived) {
            iconsSpannable = PostHelper.addIcon(iconsSpannable, PostHelper.archivedIcon, iconsTextSize);
        }

        boolean waitingForCountry = false;
        if (!TextUtils.isEmpty(post.country)) {
            loadCountryIcon(theme);
            waitingForCountry = true;
        }

        if (iconsSpannable.length() > 0 || waitingForCountry) {
            icons.setVisibility(VISIBLE);
            icons.setText(iconsSpannable);
        } else {
            icons.setVisibility(GONE);
            icons.setText("");
        }

        CharSequence commentText;
        if (post.comment.length() > COMMENT_MAX_LENGTH_BOARD && !threadMode) {
            commentText = post.comment.subSequence(0, COMMENT_MAX_LENGTH_BOARD);
        } else {
            commentText = post.comment;
        }

        comment.setText(commentText);

        if (commentClickable != threadMode) {
            commentClickable = threadMode;
            if (commentClickable) {
                PostViewMovementMethod movementMethod = new PostViewMovementMethod();
                comment.setMovementMethod(movementMethod);
                comment.setOnClickListener(selfClicked);
                title.setMovementMethod(movementMethod);
            } else {
                comment.setOnClickListener(null);
                comment.setClickable(false);
                comment.setMovementMethod(null);
                title.setMovementMethod(null);
            }
        }

        if ((!threadMode && post.replies > 0) || (post.repliesFrom.size() > 0)) {
            replies.setVisibility(View.VISIBLE);

            int replyCount = threadMode ? post.repliesFrom.size() : post.replies;
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
    }

    private void unbindPost(Post post) {
        bound = false;

        if (countryIconRequest != null) {
            countryIconRequest.cancelRequest();
            countryIconRequest = null;
        }

        setPostLinkableListener(post, null);
    }

    private void setPostLinkableListener(Post post, PostLinkable.Callback callback) {
        if (post.comment instanceof Spanned) {
            Spanned commentSpannable = (Spanned) post.comment;
            PostLinkable[] linkables = commentSpannable.getSpans(0, commentSpannable.length(), PostLinkable.class);
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
        }
    }

    private void loadCountryIcon(final Theme theme) {
        countryIconRequest = Chan.getVolleyImageLoader().get(post.countryUrl, new ImageLoader.ImageListener() {
            @Override
            public void onResponse(ImageLoader.ImageContainer response, boolean isImmediate) {
                if (response.getBitmap() != null) {
                    CharSequence countryIcon = PostHelper.addIcon(new BitmapDrawable(getRes(), response.getBitmap()), iconsTextSize);

                    SpannableString countryText = new SpannableString(post.countryName);
                    countryText.setSpan(new StyleSpan(Typeface.ITALIC), 0, countryText.length(), 0);
                    countryText.setSpan(new ForegroundColorSpan(theme.detailsColor), 0, countryText.length(), 0);
                    countryText.setSpan(new AbsoluteSizeSpan(countrySizePx), 0, countryText.length(), 0);

                    iconsSpannable = TextUtils.concat(iconsSpannable, countryIcon, countryText);

                    if (!isImmediate) {
                        icons.setVisibility(VISIBLE);
                        icons.setText(iconsSpannable);
                    }
                }
            }

            @Override
            public void onErrorResponse(VolleyError error) {
            }
        });
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
}
