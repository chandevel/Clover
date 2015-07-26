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
package org.floens.chan.ui.layout;

import android.content.Context;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.floens.chan.R;
import org.floens.chan.core.model.ChanThread;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.PostImage;
import org.floens.chan.core.presenter.ReplyPresenter;
import org.floens.chan.ui.adapter.PostAdapter;
import org.floens.chan.ui.adapter.PostsFilter;
import org.floens.chan.ui.cell.PostCell;
import org.floens.chan.ui.cell.PostCellInterface;
import org.floens.chan.ui.cell.ThreadStatusCell;
import org.floens.chan.ui.view.ThumbnailView;
import org.floens.chan.utils.AndroidUtils;
import org.floens.chan.utils.AnimationUtils;

import java.util.List;

import static org.floens.chan.utils.AndroidUtils.ROBOTO_MEDIUM;
import static org.floens.chan.utils.AndroidUtils.dp;
import static org.floens.chan.utils.AndroidUtils.getAttrColor;

/**
 * A layout that wraps around a {@link RecyclerView} to manage showing posts.
 */
public class ThreadListLayout extends LinearLayout implements ReplyLayout.ReplyLayoutCallback {
    public static final int MAX_SMOOTH_SCROLL_DISTANCE = 20;

    private ReplyLayout reply;
    private TextView searchStatus;
    private RecyclerView recyclerView;
    private RecyclerView.LayoutManager layoutManager;
    private PostAdapter postAdapter;
    private ChanThread showingThread;
    private ThreadListLayoutCallback callback;
    private ReplyLayoutStateCallback replyLayoutStateCallback;
    private boolean replyOpen;
    private PostCellInterface.PostViewMode postViewMode;
    private int spanCount = 2;
    private int background;

    public ThreadListLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        reply = (ReplyLayout) findViewById(R.id.reply);
        reply.setCallback(this);

        searchStatus = (TextView) findViewById(R.id.search_status);
        searchStatus.setTypeface(ROBOTO_MEDIUM);

        recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
    }

    public void setCallbacks(PostAdapter.PostAdapterCallback postAdapterCallback, PostCell.PostCellCallback postCellCallback,
                             ThreadStatusCell.Callback statusCellCallback, ThreadListLayoutCallback callback,
                             ReplyLayoutStateCallback replyLayoutStateCallback) {
        this.callback = callback;
        this.replyLayoutStateCallback = replyLayoutStateCallback;

        postAdapter = new PostAdapter(recyclerView, postAdapterCallback, postCellCallback, statusCellCallback);
        recyclerView.setAdapter(postAdapter);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                // onScrolled can be called after cleanup()
                if (showingThread != null) {
                    switch (postViewMode) {
                        case LIST:
                            showingThread.loadable.listViewIndex = Math.max(0, getTopAdapterPosition());
                            break;
                        case CARD:
                            showingThread.loadable.listViewIndex = Math.max(0, getTopAdapterPosition());
                            break;
                    }
                }
            }
        });
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int cardWidth = getResources().getDimensionPixelSize(R.dimen.grid_card_width);
        int maxSpans = getResources().getInteger(R.integer.grid_card_max_spans);

        spanCount = Math.max(1, Math.round(getMeasuredWidth() / cardWidth));
        if (maxSpans > 1 && spanCount > maxSpans) {
            spanCount = maxSpans;
        }

        if (postViewMode == PostCellInterface.PostViewMode.CARD) {
            ((GridLayoutManager) layoutManager).setSpanCount(spanCount);
        }
    }

    public void setPostViewMode(PostCellInterface.PostViewMode postViewMode) {
        if (this.postViewMode != postViewMode) {
            this.postViewMode = postViewMode;

            layoutManager = null;

            switch (postViewMode) {
                case LIST:
                    LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
                    recyclerView.setPadding(0, 0, 0, 0);
                    recyclerView.setLayoutManager(linearLayoutManager);
                    layoutManager = linearLayoutManager;

                    if (background != R.attr.backcolor) {
                        background = R.attr.backcolor;
                        setBackgroundColor(getAttrColor(getContext(), R.attr.backcolor));
                    }

                    break;
                case CARD:
                    GridLayoutManager gridLayoutManager = new GridLayoutManager(null, spanCount, GridLayoutManager.VERTICAL, false);
                    // The cards have a 4dp padding, this way there is always 8dp between the edges
                    recyclerView.setPadding(dp(4), dp(4), dp(4), dp(4));
                    recyclerView.setLayoutManager(gridLayoutManager);
                    layoutManager = gridLayoutManager;

                    if (background != R.attr.backcolor_secondary) {
                        background = R.attr.backcolor_secondary;
                        setBackgroundColor(getAttrColor(getContext(), R.attr.backcolor_secondary));
                    }

                    break;
            }

            recyclerView.getRecycledViewPool().clear();

            postAdapter.setPostViewMode(postViewMode);
        }
    }

    public void showPosts(ChanThread thread, PostsFilter filter, boolean initial) {
        showingThread = thread;
        if (initial) {
            reply.bindLoadable(showingThread.loadable);

            recyclerView.setLayoutManager(null);
            recyclerView.setLayoutManager(layoutManager);
            recyclerView.getRecycledViewPool().clear();

            switch (postViewMode) {
                case LIST:
                    ((LinearLayoutManager) layoutManager).scrollToPositionWithOffset(thread.loadable.listViewIndex, 0);
                    break;
                case CARD:
                    ((GridLayoutManager) layoutManager).scrollToPositionWithOffset(thread.loadable.listViewIndex, 0);
                    break;
            }
        } else {
            switch (postViewMode) {
                case LIST:
                    LinearLayoutManager linearLayoutManager = (LinearLayoutManager) layoutManager;
                    if (linearLayoutManager.findLastCompletelyVisibleItemPosition() == postAdapter.getItemCount() - 1) {
                        linearLayoutManager.scrollToPositionWithOffset(postAdapter.getItemCount() - 1, 0);
                    }
                    break;
                case CARD:
                    // No op
                    break;
            }
        }

        postAdapter.setThread(thread, filter);
    }

    public boolean onBack() {
        if (reply.onBack()) {
            return true;
        } else if (replyOpen) {
            openReply(false);
            return true;
        } else {
            return false;
        }
    }

    public void openReply(boolean open) {
        if (showingThread != null && replyOpen != open) {
            this.replyOpen = open;
            AnimationUtils.animateHeight(reply, replyOpen, getWidth(), 500, reply);
            if (open) {
                reply.focusComment();
            } else {
                AndroidUtils.hideKeyboard(reply);
            }
            replyLayoutStateCallback.replyLayoutOpen(open);
        }
    }

    public ReplyPresenter getReplyPresenter() {
        return reply.getPresenter();
    }

    public void showError(String error) {
        postAdapter.showError(error);
    }

    public void showSearch(boolean show) {
        AnimationUtils.animateHeight(searchStatus, show);

        if (show) {
            searchStatus.setText(R.string.search_empty);
        }
    }

    public void setSearchStatus(String query, boolean setEmptyText, boolean hideKeyboard) {
        if (hideKeyboard) {
            AndroidUtils.hideKeyboard(this);
        }

        if (setEmptyText) {
            searchStatus.setText(R.string.search_empty);
        }

        if (query != null) {
            int size = postAdapter.getDisplaySize();
            searchStatus.setText(getContext().getString(R.string.search_results,
                    size, getContext().getResources().getQuantityString(R.plurals.posts, size, size), query));
        }
    }

    public boolean canChildScrollUp() {
        if (replyOpen) {
            return true;
        }

        switch (postViewMode) {
            case LIST:
                if (getTopAdapterPosition() == 0) {
                    View top = layoutManager.findViewByPosition(0);
                    return top.getTop() != 0;
                }
                break;
            case CARD:
                if (getTopAdapterPosition() == 0) {
                    View top = layoutManager.findViewByPosition(0);
                    return top.getTop() != dp(8); // 4dp for the cards, 4dp for this layout
                }
                break;
        }
        return true;
    }

    public boolean scrolledToBottom() {
        switch (postViewMode) {
            case LIST:
                if (((LinearLayoutManager) layoutManager).findLastVisibleItemPosition() == postAdapter.getItemCount() - 1) {
                    return true;
                }
                break;
            case CARD:
                if (((GridLayoutManager) layoutManager).findLastVisibleItemPosition() == postAdapter.getItemCount() - 1) {
                    return true;
                }
                break;
        }
        return false;
    }

    public void cleanup() {
        /*if (ChanBuild.DEVELOPER_MODE) {
            Pin pin = ChanApplication.getWatchManager().findPinByLoadable(showingThread.loadable);
            if (pin == null) {
                for (Post post : showingThread.posts) {
                    if (post.comment instanceof SpannedString) {
                        SpannedString commentSpannable = (SpannedString) post.comment;
                        PostLinkable[] linkables = commentSpannable.getSpans(0, commentSpannable.length(), PostLinkable.class);
                        for (PostLinkable linkable : linkables) {
                            ChanApplication.getRefWatcher().watch(linkable, linkable.key + " " + linkable.value);
                        }
                    }
                }
            }
        }*/

        postAdapter.cleanup();
        reply.cleanup();
        openReply(false);
        showSearch(false);
        showingThread = null;
    }

    public List<Post> getDisplayingPosts() {
        return postAdapter.getDisplayList();
    }

    public ThumbnailView getThumbnail(PostImage postImage) {
        RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();

        ThumbnailView thumbnail = null;
        for (int i = 0; i < layoutManager.getChildCount(); i++) {
            View view = layoutManager.getChildAt(i);
            if (view instanceof PostCellInterface) {
                PostCellInterface postView = (PostCellInterface) view;
                Post post = postView.getPost();
                if (post.hasImage && post.imageUrl.equals(postImage.imageUrl)) {
                    thumbnail = postView.getThumbnailView();
                    break;
                }
            }
        }
        return thumbnail;
    }

    public void scrollTo(int position, boolean smooth) {
        if (position < 0) {
            position = recyclerView.getAdapter().getItemCount() - 1;
        }

        int difference = Math.abs(position - getTopAdapterPosition());
        if (difference > MAX_SMOOTH_SCROLL_DISTANCE) {
            smooth = false;
        }

        if (smooth) {
            recyclerView.smoothScrollToPosition(position);
        } else {
            recyclerView.scrollToPosition(position);
        }
    }

    public void highlightPost(Post post) {
        postAdapter.highlightPost(post);
    }

    public void highlightPostId(String id) {
        postAdapter.highlightPostId(id);
    }

    public void highlightPostTripcode(String tripcode) {
        postAdapter.highlightPostTripcode(tripcode);
    }

    @Override
    public void highlightPostNo(int no) {
        postAdapter.highlightPostNo(no);
    }

    @Override
    public void showThread(Loadable loadable) {
        callback.showThread(loadable);
    }

    @Override
    public void requestNewPostLoad() {
        callback.requestNewPostLoad();
    }

    private int getTopAdapterPosition() {
        switch (postViewMode) {
            case LIST:
                return ((LinearLayoutManager) layoutManager).findFirstVisibleItemPosition();
            case CARD:
                return ((GridLayoutManager) layoutManager).findFirstVisibleItemPosition();
        }
        return -1;
    }

    public interface ThreadListLayoutCallback {
        void showThread(Loadable loadable);

        void requestNewPostLoad();
    }

    public interface ReplyLayoutStateCallback {
        void replyLayoutOpen(boolean open);
    }
}
