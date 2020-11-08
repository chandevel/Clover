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
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.cache.CacheHandler;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostHttpIcon;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.PostLinkable;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.core.repository.PageRepository;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.ChanPage;
import com.github.adamantcheese.chan.features.embedding.EmbeddingEngine.InvalidateFunction;
import com.github.adamantcheese.chan.ui.helper.PostHelper;
import com.github.adamantcheese.chan.ui.text.AbsoluteSizeSpanHashed;
import com.github.adamantcheese.chan.ui.text.ForegroundColorSpanHashed;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.ui.view.FloatingMenu;
import com.github.adamantcheese.chan.ui.view.FloatingMenuItem;
import com.github.adamantcheese.chan.ui.view.PostImageThumbnailView;
import com.github.adamantcheese.chan.ui.view.ThumbnailView;
import com.github.adamantcheese.chan.utils.NetUtils;
import com.github.adamantcheese.chan.utils.NetUtilsClasses;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import okhttp3.Call;
import okhttp3.HttpUrl;

import static android.text.TextUtils.isEmpty;
import static android.view.View.MeasureSpec.AT_MOST;
import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.UNSPECIFIED;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM;
import static android.widget.RelativeLayout.ALIGN_PARENT_RIGHT;
import static android.widget.RelativeLayout.BELOW;
import static android.widget.RelativeLayout.RIGHT_OF;
import static com.github.adamantcheese.chan.Chan.instance;
import static com.github.adamantcheese.chan.ui.adapter.PostsFilter.Order.isNotBumpOrder;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getDimen;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getDisplaySize;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getQuantityString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.openIntent;
import static com.github.adamantcheese.chan.utils.AndroidUtils.sp;
import static com.github.adamantcheese.chan.utils.PostUtils.getReadableFileSize;

