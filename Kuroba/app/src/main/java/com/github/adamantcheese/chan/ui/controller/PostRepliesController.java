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
package com.github.adamantcheese.chan.ui.controller;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.ChanThread;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.presenter.ThreadPresenter;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.adapter.PostAdapter;
import com.github.adamantcheese.chan.ui.adapter.PostsFilter;
import com.github.adamantcheese.chan.ui.cell.PostCellInterface;
import com.github.adamantcheese.chan.ui.helper.PostPopupHelper;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.ui.view.LoadView;
import com.github.adamantcheese.chan.ui.view.ThumbnailView;
import com.github.adamantcheese.chan.utils.RecyclerUtils;

public class PostRepliesController
        extends BaseFloatingController {
    private final PostPopupHelper postPopupHelper;
    private final ThreadPresenter presenter;

    private boolean first = true;

    private LoadView loadView;
    private RecyclerView recyclerView;
    private PostPopupHelper.RepliesData displayingData;

    public PostRepliesController(Context context, PostPopupHelper postPopupHelper, ThreadPresenter presenter) {
        super(context);
        this.postPopupHelper = postPopupHelper;
        this.presenter = presenter;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Clicking outside the popup view
        view.setOnClickListener(v -> postPopupHelper.pop());

        loadView = view.findViewById(R.id.loadview);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.layout_post_replies_container;
    }

    public ThumbnailView getThumbnail(PostImage postImage) {
        if (recyclerView == null) return null;
        ThumbnailView thumbnail = null;
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View view = recyclerView.getChildAt(i);
            if (view instanceof PostCellInterface) {
                PostCellInterface postView = (PostCellInterface) view;
                Post post = postView.getPost();

                for (PostImage p : post.images) {
                    if (p.equals(postImage)) {
                        thumbnail = postView.getThumbnailView(postImage);
                    }
                }
            }
        }
        return thumbnail;
    }

    public void scrollTo(int displayPosition) {
        if (displayPosition >= 0) {
            recyclerView.smoothScrollToPosition(displayPosition);
        } else {
            recyclerView.smoothScrollToPosition(displayingData.posts.size() - 1);
        }
    }

    public void displayData(Loadable loadable, final PostPopupHelper.RepliesData data) {
        displayingData = data;

        View dataView = LayoutInflater.from(context)
                .inflate(ChanSettings.repliesButtonsBottom.get()
                        ? R.layout.layout_post_replies_bottombuttons
                        : R.layout.layout_post_replies, null);

        recyclerView = dataView.findViewById(R.id.post_list);

        View repliesBack = dataView.findViewById(R.id.replies_back);
        repliesBack.setOnClickListener(v -> postPopupHelper.pop());

        View repliesClose = dataView.findViewById(R.id.replies_close);
        repliesClose.setOnClickListener(v -> postPopupHelper.popAll());

        PostAdapter adapter = new PostAdapter(recyclerView, null, presenter, null, ThemeHelper.getTheme()) {
            @Override
            public boolean isInPopup() {
                return true;
            }

            @Override
            public boolean shouldHighlight(Post post) {
                return false;
            }

            @Override
            public boolean isCompact() {
                return false;
            }

            @Override
            public boolean showStatusView() {
                return false;
            }
        };
        recyclerView.setAdapter(adapter);
        adapter.setThread(new ChanThread(loadable, displayingData.posts),
                new PostsFilter(PostsFilter.Order.BUMP, null)
        );
        adapter.lastSeenIndicatorPosition = Integer.MIN_VALUE; //disable last seen indicator inside of reply popups
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        layoutManager.scrollToPositionWithOffset(data.position.index, data.position.top);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                data.position = RecyclerUtils.getIndexAndTop(recyclerView);
            }
        });

        loadView.setFadeDuration(first ? 0 : 150);
        first = false;
        loadView.setView(dataView);
    }

    @Override
    public boolean onBack() {
        postPopupHelper.pop();
        return true;
    }
}
