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

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.support.v7.widget.CardView;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.adamantcheese.github.chan.R;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.layout.FixedRatioLinearLayout;
import com.github.adamantcheese.chan.ui.text.FastTextView;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.ui.view.FloatingMenu;
import com.github.adamantcheese.chan.ui.view.FloatingMenuItem;
import com.github.adamantcheese.chan.ui.view.PostImageThumbnailView;
import com.github.adamantcheese.chan.ui.view.ThumbnailView;

import java.util.ArrayList;
import java.util.List;

import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.setRoundItemBackground;

public class CardPostCell extends CardView implements PostCellInterface, View.OnClickListener, View.OnLongClickListener {
    private static final int COMMENT_MAX_LENGTH = 200;

    private boolean bound;
    private Post post;
    private PostCellInterface.PostCellCallback callback;
    private boolean compact = false;

    private PostImageThumbnailView thumbnailView;
    private TextView title;
    private FastTextView comment;
    private TextView replies;
    private ImageView options;
    private View filterMatchColor;

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

        FixedRatioLinearLayout content = findViewById(R.id.card_content);
        content.setRatio(9f / 18f);
        thumbnailView = findViewById(R.id.thumbnail);
        thumbnailView.setRatio(16f / 13f);
        thumbnailView.setOnClickListener(this);
        title = findViewById(R.id.title);
        comment = findViewById(R.id.comment);
        replies = findViewById(R.id.replies);
        options = findViewById(R.id.options);
        setRoundItemBackground(options);
        filterMatchColor = findViewById(R.id.filter_match_color);

        setOnClickListener(this);
        setOnLongClickListener(this);

        setCompact(compact);

        options.setOnClickListener(v -> {
            List<FloatingMenuItem> items = new ArrayList<>();
            List<FloatingMenuItem> extraItems = new ArrayList<>();
            Object extraOption = callback.onPopulatePostOptions(post, items, extraItems);
            showOptions(v, items, extraItems, extraOption);
        });
    }

    private void showOptions(View anchor, List<FloatingMenuItem> items,
                             List<FloatingMenuItem> extraItems,
                             Object extraOption) {
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
            }
        });
        menu.show();
    }

    @Override
    public void onClick(View v) {
        if (v == thumbnailView) {
            callback.onThumbnailClicked(post.image(), thumbnailView);
        } else if (v == this) {
            callback.onPostClicked(post);
        }
    }

    @Override
    public boolean onLongClick(View v) {
        if (v == this) {
            callback.onPostLongClicked(post);
        }

        return true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (post != null && bound) {
            bound = false;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (post != null && !bound) {
            bindPost(post);
        }
    }

    public void setPost(final Post post, PostCellInterface.PostCellCallback callback,
                        boolean selectable, boolean highlighted, boolean selected, int markedNo,
                        boolean showDivider, ChanSettings.PostViewMode postViewMode,
                        boolean compact) {
        if (this.post == post) {
            return;
        }

        if (this.post != null && bound) {
            bound = false;
            this.post = null;
        }

        this.post = post;
        this.callback = callback;

        bindPost(post);

        if (this.compact != compact) {
            this.compact = compact;
            setCompact(compact);
        }
    }

    public Post getPost() {
        return post;
    }

    public ThumbnailView getThumbnailView(PostImage postImage) {
        return thumbnailView;
    }

    @Override
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public boolean hasOverlappingRendering() {
        return false;
    }

    private void bindPost(Post post) {
        bound = true;

        if (post.image() != null && !ChanSettings.textOnly.get()) {
            thumbnailView.setVisibility(View.VISIBLE);
            thumbnailView.setPostImage(post.image(), thumbnailView.getWidth(), thumbnailView.getHeight());
        } else {
            thumbnailView.setVisibility(View.GONE);
            thumbnailView.setPostImage(null, 0, 0);
        }

        if (post.filterHighlightedColor != 0) {
            filterMatchColor.setVisibility(View.VISIBLE);
            filterMatchColor.setBackgroundColor(post.filterHighlightedColor);
        } else {
            filterMatchColor.setVisibility(View.GONE);
        }

        if (!TextUtils.isEmpty(post.subjectSpan)) {
            title.setVisibility(View.VISIBLE);
            title.setText(post.subjectSpan);
        } else {
            title.setVisibility(View.GONE);
            title.setText(null);
        }

        CharSequence commentText;
        if (post.comment.length() > COMMENT_MAX_LENGTH) {
            commentText = post.comment.subSequence(0, COMMENT_MAX_LENGTH);
        } else {
            commentText = post.comment;
        }

        comment.setText(commentText);
        comment.setTextColor(ThemeHelper.theme().textPrimary);

        replies.setText(getResources().getString(R.string.card_stats, post.getReplies(), post.getImagesCount()));
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
