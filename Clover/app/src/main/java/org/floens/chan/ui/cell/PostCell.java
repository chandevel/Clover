package org.floens.chan.ui.cell;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.support.annotation.NonNull;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannedString;
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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.PostLinkable;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.helper.PostHelper;
import org.floens.chan.ui.view.FloatingMenu;
import org.floens.chan.ui.view.FloatingMenuItem;
import org.floens.chan.ui.view.ThumbnailView;
import org.floens.chan.utils.ThemeHelper;
import org.floens.chan.utils.Time;

import java.util.ArrayList;
import java.util.List;

import static org.floens.chan.utils.AndroidUtils.dp;
import static org.floens.chan.utils.AndroidUtils.getAttrDrawable;
import static org.floens.chan.utils.AndroidUtils.getRes;
import static org.floens.chan.utils.AndroidUtils.sp;

public class PostCell extends RelativeLayout implements PostLinkable.Callback {
    private static final int COMMENT_MAX_LENGTH_BOARD = 500;

    private Post post;
    private boolean threadMode;

    private FrameLayout thumbnailViewContainer;
    private ThumbnailView thumbnailView;
    private TextView title;
    private TextView icons;
    private TextView comment;
    private TextView replies;
    private ImageView options;

    private boolean commentClickable = false;
    private CharSequence iconsSpannable;
    private int detailsSizePx;
    private int detailsColor;
    private int iconsTextSize;
    private int countrySizePx;
    private boolean ignoreNextOnClick;
    private int highlightColor;
    private int savedColor;

    private int paddingPx;
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

        thumbnailViewContainer = (FrameLayout) findViewById(R.id.thumbnail_container);
        thumbnailView = (ThumbnailView) findViewById(R.id.thumbnail_view);
        title = (TextView) findViewById(R.id.title);
        icons = (TextView) findViewById(R.id.icons);
        comment = (TextView) findViewById(R.id.comment);
        replies = (TextView) findViewById(R.id.replies);
        options = (ImageView) findViewById(R.id.options);

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            replies.setBackground(getAttrDrawable(getContext(), android.R.attr.selectableItemBackgroundBorderless));
        } else {
            replies.setBackgroundResource(R.drawable.item_background);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            options.setBackground(getAttrDrawable(getContext(), android.R.attr.selectableItemBackgroundBorderless));
        } else {
            options.setBackgroundResource(R.drawable.item_background);
        }

        TypedArray ta = getContext().obtainStyledAttributes(new int[]{
                R.attr.post_details_color,
                R.attr.post_highlighted_color,
                R.attr.post_saved_reply_color
        });

        detailsColor = ta.getColor(0, 0);
        highlightColor = ta.getColor(1, 0);
        savedColor = ta.getColor(2, 0);

        ta.recycle();

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

        if (post != null) {
            unbindPost(post);
        }
    }

    public void setPost(final Post post, PostCellCallback callback, boolean highlighted, int markedNo) {
        if (this.post != null) {
            unbindPost(this.post);
        }

        this.post = post;
        this.callback = callback;
        this.highlighted = highlighted;
        this.markedNo = markedNo;

        bindPost(post);
    }

    public Post getPost() {
        return post;
    }

    public ThumbnailView getThumbnailView() {
        return thumbnailView;
    }

    private void bindPost(Post post) {
        threadMode = callback.getLoadable().isThreadMode();

        setPostLinkableListener(post, this);

        replies.setClickable(threadMode);

        if (!threadMode) {
            replies.setBackgroundResource(0);
        }

        if (highlighted) {
            setBackgroundColor(highlightColor);
        } else if (post.isSavedReply) {
            setBackgroundColor(savedColor);
        } else if (threadMode) {
            setBackgroundResource(0);
        } else {
            setBackgroundResource(R.drawable.item_background);
        }

        if (post.hasImage) {
            thumbnailViewContainer.setVisibility(View.VISIBLE);
            thumbnailView.setUrl(post.thumbnailUrl, thumbnailView.getLayoutParams().width, thumbnailView.getLayoutParams().height);
        } else {
            thumbnailViewContainer.setVisibility(View.GONE);
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
        SpannableString date = new SpannableString("No." + post.no + " " + relativeTime);
        date.setSpan(new ForegroundColorSpan(detailsColor), 0, date.length(), 0);
        date.setSpan(new AbsoluteSizeSpan(detailsSizePx), 0, date.length(), 0);

        titleParts[titlePartsCount] = date;

        title.setText(TextUtils.concat(titleParts));

        iconsSpannable = new SpannableString("");

        if (post.sticky) {
            iconsSpannable = PostHelper.addIcon(iconsSpannable, PostHelper.stickyIcon, iconsTextSize);
        }

        if (post.closed) {
            iconsSpannable = PostHelper.addIcon(iconsSpannable, PostHelper.closedIcon, iconsTextSize);
        }

        if (post.deleted) {
            iconsSpannable = PostHelper.addIcon(iconsSpannable, PostHelper.trashIcon, iconsTextSize);
        }

        if (post.archived) {
            iconsSpannable = PostHelper.addIcon(iconsSpannable, PostHelper.archivedIcon, iconsTextSize);
        }

        boolean waitingForCountry = false;
        if (!TextUtils.isEmpty(post.country)) {
            loadCountryIcon();
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
                comment.setMovementMethod(new PostViewMovementMethod());
                comment.setOnClickListener(selfClicked);
            } else {
                comment.setOnClickListener(null);
                comment.setClickable(false);
                comment.setMovementMethod(null);
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
        setPostLinkableListener(post, null);
    }

    private void setPostLinkableListener(Post post, PostLinkable.Callback callback) {
        if (post.comment instanceof SpannedString) {
            SpannedString commentSpannable = (SpannedString) post.comment;
            PostLinkable[] linkables = commentSpannable.getSpans(0, commentSpannable.length(), PostLinkable.class);
            for (PostLinkable linkable : linkables) {
                if (callback == null) {
                    if (linkable.hasCallback(this)) {
                        linkable.removeCallback(this);
                    }
                } else {
                    linkable.addCallback(callback);
                }
            }
        }
    }

    private void loadCountryIcon() {
        final Post requestedPost = post;
        ChanApplication.getVolleyImageLoader().get(post.countryUrl, new ImageLoader.ImageListener() {
            @Override
            public void onResponse(ImageLoader.ImageContainer response, boolean isImmediate) {
                if (response.getBitmap() != null && PostCell.this.post == requestedPost) {
                    CharSequence countryIcon = PostHelper.addIcon(new BitmapDrawable(getRes(), response.getBitmap()), iconsTextSize);

                    SpannableString countryText = new SpannableString(post.countryName);
                    countryText.setSpan(new StyleSpan(Typeface.ITALIC), 0, countryText.length(), 0);
                    countryText.setSpan(new ForegroundColorSpan(detailsColor), 0, countryText.length(), 0);
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

    public interface PostCellCallback {
        Loadable getLoadable();

        void onPostClicked(Post post);

        void onThumbnailClicked(Post post, ThumbnailView thumbnail);

        void onShowPostReplies(Post post);

        void onPopulatePostOptions(Post post, List<FloatingMenuItem> menu);

        void onPostOptionClicked(Post post, Object id);

        void onPostLinkableClicked(PostLinkable linkable);
    }

    private static BackgroundColorSpan BACKGROUND_SPAN = new BackgroundColorSpan(0x6633B5E5);

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
                    } else if (action == MotionEvent.ACTION_DOWN) {
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
}
