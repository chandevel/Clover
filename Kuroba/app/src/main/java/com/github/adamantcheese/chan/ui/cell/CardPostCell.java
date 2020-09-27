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
import com.github.adamantcheese.chan.core.site.parser.CommentParserHelper;
import com.github.adamantcheese.chan.ui.layout.FixedRatioLinearLayout;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.ui.view.FloatingMenu;
import com.github.adamantcheese.chan.ui.view.FloatingMenuItem;
import com.github.adamantcheese.chan.ui.view.PostImageThumbnailView;
import com.github.adamantcheese.chan.ui.view.ThumbnailView;

import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;

import static com.github.adamantcheese.chan.ui.adapter.PostsFilter.Order.isNotBumpOrder;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

public class CardPostCell
        extends CardView
        implements PostCellInterface, View.OnClickListener {
    private static final int COMMENT_MAX_LINES = 10;

    private boolean bound;
    private Post post;
    private Loadable loadable;
    private PostCellInterface.PostCellCallback callback;
    private boolean compact = false;
    private Theme theme;
    private List<Call> extraCalls;

    private PostImageThumbnailView thumbView;
    private TextView title;
    private TextView comment;
    private TextView replies;
    private ImageView options;
    private View filterMatchColor;
    private RecyclerView recyclerView;

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
        thumbView.setOnClickListener(this);
        title = findViewById(R.id.title);
        comment = findViewById(R.id.comment);
        replies = findViewById(R.id.replies);
        options = findViewById(R.id.options);
        filterMatchColor = findViewById(R.id.filter_match_color);

        setOnClickListener(this);

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

    @Override
    public void onClick(View v) {
        if (v == thumbView) {
            callback.onThumbnailClicked(post.image(), thumbView);
        } else if (v == this) {
            callback.onPostClicked(post);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (extraCalls != null) {
            for (Call c : extraCalls) {
                c.cancel();
            }
            extraCalls = null;
        }

        bound = false;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (post != null && !bound) {
            bindPost(theme, post);
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
            Theme theme,
            RecyclerView attachedTo
    ) {
        if (this.post == post) {
            return;
        }

        if (this.post != null && bound) {
            bound = false;
            this.post = null;
        }

        this.loadable = loadable;
        this.post = post;
        this.callback = callback;
        this.theme = theme;
        this.recyclerView = attachedTo;

        bindPost(theme, post);

        if (this.compact != compact) {
            this.compact = compact;
            setCompact(compact);
        }
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

        if (post.image() != null && !ChanSettings.textOnly.get()) {
            thumbView.setVisibility(VISIBLE);
            thumbView.setPostImage(loadable, post.image());
        } else {
            thumbView.setVisibility(GONE);
            thumbView.setPostImage(loadable, null);
        }

        if (post.filterHighlightedColor != 0) {
            filterMatchColor.setVisibility(VISIBLE);
            filterMatchColor.setBackgroundColor(post.filterHighlightedColor);
        } else {
            filterMatchColor.setVisibility(GONE);
        }

        if (!TextUtils.isEmpty(post.subjectSpan)) {
            title.setVisibility(VISIBLE);
            title.setText(post.subjectSpan);
        } else {
            title.setVisibility(GONE);
            title.setText(null);
        }

        if (ChanSettings.getBoardColumnCount() != 1) {
            comment.setMaxLines(COMMENT_MAX_LINES);
            comment.setEllipsize(TextUtils.TruncateAt.END);
        } else {
            comment.setMaxLines(Integer.MAX_VALUE);
            comment.setEllipsize(null);
        }
        comment.setText(post.comment);

        String status = getString(R.string.card_stats, post.getReplies(), post.getImagesCount());
        if (!ChanSettings.neverShowPages.get()) {
            ChanPage p = PageRepository.getPage(post);
            if (p != null && isNotBumpOrder(ChanSettings.boardOrder.get())) {
                status += " Pg " + p.page;
            }
        }

        replies.setText(status);

        CommentParserHelper.addMathSpans(post, comment);
        if (post.needsExtraParse && extraCalls == null) {
            extraCalls = CommentParserHelper.replaceVideoLinks(theme, post, this::refresh, comment);
        }
    }

    private Void refresh() {
        if (recyclerView.getAdapter() != null) {
            recyclerView.getAdapter().notifyItemChanged(recyclerView.getChildAdapterPosition(this));
        }
        return null;
    }

    private void setCompact(boolean compact) {
        int textReduction = compact ? -2 : 0;
        int textSizeSp = Integer.parseInt(ChanSettings.fontSize.get()) + textReduction;
        title.setTextSize(textSizeSp);
        comment.setTextSize(textSizeSp);
        replies.setTextSize(textSizeSp);

        int p = compact ? dp(3) : dp(8);

        // Same as the layout.
        title.setPadding(p, p, p, 0);
        comment.setPadding(p, p, p, 0);
        replies.setPadding(p, p / 2, p, p);

        int optionsPadding = compact ? 0 : dp(5);
        options.setPadding(0, optionsPadding, optionsPadding, 0);
    }
}
