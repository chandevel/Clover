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

import static android.text.TextUtils.isEmpty;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.widget.RelativeLayout.BELOW;
import static android.widget.RelativeLayout.LEFT_OF;
import static android.widget.RelativeLayout.RIGHT_OF;
import static com.github.adamantcheese.chan.core.settings.ChanSettings.getThumbnailSize;
import static com.github.adamantcheese.chan.core.site.SiteEndpoints.IconType.OTHER;
import static com.github.adamantcheese.chan.ui.adapter.PostsFilter.PostsOrder.BUMP_ORDER;
import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.utils.AndroidUtils.*;
import static com.github.adamantcheese.chan.utils.PostUtils.getReadableFileSize;
import static com.github.adamantcheese.chan.utils.StringUtils.applySearchSpans;
import static com.github.adamantcheese.chan.utils.StringUtils.span;
import static java.util.concurrent.TimeUnit.SECONDS;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.text.*;
import android.text.format.DateUtils;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.AttributeSet;
import android.view.*;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.OneShotPreDrawListener;
import androidx.recyclerview.widget.RecyclerView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.*;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.net.*;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.core.repository.PageRepository;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.ChanPage;
import com.github.adamantcheese.chan.ui.cell.PostCellInterface.PostCellCallback.PostOptions;
import com.github.adamantcheese.chan.ui.helper.PostHelper;
import com.github.adamantcheese.chan.ui.text.AbsoluteSizeSpanHashed;
import com.github.adamantcheese.chan.ui.text.ForegroundColorSpanHashed;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.ui.view.*;
import com.github.adamantcheese.chan.utils.RecyclerUtils.DPSpacingItemDecoration;

import java.util.ArrayList;
import java.util.List;

import okhttp3.HttpUrl;

