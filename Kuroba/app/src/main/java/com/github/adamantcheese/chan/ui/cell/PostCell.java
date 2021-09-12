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
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.OneShotPreDrawListener;
import androidx.recyclerview.widget.RecyclerView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostHttpIcon;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.net.ImageLoadable;
import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
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
import com.github.adamantcheese.chan.ui.view.FloatingMenu;
import com.github.adamantcheese.chan.ui.view.FloatingMenuItem;
import com.github.adamantcheese.chan.ui.view.ShapeablePostImageView;
import com.google.android.material.shape.ShapeAppearanceModel;

import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.HttpUrl;

import static android.text.TextUtils.isEmpty;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.widget.RelativeLayout.BELOW;
import static android.widget.RelativeLayout.LEFT_OF;
import static android.widget.RelativeLayout.RIGHT_OF;
import static com.github.adamantcheese.chan.core.settings.ChanSettings.getThumbnailSize;
import static com.github.adamantcheese.chan.core.site.SiteEndpoints.IconType.OTHER;
import static com.github.adamantcheese.chan.ui.adapter.PostsFilter.PostsOrder.BUMP;
import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getQuantityString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.openIntent;
import static com.github.adamantcheese.chan.utils.AndroidUtils.setClipboardContent;
import static com.github.adamantcheese.chan.utils.AndroidUtils.sp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.updatePaddings;
import static com.github.adamantcheese.chan.utils.PostUtils.getReadableFileSize;
import static com.github.adamantcheese.chan.utils.StringUtils.applySearchSpans;
import static java.util.concurrent.TimeUnit.SECONDS;

