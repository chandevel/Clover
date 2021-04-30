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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
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
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostHttpIcon;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.PostLinkable;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.core.repository.PageRepository;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.ChanPage;
import com.github.adamantcheese.chan.features.embedding.EmbeddingEngine;
import com.github.adamantcheese.chan.features.embedding.InvalidateFunction;
import com.github.adamantcheese.chan.ui.cell.PostCellInterface.PostCellCallback.PostOptions;
import com.github.adamantcheese.chan.ui.helper.PostHelper;
import com.github.adamantcheese.chan.ui.text.AbsoluteSizeSpanHashed;
import com.github.adamantcheese.chan.ui.text.ForegroundColorSpanHashed;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.ui.view.FloatingMenu;
import com.github.adamantcheese.chan.ui.view.FloatingMenuItem;
import com.github.adamantcheese.chan.ui.view.PostImageThumbnailView;
import com.github.adamantcheese.chan.ui.view.ThumbnailView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import okhttp3.Call;
import okhttp3.HttpUrl;

import static android.text.TextUtils.isEmpty;
import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM;
import static android.widget.RelativeLayout.ALIGN_PARENT_RIGHT;
import static android.widget.RelativeLayout.BELOW;
import static android.widget.RelativeLayout.RIGHT_OF;
import static com.github.adamantcheese.chan.core.settings.ChanSettings.getThumbnailSize;
import static com.github.adamantcheese.chan.ui.adapter.PostsFilter.Order.isNotBumpOrder;
import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getQuantityString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.openIntent;
import static com.github.adamantcheese.chan.utils.AndroidUtils.setClipboardContent;
import static com.github.adamantcheese.chan.utils.AndroidUtils.sp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.waitForLayout;
import static com.github.adamantcheese.chan.utils.PostUtils.getReadableFileSize;
import static com.github.adamantcheese.chan.utils.StringUtils.applySearchSpans;