public class PostCell
        extends LinearLayout
        implements PostCellInterface {
    private RecyclerView thumbnailViews;
    private TextView title;
    private PostIcons icons;
    private TextView comment;
    private TextView replies;
    private MulticolorBarView filterMatchColor;

    private RelativeLayout headerWrapper;
    private ConstraintLayout bodyWrapper;

    private float detailsSizePx;
    private boolean threadMode;

    private Post post;
    private PostCellCallback callback;
    private boolean inPopup;
    private boolean highlighted;

    private GestureDetector doubleTapComment;

    private OneShotPreDrawListener shifter = null;

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

        headerWrapper = findViewById(R.id.header_wrapper);
        bodyWrapper = findViewById(R.id.body_wrapper);

        int textSizeSp = isInEditMode() ? 15 : ChanSettings.fontSize.get();
        float paddingPx = dp(getContext(), textSizeSp - 7);
        detailsSizePx = sp(getContext(), textSizeSp - 4);

        thumbnailViews.addItemDecoration(new DPSpacingItemDecoration(getContext(), 2));
        ((MarginLayoutParams) thumbnailViews.getLayoutParams()).setMargins((!isInEditMode()
                        && ChanSettings.flipPostCells.get()) ? 0 : (int) paddingPx,
                (int) paddingPx,
                (!isInEditMode() && ChanSettings.flipPostCells.get()) ? (int) paddingPx : 0,
                (int) paddingPx
        );

        title.setTextSize(textSizeSp);
        updatePaddings(title, paddingPx, dp(getContext(), 24), paddingPx, paddingPx / 2);

        icons.getLayoutParams().height = (int) sp(getContext(), textSizeSp);
        updatePaddings(icons, paddingPx, 0, 0, 0);

        if (isInEditMode()) {
            BitmapRepository.initialize(getContext());
            icons.set(new Post.Builder()
                    .sticky(true)
                    .closed(true)
                    .archived(true)
                    .board(Board.getDummyBoard())
                    .no(1)
                    .opId(1)
                    .setUnixTimestampSeconds(System.currentTimeMillis())
                    .comment("")
                    .addHttpIcon(new PostHttpIcon(OTHER, null, new NetUtilsClasses.PassthroughBitmapResult() {
                        @Override
                        public void onBitmapSuccess(
                                @NonNull HttpUrl source, @NonNull Bitmap bitmap, boolean fromCache
                        ) {
                            super.onBitmapSuccess(source, BitmapRepository.youtubeIcon, fromCache);
                        }
                    }, "", ""))
                    .build(), true);
        }

        comment.setTextSize(textSizeSp);

        replies.setTextSize(textSizeSp);
        updatePaddings(replies, paddingPx, paddingPx, paddingPx / 4, paddingPx);
        replies.setOnClickListener(v -> {
            if (replies.getVisibility() != VISIBLE || !threadMode) {
                return;
            }

            callback.onShowPostReplies(post);
        });

        options.setOnClickListener(v -> {
            List<FloatingMenuItem<PostOptions>> items = new ArrayList<>();
            List<FloatingMenuItem<PostOptions>> extraItems = new ArrayList<>();
            Object extraOption = callback.onPopulatePostOptions(post, items, extraItems);
            showOptions(v, items, extraItems, extraOption);
        });

        setOnClickListener(v -> callback.onPostClicked(post));

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
            final Post post,
            PostCellCallback callback,
            boolean inPopup,
            boolean highlighted,
            boolean compact,
            Theme theme
    ) {
        this.callback = callback;
        this.inPopup = inPopup;
        this.highlighted = highlighted;

        bindPost(theme, post, callback);
        if (shifter != null) {
            shifter.removeListener();
            shifter = null;
        }
        if (ChanSettings.shiftPostFormat.get()) {
            shifter = OneShotPreDrawListener.add(this, this::doShiftPostFormatting);
        }

        if (inPopup) {
            setOnTouchListener((v, ev) -> doubleTapComment.onTouchEvent(ev));
        }
    }

    public Post getPost() {
        return post;
    }

    public ImageView getThumbnailView(PostImage postImage) {
        int pos = post.images.indexOf(postImage);
        RecyclerView.ViewHolder foundView = thumbnailViews.findViewHolderForLayoutPosition(pos);
        return (ChanSettings.textOnly.get() || foundView == null) ? null : (ImageView) foundView.itemView;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private void bindPost(Theme theme, Post post, PostCellCallback callback) {
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

        filterMatchColor.setColors(post.filterHighlightedColors);

        if (post.images.isEmpty() || ChanSettings.textOnly.get()) {
            thumbnailViews.setAdapter(null);
            thumbnailViews.setVisibility(GONE);
        } else {
            thumbnailViews.swapAdapter(new PostImagesAdapter(), false);
            thumbnailViews.setVisibility(VISIBLE);
        }

        SpannableStringBuilder titleParts = new SpannableStringBuilder();

        if (post.subjectSpan != null) {
            titleParts.append(applySearchSpans(theme, post.subjectSpan, callback.getSearchQuery())).append("\n");
        }
        titleParts.append(applySearchSpans(theme, post.nameTripcodeIdCapcodeSpan, callback.getSearchQuery()));

        int detailsColor = getAttrColor(getContext(), R.attr.post_details_color);
        CharSequence dubs = ChanSettings.addDubs.get() ? getRepeatDigits(post.no) : "";
        SpannableStringBuilder date = new SpannableStringBuilder()
                .append("No. ")
                .append(String.valueOf(post.no))
                .append(" ")
                .append(dubs)
                .append(ChanSettings.addDubs.get() ? (dubs.length() > 0 ? " " : "") : "")
                .append(ChanSettings.postFullDate.get()
                        ? PostHelper.getLocalDate(post)
                        : DateUtils.getRelativeTimeSpanString(SECONDS.toMillis(post.time),
                                System.currentTimeMillis(),
                                DateUtils.SECOND_IN_MILLIS,
                                0
                        ));
        titleParts.append(span(date,
                new ForegroundColorSpanHashed(detailsColor),
                new AbsoluteSizeSpanHashed((int) detailsSizePx)
        ));

        for (PostImage image : post.images) {
            if (ChanSettings.textOnly.get()) continue;
            boolean postFileName = ChanSettings.postFilename.get();
            if (postFileName) {
                //that special character forces it to be left-to-right, as textDirection didn't want to be obeyed
                String filename = '\u200E' + (image.spoiler() ? (image.hidden
                        ? getString(R.string.image_hidden_filename)
                        : getString(R.string.image_spoiler_filename)) : image.filename + "." + image.extension);
                SpannableStringBuilder fileInfo = new SpannableStringBuilder()
                        .append("\n")
                        .append(applySearchSpans(theme, filename, callback.getSearchQuery()));
                titleParts.append(span(fileInfo,
                        new ForegroundColorSpanHashed(detailsColor),
                        new AbsoluteSizeSpanHashed((int) detailsSizePx),
                        new UnderlineSpan()
                ));
            }

            if (ChanSettings.postFileInfo.get()) {
                SpannableStringBuilder fileInfo = new SpannableStringBuilder();
                fileInfo.append(postFileName ? " " : "\n");
                fileInfo.append(image.extension.toUpperCase());
                fileInfo.append(image.isInlined ? "" : " " + getReadableFileSize(image.size));
                fileInfo.append(image.isInlined ? "" : " " + image.imageWidth + "x" + image.imageHeight);
                titleParts.append(span(fileInfo,
                        new ForegroundColorSpanHashed(detailsColor),
                        new AbsoluteSizeSpanHashed((int) detailsSizePx)
                ));
            }
        }

        title.setText(titleParts);

        icons.set(post, true);

        if (threadMode) {
            comment.setMaxLines(Integer.MAX_VALUE);
            comment.setEllipsize(null);
        } else {
            comment.setMaxLines(25);
            comment.setEllipsize(TextUtils.TruncateAt.END);
        }

        if (ChanSettings.shiftPostFormat.get()) {
            comment.setVisibility(isEmpty(post.comment) ? GONE : VISIBLE);
        } else {
            comment.setVisibility(isEmpty(post.comment) && post.images.isEmpty() ? GONE : VISIBLE);
        }

        comment.setText(applySearchSpans(theme, post.comment, callback.getSearchQuery()));

        if (threadMode) {
            comment.setTextIsSelectable(true);
            comment.setMovementMethod(new PostViewMovementMethod(post, callback));
            comment.setOnTouchListener((v, event) -> doubleTapComment.onTouchEvent(event));

            comment.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
                private MenuItem quoteMenuItem;
                private MenuItem webSearchItem;
                private boolean processed;

                @Override
                public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                    if (post.board.site.siteFeature(Site.SiteFeature.POSTING)) {
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
                    CharSequence selection = "Error getting selection!";
                    try {
                        // ensure that the start and end are in the right order, in case the selection start/end are flipped
                        int start = Math.min(comment.getSelectionEnd(), comment.getSelectionStart());
                        int end = Math.max(comment.getSelectionEnd(), comment.getSelectionStart());
                        selection = comment.getText().subSequence(start, end);
                    } catch (Exception ignored) {}
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

            if (post.board.site.siteFeature(Site.SiteFeature.POSTING)) {
                if (ChanSettings.shortTapPostCellQuote.get()) {
                    headerWrapper.setOnClickListener(v -> callback.onPostNoClicked(post));
                } else {
                    headerWrapper.setOnLongClickListener(v -> {
                        callback.onPostNoClicked(post);
                        return true;
                    });
                }
            }
        } else {
            comment.setTextIsSelectable(false);
            comment.setMovementMethod(null);
            comment.setOnTouchListener(null);

            headerWrapper.setBackgroundResource(0);
        }

        int textSizeSp = isInEditMode() ? 15 : ChanSettings.fontSize.get();
        float paddingPx = dp(getContext(), textSizeSp - 7);

        if ((!threadMode && post.replies > 0) || (post.repliesFrom.size() > 0)) {
            replies.setVisibility(VISIBLE);

            int replyCount = threadMode ? post.repliesFrom.size() : post.replies;
            SpannableStringBuilder text =
                    new SpannableStringBuilder().append(getQuantityString(R.plurals.reply, replyCount));

            if (!threadMode && post.imagesCount > 0) {
                text.append(", ").append(getQuantityString(R.plurals.image, post.imagesCount));
            }

            if (!ChanSettings.neverShowPages.get() && callback.getLoadable().isCatalogMode()) {
                ChanPage p = PageRepository.getPage(post);
                if (p != null && ChanSettings.boardOrder.get() != BUMP_ORDER) {
                    text.append(", page ").append(String.valueOf(p.page));
                }
            }

            updatePaddings(comment, paddingPx, paddingPx, paddingPx / 2, paddingPx / 4);
            replies.setText(text);
        } else {
            updatePaddings(comment, paddingPx, paddingPx, paddingPx / 2, paddingPx);
            replies.setVisibility(GONE);
        }
    }

    // matches cell_post's declarations, and adjustments from those declarations
    private static final RelativeLayout.LayoutParams DEFAULT_BODY_PARAMS =
            new RelativeLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT);
    private static final RelativeLayout.LayoutParams DEFAULT_FLIP_BODY_PARAMS =
            new RelativeLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT);
    private static final RelativeLayout.LayoutParams SHIFT_LEFT_PARAMS;
    private static final RelativeLayout.LayoutParams SHIFT_RIGHT_PARAMS;
    private static final RelativeLayout.LayoutParams SHIFT_LEFT_BELOW_PARAMS;
    private static final RelativeLayout.LayoutParams SHIFT_RIGHT_BELOW_PARAMS;

    static {
        DEFAULT_BODY_PARAMS.alignWithParent = true;
        DEFAULT_BODY_PARAMS.addRule(BELOW, R.id.header_wrapper);
        DEFAULT_BODY_PARAMS.addRule(RIGHT_OF, R.id.thumbnail_views);

        DEFAULT_FLIP_BODY_PARAMS.alignWithParent = true;
        DEFAULT_FLIP_BODY_PARAMS.addRule(BELOW, R.id.header_wrapper);
        DEFAULT_FLIP_BODY_PARAMS.addRule(LEFT_OF, R.id.thumbnail_views);

        SHIFT_LEFT_PARAMS = new RelativeLayout.LayoutParams(DEFAULT_BODY_PARAMS);
        SHIFT_LEFT_PARAMS.removeRule(RIGHT_OF);

        SHIFT_RIGHT_PARAMS = new RelativeLayout.LayoutParams(DEFAULT_FLIP_BODY_PARAMS);
        SHIFT_RIGHT_PARAMS.removeRule(LEFT_OF);

        SHIFT_LEFT_BELOW_PARAMS = new RelativeLayout.LayoutParams(SHIFT_LEFT_PARAMS);
        SHIFT_LEFT_BELOW_PARAMS.addRule(BELOW, R.id.thumbnail_views);

        SHIFT_RIGHT_BELOW_PARAMS = new RelativeLayout.LayoutParams(SHIFT_RIGHT_PARAMS);
        SHIFT_RIGHT_BELOW_PARAMS.addRule(BELOW, R.id.thumbnail_views);
    }

    public void doShiftPostFormatting() {
        if (isInEditMode() || comment.getVisibility() != VISIBLE) return;
        int thumbnailViewsHeight = thumbnailViews.getVisibility() == VISIBLE ? thumbnailViews.getHeight() : 0;
        int headerHeight = headerWrapper.getHeight();
        int wrapHeight = headerHeight + comment.getHeight();
        boolean shiftSideOfThumb = headerHeight > thumbnailViewsHeight;
        boolean shiftBelowThumb = wrapHeight > 2 * thumbnailViewsHeight;
        boolean shift = post != null && (post.images.size() == 1 || (post.images.size() > 1
                && headerHeight > thumbnailViewsHeight / 2));
        if (shift) {
            if (!shiftSideOfThumb && shiftBelowThumb) {
                bodyWrapper.setLayoutParams(ChanSettings.flipPostCells.get()
                        ? SHIFT_RIGHT_BELOW_PARAMS
                        : SHIFT_LEFT_BELOW_PARAMS);
            } else if (shiftSideOfThumb) {
                bodyWrapper.setLayoutParams(ChanSettings.flipPostCells.get() ? SHIFT_RIGHT_PARAMS : SHIFT_LEFT_PARAMS);
            }
        }
    }

    private final String[] dubTexts =
            {"", "(Dubs)", "(Trips)", "(Quads)", "(Quints)", "(Sexes)", "(Septs)", "(Octs)", "(Nons)", "(Decs)"};

    private Spannable getRepeatDigits(int no) {
        CharSequence number = new StringBuilder().append(no).reverse();
        char init = number.charAt(0);
        int count = 1;
        for (int i = 1; i < number.length(); i++) {
            if (number.charAt(i) == init) {
                count += count == dubTexts.length ? 0 : 1;
                init = number.charAt(i);
            } else {
                break;
            }
        }
        return span(dubTexts[count - 1], new StyleSpan(Typeface.BOLD));
    }

    @Override
    public void unsetPost() {
        if (ChanSettings.shiftPostFormat.get()) {
            if (shifter != null) {
                shifter.removeListener();
                shifter = null;
            }
            bodyWrapper.setLayoutParams(ChanSettings.flipPostCells.get()
                    ? DEFAULT_FLIP_BODY_PARAMS
                    : DEFAULT_BODY_PARAMS);
        }
        icons.clear();
        headerWrapper.setOnLongClickListener(null);
        headerWrapper.setLongClickable(false);
        comment.setTextIsSelectable(false);
        comment.setOnTouchListener(null);
        comment.setMovementMethod(null);
        post = null;
    }

    private class PostImagesAdapter
            extends RecyclerView.Adapter<PostImagesAdapter.PostImageViewHolder> {
        public PostImagesAdapter() {
            setHasStableIds(true);
        }

        @NonNull
        @Override
        public PostImagesAdapter.PostImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context c = parent.getContext();
            ShapeablePostImageView thumbnailView = (ShapeablePostImageView) LayoutInflater
                    .from(parent.getContext())
                    .inflate(R.layout.subcell_post_cell_image, parent, false);
            thumbnailView.setLayoutParams(new ViewGroup.MarginLayoutParams(getThumbnailSize(c), getThumbnailSize(c)));
            return new PostImageViewHolder(thumbnailView);
        }

        @Override
        public void onBindViewHolder(@NonNull PostImagesAdapter.PostImageViewHolder holder, int position) {
            final PostImage image = post.images.get(position);
            holder.thumbnailView.setType(image);
            holder.loadPostImage(image, holder.thumbnailView);

            final Post internalPost = post;
            holder.thumbnailView.setOnClickListener(v -> {
                if (!internalPost.deleted || image.isInlined || NetUtils.isCached(image.imageUrl)) {
                    callback.onThumbnailClicked(image, holder.thumbnailView);
                }
            });

            if (ChanSettings.enableLongPressURLCopy.get()) {
                holder.thumbnailView.setOnLongClickListener(v -> {
                    setClipboardContent("Image URL", image.imageUrl.toString());
                    showToast(getContext(), R.string.image_url_copied_to_clipboard);
                    return true;
                });
            }
        }

        @Override
        public void onViewRecycled(
                @NonNull PostImagesAdapter.PostImageViewHolder holder
        ) {
            holder.thumbnailView.setType(null);
            holder.cancelLoad(holder.thumbnailView);
            holder.thumbnailView.setOnClickListener(null);
            holder.thumbnailView.setOnLongClickListener(null);
        }

        @Override
        public int getItemCount() {
            return post.images.size();
        }

        @Override
        public long getItemId(int position) {
            return post.images.get(position).hashCode();
        }

        private class PostImageViewHolder
                extends RecyclerView.ViewHolder
                implements ImageLoadable {
            private final ShapeablePostImageView thumbnailView;
            private ImageLoadableData data;

            public PostImageViewHolder(@NonNull ShapeablePostImageView itemView) {
                super(itemView);
                thumbnailView = itemView;
            }

            @Override
            public ImageLoadableData getImageLoadableData() {
                return data;
            }

            @Override
            public void setImageLoadableData(ImageLoadableData data) {
                this.data = data;
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
