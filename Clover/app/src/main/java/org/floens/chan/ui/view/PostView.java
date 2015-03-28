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
import android.content.res.TypedArray;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
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
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.PostLinkable;
import org.floens.chan.utils.IconCache;
import org.floens.chan.utils.ThemeHelper;
import org.floens.chan.utils.Time;

import static org.floens.chan.utils.AndroidUtils.setPressedDrawable;

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

    private Post post;
    private PostViewCallback callback;
    private Loadable loadable;
    private int highlightQuotesNo = -1;

    private boolean isBuild = false;
    private LinearLayout full;
    private LinearLayout contentContainer;
    private CustomNetworkImageView imageView;
    private TextView titleView;
    private TextView commentView;
    private TextView repliesCountView;
    private LinearLayout iconsView;
    private ImageView stickyView;
    private ImageView closedView;
    private ImageView deletedView;
    private ImageView archivedView;
    private NetworkImageView countryView;
    private ImageView optionsView;
    private View lastSeen;

    /**
     * Represents a post. Use setPost(Post ThreadManager) to fill it with data.
     * setPost can be called multiple times (useful for ListView).
     *
     * @param activity
     */
    public PostView(Context activity) {
        super(activity);
        context = (Activity) activity;
    }

    public PostView(Context activity, AttributeSet attbs) {
        super(activity, attbs);
        context = (Activity) activity;
    }

    public PostView(Context activity, AttributeSet attbs, int style) {
        super(activity, attbs, style);
        context = (Activity) activity;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (post != null) {
            post.setLinkableListener(null);
        }
    }

    public void setPost(final Post post, final PostViewCallback callback) {
        this.post = post;
        this.callback = callback;
        this.loadable = callback.getLoadable();

        highlightQuotesNo = -1;

        boolean boardCatalogMode = loadable.isBoardMode() || loadable.isCatalogMode();

        TypedArray ta = context.obtainStyledAttributes(null, R.styleable.PostView, R.attr.post_style, 0);

        if (!isBuild) {
            buildView(context, ta);
            isBuild = true;
        }

        int dateColor = ta.getColor(R.styleable.PostView_date_color, 0);
        int savedReplyColor = ta.getColor(R.styleable.PostView_saved_reply_color, 0);
        int highlightedColor = ta.getColor(R.styleable.PostView_highlighted_color, 0);
        int detailSize = ta.getDimensionPixelSize(R.styleable.PostView_detail_size, 0);

        ta.recycle();

        if (post.hasImage) {
            imageView.setVisibility(View.VISIBLE);
            imageView.setImageUrl(post.thumbnailUrl, ChanApplication.getVolleyImageLoader());
        } else {
            imageView.setVisibility(View.GONE);
            imageView.setImageUrl(null, null);
        }

        CharSequence total = new SpannableString("");

        if (post.subjectSpan != null) {
            total = TextUtils.concat(total, post.subjectSpan);
        }

        if (isList()) {
            CharSequence relativeTime = DateUtils.getRelativeTimeSpanString(post.time * 1000L, Time.get(), DateUtils.SECOND_IN_MILLIS, 0);
            SpannableString date = new SpannableString("No." + post.no + " " + relativeTime);
            date.setSpan(new ForegroundColorSpan(dateColor), 0, date.length(), 0);
            date.setSpan(new AbsoluteSizeSpan(detailSize), 0, date.length(), 0);

            total = TextUtils.concat(total, post.subjectSpan == null ? "" : "\n", post.nameTripcodeIdCapcodeSpan, date, " ");
        }

        if (!TextUtils.isEmpty(total)) {
            titleView.setText(total);
            titleView.setVisibility(View.VISIBLE);
        } else {
            titleView.setVisibility(View.GONE);
        }

        commentView.setText(post.comment);

        if (loadable.isThreadMode()) {
            post.setLinkableListener(this);
            commentView.setMovementMethod(new PostViewMovementMethod());
            commentView.setOnClickListener(this);
        } else {
            post.setLinkableListener(null);
            commentView.setOnClickListener(null);
            commentView.setClickable(false);
            commentView.setMovementMethod(null);
        }

        if (isGrid() || ((post.isOP && boardCatalogMode && post.replies > 0) || (post.repliesFrom.size() > 0))) {
            repliesCountView.setVisibility(View.VISIBLE);

            String text = "";

            int count = boardCatalogMode ? post.replies : post.repliesFrom.size();

            if (count != 1) {
                text = count + " " + context.getString(R.string.multiple_replies);
            } else if (count == 1) {
                text = count + " " + context.getString(R.string.one_reply);
            }

            if (boardCatalogMode && post.images > 0) {
                if (post.images != 1) {
                    text += ", " + post.images + " " + context.getString(R.string.multiple_images);
                } else {
                    text += ", " + post.images + " " + context.getString(R.string.one_image);
                }
            }

            if (loadable.isThreadMode()) {
                repliesCountView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        callback.onShowPostReplies(post);
                    }
                });
            }

            repliesCountView.setText(text);
        } else {
            repliesCountView.setVisibility(View.GONE);
            repliesCountView.setOnClickListener(null);
        }

        boolean showCountryFlag = isList() && !TextUtils.isEmpty(post.country) && !TextUtils.isEmpty(post.countryUrl);
        boolean showStickyIcon = isList() && post.sticky;
        boolean showDeletedIcon = isList() && post.deleted;
        boolean showArchivedIcon = isList() && post.archived;
        boolean showClosedIcon = isList() && post.closed && !showArchivedIcon;

        iconsView.setVisibility((showCountryFlag || showStickyIcon || showClosedIcon || showDeletedIcon || showArchivedIcon) ? View.VISIBLE : View.GONE);

        stickyView.setVisibility(showStickyIcon ? View.VISIBLE : View.GONE);
        closedView.setVisibility(showClosedIcon ? View.VISIBLE : View.GONE);
        deletedView.setVisibility(showDeletedIcon ? View.VISIBLE : View.GONE);
        archivedView.setVisibility(showArchivedIcon ? View.VISIBLE : View.GONE);
        if (showCountryFlag) {
            countryView.setVisibility(View.VISIBLE);
            countryView.setImageUrl(post.countryUrl, ChanApplication.getVolleyImageLoader());
        } else {
            countryView.setVisibility(View.GONE);
            countryView.setImageUrl(null, null);
        }

        if (post.isSavedReply) {
            full.setBackgroundColor(savedReplyColor);
        } else if (callback.isPostHightlighted(post)) {
            full.setBackgroundColor(highlightedColor);
        } else {
            full.setBackgroundColor(0x00000000);
        }

        if (callback.isPostLastSeen(post)) {
            lastSeen.setVisibility(View.VISIBLE);
        } else {
            lastSeen.setVisibility(View.GONE);
        }
    }

    public Post getPost() {
        return post;
    }

    public ImageView getThumbnail() {
        return imageView;
    }

    public void setHighlightQuotesWithNo(int no) {
        highlightQuotesNo = no;
    }

    public int getHighlightQuotesWithNo() {
        return highlightQuotesNo;
    }

    private void buildView(final Context context, TypedArray ta) {
        int thumbnailBackground = ta.getColor(R.styleable.PostView_thumbnail_background, 0);
        int replyCountColor = ta.getColor(R.styleable.PostView_reply_count_color, 0);

        int iconPadding = ta.getDimensionPixelSize(R.styleable.PostView_icon_padding, 0);
        int iconWidth = ta.getDimensionPixelSize(R.styleable.PostView_icon_width, 0);
        int iconHeight = ta.getDimensionPixelSize(R.styleable.PostView_icon_height, 0);
        int gridHeight = ta.getDimensionPixelSize(R.styleable.PostView_grid_height, 0);
        int optionsSpacing = ta.getDimensionPixelSize(R.styleable.PostView_options_spacing, 0);
        int titleSize = ta.getDimensionPixelSize(R.styleable.PostView_title_size, 0);
        int optionsLeftPadding = ta.getDimensionPixelSize(R.styleable.PostView_options_left_padding, 0);
        int optionsTopPadding = ta.getDimensionPixelSize(R.styleable.PostView_options_top_padding, 0);
        int optionsRightPadding = ta.getDimensionPixelSize(R.styleable.PostView_options_right_padding, 0);
        int optionsBottomPadding = ta.getDimensionPixelSize(R.styleable.PostView_options_bottom_padding, 0);
        int lastSeenHeight = ta.getDimensionPixelSize(R.styleable.PostView_last_seen_height, 0);

        int postListMaxHeight = ta.getDimensionPixelSize(R.styleable.PostView_list_comment_max_height, 0);

        int postCommentSize = 0;
        int commentPadding = 0;
        int postPadding = 0;
        int imageSize = 0;
        int repliesCountSize = 0;
        if (isList()) {
            postCommentSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, ThemeHelper.getInstance().getFontSize(), getResources().getDisplayMetrics());
            commentPadding = ta.getDimensionPixelSize(R.styleable.PostView_list_comment_padding, 0);
            postPadding = ta.getDimensionPixelSize(R.styleable.PostView_list_padding, 0);
            imageSize = ta.getDimensionPixelSize(R.styleable.PostView_list_image_size, 0);
            repliesCountSize = ta.getDimensionPixelSize(R.styleable.PostView_list_replies_count_size, 0);
        } else if (isGrid()) {
            postCommentSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, ThemeHelper.getInstance().getFontSize() - 1, getResources().getDisplayMetrics());
            commentPadding = ta.getDimensionPixelSize(R.styleable.PostView_grid_comment_padding, 0);
            postPadding = ta.getDimensionPixelSize(R.styleable.PostView_grid_padding, 0);
            imageSize = ta.getDimensionPixelSize(R.styleable.PostView_grid_image_size, 0);
            repliesCountSize = ta.getDimensionPixelSize(R.styleable.PostView_grid_replies_count_size, 0);
        }

        RelativeLayout wrapper = new RelativeLayout(context);
        wrapper.setLayoutParams(matchParams);

        full = new LinearLayout(context);
        if (isList()) {
            full.setOrientation(HORIZONTAL);
            wrapper.addView(full, matchParams);
        } else if (isGrid()) {
            full.setOrientation(VERTICAL);
            wrapper.addView(full, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, gridHeight));
        }

        LinearLayout imageContainer = new LinearLayout(context);
        imageContainer.setOrientation(VERTICAL);
        imageContainer.setBackgroundColor(thumbnailBackground);

        // Create thumbnail
        imageView = new CustomNetworkImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setFadeIn(100);

        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                callback.onThumbnailClicked(post, imageView);
            }
        });

        if (isList()) {
            imageContainer.addView(imageView, new LinearLayout.LayoutParams(imageSize, imageSize));
            full.addView(imageContainer, wrapMatchParams);
            full.setMinimumHeight(imageSize);
        } else if (isGrid()) {
            imageContainer.addView(imageView, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, imageSize));
            full.addView(imageContainer, matchWrapParams);
        }

        contentContainer = new LinearLayout(context);
        contentContainer.setOrientation(VERTICAL);

        LinearLayout titleContainer = new LinearLayout(context);
        titleContainer.setOrientation(HORIZONTAL);

        if (isList()) {
            // 25 padding to give optionsView some space
            titleContainer.setPadding(0, 0, optionsSpacing, 0);
        }

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_PX, titleSize);
        titleView.setPadding(postPadding, postPadding, postPadding, 0);

        titleContainer.addView(titleView, wrapParams);

        contentContainer.addView(titleContainer, matchWrapParams);

        iconsView = new LinearLayout(context);
        iconsView.setOrientation(HORIZONTAL);
        iconsView.setPadding(postPadding, iconPadding, postPadding, 0);

        stickyView = new ImageView(context);
        stickyView.setImageDrawable(IconCache.stickyIcon);
        iconsView.addView(stickyView, new LinearLayout.LayoutParams(iconWidth, iconHeight));

        closedView = new ImageView(context);
        closedView.setImageDrawable(IconCache.closedIcon);
        iconsView.addView(closedView, new LinearLayout.LayoutParams(iconWidth, iconHeight));

        deletedView = new ImageView(context);
        deletedView.setImageDrawable(IconCache.trashIcon);
        iconsView.addView(deletedView, new LinearLayout.LayoutParams(iconWidth, iconHeight));

        archivedView = new ImageView(context);
        archivedView.setImageDrawable(IconCache.archivedIcon);
        iconsView.addView(archivedView, new LinearLayout.LayoutParams(iconWidth, iconHeight));

        countryView = new NetworkImageView(context);
        countryView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iconsView.addView(countryView, new LinearLayout.LayoutParams(iconWidth, iconHeight));

        contentContainer.addView(iconsView, matchWrapParams);

        commentView = new TextView(context);
        commentView.setTextSize(TypedValue.COMPLEX_UNIT_PX, postCommentSize);

        if (isList()) {
            commentView.setPadding(postPadding, commentPadding, postPadding, commentPadding);

            if (loadable.isBoardMode() || loadable.isCatalogMode()) {
                commentView.setMaxHeight(postListMaxHeight);
            }
        } else if (isGrid()) {
            commentView.setPadding(postPadding, commentPadding, postPadding, 0);
            // So that is fills up all the height using weight later on
            commentView.setMinHeight(10000);
        }

        if (isList()) {
            contentContainer.addView(commentView, matchWrapParams);
        } else if (isGrid()) {
            contentContainer.addView(commentView, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        }

        repliesCountView = new TextView(context);
        setPressedDrawable(repliesCountView);
        repliesCountView.setTextColor(replyCountColor);
        repliesCountView.setPadding(postPadding, postPadding, postPadding, postPadding);
        repliesCountView.setTextSize(TypedValue.COMPLEX_UNIT_PX, repliesCountSize);
        repliesCountView.setSingleLine();

        contentContainer.addView(repliesCountView, wrapParams);

        lastSeen = new View(context);
        lastSeen.setBackgroundColor(0xffff0000);
        contentContainer.addView(lastSeen, new LayoutParams(LayoutParams.MATCH_PARENT, lastSeenHeight));

        if (!loadable.isThreadMode()) {
            setPressedDrawable(contentContainer);
        }

        full.addView(contentContainer, matchWrapParams);

        optionsView = new ImageView(context);
        optionsView.setImageResource(R.drawable.ic_overflow);
        setPressedDrawable(optionsView);
        optionsView.setPadding(optionsLeftPadding, optionsTopPadding, optionsRightPadding, optionsBottomPadding);
        optionsView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View v) {
                PopupMenu popupMenu = new PopupMenu(context, v);
                callback.onPopulatePostOptions(post, popupMenu.getMenu());
                popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        callback.onPostOptionClicked(post, item.getItemId());
                        return true;
                    }
                });
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
        callback.onPostLinkableClicked(linkable);
    }

    @Override
    public void onClick(View v) {
        callback.onPostClicked(post);
    }

    private boolean isList() {
        return true;
        // TODO
//        return callback.getViewMode() == ThreadManager.ViewMode.LIST;
    }

    private boolean isGrid() {
        return false;
        // TODO
//        return callback.getViewMode() == ThreadManager.ViewMode.GRID;
    }

    public interface PostViewCallback {
        public Loadable getLoadable();

        public void onPostClicked(Post post);

        public void onThumbnailClicked(Post post, ImageView thumbnail);

        public void onShowPostReplies(Post post);

        public void onPopulatePostOptions(Post post, Menu menu);

        public void onPostOptionClicked(Post post, int id);

        public void onPostLinkableClicked(PostLinkable linkable);

        public boolean isPostHightlighted(Post post);

        public boolean isPostLastSeen(Post post);
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
                    }

                    commentView.invalidate();

                    return true;
                } else {
                    // Changed this to propagate events
                    PostView.this.onTouchEvent(event);
                    return true;
                }
            } else {
                PostView.this.onTouchEvent(event);
                return true;
            }
        }
    }
}
