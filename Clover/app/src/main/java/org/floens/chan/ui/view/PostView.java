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
package org.floens.chan.ui.view;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.UnderlineSpan;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.volley.toolbox.NetworkImageView;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.chan.ChanUrls;
import org.floens.chan.core.manager.ThreadManager;
import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.PostLinkable;
import org.floens.chan.utils.IconCache;
import org.floens.chan.utils.ThemeHelper;
import org.floens.chan.utils.Time;
import org.floens.chan.utils.Utils;

public class PostView extends LinearLayout implements View.OnClickListener {
    private final static LinearLayout.LayoutParams matchParams = new LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    private final static LinearLayout.LayoutParams wrapParams = new LinearLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    private final static LinearLayout.LayoutParams matchWrapParams = new LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    private final static LinearLayout.LayoutParams wrapMatchParams = new LinearLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);

    private final Activity context;

    private ThreadManager manager;
    private Post post;

    private boolean isBuild = false;
    private LinearLayout full;
    private LinearLayout right;
    private CustomNetworkImageView imageView;
    private TextView titleView;
    private TextView commentView;
    private TextView repliesCountView;
    private LinearLayout iconView;
    private ImageView stickyView;
    private ImageView closedView;
    private NetworkImageView countryView;
    private ImageView optionsView;
    private View lastSeen;

    private int thumbnailBackground;
    private int savedReplyColor;
    private int highlightedColor;
    private int replyCountColor;
    private int dateColor;

    /**
     * Represents a post. Use setPost(Post ThreadManager) to fill it with data.
     * setPost can be called multiple times (useful for ListView).
     *
     * @param activity
     */
    public PostView(Context activity) {
        super(activity);
        context = (Activity) activity;
        init();
    }

    public PostView(Context activity, AttributeSet attbs) {
        super(activity, attbs);
        context = (Activity) activity;
        init();
    }

    public PostView(Context activity, AttributeSet attbs, int style) {
        super(activity, attbs, style);
        context = (Activity) activity;
        init();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (post != null) {
            post.setLinkableListener(null);
        }
    }

    public void setPost(final Post post, final ThreadManager manager) {
        this.post = post;
        this.manager = manager;

        post.setLinkableListener(null);

        if (!post.parsedSpans) {
            post.parsedSpans = true;
            parseSpans(post);
        }

        buildView(context);

        if (post.hasImage) {
            imageView.setVisibility(View.VISIBLE);
            imageView.setImageUrl(post.thumbnailUrl, ChanApplication.getImageLoader());
        } else {
            imageView.setVisibility(View.GONE);
            imageView.setImageUrl(null, null);
        }

        CharSequence total = new SpannableString("");

        if (post.subjectSpan != null) {
            total = TextUtils.concat(total, post.subjectSpan, "\n");
        }

        if (post.nameSpan != null) {
            total = TextUtils.concat(total, post.nameSpan, " ");
        }

        if (post.tripcodeSpan != null) {
            total = TextUtils.concat(total, post.tripcodeSpan, " ");
        }

        if (post.idSpan != null) {
            total = TextUtils.concat(total, post.idSpan, " ");
        }

        if (post.capcodeSpan != null) {
            total = TextUtils.concat(total, post.capcodeSpan, " ");
        }

        CharSequence relativeTime = DateUtils.getRelativeTimeSpanString(post.time * 1000L, Time.get(),
                DateUtils.SECOND_IN_MILLIS, 0);

        SpannableString date = new SpannableString("No." + post.no + " " + relativeTime);
        date.setSpan(new ForegroundColorSpan(dateColor), 0, date.length(), 0);
        date.setSpan(new AbsoluteSizeSpan(10, true), 0, date.length(), 0);
        total = TextUtils.concat(total, date, " ");

        titleView.setText(total);

        if (!TextUtils.isEmpty(post.comment)) {
            commentView.setVisibility(View.VISIBLE);
            commentView.setText(post.comment);

            if (manager.getLoadable().isThreadMode()) {
                commentView.setMovementMethod(new PostViewMovementMethod());
            }

            commentView.setOnClickListener(this);

            if (manager.getLoadable().isThreadMode()) {
                post.setLinkableListener(this);
            }

            if (manager.getLoadable().isBoardMode()) {
                int maxHeight = context.getResources().getDimensionPixelSize(R.dimen.post_max_height);
                commentView.setMaxHeight(maxHeight);
            } else {
                commentView.setMaxHeight(10000);
            }
        } else {
            commentView.setVisibility(View.GONE);
            commentView.setText("");
            commentView.setOnClickListener(null);
            commentView.setOnLongClickListener(null);
            post.setLinkableListener(null);
        }

        if ((post.isOP && manager.getLoadable().isBoardMode() && post.replies > 0) || (post.repliesFrom.size() > 0)) {
            repliesCountView.setVisibility(View.VISIBLE);

            String text = "";

            int count = manager.getLoadable().isBoardMode() ? post.replies : post.repliesFrom.size();

            if (count > 1) {
                text = count + " " + context.getString(R.string.multiple_replies);
            } else if (count == 1) {
                text = count + " " + context.getString(R.string.one_reply);
            }

            if (manager.getLoadable().isBoardMode() && post.images > 0) {
                if (post.images > 1) {
                    text += ", " + post.images + " " + context.getString(R.string.multiple_images);
                } else {
                    text += ", " + post.images + " " + context.getString(R.string.one_image);
                }
            }

            if (manager.getLoadable().isThreadMode()) {
                repliesCountView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        manager.showPostReplies(post);
                    }
                });
            }

            repliesCountView.setText(text);
        } else {
            repliesCountView.setVisibility(View.GONE);
            repliesCountView.setOnClickListener(null);
        }

        boolean showCountryFlag = !TextUtils.isEmpty(post.country);
        boolean showStickyIcon = post.sticky;
        boolean showClosedIcon = post.closed;

        iconView.setVisibility((showCountryFlag || showStickyIcon || showClosedIcon) ? View.VISIBLE : View.GONE);

        stickyView.setVisibility(showStickyIcon ? View.VISIBLE : View.GONE);
        closedView.setVisibility(showClosedIcon ? View.VISIBLE : View.GONE);
        if (showCountryFlag) {
            countryView.setVisibility(View.VISIBLE);
            countryView.setImageUrl(ChanUrls.getCountryFlagUrl(post.country), ChanApplication.getImageLoader());
        } else {
            countryView.setVisibility(View.GONE);
            countryView.setImageUrl(null, null);
        }

        if (post.isSavedReply) {
            full.setBackgroundColor(savedReplyColor);
        } else if (manager.isPostHightlighted(post)) {
            full.setBackgroundColor(highlightedColor);
        } else {
            full.setBackgroundColor(0x00000000);
        }

        if (manager.isPostLastSeen(post)) {
            lastSeen.setVisibility(View.VISIBLE);
        } else {
            lastSeen.setVisibility(View.GONE);
        }

        if (manager.getLoadable().isBoardMode()) {
            Utils.setPressedDrawable(right);
        }
    }

    private void init() {
        TypedArray ta = context.obtainStyledAttributes(null, R.styleable.PostView, R.attr.post_style, 0);
        thumbnailBackground = ta.getColor(R.styleable.PostView_thumbnail_background, 0);
        savedReplyColor = ta.getColor(R.styleable.PostView_saved_reply_color, 0);
        highlightedColor = ta.getColor(R.styleable.PostView_highlighted_color, 0);
        replyCountColor = ta.getColor(R.styleable.PostView_reply_count_color, 0);
        dateColor = ta.getColor(R.styleable.PostView_date_color, 0);
        ta.recycle();
    }

    private void parseSpans(Post post) {
        TypedArray ta = context.obtainStyledAttributes(null, R.styleable.PostView, R.attr.post_style, 0);

        if (!TextUtils.isEmpty(post.subject)) {
            post.subjectSpan = new SpannableString(post.subject);
            post.subjectSpan.setSpan(new ForegroundColorSpan(ta.getColor(R.styleable.PostView_subject_color, 0)), 0, post.subjectSpan.length(), 0);
        }

        if (!TextUtils.isEmpty(post.name)) {
            post.nameSpan = new SpannableString(post.name);
            post.nameSpan.setSpan(new ForegroundColorSpan(ta.getColor(R.styleable.PostView_name_color, 0)), 0, post.nameSpan.length(), 0);
            if (!TextUtils.isEmpty(post.email)) {
                post.nameSpan.setSpan(new UnderlineSpan(), 0, post.nameSpan.length(), 0);
            }
        }

        if (!TextUtils.isEmpty(post.tripcode)) {
            post.tripcodeSpan = new SpannableString(post.tripcode);
            post.tripcodeSpan.setSpan(new ForegroundColorSpan(ta.getColor(R.styleable.PostView_name_color, 0)), 0, post.tripcodeSpan.length(), 0);
            post.tripcodeSpan.setSpan(new AbsoluteSizeSpan(10, true), 0, post.tripcodeSpan.length(), 0);
        }

        if (!TextUtils.isEmpty(post.id)) {
            post.idSpan = new SpannableString("  ID: " + post.id + "  ");

            // Stolen from the 4chan extension
            int hash = post.id.hashCode();

            int r = (hash >> 24) & 0xff;
            int g = (hash >> 16) & 0xff;
            int b = (hash >> 8) & 0xff;

            int idColor = (0xff << 24) + (r << 16) + (g << 8) + b;
            boolean lightColor = (r * 0.299f) + (g * 0.587f) + (b * 0.114f) > 125f;
            int idBgColor = lightColor ? ta.getColor(R.styleable.PostView_id_background_light, 0) : ta.getColor(R.styleable.PostView_id_background_dark, 0);

            post.idSpan.setSpan(new ForegroundColorSpan(idColor), 0, post.idSpan.length(), 0);
            post.idSpan.setSpan(new BackgroundColorSpan(idBgColor), 0, post.idSpan.length(), 0);
            post.idSpan.setSpan(new AbsoluteSizeSpan(10, true), 0, post.idSpan.length(), 0);
        }

        if (!TextUtils.isEmpty(post.capcode)) {
            post.capcodeSpan = new SpannableString("Capcode: " + post.capcode);
            post.capcodeSpan.setSpan(new ForegroundColorSpan(ta.getColor(R.styleable.PostView_capcode_color, 0)), 0, post.capcodeSpan.length(), 0);
            post.capcodeSpan.setSpan(new AbsoluteSizeSpan(10, true), 0, post.capcodeSpan.length(), 0);
        }

        ta.recycle();
    }

    private void buildView(final Context context) {
        if (isBuild)
            return;
        isBuild = true;

        Resources resources = context.getResources();
        int postPadding = resources.getDimensionPixelSize(R.dimen.post_padding);
        int commentPadding = resources.getDimensionPixelSize(R.dimen.post_comment_padding);
        int iconPadding = resources.getDimensionPixelSize(R.dimen.post_icon_padding);
        int iconWidth = resources.getDimensionPixelSize(R.dimen.post_icon_width);
        int iconHeight = resources.getDimensionPixelSize(R.dimen.post_icon_height);
        int imageSize = resources.getDimensionPixelSize(R.dimen.thumbnail_size);

        RelativeLayout wrapper = new RelativeLayout(context);
        wrapper.setLayoutParams(matchParams);

        full = new LinearLayout(context);
        full.setOrientation(HORIZONTAL);
        wrapper.addView(full, matchParams);

        // Create thumbnail
        imageView = new CustomNetworkImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setFadeIn(100);

        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                manager.onThumbnailClicked(post);
            }
        });

        LinearLayout left = new LinearLayout(context);
        left.setOrientation(VERTICAL);
        left.setBackgroundColor(thumbnailBackground);

        left.addView(imageView, new LinearLayout.LayoutParams(imageSize, imageSize));

        full.addView(left, wrapMatchParams);
        full.setMinimumHeight(imageSize);

        right = new LinearLayout(context);
        right.setOrientation(VERTICAL);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        // 25 padding to give optionsView some space
        header.setPadding(0, 0, Utils.dp(25), 0);

        titleView = new TextView(context);
        titleView.setTextSize(14);
        titleView.setPadding(postPadding, postPadding, postPadding, 0);
        header.addView(titleView, wrapParams);

        right.addView(header, matchWrapParams);

        iconView = new LinearLayout(context);
        iconView.setOrientation(HORIZONTAL);
        iconView.setPadding(postPadding, iconPadding, postPadding, 0);

        stickyView = new ImageView(context);
        stickyView.setImageBitmap(IconCache.stickyIcon);
        iconView.addView(stickyView, new LinearLayout.LayoutParams(iconWidth, iconHeight));

        closedView = new ImageView(context);
        closedView.setImageBitmap(IconCache.closedIcon);
        iconView.addView(closedView, new LinearLayout.LayoutParams(iconWidth, iconHeight));

        countryView = new NetworkImageView(context);
        countryView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iconView.addView(countryView, new LinearLayout.LayoutParams(iconWidth, iconHeight));

        right.addView(iconView, matchWrapParams);

        commentView = new TextView(context);
        commentView.setTextSize(15);
        commentView.setPadding(postPadding, commentPadding, postPadding, commentPadding);
        right.addView(commentView, matchWrapParams);

        repliesCountView = new TextView(context);

        // Set the drawable before the padding, because setting the background resets the padding
        // This behavior differs with 4.4 / 4.1
        Utils.setPressedDrawable(repliesCountView);

        repliesCountView.setTextColor(replyCountColor);
        repliesCountView.setPadding(postPadding, postPadding, postPadding, postPadding);
        repliesCountView.setTextSize(14);

        right.addView(repliesCountView, wrapParams);

        lastSeen = new View(context);
        lastSeen.setBackgroundColor(0xffff0000);
        right.addView(lastSeen, new LayoutParams(LayoutParams.MATCH_PARENT, Utils.dp(6f)));

        full.addView(right, matchWrapParams);

        optionsView = new ImageView(context);
        optionsView.setImageResource(R.drawable.ic_overflow);
        Utils.setPressedDrawable(optionsView);
        optionsView.setPadding(Utils.dp(15), Utils.dp(5), Utils.dp(5), Utils.dp(15));
        optionsView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                PopupMenu popupMenu = new PopupMenu(context, v);
                manager.showPostOptions(post, popupMenu);
                popupMenu.show();
                if (ThemeHelper.getInstance().getTheme().isLightTheme) {
                    optionsView.setImageResource(R.drawable.ic_overflow_black);
                    popupMenu.setOnDismissListener(new PopupMenu.OnDismissListener() {
                        @Override
                        public void onDismiss(final PopupMenu menu) {
                            optionsView.setImageResource(R.drawable.ic_overflow);
                        }
                    });
                }
            }
        });
        wrapper.addView(optionsView, wrapParams);
        RelativeLayout.LayoutParams optionsParams = (RelativeLayout.LayoutParams) optionsView.getLayoutParams();
        optionsParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        optionsParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        optionsView.setLayoutParams(optionsParams);

        addView(wrapper, matchParams);

        wrapper.setOnClickListener(this);
    }

    public void setOnClickListeners(View.OnClickListener listener) {
        commentView.setOnClickListener(listener);
        full.setOnClickListener(listener);
    }

    public void onLinkableClick(PostLinkable linkable) {
        manager.onPostLinkableClicked(linkable);
    }

    @Override
    public void onClick(View v) {
        manager.onPostClicked(post);
    }

    private class PostViewMovementMethod extends LinkMovementMethod {
        @Override
        public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
            int action = event.getAction();

            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
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
                        link[0].onClick(widget);
                    } else if (action == MotionEvent.ACTION_DOWN) {
                        Selection.setSelection(buffer, buffer.getSpanStart(link[0]), buffer.getSpanEnd(link[0]));
                    }

                    return true;
                } else {
                    Selection.removeSelection(buffer);

                    // Changed this to propagate events
                    PostView.this.onTouchEvent(event);
                    return true;
                }
            } else {
                PostView.this.onTouchEvent(event);
                return true;
            }

            //            return Touch.onTouchEvent(widget, buffer, event);
        }
    }
}
