/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
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
package com.github.adamantcheese.chan.ui.cell;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.UnderlineSpan;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader.ImageContainer;
import com.android.volley.toolbox.ImageLoader.ImageListener;
import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.cache.CacheHandler;
import com.github.adamantcheese.chan.core.image.ImageLoaderV2;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostHttpIcon;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.PostLinkable;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.parser.CommentParserHelper;
import com.github.adamantcheese.chan.ui.helper.PostHelper;
import com.github.adamantcheese.chan.ui.text.AbsoluteSizeSpanHashed;
import com.github.adamantcheese.chan.ui.text.FastTextView;
import com.github.adamantcheese.chan.ui.text.FastTextViewMovementMethod;
import com.github.adamantcheese.chan.ui.text.ForegroundColorSpanHashed;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.ui.view.FloatingMenu;
import com.github.adamantcheese.chan.ui.view.FloatingMenuItem;
import com.github.adamantcheese.chan.ui.view.PostImageThumbnailView;
import com.github.adamantcheese.chan.ui.view.ThumbnailView;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import okhttp3.HttpUrl;

import static android.text.TextUtils.isEmpty;
import static android.view.View.MeasureSpec.AT_MOST;
import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.UNSPECIFIED;
import static com.github.adamantcheese.chan.Chan.instance;
import static com.github.adamantcheese.chan.core.settings.ChanSettings.LayoutMode.AUTO;
import static com.github.adamantcheese.chan.core.settings.ChanSettings.LayoutMode.SPLIT;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getDimen;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getDisplaySize;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getQuantityString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getRes;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.isTablet;
import static com.github.adamantcheese.chan.utils.AndroidUtils.openIntent;
import static com.github.adamantcheese.chan.utils.AndroidUtils.setRoundItemBackground;
import static com.github.adamantcheese.chan.utils.AndroidUtils.sp;
import static com.github.adamantcheese.chan.utils.PostUtils.getReadableFileSize;

