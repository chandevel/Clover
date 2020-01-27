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
import static com.github.adamantcheese.chan.utils.AndroidUtils.setRoundItemBackground;

public class PostStubCell
        extends RelativeLayout
        implements PostCellInterface, View.OnClickListener {
    private static final int TITLE_MAX_LENGTH = 100;

    private boolean bound;
    private Post post;
    private ChanSettings.PostViewMode postViewMode;
    private boolean showDivider;
    private PostCellInterface.PostCellCallback callback;

    private TextView title;
    private View divider;

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
        setRoundItemBackground(options);
        divider = findViewById(R.id.divider);

        int textSizeSp = Integer.parseInt(ChanSettings.fontSize.get());
        title.setTextSize(textSizeSp);

        int paddingPx = dp(textSizeSp - 6);
        title.setPadding(paddingPx, 0, 0, 0);

        RelativeLayout.LayoutParams dividerParams = (RelativeLayout.LayoutParams) divider.getLayoutParams();
        dividerParams.leftMargin = paddingPx;
        dividerParams.rightMargin = paddingPx;
        divider.setLayoutParams(dividerParams);

        setOnClickListener(this);

        options.setOnClickListener(v -> {
            List<FloatingMenuItem> items = new ArrayList<>();
            List<FloatingMenuItem> extraItems = new ArrayList<>();
            Object extraOption = callback.onPopulatePostOptions(post, items, extraItems);
            showOptions(v, items, extraItems, extraOption);
        });
    }

    private void showOptions(
            View anchor, List<FloatingMenuItem> items, List<FloatingMenuItem> extraItems, Object extraOption
    ) {
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
        if (v == this) {
            callback.onPostClicked(post);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (post != null && bound) {
            unbindPost();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (post != null && !bound) {
            bindPost(post);
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
            Theme theme
    ) {
        if (this.post == post) {
            return;
        }

        if (this.post != null && bound) {
            unbindPost();
            this.post = null;
        }

        this.post = post;
        this.callback = callback;
        this.postViewMode = postViewMode;
        this.showDivider = showDivider;

        bindPost(post);
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

    private void bindPost(Post post) {
        bound = true;

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

        divider.setVisibility(postViewMode == ChanSettings.PostViewMode.CARD ? GONE : (showDivider ? VISIBLE : GONE));
    }

    private void unbindPost() {
        bound = false;
    }
}