public class PostCell
        extends LinearLayout
        implements PostCellInterface, InvalidateFunction<Post> {
    private static final int COMMENT_MAX_LINES_BOARD = 25;

    private RecyclerView thumbnailViews;
    private TextView title;
    private PostIcons icons;
    private TextView comment;
    private TextView replies;
    private View filterMatchColor;

    private int detailsSizePx;
    private int iconSizePx;
    private int paddingPx;
    private boolean threadMode;
    private boolean ignoreNextOnClick;

    private Loadable loadable;
    private Post post;
    private PostCellCallback callback;
    private boolean inPopup;
    private boolean highlighted;

    private GestureDetector doubleTapComment;

    private final PostViewMovementMethod commentMovementMethod = new PostViewMovementMethod();

    private ViewTreeObserver.OnPreDrawListener preDrawListener;
    private final List<Call> embedCalls = new CopyOnWriteArrayList<>();

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

        thumbnailViews = findViewById(R.id.thumbnail_views);
        title = findViewById(R.id.title);
        icons = findViewById(R.id.icons);
        comment = findViewById(R.id.comment);
        replies = findViewById(R.id.replies);
        ImageView options = findViewById(R.id.options);
        filterMatchColor = findViewById(R.id.filter_match_color);

        if (!isInEditMode()) {
            int textSizeSp = ChanSettings.fontSize.get();
            paddingPx = dp(textSizeSp - 7);
            detailsSizePx = sp(textSizeSp - 4);

            thumbnailViews.addItemDecoration(new DPSpacingItemDecoration(2));
            ((MarginLayoutParams) thumbnailViews.getLayoutParams()).setMargins(paddingPx, paddingPx, 0, paddingPx);

            title.setTextSize(textSizeSp);
            title.setPadding(paddingPx, paddingPx - dp(2), dp(16), 0);

            iconSizePx = sp(textSizeSp - 3);
            icons.setHeight(sp(textSizeSp));
            icons.setSpacing(dp(4));
            icons.setPadding(paddingPx, dp(4), paddingPx, 0);

            comment.setTextSize(textSizeSp);
            comment.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

            replies.setTextSize(textSizeSp);
            replies.setPadding(paddingPx, 0, paddingPx, paddingPx);
        }

        OnClickListener repliesClickListener = v -> {
            if (replies.getVisibility() != VISIBLE || !threadMode) {
                return;
            }

            if (post.repliesFrom.size() > 0) {
                callback.onShowPostReplies(post);
            }
        };
        replies.setOnClickListener(repliesClickListener);

        options.setOnClickListener(v -> {
            List<FloatingMenuItem<PostOptions>> items = new ArrayList<>();
            List<FloatingMenuItem<PostOptions>> extraItems = new ArrayList<>();
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

        doubleTapComment = new GestureDetector(getContext(), new DoubleTapCommentGestureListener());
    }

    private void showOptions(
            View anchor,
            List<FloatingMenuItem<PostOptions>> items,
            List<FloatingMenuItem<PostOptions>> extraItems,
            Object extraOption
    ) {
        FloatingMenu<PostOptions> menu = new FloatingMenu<>(getContext(), anchor, items);
        menu.setCallback(new FloatingMenu.ClickCallback<PostOptions>() {
            @Override
            public void onFloatingMenuItemClicked(FloatingMenu<PostOptions> menu, FloatingMenuItem<PostOptions> item) {
                if (item.getId() == extraOption) {
                    showOptions(anchor, extraItems, null, null);
                }

                callback.onPostOptionClicked(anchor, post, item.getId(), inPopup);
            }
        });
        menu.show();
    }

    public void setPost(
            Loadable loadable,
            final Post post,
            PostCellCallback callback,
            boolean inPopup,
            boolean highlighted,
            boolean compact,
            Theme theme
    ) {
        this.loadable = loadable;
        this.callback = callback;
        this.inPopup = inPopup;
        this.highlighted = highlighted;

        bindPost(theme, post);

        if (inPopup) {
            setOnTouchListener((v, ev) -> doubleTapComment.onTouchEvent(ev));
        }
    }

    public Post getPost() {
        return post;
    }

    public ThumbnailView getThumbnailView(PostImage postImage) {
        int pos = post.images.indexOf(postImage);
        RecyclerView.ViewHolder foundView = thumbnailViews.findViewHolderForLayoutPosition(pos);
        return (ChanSettings.textOnly.get() || foundView == null) ? null : (ThumbnailView) foundView.itemView;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private void bindPost(Theme theme, Post post) {
        this.post = post;

        // Assume that we're in thread mode if the loadable is null
        threadMode = callback.getLoadable() == null || callback.getLoadable().isThreadMode();

        replies.setClickable(threadMode);

        if (!threadMode) {
            replies.setBackgroundResource(0);
        }

        if (highlighted || post.isSavedReply) {
            setBackgroundColor(getAttrColor(getContext(), R.attr.highlight_color));
        } else if (threadMode) {
            setBackgroundResource(0);
        } else {
            setBackgroundResource(R.drawable.ripple_item_background);
        }

        if (post.filterHighlightedColor != 0) {
            filterMatchColor.setVisibility(VISIBLE);
            filterMatchColor.setBackgroundColor(post.filterHighlightedColor);
        } else {
            filterMatchColor.setVisibility(GONE);
        }

        thumbnailViews.setAdapter(new PostImagesAdapter());
        if (post.images.isEmpty() || ChanSettings.textOnly.get()) {
            thumbnailViews.setVisibility(GONE);
        } else {
            thumbnailViews.setVisibility(VISIBLE);
        }

        SpannableStringBuilder titleParts = new SpannableStringBuilder();

        if (post.subjectSpan != null) {
            titleParts.append(applySearchSpans(theme, post.subjectSpan, callback.getSearchQuery())).append("\n");
        }
        titleParts.append(applySearchSpans(theme, post.nameTripcodeIdCapcodeSpan, callback.getSearchQuery()));

        int detailsColor = getAttrColor(getContext(), R.attr.post_details_color);
        SpannableString date = new SpannableString(
                "No. " + post.no + (ChanSettings.addDubs.get() ? " " + getRepeatDigits(post.no) : "") + " " + (
                        ChanSettings.postFullDate.get()
                                ? PostHelper.getLocalDate(post)
                                : DateUtils.getRelativeTimeSpanString(post.time * 1000L,
                                        System.currentTimeMillis(),
                                        DateUtils.SECOND_IN_MILLIS,
                                        0
                                )));
        date.setSpan(new ForegroundColorSpanHashed(detailsColor), 0, date.length(), 0);
        date.setSpan(new AbsoluteSizeSpanHashed(detailsSizePx), 0, date.length(), 0);

        titleParts.append(date);

        for (PostImage image : post.images) {
            if (ChanSettings.textOnly.get()) continue;
            boolean postFileName = ChanSettings.postFilename.get();
            if (postFileName) {
                //that special character forces it to be left-to-right, as textDirection didn't want to be obeyed
                String filename = '\u200E' + (image.spoiler() ? (image.hidden
                        ? getString(R.string.image_hidden_filename)
                        : getString(R.string.image_spoiler_filename)) : image.filename + "." + image.extension);
                SpannableStringBuilder fileInfo = new SpannableStringBuilder().append("\n")
                        .append(applySearchSpans(theme, filename, callback.getSearchQuery()));
                fileInfo.setSpan(new ForegroundColorSpanHashed(detailsColor), 0, fileInfo.length(), 0);
                fileInfo.setSpan(new AbsoluteSizeSpanHashed(detailsSizePx), 0, fileInfo.length(), 0);
                fileInfo.setSpan(new UnderlineSpan(), 0, fileInfo.length(), 0);
                titleParts.append(fileInfo);
            }

            if (ChanSettings.postFileInfo.get()) {
                SpannableStringBuilder fileInfo = new SpannableStringBuilder();
                fileInfo.append(postFileName ? " " : "\n");
                fileInfo.append(image.extension.toUpperCase());
                fileInfo.append(image.isInlined ? "" : " " + getReadableFileSize(image.size));
                fileInfo.append(image.isInlined ? "" : " " + image.imageWidth + "x" + image.imageHeight);
                fileInfo.setSpan(new ForegroundColorSpanHashed(detailsColor), 0, fileInfo.length(), 0);
                fileInfo.setSpan(new AbsoluteSizeSpanHashed(detailsSizePx), 0, fileInfo.length(), 0);
                titleParts.append(fileInfo);
            }
        }

        title.setText(titleParts);

        icons.edit();
        icons.set(PostIcons.STICKY, post.isSticky());
        icons.set(PostIcons.CLOSED, post.isClosed());
        icons.set(PostIcons.DELETED, post.deleted.get());
        icons.set(PostIcons.ARCHIVED, post.isArchived());
        icons.set(PostIcons.HTTP_ICONS, post.httpIcons != null);

        if (post.httpIcons != null) {
            icons.setHttpIcons(post.httpIcons, iconSizePx);
            comment.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
        } else {
            comment.setPadding(paddingPx, paddingPx / 2, paddingPx, paddingPx);
        }

        icons.apply();

        if (!threadMode) {
            comment.setMaxLines(COMMENT_MAX_LINES_BOARD);
            comment.setEllipsize(TextUtils.TruncateAt.END);
        } else {
            comment.setMaxLines(Integer.MAX_VALUE);
            comment.setEllipsize(null);
        }

        if (!theme.altFontIsMain && ChanSettings.fontAlternate.get()) {
            comment.setTypeface(theme.altFont);
        }

        if (theme.altFontIsMain) {
            comment.setTypeface(ChanSettings.fontAlternate.get() ? Typeface.DEFAULT : theme.altFont);
        }

        if (ChanSettings.shiftPostFormat.get()) {
            comment.setVisibility(isEmpty(post.comment) ? GONE : VISIBLE);
        } else {
            comment.setVisibility(isEmpty(post.comment) && post.images.isEmpty() ? GONE : VISIBLE);
        }

        comment.setText(applySearchSpans(theme, post.comment, callback.getSearchQuery()));

        if (threadMode) {
            comment.setTextIsSelectable(true);
            comment.setFocusable(true);
            comment.setFocusableInTouchMode(true);
            comment.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
                private MenuItem quoteMenuItem;
                private MenuItem webSearchItem;
                private boolean processed;

                @Override
                public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                    if (loadable.site.siteFeature(Site.SiteFeature.POSTING)) {
                        quoteMenuItem = menu.add(Menu.NONE, R.id.post_selection_action_quote, 0, R.string.post_quote);
                    }
                    webSearchItem = menu.add(Menu.NONE, R.id.post_selection_action_search, 1, R.string.post_web_search);
                    return true;
                }

                @Override
                public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                    return true;
                }

                @Override
                public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                    // ensure that the start and end are in the right order, in case the selection start/end are flipped
                    int start = Math.min(comment.getSelectionEnd(), comment.getSelectionStart());
                    int end = Math.max(comment.getSelectionEnd(), comment.getSelectionStart());
                    CharSequence selection = comment.getText().subSequence(start, end);
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
            comment.setOnTouchListener((v, event) -> doubleTapComment.onTouchEvent(event));

            if (loadable.site.siteFeature(Site.SiteFeature.POSTING)) {
                title.setOnLongClickListener(v -> {
                    callback.onPostNoClicked(post);
                    return true;
                });
            }
        } else {
            comment.setOnTouchListener(null);
            comment.setClickable(false);

            // Sets focusable to auto, clickable and longclickable to false.
            comment.setMovementMethod(null);

            title.setBackgroundResource(0);
            title.setLongClickable(false);
        }

        if ((!threadMode && post.getReplies() > 0) || (post.repliesFrom.size() > 0)) {
            replies.setVisibility(VISIBLE);

            int replyCount = threadMode ? post.repliesFrom.size() : post.getReplies();
            String text = getQuantityString(R.plurals.reply, replyCount, replyCount);

            if (!threadMode && post.getImagesCount() > 0) {
                text += ", " + getQuantityString(R.plurals.image, post.getImagesCount(), post.getImagesCount());
            }

            if (!ChanSettings.neverShowPages.get() && loadable.isCatalogMode()) {
                ChanPage p = PageRepository.getPage(post);
                if (p != null && isNotBumpOrder(ChanSettings.boardOrder.get())) {
                    text += ", page " + p.page;
                }
            }

            replies.setText(text);
        } else {
            replies.setVisibility(GONE);
        }

        // we need to wait for the measurements of all the views, so we can shift-format stuff without issue
        if (post.images.size() == 1) {
            preDrawListener = waitForLayout(this, view -> {
                doShiftPostFormatting();
                // always return true; this returning false will otherwise lock up the app in certain circumstances
                return true;
            });
        }

        findViewById(R.id.embed_spinner).setVisibility(GONE);
        embedCalls.addAll(EmbeddingEngine.getInstance().embed(theme, post, this));
        if (!embedCalls.isEmpty()) {
            findViewById(R.id.embed_spinner).setVisibility(VISIBLE);
        }
    }

    private void clearShiftPostFormatting() {
        if (!ChanSettings.shiftPostFormat.get()) return;
        RelativeLayout.LayoutParams commentParams = new RelativeLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        commentParams.alignWithParent = true;
        commentParams.addRule(BELOW, R.id.icons);
        commentParams.addRule(ALIGN_PARENT_RIGHT);
        commentParams.addRule(RIGHT_OF, R.id.thumbnail_views);
        comment.setLayoutParams(commentParams);

        RelativeLayout.LayoutParams replyParams = new RelativeLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        replyParams.alignWithParent = true;
        replyParams.addRule(ALIGN_PARENT_BOTTOM);
        replyParams.addRule(BELOW, R.id.comment);
        replyParams.addRule(RIGHT_OF, R.id.thumbnail_views);
        replies.setLayoutParams(replyParams);

        requestLayout();
    }

    private void doShiftPostFormatting() {
        if (!ChanSettings.shiftPostFormat.get() || comment.getVisibility() != VISIBLE || ChanSettings.textOnly.get())
            return;
        float wrapHeightCheck = 0.8f * thumbnailViews.getHeight();
        int wrapHeightActual = title.getHeight() + icons.getHeight();
        if ((wrapHeightActual >= wrapHeightCheck) || (wrapHeightActual + comment.getHeight() >= 2f * wrapHeightCheck)) {
            RelativeLayout.LayoutParams commentParams = (RelativeLayout.LayoutParams) comment.getLayoutParams();
            commentParams.removeRule(RelativeLayout.RIGHT_OF);
            commentParams.addRule(RelativeLayout.BELOW, R.id.thumbnail_views);
            comment.setLayoutParams(commentParams);

            RelativeLayout.LayoutParams replyParams = (RelativeLayout.LayoutParams) replies.getLayoutParams();
            replyParams.removeRule(RelativeLayout.RIGHT_OF);
            replies.setLayoutParams(replyParams);
        }
    }

    @Override
    public void invalidateView(@NonNull Theme theme, @NonNull Post post) {
        if (!post.equals(this.post)) return;
        embedCalls.clear();
        bindPost(theme, post);
    }

    private final String[] dubTexts =
            {"", "(Dubs)", "(Trips)", "(Quads)", "(Quints)", "(Sexes)", "(Septs)", "(Octs)", "(Nons)", "(Decs)"};

    private String getRepeatDigits(int no) {
        CharSequence number = new StringBuilder().append(no).reverse();
        char init = number.charAt(0);
        int count = 1;
        for (int i = 1; i < number.length(); i++) {
            if (number.charAt(i) == init) {
                count++;
                init = number.charAt(i);
            } else {
                break;
            }
        }
        return dubTexts[count - 1];
    }

    @Override
    public void unsetPost() {
        icons.cancelRequests();
        title.setOnLongClickListener(null);
        title.setLongClickable(false);
        comment.setOnTouchListener(null);
        comment.setMovementMethod(null);
        post.comment.removeSpan(BACKGROUND_SPAN);
        thumbnailViews.setAdapter(null);
        for (Call c : embedCalls) {
            c.cancel();
        }
        embedCalls.clear();
        findViewById(R.id.embed_spinner).setVisibility(GONE);
        if (this.getViewTreeObserver().isAlive()) {
            this.getViewTreeObserver().removeOnPreDrawListener(preDrawListener);
        }
        preDrawListener = null;
        clearShiftPostFormatting();
        post = null;
    }

    private static final BackgroundColorSpan BACKGROUND_SPAN = new BackgroundColorSpan(0x6633B5E5);

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
                            } else if (!(s instanceof PostLinkable)) {
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

    private class PostImagesAdapter
            extends RecyclerView.Adapter<PostImagesAdapter.PostImageViewHolder> {
        public PostImagesAdapter() {
            setHasStableIds(true);
        }

        @NonNull
        @Override
        public PostImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            PostImageThumbnailView thumbnailView = new PostImageThumbnailView(parent.getContext());
            thumbnailView.setLayoutParams(new ViewGroup.MarginLayoutParams(getThumbnailSize(), getThumbnailSize()));
            thumbnailView.setRounding(dp(2));
            thumbnailView.setOnLongClickListener(v -> {
                if (thumbnailView.getPostImage() == null || !ChanSettings.enableLongPressURLCopy.get()) {
                    return false;
                }

                setClipboardContent("Image URL", thumbnailView.getPostImage().imageUrl.toString());
                showToast(getContext(), R.string.image_url_copied_to_clipboard);

                return true;
            });
            return new PostImageViewHolder(thumbnailView);
        }

        @Override
        public void onBindViewHolder(@NonNull PostImageViewHolder holder, int position) {
            PostImageThumbnailView thumbnailView = (PostImageThumbnailView) holder.itemView;
            PostImage image = post.images.get(position);
            thumbnailView.setPostImage(image, getThumbnailSize());

            thumbnailView.setOnClickListener(v -> {
                if (!post.deleted.get() || image.isInlined || NetUtils.isCached(image.imageUrl)) {
                    callback.onThumbnailClicked(image, thumbnailView);
                }
            });
        }

        @Override
        public void onViewRecycled(
                @NonNull PostImageViewHolder holder
        ) {
            PostImageThumbnailView thumbnailView = (PostImageThumbnailView) holder.itemView;
            thumbnailView.setPostImage(null, 0);
            thumbnailView.setOnClickListener(null);
        }

        @Override
        public int getItemCount() {
            return post.images.size();
        }

        @Override
        public long getItemId(int position) {
            return post.images.get(position).imageUrl.hashCode();
        }

        private class PostImageViewHolder
                extends RecyclerView.ViewHolder {
            public PostImageViewHolder(@NonNull View itemView) {
                super(itemView);
            }
        }
    }

    public static class DPSpacingItemDecoration
            extends RecyclerView.ItemDecoration {
        private final int spacing;

        public DPSpacingItemDecoration(int spacing) {
            this.spacing = dp(spacing);
        }

        @Override
        public void getItemOffsets(
                @NonNull Rect outRect,
                @NonNull View view,
                @NonNull RecyclerView parent,
                @NonNull RecyclerView.State state
        ) {
            super.getItemOffsets(outRect, view, parent, state);
            outRect.bottom = spacing;
        }
    }

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

        public void setHttpIcons(List<PostHttpIcon> icons, int size) {
            httpIconTextColor = getAttrColor(getContext(), R.attr.post_details_color);
            httpIconTextSize = size;
            httpIcons = new ArrayList<>(icons.size());
            for (PostHttpIcon icon : icons) {
                int codeIndex = icon.name.indexOf('/'); //this is for country codes
                String name = icon.name.substring(0, codeIndex != -1 ? codeIndex : icon.name.length());
                PostIconsHttpIcon j = new PostIconsHttpIcon(this, name, icon.url);
                httpIcons.add(j);
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
                    offset += drawBitmap(canvas, BitmapRepository.stickyIcon, offset);
                }

                if (get(CLOSED)) {
                    offset += drawBitmap(canvas, BitmapRepository.closedIcon, offset);
                }

                if (get(DELETED)) {
                    offset += drawBitmap(canvas, BitmapRepository.trashIcon, offset);
                }

                if (get(ARCHIVED)) {
                    offset += drawBitmap(canvas, BitmapRepository.archivedIcon, offset);
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

    private static class PostIconsHttpIcon {
        private final String name;
        private Call request;
        private Bitmap bitmap;

        private PostIconsHttpIcon(final PostIcons postIcons, String name, HttpUrl url) {
            this.name = name;

            request = NetUtils.makeBitmapRequest(url, new NetUtilsClasses.BitmapResult() {
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
        }

        private void cancel() {
            if (request != null) {
                request.cancel();
                request = null;
            }
        }
    }

    private class DoubleTapCommentGestureListener
            extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            callback.onPostDoubleClicked(post);
            return true;
        }
    }
}