public class PostCell
        extends LinearLayout
        implements PostCellInterface {
    private static final String TAG = "PostCell";
    private static final int COMMENT_MAX_LENGTH_BOARD = 350;

    private List<PostImageThumbnailView> thumbnailViews = new ArrayList<>(1);

    private RelativeLayout relativeLayoutContainer;
    private FastTextView title;
    private PostIcons icons;
    private TextView comment;
    private FastTextView replies;
    private View repliesAdditionalArea;
    private ImageView options;
    private View divider;
    private View filterMatchColor;

    private int detailsSizePx;
    private int iconSizePx;
    private int paddingPx;
    private boolean threadMode;
    private boolean ignoreNextOnClick;

    private boolean bound = false;
    private Loadable loadable;
    private Post post;
    private PostCellCallback callback;
    private boolean inPopup;
    private boolean highlighted;
    private boolean selected;
    private int markedNo;
    private boolean showDivider;

    private GestureDetector gestureDetector;

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

        relativeLayoutContainer = findViewById(R.id.relative_layout_container);
        title = findViewById(R.id.title);
        icons = findViewById(R.id.icons);
        comment = findViewById(R.id.comment);
        replies = findViewById(R.id.replies);
        repliesAdditionalArea = findViewById(R.id.replies_additional_area);
        options = findViewById(R.id.options);
        divider = findViewById(R.id.divider);
        filterMatchColor = findViewById(R.id.filter_match_color);

        int textSizeSp = Integer.parseInt(ChanSettings.fontSize.get());
        paddingPx = dp(textSizeSp - 6);
        detailsSizePx = sp(textSizeSp - 4);
        title.setTextSize(textSizeSp);
        title.setPadding(paddingPx, paddingPx, dp(16), 0);

        iconSizePx = sp(textSizeSp - 3);
        icons.setHeight(sp(textSizeSp));
        icons.setSpacing(dp(4));
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

        OnClickListener repliesClickListener = v -> {
            if (replies.getVisibility() != VISIBLE || !threadMode) {
                return;
            }
            int repliesFromSize;
            synchronized (post.repliesFrom) {
                repliesFromSize = post.repliesFrom.size();
            }

            if (repliesFromSize > 0) {
                callback.onShowPostReplies(post);
            }
        };
        replies.setOnClickListener(repliesClickListener);
        repliesAdditionalArea.setOnClickListener(repliesClickListener);

        options.setOnClickListener(v -> {
            List<FloatingMenuItem> items = new ArrayList<>();
            List<FloatingMenuItem> extraItems = new ArrayList<>();
            Object extraOption = callback.onPopulatePostOptions(post, items, extraItems);
            showOptions(v, items, extraItems, extraOption);
        });

        setOnClickListener(v -> {
            if (ignoreNextOnClick) {
                ignoreNextOnClick = false;
            } else {
                callback.onPostClicked(post);
            }
        });

        gestureDetector = new GestureDetector(getContext(), new DoubleTapGestureListener());
    }

    private void showOptions(
            View anchor, List<FloatingMenuItem> items, List<FloatingMenuItem> extraItems, Object extraOption
    ) {
        if (ThemeHelper.getTheme().isLightTheme) {
            options.setImageResource(R.drawable.ic_overflow_black);
        }

        FloatingMenu menu = new FloatingMenu(getContext(), anchor, items);
        menu.setCallback(new FloatingMenu.FloatingMenuCallback() {
            @Override
            public void onFloatingMenuItemClicked(FloatingMenu menu, FloatingMenuItem item) {
                if (item.getId() == extraOption) {
                    showOptions(anchor, extraItems, null, null);
                }

                callback.onPostOptionClicked(post, item.getId());
            }

            @Override
            public void onFloatingMenuDismissed(FloatingMenu menu) {
                options.setImageResource(R.drawable.ic_overflow);
            }
        });
        menu.show();
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
            bindPost(ThemeHelper.getTheme(), post);
        }
    }

    public void setPost(
            Loadable loadable,
            final Post post,
            PostCellInterface.PostCellCallback callback,
            boolean inPopup,
            boolean highlighted,
            boolean selected,
            int markedNo,
            boolean showDivider,
            ChanSettings.PostViewMode postViewMode,
            boolean compact,
            Theme theme
    ) {
        if (this.post == post && this.inPopup == inPopup && this.highlighted == highlighted && this.selected == selected
                && this.markedNo == markedNo && this.showDivider == showDivider) {
            return;
        }

        if (this.post != null && bound) {
            unbindPost(this.post);
            this.post = null;
        }

        this.loadable = loadable;
        this.post = post;
        this.callback = callback;
        this.inPopup = inPopup;
        this.highlighted = highlighted;
        this.selected = selected;
        this.markedNo = markedNo;
        this.showDivider = showDivider;

        bindPost(theme, post);
    }

    public Post getPost() {
        return post;
    }

    public ThumbnailView getThumbnailView(PostImage postImage) {
        for (int i = 0; i < post.images.size(); i++) {
            if (post.images.get(i).equalUrl(postImage)) {
                return ChanSettings.textOnly.get() ? null : thumbnailViews.get(i);
            }
        }

        return null;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private void bindPost(Theme theme, Post post) {
        bound = true;

        // Assume that we're in thread mode if the loadable is null
        threadMode = callback.getLoadable() == null || callback.getLoadable().isThreadMode();

        setPostLinkableListener(post, true);

        options.setImageTintList(ColorStateList.valueOf(theme.textSecondary));

        replies.setClickable(threadMode);
        repliesAdditionalArea.setClickable(threadMode);

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
            filterMatchColor.setVisibility(VISIBLE);
            filterMatchColor.setBackgroundColor(post.filterHighlightedColor);
        } else {
            filterMatchColor.setVisibility(GONE);
        }

        buildThumbnails();

        List<CharSequence> titleParts = new ArrayList<>(5);

        if (post.subjectSpan != null) {
            titleParts.add(post.subjectSpan);
            titleParts.add("\n");
        }

        titleParts.add(post.nameTripcodeIdCapcodeSpan);

        CharSequence time;
        if (ChanSettings.postFullDate.get()) {
            time = PostHelper.getLocalDate(post);
        } else {
            time = DateUtils.getRelativeTimeSpanString(post.time * 1000L,
                    System.currentTimeMillis(),
                    DateUtils.SECOND_IN_MILLIS,
                    0
            );
        }

        String noText = "No. " + post.no;
        if (ChanSettings.addDubs.get()) {
            String repeat = CommentParserHelper.getRepeatDigits(post.no);
            if (repeat != null) {
                noText += " (" + repeat + ")";
            }
        }
        SpannableString date = new SpannableString(noText + " " + time);
        date.setSpan(new ForegroundColorSpanHashed(theme.detailsColor), 0, date.length(), 0);
        date.setSpan(new AbsoluteSizeSpanHashed(detailsSizePx), 0, date.length(), 0);

        if (ChanSettings.tapNoReply.get()) {
            date.setSpan(new PostNumberClickableSpan(), 0, noText.length(), 0);
        }

        titleParts.add(date);

        if (!post.images.isEmpty()) {
            for (PostImage image : post.images) {
                boolean postFileName = ChanSettings.postFilename.get();
                if (postFileName) {
                    //that special character forces it to be left-to-right, as textDirection didn't want to be obeyed
                    String filename = '\u200E' + (image.spoiler
                            ? getString(R.string.image_spoiler_filename)
                            : image.filename + "." + image.extension);
                    SpannableString fileInfo = new SpannableString("\n" + filename);
                    fileInfo.setSpan(new ForegroundColorSpanHashed(theme.detailsColor), 0, fileInfo.length(), 0);
                    fileInfo.setSpan(new AbsoluteSizeSpanHashed(detailsSizePx), 0, fileInfo.length(), 0);
                    fileInfo.setSpan(new UnderlineSpan(), 0, fileInfo.length(), 0);
                    titleParts.add(fileInfo);
                }

                if (ChanSettings.postFileInfo.get()) {
                    SpannableStringBuilder fileInfo = new SpannableStringBuilder();
                    fileInfo.append(postFileName ? " " : "\n");
                    fileInfo.append(image.extension.toUpperCase());
                    //if -1, linked image, no info
                    fileInfo.append(image.size == -1 ? "" : " " + getReadableFileSize(image.size));
                    fileInfo.append(image.size == -1 ? "" : " " + image.imageWidth + "x" + image.imageHeight);
                    fileInfo.setSpan(new ForegroundColorSpanHashed(theme.detailsColor), 0, fileInfo.length(), 0);
                    fileInfo.setSpan(new AbsoluteSizeSpanHashed(detailsSizePx), 0, fileInfo.length(), 0);
                    titleParts.add(fileInfo);
                }
            }
        }

        title.setText(TextUtils.concat(titleParts.toArray(new CharSequence[0])));

        icons.edit();
        icons.set(PostIcons.STICKY, post.isSticky());
        icons.set(PostIcons.CLOSED, post.isClosed());
        icons.set(PostIcons.DELETED, post.deleted.get());
        icons.set(PostIcons.ARCHIVED, post.isArchived());
        icons.set(PostIcons.HTTP_ICONS, post.httpIcons != null);

        if (post.httpIcons != null) {
            icons.setHttpIcons(post.httpIcons, theme, iconSizePx);
            comment.setPadding(paddingPx, paddingPx, paddingPx, 0);
        } else {
            comment.setPadding(paddingPx, paddingPx / 2, paddingPx, 0);
        }

        icons.apply();

        CharSequence commentText;
        if (!threadMode && post.comment.length() > COMMENT_MAX_LENGTH_BOARD) {
            commentText = truncatePostComment(post);
        } else {
            commentText = post.comment;
        }

        if (!theme.altFontIsMain && ChanSettings.fontAlternate.get()) {
            comment.setTypeface(theme.altFont);
        }

        if (theme.altFontIsMain) {
            comment.setTypeface(ChanSettings.fontAlternate.get() ? Typeface.DEFAULT : theme.altFont);
        }

        comment.setTextColor(theme.textPrimary);

        if (ChanSettings.shiftPostFormat.get()) {
            comment.setVisibility(isEmpty(commentText) ? GONE : VISIBLE);
        } else {
            //noinspection ConstantConditions
            comment.setVisibility(isEmpty(commentText) && post.images == null ? GONE : VISIBLE);
        }

        if (threadMode) {
            comment.setTextIsSelectable(true);
            comment.setText(commentText, TextView.BufferType.SPANNABLE);
            comment.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
                private MenuItem quoteMenuItem;
                private MenuItem webSearchItem;
                private boolean processed;

                @Override
                public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                    quoteMenuItem = menu.add(Menu.NONE, R.id.post_selection_action_quote, 0, R.string.post_quote);
                    webSearchItem = menu.add(Menu.NONE, R.id.post_selection_action_search, 1, R.string.post_web_search);
                    return true;
                }

                @Override
                public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                    return true;
                }

                @Override
                public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                    CharSequence selection =
                            comment.getText().subSequence(comment.getSelectionStart(), comment.getSelectionEnd());
                    if (item == quoteMenuItem) {
                        callback.onPostSelectionQuoted(post, selection);
                        processed = true;
                    } else if (item == webSearchItem) {
                        Intent searchIntent = new Intent(Intent.ACTION_WEB_SEARCH);
                        searchIntent.putExtra(SearchManager.QUERY, selection.toString());
                        openIntent(searchIntent);
                        processed = true;
                    }

                    if (processed) {
                        mode.finish();
                        processed = false;
                        return true;
                    } else {
                        return false;
                    }
                }

                @Override
                public void onDestroyActionMode(ActionMode mode) {
                }
            });

            // Sets focusable to auto, clickable and longclickable to true.
            comment.setMovementMethod(commentMovementMethod);

            // And this sets clickable to appropriate values again.
            comment.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));

            if (ChanSettings.tapNoReply.get()) {
                title.setMovementMethod(titleMovementMethod);
            }
        } else {
            comment.setText(commentText);
            comment.setOnTouchListener(null);
            comment.setClickable(false);

            // Sets focusable to auto, clickable and longclickable to false.
            comment.setMovementMethod(null);

            title.setMovementMethod(null);
        }

        int repliesFromSize;
        synchronized (post.repliesFrom) {
            repliesFromSize = post.repliesFrom.size();
        }

        if ((!threadMode && post.getReplies() > 0) || (repliesFromSize > 0)) {
            replies.setVisibility(VISIBLE);

            int replyCount = threadMode ? repliesFromSize : post.getReplies();
            String text = getQuantityString(R.plurals.reply, replyCount, replyCount);

            if (!threadMode && post.getImagesCount() > 0) {
                text += ", " + getQuantityString(R.plurals.image, post.getImagesCount(), post.getImagesCount());
            }

            replies.setText(text);
            comment.setPadding(comment.getPaddingLeft(), comment.getPaddingTop(), comment.getPaddingRight(), 0);
            replies.setPadding(replies.getPaddingLeft(),
                    paddingPx,
                    replies.getPaddingRight(),
                    replies.getPaddingBottom()
            );
        } else {
            replies.setVisibility(GONE);
            comment.setPadding(comment.getPaddingLeft(), comment.getPaddingTop(), comment.getPaddingRight(), paddingPx);
            replies.setPadding(replies.getPaddingLeft(), 0, replies.getPaddingRight(), replies.getPaddingBottom());
        }

        divider.setVisibility(showDivider ? VISIBLE : GONE);

        if (ChanSettings.shiftPostFormat.get() && post.images.size() == 1 && !ChanSettings.textOnly.get()) {
            //display width, we don't care about height here
            Point displaySize = getDisplaySize();

            int thumbnailSize = getDimen(R.dimen.cell_post_thumbnail_size);
            boolean isSplitMode =
                    ChanSettings.layoutMode.get() == SPLIT || (ChanSettings.layoutMode.get() == AUTO && isTablet());

            //get the width of the cell for calculations, height we don't need but measure it anyways
            //0.35 is from SplitNavigationControllerLayout; measure for the smaller of the two sides
            this.measure(
                    MeasureSpec.makeMeasureSpec(isSplitMode ? (int) (displaySize.x * 0.35) : displaySize.x, AT_MOST),
                    MeasureSpec.makeMeasureSpec(displaySize.y, AT_MOST)
            );

            //we want the heights here, but the widths must be the exact size between the thumbnail and view edge so that we calculate offsets right
            title.measure(MeasureSpec.makeMeasureSpec(this.getMeasuredWidth() - thumbnailSize, EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, UNSPECIFIED)
            );
            icons.measure(MeasureSpec.makeMeasureSpec(this.getMeasuredWidth() - thumbnailSize, EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, UNSPECIFIED)
            );
            comment.measure(MeasureSpec.makeMeasureSpec(this.getMeasuredWidth() - thumbnailSize, EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, UNSPECIFIED)
            );
            int wrapHeight = title.getMeasuredHeight() + icons.getMeasuredHeight();
            int extraWrapHeight = wrapHeight + comment.getMeasuredHeight();
            //wrap if the title+icons height is larger than 0.8x the thumbnail size, or if everything is over 1.6x the thumbnail size
            if ((wrapHeight >= 0.8f * thumbnailSize) || extraWrapHeight >= 1.6f * thumbnailSize) {
                RelativeLayout.LayoutParams commentParams = (RelativeLayout.LayoutParams) comment.getLayoutParams();
                commentParams.removeRule(RelativeLayout.RIGHT_OF);
                if (title.getMeasuredHeight() + (icons.getVisibility() == VISIBLE ? icons.getMeasuredHeight() : 0)
                        < thumbnailSize) {
                    commentParams.addRule(RelativeLayout.BELOW, R.id.thumbnail_view);
                } else {
                    commentParams.addRule(RelativeLayout.BELOW,
                            (icons.getVisibility() == VISIBLE ? R.id.icons : R.id.title)
                    );
                }
                comment.setLayoutParams(commentParams);

                RelativeLayout.LayoutParams replyParams = (RelativeLayout.LayoutParams) replies.getLayoutParams();
                replyParams.removeRule(RelativeLayout.RIGHT_OF);
                replies.setLayoutParams(replyParams);
            } else if (comment.getVisibility() == GONE) {
                RelativeLayout.LayoutParams replyParams = (RelativeLayout.LayoutParams) replies.getLayoutParams();
                replyParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                replies.setLayoutParams(replyParams);

                RelativeLayout.LayoutParams replyExtraParams =
                        (RelativeLayout.LayoutParams) repliesAdditionalArea.getLayoutParams();
                replyExtraParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                repliesAdditionalArea.setLayoutParams(replyExtraParams);
            }
        }
    }

    private void buildThumbnails() {
        for (PostImageThumbnailView thumbnailView : thumbnailViews) {
            relativeLayoutContainer.removeView(thumbnailView);
        }
        thumbnailViews.clear();

        // Places the thumbnails below each other.
        // The placement is done using the RelativeLayout BELOW rule, with generated view ids.
        if (!post.images.isEmpty() && !ChanSettings.textOnly.get()) {
            int lastId = 0;
            int generatedId = 1;
            boolean first = true;
            for (int i = 0; i < post.images.size(); i++) {
                PostImage image = post.images.get(i);
                PostImageThumbnailView v = new PostImageThumbnailView(getContext());

                // Set the correct id.
                // The first thumbnail uses thumbnail_view so that the layout can offset to that.
                final int idToSet = first ? R.id.thumbnail_view : generatedId++;
                v.setId(idToSet);
                final int size = getDimen(R.dimen.cell_post_thumbnail_size);

                RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(size, size);
                p.alignWithParent = true;

                if (!first) {
                    p.addRule(RelativeLayout.BELOW, lastId);
                }

                v.setPostImage(loadable, image, false, size, size);
                v.setClickable(true);
                //don't set a callback if the post is deleted, but if the file already exists in cache let it through
                if (!post.deleted.get() || instance(CacheHandler.class).exists(image.imageUrl.toString())) {
                    v.setOnClickListener(v2 -> callback.onThumbnailClicked(image, v));
                }
                v.setRounding(dp(2));
                p.setMargins(dp(4), first ? dp(4) : 0, 0,
                        //1 extra for bottom divider
                        i + 1 == post.images.size() ? dp(1) + dp(4) : 0
                );

                relativeLayoutContainer.addView(v, p);
                thumbnailViews.add(v);

                lastId = idToSet;
                first = false;
            }
        }
    }

    private void unbindPost(Post post) {
        bound = false;
        icons.cancelRequests();
        setPostLinkableListener(post, false);
    }

    private void setPostLinkableListener(Post post, boolean bind) {
        if (post.comment instanceof Spanned) {
            Spanned commentSpanned = (Spanned) post.comment;
            PostLinkable[] linkables = commentSpanned.getSpans(0, commentSpanned.length(), PostLinkable.class);
            for (PostLinkable linkable : linkables) {
                linkable.setMarkedNo(bind ? markedNo : -1);
            }

            if (!bind) {
                if (commentSpanned instanceof Spannable) {
                    Spannable commentSpannable = (Spannable) commentSpanned;
                    commentSpannable.removeSpan(BACKGROUND_SPAN);
                }
            }
        }
    }

    private CharSequence truncatePostComment(Post post) {
        BreakIterator bi = BreakIterator.getWordInstance();
        bi.setText(post.comment.toString());
        int precedingBoundary = bi.following(PostCell.COMMENT_MAX_LENGTH_BOARD);
        // Fallback to old method in case the comment does not have any spaces/individual words
        CharSequence commentText = precedingBoundary > 0
                ? post.comment.subSequence(0, precedingBoundary)
                : post.comment.subSequence(0, PostCell.COMMENT_MAX_LENGTH_BOARD);
        return TextUtils.concat(commentText, "\u2026"); // append ellipsis
    }

    private static BackgroundColorSpan BACKGROUND_SPAN = new BackgroundColorSpan(0x6633B5E5);

    /**
     * A MovementMethod that searches for PostLinkables.<br>
     * See {@link PostLinkable} for more information.
     */
    public class PostViewMovementMethod
            extends LinkMovementMethod {
        @Override
        public boolean onTouchEvent(@NonNull TextView widget, @NonNull Spannable buffer, @NonNull MotionEvent event) {
            int action = event.getActionMasked();

            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL
                    || action == MotionEvent.ACTION_DOWN) {
                int x = (int) event.getX();
                int y = (int) event.getY();

                x -= widget.getTotalPaddingLeft();
                y -= widget.getTotalPaddingTop();

                x += widget.getScrollX();
                y += widget.getScrollY();

                Layout layout = widget.getLayout();
                int line = layout.getLineForVertical(y);
                int off = layout.getOffsetForHorizontal(line, x);

                ClickableSpan[] links = buffer.getSpans(off, off, ClickableSpan.class);
                List<ClickableSpan> link = new ArrayList<>();
                Collections.addAll(link, links);

                if (link.size() > 0) {
                    ClickableSpan clickableSpan1 = link.get(0);
                    ClickableSpan clickableSpan2 = link.size() > 1 ? link.get(1) : null;
                    PostLinkable linkable1 =
                            clickableSpan1 instanceof PostLinkable ? (PostLinkable) clickableSpan1 : null;
                    PostLinkable linkable2 =
                            clickableSpan2 instanceof PostLinkable ? (PostLinkable) clickableSpan2 : null;
                    if (action == MotionEvent.ACTION_UP) {
                        ignoreNextOnClick = true;

                        if (linkable2 == null && linkable1 != null) {
                            //regular, non-spoilered link
                            callback.onPostLinkableClicked(post, linkable1);
                        } else if (linkable2 != null && linkable1 != null) {
                            //spoilered link, figure out which span is the spoiler
                            if (linkable1.type == PostLinkable.Type.SPOILER) {
                                if (linkable1.isSpoilerVisible()) {
                                    //linkable2 is the link and we're unspoilered
                                    callback.onPostLinkableClicked(post, linkable2);
                                } else {
                                    //linkable2 is the link and we're spoilered; don't do the click event on the link yet
                                    link.remove(linkable2);
                                }
                            } else if (linkable2.type == PostLinkable.Type.SPOILER) {
                                if (linkable2.isSpoilerVisible()) {
                                    //linkable 1 is the link and we're unspoilered
                                    callback.onPostLinkableClicked(post, linkable1);
                                } else {
                                    //linkable1 is the link and we're spoilered; don't do the click event on the link yet
                                    link.remove(linkable1);
                                }
                            } else {
                                //weird case where a double stack of linkables, but isn't spoilered (some 4chan stickied posts)
                                callback.onPostLinkableClicked(post, linkable1);
                            }
                        }

                        //do onclick on all spoiler postlinkables afterwards, so that we don't update the spoiler state early
                        for (ClickableSpan s : link) {
                            if (s instanceof PostLinkable && ((PostLinkable) s).type == PostLinkable.Type.SPOILER) {
                                s.onClick(widget);
                            }
                        }

                        buffer.removeSpan(BACKGROUND_SPAN);
                    } else if (action == MotionEvent.ACTION_DOWN && clickableSpan1 instanceof PostLinkable) {
                        buffer.setSpan(BACKGROUND_SPAN,
                                buffer.getSpanStart(clickableSpan1),
                                buffer.getSpanEnd(clickableSpan1),
                                0
                        );
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
    private class PostViewFastMovementMethod
            implements FastTextViewMovementMethod {
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

    private class PostNumberClickableSpan
            extends ClickableSpan {
        @Override
        public void onClick(View widget) {
            callback.onPostNoClicked(post);
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            ds.setUnderlineText(false);
        }
    }

    private static Bitmap stickyIcon = BitmapFactory.decodeResource(getRes(), R.drawable.sticky_icon);
    private static Bitmap closedIcon = BitmapFactory.decodeResource(getRes(), R.drawable.closed_icon);
    private static Bitmap trashIcon = BitmapFactory.decodeResource(getRes(), R.drawable.trash_icon);
    private static Bitmap archivedIcon = BitmapFactory.decodeResource(getRes(), R.drawable.archived_icon);
    private static Bitmap errorIcon = BitmapFactory.decodeResource(getRes(), R.drawable.error_icon);

    public static class PostIcons
            extends View {
        private static final int STICKY = 0x1;
        private static final int CLOSED = 0x2;
        private static final int DELETED = 0x4;
        private static final int ARCHIVED = 0x8;
        private static final int HTTP_ICONS = 0x10;

        private int height;
        private int spacing;
        private int icons;
        private int previousIcons;
        private RectF drawRect = new RectF();

        private Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Rect textRect = new Rect();

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

        public void edit() {
            previousIcons = icons;
            httpIcons = null;
        }

        public void apply() {
            if (previousIcons != icons) {
                // Require a layout only if the height changed
                if (previousIcons == 0 || icons == 0) {
                    setVisibility(icons == 0 ? GONE : VISIBLE);
                    requestLayout();
                }

                invalidate();
            }
        }

        public void setHttpIcons(List<PostHttpIcon> icons, Theme theme, int size) {
            httpIconTextColor = theme.detailsColor;
            httpIconTextSize = size;
            httpIcons = new ArrayList<>(icons.size());
            for (PostHttpIcon icon : icons) {
                int codeIndex = icon.name.indexOf('/'); //this is for country codes
                String name = icon.name.substring(0, codeIndex != -1 ? codeIndex : icon.name.length());
                PostIconsHttpIcon j = new PostIconsHttpIcon(this, name, icon.url);
                httpIcons.add(j);
                j.request();
            }
        }

        public void cancelRequests() {
            if (httpIcons != null) {
                for (PostIconsHttpIcon httpIcon : httpIcons) {
                    httpIcon.cancel();
                }
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

            setMeasuredDimension(widthMeasureSpec, MeasureSpec.makeMeasureSpec(measureHeight, EXACTLY));
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

                if (get(HTTP_ICONS)) {
                    for (PostIconsHttpIcon httpIcon : httpIcons) {
                        if (httpIcon.bitmap != null) {
                            offset += drawBitmap(canvas, httpIcon.bitmap, offset);

                            textPaint.setColor(httpIconTextColor);
                            textPaint.setTextSize(httpIconTextSize);
                            textPaint.getTextBounds(httpIcon.name, 0, httpIcon.name.length(), textRect);
                            float y = height / 2f - textRect.exactCenterY();
                            canvas.drawText(httpIcon.name, offset, y, textPaint);
                            offset += textRect.width() + spacing;
                        }
                    }
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

    private static class PostIconsHttpIcon
            implements ImageListener {
        private final PostIcons postIcons;
        private final String name;
        private final HttpUrl url;
        private ImageContainer request;
        private Bitmap bitmap;
        private ImageLoaderV2 imageLoaderV2;

        private PostIconsHttpIcon(PostIcons postIcons, String name, HttpUrl url) {
            this.postIcons = postIcons;
            this.name = name;
            this.url = url;
            this.imageLoaderV2 = instance(ImageLoaderV2.class);
        }

        private void request() {
            request = imageLoaderV2.get(url.toString(), this);
        }

        private void cancel() {
            if (request != null) {
                imageLoaderV2.cancelRequest(request);
                request = null;
            }
        }

        @Override
        public void onResponse(ImageContainer response, boolean isImmediate) {
            if (response.getBitmap() != null) {
                bitmap = response.getBitmap();
                postIcons.invalidate();
            }
        }

        @Override
        public void onErrorResponse(VolleyError error) {
            bitmap = errorIcon;
            postIcons.invalidate();
        }
    }

    private class DoubleTapGestureListener
            extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (inPopup) {
                callback.onPopupPostDoubleClicked(post);
            }
            return inPopup;
        }
    }
}
