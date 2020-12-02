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
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.repository.PageRepository;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.ChanPage;
import com.github.adamantcheese.chan.features.embedding.EmbeddingEngine.InvalidateFunction;
import com.github.adamantcheese.chan.ui.layout.FixedRatioLinearLayout;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.ui.view.FloatingMenu;
import com.github.adamantcheese.chan.ui.view.FloatingMenuItem;
import com.github.adamantcheese.chan.ui.view.PostImageThumbnailView;
import com.github.adamantcheese.chan.ui.view.ThumbnailView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import okhttp3.Call;

import static com.github.adamantcheese.chan.ui.adapter.PostsFilter.Order.isNotBumpOrder;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.StringUtils.applySearchSpans;

public class CardPostCell
        extends CardView
        implements PostCellInterface, InvalidateFunction {
    private static final int COMMENT_MAX_LINES = 10;

    private boolean bound;
    private Post post;
    private PostCellInterface.PostCellCallback callback;
    private boolean highlighted = false;
    private boolean compact = false;
    private String searchQuery;

    private PostImageThumbnailView thumbView;
    private TextView title;
    private TextView comment;
    private TextView replies;
    private ImageView options;
    private View filterMatchColor;

    private final List<Call> embedCalls = new CopyOnWriteArrayList<>();

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
        thumbView.setOnClickListener((view) -> callback.onThumbnailClicked(post.image(), (ThumbnailView) view));
        title = findViewById(R.id.title);
        comment = findViewById(R.id.comment);
        replies = findViewById(R.id.replies);
        options = findViewById(R.id.options);
        filterMatchColor = findViewById(R.id.filter_match_color);

        setOnClickListener((view) -> callback.onPostClicked(post));

        if (!isInEditMode()) {
            setCompact(compact);
        }

        options.setOnClickListener(v -> {
            List<FloatingMenuItem<Integer>> items = new ArrayList<>();
            List<FloatingMenuItem<Integer>> extraItems = new ArrayList<>();
            Object extraOption = callback.onPopulatePostOptions(post, items, extraItems);
            showOptions(v, items, extraItems, extraOption);
        });

        if (!isInEditMode() && ChanSettings.getBoardColumnCount() == 1) {
            ((LinearLayout.LayoutParams) comment.getLayoutParams()).height = ViewGroup.LayoutParams.WRAP_CONTENT;
            ((LinearLayout.LayoutParams) comment.getLayoutParams()).weight = 0;
            ((FixedRatioLinearLayout) findViewById(R.id.card_content)).setRatio(0.0f);
            thumbView.setOnLongClickListener(v -> {
                callback.onPostClicked(post);
                return true;
            });
            invalidate();
        }
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

                callback.onPostOptionClicked(anchor, post, item.getId(), false);
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
            ChanSettings.PostViewMode postViewMode,
            boolean compact,
            String searchQuery,
            Theme theme,
            RecyclerView attachedTo
    ) {
        if (this.post != null && bound) {
            bound = false;
            thumbView.setPostImage(null);
            for (Call c : embedCalls) {
                c.cancel();
            }
            embedCalls.clear();
            findViewById(R.id.embed_spinner).setVisibility(GONE);
            this.post = null;
        }

        this.post = post;
        this.highlighted = highlighted;
        this.callback = callback;
        this.searchQuery = searchQuery;

        bindPost(theme, post);

        this.compact = compact;
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

    private void bindPost(Theme theme, Post post) {
        bound = true;

        if (highlighted || post.isSavedReply) {
            setBackgroundColor(getAttrColor(getContext(), R.attr.highlight_color));
        } else {
            setBackgroundColor(getAttrColor(getContext(), R.attr.backcolor));
        }

        if (post.image() != null && !ChanSettings.textOnly.get()) {
            thumbView.setVisibility(VISIBLE);
            thumbView.setPostImage(post.image());
        } else {
            thumbView.setVisibility(GONE);
            thumbView.setPostImage(null);
        }

        if (post.filterHighlightedColor != 0) {
            filterMatchColor.setVisibility(VISIBLE);
            filterMatchColor.setBackgroundColor(post.filterHighlightedColor);
        } else {
            filterMatchColor.setVisibility(GONE);
        }

        title.setVisibility(TextUtils.isEmpty(post.subjectSpan) ? GONE : VISIBLE);
        title.setText(TextUtils.isEmpty(post.subjectSpan) ? null : applySearchSpans(post.subjectSpan, searchQuery));

        comment.setMaxLines(ChanSettings.getBoardColumnCount() != 1 ? COMMENT_MAX_LINES : Integer.MAX_VALUE);
        comment.setEllipsize(ChanSettings.getBoardColumnCount() != 1 ? TextUtils.TruncateAt.END : null);
        comment.setText(applySearchSpans(post.comment, searchQuery));

        String status = getString(R.string.card_stats, post.getReplies(), post.getImagesCount());
        if (!ChanSettings.neverShowPages.get()) {
            ChanPage p = PageRepository.getPage(post);
            if (p != null && isNotBumpOrder(ChanSettings.boardOrder.get())) {
                status += " Pg " + p.page;
            }
        }

        replies.setText(status);

        findViewById(R.id.embed_spinner).setVisibility(GONE);
        embedCalls.addAll(callback.getEmbeddingEngine().embed(theme, post, this));
        if (!embedCalls.isEmpty()) {
            findViewById(R.id.embed_spinner).setVisibility(VISIBLE);
        }
    }

    @Override
    public void invalidateView(Theme theme, Post post) {
        if (!bound || !this.post.equals(post)) return;
        embedCalls.clear();
        bindPost(theme, post);
    }

    private void setCompact(boolean compact) {
        int textSizeSp = ChanSettings.fontSize.get() + (compact ? -2 : 0);
        title.setTextSize(textSizeSp);
        comment.setTextSize(textSizeSp);
        replies.setTextSize(textSizeSp);

        int p = compact ? dp(3) : dp(8);

        // Same as the layout.
        title.setPadding(p, p, p, 0);
        comment.setPadding(p, p, p, 0);
        replies.setPadding(p, p / 2, p, p);
        options.setPadding(p, p / 2, p / 2, p / 2);
    }
}
