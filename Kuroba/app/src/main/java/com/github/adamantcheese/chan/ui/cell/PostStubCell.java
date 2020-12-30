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
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.ui.view.FloatingMenu;
import com.github.adamantcheese.chan.ui.view.FloatingMenuItem;
import com.github.adamantcheese.chan.ui.view.ThumbnailView;

import java.util.ArrayList;
import java.util.List;

import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;

public class PostStubCell
        extends RelativeLayout
        implements PostCellInterface {
    private static final int TITLE_MAX_LENGTH = 100;

    private Post post;
    private PostCellInterface.PostCellCallback callback;

    private TextView title;

    public PostStubCell(Context context) {
        super(context);
    }

    public PostStubCell(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PostStubCell(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        title = findViewById(R.id.title);
        ImageView options = findViewById(R.id.options);

        if (!isInEditMode()) {
            int textSizeSp = ChanSettings.fontSize.get();
            title.setTextSize(textSizeSp);

            int paddingPx = dp(textSizeSp - 6);
            title.setPadding(paddingPx, 0, 0, 0);
        }

        options.setOnClickListener(v -> {
            List<FloatingMenuItem<Integer>> items = new ArrayList<>();
            List<FloatingMenuItem<Integer>> extraItems = new ArrayList<>();
            Object extraOption = callback.onPopulatePostOptions(post, items, extraItems);
            showOptions(v, items, extraItems, extraOption);
        });
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
            Theme theme
    ) {
        this.post = post;
        this.callback = callback;

        bindPost(post, postViewMode);
    }

    public Post getPost() {
        return post;
    }

    public ThumbnailView getThumbnailView(PostImage postImage) {
        return null;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private void bindPost(Post post, ChanSettings.PostViewMode mode) {
        if (!TextUtils.isEmpty(post.subjectSpan)) {
            title.setText(post.subjectSpan);
        } else {
            CharSequence titleText;
            if (post.comment.length() > TITLE_MAX_LENGTH) {
                titleText = post.comment.subSequence(0, TITLE_MAX_LENGTH);
            } else {
                titleText = post.comment;
            }
            title.setText(titleText);
        }

        // These onclick listeners are overridden in PostAdapter's onBindViewHolder
        if (mode == ChanSettings.PostViewMode.CARD) {
            setBackgroundColor(getAttrColor(getContext(), R.attr.backcolor));
            int dp2 = dp(getContext(), 2);
            ((ViewGroup.MarginLayoutParams) getLayoutParams()).setMargins(dp2, dp2, dp2, dp2);
            ((RelativeLayout.LayoutParams) title.getLayoutParams()).removeRule(RelativeLayout.CENTER_VERTICAL);
            setElevation(dp2);
            title.setSingleLine(false);
            setOnClickListener(null);
        } else {
            setBackgroundResource(R.drawable.ripple_item_background);
            ((ViewGroup.MarginLayoutParams) getLayoutParams()).setMargins(0, 0, 0, 0);
            ((RelativeLayout.LayoutParams) title.getLayoutParams()).addRule(RelativeLayout.CENTER_VERTICAL);
            setElevation(0);
            title.setSingleLine(true);
            setOnClickListener(v -> callback.onPostClicked(post));
        }
    }
}