public class PostCell
        extends LinearLayout
        implements PostCellInterface, InvalidateFunction {
    private static final int COMMENT_MAX_LINES_BOARD = 25;

    private final List<PostImageThumbnailView> thumbnailViews = new ArrayList<>(1);

    private RelativeLayout relativeLayoutContainer;
    private TextView title;
    private PostIcons icons;
    private TextView comment;
    private TextView replies;
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
    private int markedNo;
    private boolean showDivider;

    private RecyclerView recyclerView;

    private GestureDetector doubleTapComment;

    private final PostViewMovementMethod commentMovementMethod = new PostViewMovementMethod();

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

        relativeLayoutContainer = findViewById(R.id.relative_layout_container);
        title = findViewById(R.id.title);
        icons = findViewById(R.id.icons);
        comment = findViewById(R.id.comment);
        replies = findViewById(R.id.replies);
        ImageView options = findViewById(R.id.options);
        divider = findViewById(R.id.divider);
        filterMatchColor = findViewById(R.id.filter_match_color);

        if (!isInEditMode()) {
            int textSizeSp = ChanSettings.fontSize.get();
            paddingPx = dp(textSizeSp - 6);
            detailsSizePx = sp(textSizeSp - 4);
            title.setTextSize(textSizeSp);
            title.setPadding(paddingPx, paddingPx, dp(16), 0);

            iconSizePx = sp(textSizeSp - 3);
            icons.setHeight(sp(textSizeSp));
            icons.setSpacing(dp(4));
            icons.setPadding(paddingPx, dp(4), paddingPx, 0);

            comment.setTextSize(textSizeSp);
            comment.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

            replies.setTextSize(textSizeSp);
            replies.setPadding(paddingPx, 0, paddingPx, paddingPx);

            RelativeLayout.LayoutParams dividerParams = (RelativeLayout.LayoutParams) divider.getLayoutParams();
            dividerParams.leftMargin = paddingPx;
            dividerParams.rightMargin = paddingPx;
            divider.setLayoutParams(dividerParams);
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
            List<FloatingMenuItem<Integer>> items = new ArrayList<>();
            List<FloatingMenuItem<Integer>> extraItems = new ArrayList<>();
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
            List<FloatingMenuItem<Integer>> items,
            List<FloatingMenuItem<Integer>> extraItems,
            Object extraOption
    ) {
        FloatingMenu<Integer> menu = new FloatingMenu<>(getContext(), anchor, items);
        menu.setCallback(new FloatingMenu.ClickCallback<Integer>() {
            @Override
            public void onFloatingMenuItemClicked(FloatingMenu<Integer> menu, FloatingMenuItem<Integer> item) {
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
            PostCellInterface.PostCellCallback callback,
            boolean inPopup,
            boolean highlighted,
            int markedNo,
            boolean showDivider,
            ChanSettings.PostViewMode postViewMode,
            boolean compact,
            Theme theme,
            RecyclerView attachedTo
    ) {
        if (this.post == post && this.inPopup == inPopup && this.highlighted == highlighted && this.markedNo == markedNo
                && this.showDivider == showDivider) {
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
        this.markedNo = markedNo;
        this.showDivider = showDivider;
        this.recyclerView = attachedTo;

        bindPost(theme, post);

        if (inPopup) {
            setOnTouchListener((v, ev) -> doubleTapComment.onTouchEvent(ev));
        }
    }

    public Post getPost() {
        return post;
    }

    public ThumbnailView getThumbnailView(PostImage postImage) {
        if (thumbnailViews.isEmpty()) return null;

        for (PostImage image : post.images) {
            if (image.equals(postImage)) {
                return ChanSettings.textOnly.get() ? null : thumbnailViews.get(post.images.indexOf(image));
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

        buildThumbnails();

        SpannableStringBuilder titleParts = new SpannableStringBuilder();

        if (post.subjectSpan != null) {
            titleParts.append(post.subjectSpan).append("\n");
        }
        titleParts.append(post.nameTripcodeIdCapcodeSpan);

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
            boolean postFileName = ChanSettings.postFilename.get();
            if (postFileName) {
                //that special character forces it to be left-to-right, as textDirection didn't want to be obeyed
                String filename = '\u200E' + (image.spoiler() ? (image.hidden
                        ? getString(R.string.image_hidden_filename)
                        : getString(R.string.image_spoiler_filename)) : image.filename + "." + image.extension);
                SpannableString fileInfo = new SpannableString("\n" + filename);
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
            comment.setVisibility(isEmpty(post.comment) && post.images == null ? GONE : VISIBLE);
        }

        if (threadMode) {
            comment.setTextIsSelectable(true);
            comment.setFocusable(true);
            comment.setFocusableInTouchMode(true);
            comment.setText(post.comment, TextView.BufferType.SPANNABLE);
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
            comment.setText(post.comment);
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

        divider.setVisibility(showDivider ? VISIBLE : GONE);

        clearShiftPostFormatting();
        doShiftPostFormatting();

        embedCalls.addAll(callback.getEmbeddingEngine().embed(theme, post, this));
        if (!embedCalls.isEmpty()) {
            findViewById(R.id.embed_spinner).setVisibility(VISIBLE);
        }
    }

    public void clearShiftPostFormatting() {
        if (ChanSettings.shiftPostFormat.get()) {
            int paddingPx = dp(ChanSettings.fontSize.get() - 6);

            RelativeLayout.LayoutParams commentParams = new RelativeLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            commentParams.alignWithParent = true;
            commentParams.addRule(BELOW, R.id.icons);
            commentParams.addRule(ALIGN_PARENT_RIGHT);
            commentParams.addRule(RIGHT_OF, R.id.thumbnail_view);
            comment.setLayoutParams(commentParams);
            comment.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

            RelativeLayout.LayoutParams replyParams = new RelativeLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            replyParams.alignWithParent = true;
            replyParams.addRule(ALIGN_PARENT_BOTTOM);
            replyParams.addRule(BELOW, R.id.comment);
            replyParams.addRule(RIGHT_OF, R.id.thumbnail_view);
            replies.setLayoutParams(replyParams);
            replies.setPadding(paddingPx, 0, paddingPx, paddingPx);
            replies.setGravity(Gravity.BOTTOM);
        }
    }

    private void doShiftPostFormatting() {
        if (ChanSettings.shiftPostFormat.get() && comment.getVisibility() == VISIBLE && post.images.size() == 1
                && !ChanSettings.textOnly.get()) {
            int widthMax = recyclerView.getMeasuredWidth();
            int heightMax = recyclerView.getMeasuredHeight();
            int thumbnailSize =
                    getDimen(getContext(), R.dimen.cell_post_thumbnail_size) * ChanSettings.thumbnailSize.get() / 100;

            //get the width of the cell for calculations, height we don't need but measure it anyways
            this.measure(MeasureSpec.makeMeasureSpec(inPopup ? getDisplaySize().x : widthMax, AT_MOST),
                    MeasureSpec.makeMeasureSpec(heightMax, AT_MOST)
            );

            int totalThumbnailWidth = thumbnailSize + paddingPx + (post.filterHighlightedColor != 0
                    ? filterMatchColor.getLayoutParams().width
                    : 0);
            //we want the heights here, but the widths must be the exact size between the thumbnail and view edge so that we calculate offsets right
            title.measure(MeasureSpec.makeMeasureSpec(this.getMeasuredWidth() - totalThumbnailWidth, EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, UNSPECIFIED)
            );
            icons.measure(MeasureSpec.makeMeasureSpec(this.getMeasuredWidth() - totalThumbnailWidth, EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, UNSPECIFIED)
            );
            comment.measure(MeasureSpec.makeMeasureSpec(this.getMeasuredWidth() - totalThumbnailWidth, EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, UNSPECIFIED)
            );
            int thumbnailHeight = thumbnailSize + paddingPx + dp(2);
            int wrapHeight = title.getMeasuredHeight() + icons.getMeasuredHeight();
            int extraWrapHeight = wrapHeight + comment.getMeasuredHeight();
            //wrap if the title+icons height is larger than 0.8x the thumbnail size, or if everything is over 1.6x the thumbnail size
            if ((wrapHeight >= 0.8f * thumbnailHeight) || extraWrapHeight >= 1.6f * thumbnailHeight) {
                RelativeLayout.LayoutParams commentParams = (RelativeLayout.LayoutParams) comment.getLayoutParams();
                commentParams.removeRule(RelativeLayout.RIGHT_OF);
                if (title.getMeasuredHeight() + (icons.getVisibility() == VISIBLE ? icons.getMeasuredHeight() : 0)
                        < thumbnailHeight) {
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
            }
        }
    }

    @Override
    public void invalidateView(Post post) {
        if (!bound || !this.post.equals(post)) return;
        embedCalls.clear();
        clearShiftPostFormatting();
        comment.setText(post.comment, TextView.BufferType.SPANNABLE);
        buildThumbnails();
        doShiftPostFormatting();
        findViewById(R.id.embed_spinner).setVisibility(GONE);
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
            for (PostImage image : post.images) {
                if (image.imageUrl == null) {
                    continue;
                }

                PostImageThumbnailView v = new PostImageThumbnailView(getContext());

                // Set the correct id.
                // The first thumbnail uses thumbnail_view so that the layout can offset to that.
                final int idToSet = image == post.image() ? R.id.thumbnail_view : generatedId++;
                v.setId(idToSet);
                int thumbnailSize = getDimen(getContext(), R.dimen.cell_post_thumbnail_size);
                final int size = thumbnailSize * ChanSettings.thumbnailSize.get() / 100;

                RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(size, size);
                p.alignWithParent = true;

                if (image != post.image()) {
                    p.addRule(RelativeLayout.BELOW, lastId);
                }

                v.setPostImage(image);
                v.setClickable(true);
                //don't set a callback if the post is deleted, but if the file already exists in cache let it through
                if (!post.deleted.get() || instance(CacheHandler.class).exists(image.imageUrl)) {
                    v.setOnClickListener(v2 -> callback.onThumbnailClicked(image, v));
                }
                v.setRounding(dp(2));
                p.setMargins(paddingPx, image == post.image() ? paddingPx + dp(2) : 0, 0,
                        //1 extra for bottom divider
                        lastId + 1 == post.images.size() ? dp(1) + paddingPx : dp(2)
                );

                relativeLayoutContainer.addView(v, p);
                thumbnailViews.add(v);

                lastId = idToSet;
            }
        }
    }

    private void unbindPost(Post post) {
        bound = false;
        icons.cancelRequests();
        title.setOnLongClickListener(null);
        title.setLongClickable(false);
        comment.setOnTouchListener(null);
        comment.setMovementMethod(null);
        setPostLinkableListener(post, false);
        for (Call c : embedCalls) {
            c.cancel();
        }
        embedCalls.clear();
        findViewById(R.id.embed_spinner).setVisibility(GONE);
    }

    private void setPostLinkableListener(Post post, boolean bind) {
        if (post.comment != null) {
            PostLinkable[] linkables = post.comment.getSpans(0, post.comment.length(), PostLinkable.class);
            for (PostLinkable linkable : linkables) {
                linkable.setMarkedNo(bind ? markedNo : -1);
            }

            if (!bind) {
                post.comment.removeSpan(BACKGROUND_SPAN);
            }
        }
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
                public void onBitmapFailure(HttpUrl source, Exception e) {
                    bitmap = BitmapRepository.error;
                    postIcons.invalidate();
                }

                @Override
                public void onBitmapSuccess(HttpUrl source, @NonNull Bitmap bitmap, boolean fromCache) {
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
