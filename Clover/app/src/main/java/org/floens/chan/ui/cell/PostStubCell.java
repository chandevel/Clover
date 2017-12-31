/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
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
package org.floens.chan.ui.cell;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.floens.chan.R;
import org.floens.chan.core.model.Post;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.theme.Theme;
import org.floens.chan.ui.theme.ThemeHelper;
import org.floens.chan.ui.view.FloatingMenu;
import org.floens.chan.ui.view.FloatingMenuItem;
import org.floens.chan.ui.view.ThumbnailView;

import java.util.ArrayList;
import java.util.List;

import static org.floens.chan.utils.AndroidUtils.dp;
import static org.floens.chan.utils.AndroidUtils.setRoundItemBackground;

public class PostStubCell extends RelativeLayout implements PostCellInterface, View.OnClickListener {
    private static final int TITLE_MAX_LENGTH = 100;

    private boolean bound;
    private Theme theme;
    private Post post;
    private ChanSettings.PostViewMode postViewMode;
    private boolean showDivider;
    private PostCellInterface.PostCellCallback callback;

    private TextView title;
    private ImageView options;
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

        title = (TextView) findViewById(R.id.title);
        options = (ImageView) findViewById(R.id.options);
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

        options.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                List<FloatingMenuItem> items = new ArrayList<>();

                callback.onPopulatePostOptions(post, items);

                FloatingMenu menu = new FloatingMenu(getContext(), v, items);
                menu.setCallback(new FloatingMenu.FloatingMenuCallback() {
                    @Override
                    public void onFloatingMenuItemClicked(FloatingMenu menu, FloatingMenuItem item) {
                        callback.onPostOptionClicked(post, item.getId());
                    }

                    @Override
                    public void onFloatingMenuDismissed(FloatingMenu menu) {
                    }
                });
                menu.show();
            }
        });
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
            unbindPost(post);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (post != null && !bound) {
            bindPost(theme, post);
        }
    }

    public void setPost(Theme theme, final Post post, PostCellInterface.PostCellCallback callback,
                        boolean selectable, boolean highlighted, boolean selected, int markedNo,
                        boolean showDivider, ChanSettings.PostViewMode postViewMode) {
        if (this.post == post) {
            return;
        }

        if (theme == null) {
            theme = ThemeHelper.theme();
        }

        if (this.post != null && bound) {
            unbindPost(this.post);
            this.post = null;
        }

        this.theme = theme;
        this.post = post;
        this.callback = callback;
        this.postViewMode = postViewMode;
        this.showDivider = showDivider;

        bindPost(theme, post);
    }

    public Post getPost() {
        return post;
    }

    public ThumbnailView getThumbnailView() {
        return null;
    }

    @Override
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public boolean hasOverlappingRendering() {
        return false;
    }

    private void bindPost(Theme theme, Post post) {
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

        divider.setVisibility(postViewMode == ChanSettings.PostViewMode.CARD ? GONE :
                (showDivider ? VISIBLE : GONE));
    }

    private void unbindPost(Post post) {
        bound = false;
    }
}
