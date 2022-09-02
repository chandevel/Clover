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

import static androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET;
import static com.github.adamantcheese.chan.ui.adapter.PostsFilter.PostsOrder.BUMP_ORDER;
import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.utils.AndroidUtils.*;
import static com.github.adamantcheese.chan.utils.StringUtils.applySearchSpans;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.OneShotPreDrawListener;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.net.ImageLoadable;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.core.repository.PageRepository;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.ChanPage;
import com.github.adamantcheese.chan.ui.cell.PostCellInterface.PostCellCallback.PostOptions;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.ui.view.*;
import com.github.adamantcheese.chan.utils.AndroidUtils;

import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.HttpUrl;

public class CardPostCell
        extends CardView
        implements PostCellInterface, ImageLoadable {
    private Post post;
    private PostCellInterface.PostCellCallback callback;

    private ShapeablePostImageView thumbView;
    private TextView title;
    private TextView comment;
    private TextView replies;
    private ImageView options;
    private View filterMatchColor;
    private PostIcons icons;

    private OneShotPreDrawListener maxLinesUpdater;
    private Call thumbnailCall;
    private HttpUrl lastHttpUrl;

    public CardPostCell(Context context) {
        super(context);
    }

    public CardPostCell(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CardPostCell(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        thumbView = findViewById(R.id.thumbnail);
        title = findViewById(R.id.title);
        comment = findViewById(R.id.comment);
        replies = findViewById(R.id.replies);
        options = findViewById(R.id.options);
        filterMatchColor = findViewById(R.id.filter_match_color);
        icons = findViewById(R.id.icons);

        setCompact(false);

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
                    .build(), false);
        }

        setOnClickListener((view) -> callback.onPostClicked(post));

        options.setOnClickListener(v -> {
            List<FloatingMenuItem<PostOptions>> items = new ArrayList<>();
            List<FloatingMenuItem<PostOptions>> extraItems = new ArrayList<>();
            Object extraOption = callback.onPopulatePostOptions(post, items, extraItems);
            showOptions(v, items, extraItems, extraOption);
        });

        if (!isInEditMode() && ChanSettings.getBoardColumnCount() == 1) {
            thumbView.setOnLongClickListener(v -> {
                callback.onPostClicked(post);
                return true;
            });
        }
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
            public void onFloatingMenuItemClicked(
                    FloatingMenu<PostOptions> menu, FloatingMenuItem<PostOptions> item
            ) {
                if (item.getId() == extraOption) {
                    showOptions(anchor, extraItems, null, null);
                }

                callback.onPostOptionClicked(anchor, post, item.getId(), false);
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
        setCompact(compact);
        this.post = post;

        if (highlighted || post.isSavedReply) {
            setBackgroundColor(getAttrColor(getContext(), R.attr.highlight_color));
        } else {
            setBackgroundColor(getAttrColor(getContext(), R.attr.backcolor));
        }

        if (post.image() != null && !ChanSettings.textOnly.get()) {
            thumbView.setVisibility(VISIBLE);
            thumbView.setType(post.image());
            ((ConstraintLayout.LayoutParams) icons.getLayoutParams()).bottomToBottom = R.id.thumbnail_holder;
            ((ConstraintLayout.LayoutParams) icons.getLayoutParams()).bottomToTop = UNSET;
            icons.setBackgroundColor(0);
            loadPostImage(post.image(), thumbView);
        } else {
            thumbView.setVisibility(GONE);
            thumbView.setType(null);
            ((ConstraintLayout.LayoutParams) icons.getLayoutParams()).bottomToBottom = UNSET;
            ((ConstraintLayout.LayoutParams) icons.getLayoutParams()).bottomToTop = R.id.replies_section;
            icons.setBackgroundColor(AndroidUtils.getThemeAttrColor(theme, R.attr.backcolor));
            cancelLoad(thumbView);
        }

        thumbView.setOnClickListener((view) -> callback.onThumbnailClicked(post.image(), thumbView));
        if (ChanSettings.enableLongPressURLCopy.get()) {
            thumbView.setOnLongClickListener(v -> {
                if (post.image() != null) {
                    setClipboardContent("Image URL", post.image().imageUrl.toString());
                    showToast(getContext(), R.string.image_url_copied_to_clipboard);
                    return true;
                }
                return false;
            });
        }

        if (post.filterHighlightedColor != 0) {
            filterMatchColor.setVisibility(VISIBLE);
            filterMatchColor.setBackgroundColor(post.filterHighlightedColor);
        } else {
            filterMatchColor.setVisibility(GONE);
        }

        icons.set(post, false);

        title.setVisibility(TextUtils.isEmpty(post.subjectSpan) ? GONE : VISIBLE);
        title.setText(TextUtils.isEmpty(post.subjectSpan)
                ? null
                : applySearchSpans(theme, post.subjectSpan, callback.getSearchQuery()));

        comment.setText(applySearchSpans(theme, post.comment, callback.getSearchQuery()));
        // for ellipsize to work, set maxLines equal to the proper value after a measure
        if (ChanSettings.boardViewMode.get() == ChanSettings.PostViewMode.GRID) {
            maxLinesUpdater = OneShotPreDrawListener.add(this, () -> {
                comment.setMaxLines((int) Math.floor((comment.getHeight()
                        - comment.getPaddingTop()
                        - comment.getPaddingBottom()) / (float) comment.getLineHeight()));
            });
        } else {
            comment.setMaxLines(ChanSettings.getBoardColumnCount() == 1 ? 20 : 10);
        }

        String status = getString(R.string.card_stats, post.replies, post.imagesCount);
        if (!ChanSettings.neverShowPages.get()) {
            ChanPage p = PageRepository.getPage(post);
            if (p != null && ChanSettings.boardOrder.get() != BUMP_ORDER) {
                status += " Pg " + p.page;
            }
        }

        replies.setText(status);
    }

    public Post getPost() {
        return post;
    }

    public ImageView getThumbnailView(PostImage postImage) {
        return thumbView;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    public void unsetPost() {
        icons.clear();
        thumbView.setType(null);
        cancelLoad(thumbView);
        thumbView.setOnClickListener(null);
        thumbView.setOnLongClickListener(null);
        if (maxLinesUpdater != null) {
            maxLinesUpdater.removeListener();
            maxLinesUpdater = null;
        }
        comment.setText(null);
        comment.setMaxLines(Integer.MAX_VALUE);
        post = null;
    }

    private void setCompact(boolean compact) {
        int textSizeSp = isInEditMode() ? 15 : ChanSettings.fontSize.get();
        int compactSize = textSizeSp + (compact ? -2 : 0);
        title.setTextSize(compactSize);
        comment.setTextSize(compactSize);
        replies.setTextSize(compactSize);

        icons.getLayoutParams().height = (int) sp(getContext(), compactSize);

        float p = compact ? dp(getContext(), 3) : dp(getContext(), 8);

        // Same as the layout.
        updatePaddings(title, p, p, p, 0);
        updatePaddings(comment, p, p, p, 0);
        updatePaddings(replies, p, p / 2, p, p);
        updatePaddings(options, p, p / 2, p / 2, p / 2);

        ViewGroup.LayoutParams params = options.getLayoutParams();
        params.height = (int) (dp(getContext(), 32) - (compact ? 2 * p : 0));
        options.setLayoutParams(params);
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
        return thumbnailCall;
    }

    @Override
    public void setImageCall(Call call) {
        this.thumbnailCall = call;
    }
}