public class PostCell
        extends LinearLayout
        implements PostCellInterface {
    private RecyclerView thumbnailViews;
    private TextView title;
    private PostIcons icons;
    private TextView comment;
    private TextView replies;
    private View filterMatchColor;

    private RelativeLayout headerWrapper;
    private ConstraintLayout bodyWrapper;

    private float detailsSizePx;
    private boolean threadMode;

    private Loadable loadable;
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
        ((MarginLayoutParams) thumbnailViews.getLayoutParams()).setMargins(
                (!isInEditMode() && ChanSettings.flipPostCells.get()) ? 0 : (int) paddingPx,
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
            icons.set(new Post.Builder().sticky(true)
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

        if (post.filterHighlightedColor != 0) {
            filterMatchColor.setVisibility(VISIBLE);
            filterMatchColor.setBackgroundColor(post.filterHighlightedColor);
        } else {
            filterMatchColor.setVisibility(GONE);
        }

        if (post.images.isEmpty() || ChanSettings.textOnly.get()) {
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
        SpannableStringBuilder date = new SpannableStringBuilder().append("No. ")
                .append(String.valueOf(post.no))
                .append(" ")
                .append(dubs)
                .append(ChanSettings.addDubs.get() ? (dubs.length() > 0 ? " " : "") : "")
                .append(ChanSettings.postFullDate.get()
                        ? PostHelper.getLocalDate(post)
                        : DateUtils.getRelativeTimeSpanString(
                                SECONDS.toMillis(post.time),
                                System.currentTimeMillis(),
                                DateUtils.SECOND_IN_MILLIS,
                                0
                        ));
        date.setSpan(new ForegroundColorSpanHashed(detailsColor), 0, date.length(), 0);
        date.setSpan(new AbsoluteSizeSpanHashed((int) detailsSizePx), 0, date.length(), 0);

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
                fileInfo.setSpan(new AbsoluteSizeSpanHashed((int) detailsSizePx), 0, fileInfo.length(), 0);
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
                fileInfo.setSpan(new AbsoluteSizeSpanHashed((int) detailsSizePx), 0, fileInfo.length(), 0);
                titleParts.append(fileInfo);
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

            // Sets focusable to auto, clickable and longclickable to true.
            comment.setMovementMethod(new PostViewMovementMethod(post, callback));

            // And this sets clickable to appropriate values again.
            comment.setOnTouchListener((v, event) -> doubleTapComment.onTouchEvent(event));

            if (loadable.site.siteFeature(Site.SiteFeature.POSTING)) {
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
            comment.setOnTouchListener(null);
            comment.setClickable(false);

            // Sets focusable to auto, clickable and longclickable to false.
            comment.setMovementMethod(null);

            headerWrapper.setBackgroundResource(0);
            headerWrapper.setLongClickable(false);
        }

        int textSizeSp = isInEditMode() ? 15 : ChanSettings.fontSize.get();
        float paddingPx = dp(getContext(), textSizeSp - 7);

        if ((!threadMode && post.getReplies() > 0) || (post.repliesFrom.size() > 0)) {
            replies.setVisibility(VISIBLE);

            int replyCount = threadMode ? post.repliesFrom.size() : post.getReplies();
            SpannableStringBuilder text = new SpannableStringBuilder();
            text.append(getQuantityString(R.plurals.reply, replyCount));
            if (replyCount > 7 && loadable.isThreadMode()) {
                text.setSpan(new StyleSpan(Typeface.BOLD), 0, text.length(), 0);
            }

            if (!threadMode && post.getImagesCount() > 0) {
                text.append(", ").append(getQuantityString(R.plurals.image, post.getImagesCount()));
            }

            if (!ChanSettings.neverShowPages.get() && loadable.isCatalogMode()) {
                ChanPage p = PageRepository.getPage(post);
                if (p != null && ChanSettings.boardOrder.get() != BUMP) {
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

    private SpannableString getRepeatDigits(int no) {
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
        SpannableString s = new SpannableString(dubTexts[count - 1]);
        s.setSpan(new StyleSpan(Typeface.BOLD), 0, s.length(), 0);
        return s;
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
            ShapeablePostImageView thumbnailView = new ShapeablePostImageView(c);
            thumbnailView.setLayoutParams(new ViewGroup.MarginLayoutParams(getThumbnailSize(c), getThumbnailSize(c)));
            thumbnailView.setShapeAppearanceModel(ShapeAppearanceModel.builder().setAllCornerSizes(dp(c, 2)).build());
            return new PostImageViewHolder(thumbnailView);
        }

        @Override
        public void onBindViewHolder(@NonNull PostImagesAdapter.PostImageViewHolder holder, int position) {
            final PostImage image = post.images.get(position);
            holder.thumbnailView.setType(image);
            holder.loadPostImage(image, holder.thumbnailView);

            final Post internalPost = post;
            holder.thumbnailView.setOnClickListener(v -> {
                if (!internalPost.deleted.get() || image.isInlined || NetUtils.isCached(image.imageUrl)) {
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
            private Call imageCall;
            private HttpUrl lastHttpUrl;

            public PostImageViewHolder(@NonNull ShapeablePostImageView itemView) {
                super(itemView);
                thumbnailView = itemView;
            }

            @Override
            public HttpUrl getLastHttpUrl() {
                return lastHttpUrl;
            }

            @Override
            public void setLastHttpUrl(HttpUrl url) {
                lastHttpUrl = url;
            }

            @Override
            public Call getImageCall() {
                return imageCall;
            }

            @Override
            public void setImageCall(Call call) {
                this.imageCall = call;
            }

            @Override
            public float getMaxImageSize() {
                return getThumbnailSize(getContext());
            }
        }
    }

    public static class DPSpacingItemDecoration
            extends RecyclerView.ItemDecoration {
        private final float spacing;

        public DPSpacingItemDecoration(Context context, float spacing) {
            this.spacing = dp(context, spacing);
        }

        @Override
        public void getItemOffsets(
                @NonNull Rect outRect,
                @NonNull View view,
                @NonNull RecyclerView parent,
                @NonNull RecyclerView.State state
        ) {
            super.getItemOffsets(outRect, view, parent, state);
            outRect.bottom = (int) spacing;
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
