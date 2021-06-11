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

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.core.repository.PageRepository;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.ChanPage;
import com.github.adamantcheese.chan.ui.cell.PostCellInterface.PostCellCallback.PostOptions;
import com.github.adamantcheese.chan.ui.layout.FixedRatioLinearLayout;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.ui.view.FloatingMenu;
import com.github.adamantcheese.chan.ui.view.FloatingMenuItem;
import com.github.adamantcheese.chan.ui.view.PostImageThumbnailView;
import com.github.adamantcheese.chan.ui.view.ThumbnailView;

import java.util.ArrayList;
import java.util.List;

import static com.github.adamantcheese.chan.ui.adapter.PostsFilter.Order.isNotBumpOrder;
import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.setClipboardContent;
import static com.github.adamantcheese.chan.utils.AndroidUtils.sp;
import static com.github.adamantcheese.chan.utils.StringUtils.applySearchSpans;

public class CardPostCell
        extends CardView
        implements PostCellInterface {
    private static final int COMMENT_MAX_LINES = 10;

    private Post post;
    private PostCellInterface.PostCellCallback callback;

    private int iconSizePx;

    private PostImageThumbnailView thumbView;
    private TextView title;
    private TextView comment;
    private TextView replies;
    private ImageView options;
    private View filterMatchColor;
    private PostIcons icons;

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
            icons.setWithoutText(new Post.Builder().sticky(true)
                    .closed(true)
                    .archived(true)
                    .board(Board.getDummyBoard())
                    .no(1)
                    .opId(1)
                    .setUnixTimestampSeconds(System.currentTimeMillis())
                    .comment("")
                    .build(), iconSizePx);
        }

        setOnClickListener((view) -> callback.onPostClicked(post));

        options.setOnClickListener(v -> {
            List<FloatingMenuItem<PostOptions>> items = new ArrayList<>();
            List<FloatingMenuItem<PostOptions>> extraItems = new ArrayList<>();
            Object extraOption = callback.onPopulatePostOptions(post, items, extraItems);
            showOptions(v, items, extraItems, extraOption);
        });

        if (!isInEditMode() && ChanSettings.getBoardColumnCount() == 1) {
            ((FixedRatioLinearLayout) findViewById(R.id.card_content)).setRatio(0.0f);
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
            Loadable loadable,
            final Post post,
            PostCellCallback callback,
            boolean inPopup,
            boolean highlighted,
            boolean compact,
            Theme theme
    ) {
        this.callback = callback;

        bindPost(theme, post, highlighted);
        setCompact(compact);
    }

    public Post getPost() {
        return post;
    }

    public ThumbnailView getThumbnailView(PostImage postImage) {
        return thumbView;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private void bindPost(Theme theme, Post post, boolean highlighted) {
        this.post = post;

        if (highlighted || post.isSavedReply) {
            setBackgroundColor(getAttrColor(getContext(), R.attr.highlight_color));
        } else {
            setBackgroundColor(getAttrColor(getContext(), R.attr.backcolor));
        }

        if (post.image() != null && !ChanSettings.textOnly.get()) {
            thumbView.setVisibility(VISIBLE);
            thumbView.setPostImage(post.image(), -1);
        } else {
            thumbView.setVisibility(GONE);
            thumbView.setPostImage(null, 0);
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

        icons.setWithoutText(post, iconSizePx);

        title.setVisibility(TextUtils.isEmpty(post.subjectSpan) ? GONE : VISIBLE);
        title.setText(TextUtils.isEmpty(post.subjectSpan)
                ? null
                : applySearchSpans(theme, post.subjectSpan, callback.getSearchQuery()));

        comment.setMaxLines(ChanSettings.getBoardColumnCount() != 1 ? COMMENT_MAX_LINES : Integer.MAX_VALUE);
        comment.setText(applySearchSpans(theme, post.comment, callback.getSearchQuery()));

        String status = getString(R.string.card_stats, post.getReplies(), post.getImagesCount());
        if (!ChanSettings.neverShowPages.get()) {
            ChanPage p = PageRepository.getPage(post);
            if (p != null && isNotBumpOrder(ChanSettings.boardOrder.get())) {
                status += " Pg " + p.page;
            }
        }

        replies.setText(status);
    }

    @Override
    public void unsetPost() {
        icons.clear();
        thumbView.setPostImage(null, 0);
        thumbView.setOnClickListener(null);
        thumbView.setOnLongClickListener(null);
        post = null;
    }

    private void setCompact(boolean compact) {
        int textSizeSp = isInEditMode() ? 15 : ChanSettings.fontSize.get();
        int compactSize = textSizeSp + (compact ? -2 : 0);
        iconSizePx = sp(getContext(), compactSize);
        title.setTextSize(compactSize);
        icons.setHeight(iconSizePx);
        comment.setTextSize(compactSize);
        replies.setTextSize(compactSize);

        icons.setSpacing(dp(getContext(), 4));

        int p = compact ? dp(getContext(), 3) : dp(getContext(), 8);

        // Same as the layout.
        title.setPadding(p, p, p, 0);
        comment.setPadding(p, p, p, 0);
        replies.setPadding(p, p, p / 2, p);
        options.setPadding(p, p / 2, p / 2, p / 2);

        ViewGroup.LayoutParams params = options.getLayoutParams();
        params.height = dp(getContext(), 32) - (compact ? 2 * p : 0);
        options.setLayoutParams(params);
    }
}
