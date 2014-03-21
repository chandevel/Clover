package org.floens.chan.view;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.manager.ThreadManager;
import org.floens.chan.model.Post;
import org.floens.chan.model.PostLinkable;
import org.floens.chan.net.ChanUrls;
import org.floens.chan.utils.IconCache;
import org.floens.chan.utils.Utils;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.volley.toolbox.NetworkImageView;

public class PostView extends LinearLayout implements View.OnClickListener, View.OnLongClickListener {
    private final static LinearLayout.LayoutParams matchParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    private final static LinearLayout.LayoutParams wrapParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    private final static LinearLayout.LayoutParams matchWrapParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    private final static LinearLayout.LayoutParams wrapMatchParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);

    private final Activity context;

    private ThreadManager manager;
    private Post post;

    private boolean isBuild = false;
    private LinearLayout full;
    private LinearLayout right;
    private NetworkImageView imageView;
    private TextView titleView;
    private TextView commentView;
    private TextView repliesCountView;
    private LinearLayout iconView;
    private ImageView stickyView;
    private NetworkImageView countryView;

    /**
     * Represents a post.
     * Use setPost(Post ThreadManager) to fill it with data.
     * setPost can be called multiple times (useful for ListView).
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

    public void setPost(final Post post, final ThreadManager manager) {
        this.post = post;
        this.manager = manager;

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

        CharSequence relativeTime = DateUtils.getRelativeTimeSpanString(post.time * 1000L, System.currentTimeMillis(),
                DateUtils.SECOND_IN_MILLIS, 0);

        SpannableString date = new SpannableString("No." + post.no + " " + relativeTime);
        date.setSpan(new ForegroundColorSpan(Color.argb(255, 100, 100, 100)), 0, date.length(), 0);
        date.setSpan(new AbsoluteSizeSpan(10, true), 0, date.length(), 0);
        total = TextUtils.concat(total, date, " ");

        titleView.setText(total);

        if (!TextUtils.isEmpty(post.comment)) {
            commentView.setVisibility(View.VISIBLE);
            commentView.setText(post.comment);
            commentView.setMovementMethod(new PostViewMovementMethod());
            commentView.setOnClickListener(this);
            commentView.setOnLongClickListener(this);

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

        iconView.setVisibility((showCountryFlag || showStickyIcon) ? View.VISIBLE : View.GONE);

        stickyView.setVisibility(showStickyIcon ? View.VISIBLE : View.GONE);
        if (showCountryFlag) {
            countryView.setVisibility(View.VISIBLE);
            countryView.setImageUrl(ChanUrls.getCountryFlagUrl(post.country), ChanApplication.getImageLoader());
        } else {
            countryView.setVisibility(View.GONE);
            countryView.setImageUrl(null, null);
        }

        if (post.isSavedReply) {
            full.setBackgroundColor(0xFFD6BAD0);
        } else {
            full.setBackgroundColor(0x00000000);
        }

        if (manager.getLoadable().isBoardMode()) {
            Utils.setPressedDrawable(right);
        }
    }

    private void buildView(final Context context) {
        if (isBuild) return;
        isBuild = true;

        Resources resources = context.getResources();
        int postPadding = resources.getDimensionPixelSize(R.dimen.post_padding);
        int commentPadding = resources.getDimensionPixelSize(R.dimen.post_comment_padding);
        int iconPadding = resources.getDimensionPixelSize(R.dimen.post_icon_padding);
        int iconWidth = resources.getDimensionPixelSize(R.dimen.post_icon_width);
        int iconHeight = resources.getDimensionPixelSize(R.dimen.post_icon_height);
        int imageSize = resources.getDimensionPixelSize(R.dimen.thumbnail_size);

        full = new LinearLayout(context);
        full.setLayoutParams(matchParams);
        full.setOrientation(HORIZONTAL);

        // Create thumbnail
        imageView = new NetworkImageView(context);
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
        left.setBackgroundColor(0xffdddddd);

        left.addView(imageView, new LinearLayout.LayoutParams(imageSize, imageSize));

        full.addView(left, wrapMatchParams);
        full.setMinimumHeight(imageSize);

        right = new LinearLayout(context);
        right.setOrientation(VERTICAL);

            LinearLayout header = new LinearLayout(context);
            header.setOrientation(HORIZONTAL);

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

            repliesCountView.setTextColor(Color.argb(255, 100, 100, 100));
            repliesCountView.setPadding(postPadding, postPadding, postPadding, postPadding);
            repliesCountView.setTextSize(14);

            right.addView(repliesCountView, wrapParams);

        full.addView(right, matchWrapParams);

        addView(full, matchParams);

        full.setOnClickListener(this);
        full.setOnLongClickListener(this);
    }

    public void onLinkableClick(PostLinkable linkable) {
        manager.onPostLinkableClicked(linkable);
    }

    @Override
    public void onClick(View v) {
        manager.onPostClicked(post);
    }

    @Override
    public boolean onLongClick(View v) {
        manager.onPostLongClicked(post);

        return true;
    }

    private class PostViewMovementMethod extends LinkMovementMethod {
        @Override
        public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
            int action = event.getAction();

            if (action == MotionEvent.ACTION_UP ||
                action == MotionEvent.ACTION_DOWN) {
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
                        Selection.setSelection(buffer,
                                               buffer.getSpanStart(link[0]),
                                               buffer.getSpanEnd(link[0]));
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





