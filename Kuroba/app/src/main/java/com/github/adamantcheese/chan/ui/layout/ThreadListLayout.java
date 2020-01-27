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
package com.github.adamantcheese.chan.ui.layout;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.manager.WatchManager;
import com.github.adamantcheese.chan.core.model.ChanThread;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.model.orm.Pin;
import com.github.adamantcheese.chan.core.presenter.ReplyPresenter;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.http.Reply;
import com.github.adamantcheese.chan.core.site.sites.chan4.Chan4;
import com.github.adamantcheese.chan.ui.adapter.PostAdapter;
import com.github.adamantcheese.chan.ui.adapter.PostsFilter;
import com.github.adamantcheese.chan.ui.cell.PostCell;
import com.github.adamantcheese.chan.ui.cell.PostCellInterface;
import com.github.adamantcheese.chan.ui.cell.PostStubCell;
import com.github.adamantcheese.chan.ui.cell.ThreadStatusCell;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.ui.toolbar.Toolbar;
import com.github.adamantcheese.chan.ui.view.FastScroller;
import com.github.adamantcheese.chan.ui.view.FastScrollerHelper;
import com.github.adamantcheese.chan.ui.view.ThumbnailView;
import com.github.adamantcheese.chan.utils.Logger;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static com.github.adamantcheese.chan.Chan.instance;
import static com.github.adamantcheese.chan.ui.adapter.PostAdapter.TYPE_POST;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getDimen;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getQuantityString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.hideKeyboard;
import static com.github.adamantcheese.chan.utils.AndroidUtils.waitForLayout;

/**
 * A layout that wraps around a {@link RecyclerView} and a {@link ReplyLayout} to manage showing and replying to posts.
 */
public class ThreadListLayout
        extends FrameLayout
        implements ReplyLayout.ReplyLayoutCallback {
    private static final String TAG = "ThreadListLayout";
    public static final int MAX_SMOOTH_SCROLL_DISTANCE = 20;

    private ReplyLayout reply;
    private TextView searchStatus;
    private RecyclerView recyclerView;
    private RecyclerView.LayoutManager layoutManager;
    private FastScroller fastScroller;
    private PostAdapter postAdapter;
    private ChanThread showingThread;
    private ThreadListLayoutPresenterCallback callback;
    private ThreadListLayoutCallback threadListLayoutCallback;
    private boolean replyOpen;
    private ChanSettings.PostViewMode postViewMode;
    private int spanCount = 2;
    private int background;
    private boolean searchOpen;
    private int lastPostCount;

    private Handler mainHandler = new Handler(Looper.getMainLooper());

    private RecyclerView.OnScrollListener scrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            onRecyclerViewScrolled();
        }
    };

    public ThreadListLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        // View binding
        reply = findViewById(R.id.reply);
        searchStatus = findViewById(R.id.search_status);
        recyclerView = findViewById(R.id.recycler_view);

        if (ChanSettings.moveInputToBottom.get()) {
            LayoutParams params = (LayoutParams) reply.getLayoutParams();
            params.gravity = Gravity.BOTTOM;
            reply.setLayoutParams(params);
        }

        // View setup
        reply.setCallback(this);
        searchStatus.setTypeface(ThemeHelper.getTheme().mainFont);
    }

    public void setCallbacks(
            PostAdapter.PostAdapterCallback postAdapterCallback,
            PostCell.PostCellCallback postCellCallback,
            ThreadStatusCell.Callback statusCellCallback,
            ThreadListLayoutPresenterCallback callback,
            ThreadListLayoutCallback threadListLayoutCallback
    ) {
        this.callback = callback;
        this.threadListLayoutCallback = threadListLayoutCallback;

        postAdapter = new PostAdapter(recyclerView, postAdapterCallback, postCellCallback, statusCellCallback);
        recyclerView.setAdapter(postAdapter);
        if (ChanSettings.shiftPostFormat.get()) {
            recyclerView.getRecycledViewPool().setMaxRecycledViews(TYPE_POST, 0);
        }
        recyclerView.addOnScrollListener(scrollListener);

        setFastScroll(false);

        attachToolbarScroll(true);

        if (ChanSettings.moveInputToBottom.get()) {
            reply.setPadding(0, 0, 0, 0);
        } else {
            reply.setPadding(0, toolbarHeight(), 0, 0);
        }
        searchStatus.setPadding(searchStatus.getPaddingLeft(),
                searchStatus.getPaddingTop() + toolbarHeight(),
                searchStatus.getPaddingRight(),
                searchStatus.getPaddingBottom()
        );
    }

    private void onRecyclerViewScrolled() {
        // onScrolled can be called after cleanup()
        if (showingThread != null) {
            int[] indexTop = getIndexAndTop();

            showingThread.getLoadable().setListViewIndex(indexTop[0]);
            showingThread.getLoadable().setListViewTop(indexTop[1]);

            int last = getCompleteBottomAdapterPosition();
            if (last == postAdapter.getItemCount() - 1 && last > lastPostCount) {
                lastPostCount = last;

                // As requested by the RecyclerView, make sure that the adapter isn't changed
                // while in a layout pass. Postpone to the next frame.
                mainHandler.post(() -> ThreadListLayout.this.callback.onListScrolledToBottom());
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int cardWidth = getDimen(R.dimen.grid_card_width);
        int gridCountSetting = ChanSettings.boardGridSpanCount.get();
        boolean compactMode;
        if (gridCountSetting > 0) {
            spanCount = gridCountSetting;
            compactMode = (getMeasuredWidth() / spanCount) < dp(120);
        } else {
            spanCount = Math.max(1, Math.round((float) getMeasuredWidth() / cardWidth));
            compactMode = false;
        }

        if (postViewMode == ChanSettings.PostViewMode.CARD) {
            postAdapter.setCompact(compactMode);

            ((GridLayoutManager) layoutManager).setSpanCount(spanCount);
        }
    }

    public void setPostViewMode(ChanSettings.PostViewMode postViewMode) {
        if (this.postViewMode != postViewMode) {
            this.postViewMode = postViewMode;

            layoutManager = null;

            switch (postViewMode) {
                case LIST:
                    LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext()) {
                        @Override
                        public boolean requestChildRectangleOnScreen(
                                RecyclerView parent,
                                View child,
                                Rect rect,
                                boolean immediate,
                                boolean focusedChildVisible
                        ) {
                            return false;
                        }
                    };
                    setRecyclerViewPadding();
                    recyclerView.setLayoutManager(linearLayoutManager);
                    layoutManager = linearLayoutManager;

                    if (background != R.attr.backcolor) {
                        background = R.attr.backcolor;
                        setBackgroundColor(getAttrColor(getContext(), R.attr.backcolor));
                    }

                    break;
                case CARD:
                    GridLayoutManager gridLayoutManager =
                            new GridLayoutManager(null, spanCount, GridLayoutManager.VERTICAL, false) {
                                @Override
                                public boolean requestChildRectangleOnScreen(
                                        RecyclerView parent,
                                        View child,
                                        Rect rect,
                                        boolean immediate,
                                        boolean focusedChildVisible
                                ) {
                                    return false;
                                }
                            };
                    setRecyclerViewPadding();
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

    public void showPosts(
            ChanThread thread, PostsFilter filter, boolean initial, boolean refreshAfterHideOrRemovePosts
    ) {
        showingThread = thread;
        if (initial) {
            reply.bindLoadable(showingThread.getLoadable());

            recyclerView.setLayoutManager(null);
            recyclerView.setLayoutManager(layoutManager);
            recyclerView.getRecycledViewPool().clear();

            int index = thread.getLoadable().listViewIndex;
            int top = thread.getLoadable().listViewTop;

            switch (postViewMode) {
                case LIST:
                    ((LinearLayoutManager) layoutManager).scrollToPositionWithOffset(index, top);
                    break;
                case CARD:
                    ((GridLayoutManager) layoutManager).scrollToPositionWithOffset(index, top);
                    break;
            }

            party();
        }

        setFastScroll(true);

        /*
         * We call a blocking function that accesses the database from a background thread but doesn't
         * throw an exception here. Why, you would ask? Because we can't use callbacks here, otherwise
         * everything in ThreadPresenter.onChanLoaderData() below showPosts will be executed BEFORE
         * filtered posts are shown in the RecyclerView. This will break scrolling to the last seen
         * post as well as introduce some visual posts jiggling. This can be fixed by executing everything
         * in ThreadPresenter.onChanLoaderData() below showPosts in a callback that is called after
         * posts are assigned to the adapter. But that's a lot of code and it may break something else.
         *
         * This solution works but it will hang the main thread for some time (it shouldn't be for very
         * long since we have like 300-500 posts in a thread to filter in the database).
         * BUT if for some reason it starts to cause ANRs then we will have to apply the callback solution.
         */
        List<Post> filteredPosts =
                filter.apply(thread.getPosts(), thread.getLoadable().siteId, thread.getLoadable().board.code);

        //Filter out any bookmarked threads from the catalog
        if (ChanSettings.removeWatchedFromCatalog.get() && thread.getLoadable().isCatalogMode()) {
            List<Post> toRemove = new ArrayList<>();
            for (Pin pin : instance(WatchManager.class).getAllPins()) {
                for (Post post : filteredPosts) {
                    if (pin.loadable.equals(Loadable.forThread(thread.getLoadable().site,
                            thread.getLoadable().board,
                            post.no,
                            ""
                    ))) {
                        toRemove.add(post);
                    }
                }
            }
            filteredPosts.removeAll(toRemove);
        }

        postAdapter.setThread(thread.getLoadable(), filteredPosts, refreshAfterHideOrRemovePosts);
    }

    public boolean onBack() {
        if (reply.onBack()) {
            return true;
        } else if (replyOpen) {
            openReply(false);
            return true;
        } else {
            return threadListLayoutCallback.threadBackPressed();
        }
    }

    public boolean sendKeyEvent(KeyEvent event) {
        if (ChanSettings.volumeKeysScrolling.get()) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_VOLUME_UP:
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        boolean down = event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN;
                        int scroll = (int) (getHeight() * 0.75);
                        recyclerView.smoothScrollBy(0, down ? scroll : -scroll);
                    }
                    return true;
            }
        }
        return false;
    }

    public void gainedFocus() {
        showToolbarIfNeeded();
    }

    public void openReply(boolean open) {
        if (showingThread != null && replyOpen != open) {
            this.replyOpen = open;

            reply.measure(MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            );
            int height = reply.getMeasuredHeight();

            final ViewPropertyAnimator viewPropertyAnimator = reply.animate();
            viewPropertyAnimator.setListener(null);
            viewPropertyAnimator.setInterpolator(new DecelerateInterpolator(2f));
            viewPropertyAnimator.setDuration(600);

            if (open) {
                reply.setVisibility(VISIBLE);
                reply.setTranslationY(ChanSettings.moveInputToBottom.get() ? height : -height);
                viewPropertyAnimator.translationY(0f);
            } else {
                reply.setTranslationY(0f);
                viewPropertyAnimator.translationY(ChanSettings.moveInputToBottom.get() ? height : -height);
                viewPropertyAnimator.setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        viewPropertyAnimator.setListener(null);
                        reply.setVisibility(GONE);
                    }
                });
            }

            reply.onOpen(open);
            setRecyclerViewPadding();
            if (!open) {
                hideKeyboard(reply);
            }
            threadListLayoutCallback.replyLayoutOpen(open);

            attachToolbarScroll(!(open || searchOpen));
        }
    }

    public ReplyPresenter getReplyPresenter() {
        return reply.getPresenter();
    }

    public void showError(String error) {
        postAdapter.showError(error);
    }

    public void openSearch(boolean open) {
        if (showingThread != null && searchOpen != open) {
            searchOpen = open;

            searchStatus.measure(MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            );
            int height = searchStatus.getMeasuredHeight();

            final ViewPropertyAnimator viewPropertyAnimator = searchStatus.animate();
            viewPropertyAnimator.setListener(null);
            viewPropertyAnimator.setInterpolator(new DecelerateInterpolator(2f));
            viewPropertyAnimator.setDuration(600);

            if (open) {
                searchStatus.setVisibility(VISIBLE);
                searchStatus.setTranslationY(-height);
                viewPropertyAnimator.translationY(0f);
            } else {
                searchStatus.setTranslationY(0f);
                viewPropertyAnimator.translationY(-height);
                viewPropertyAnimator.setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        viewPropertyAnimator.setListener(null);
                        searchStatus.setVisibility(GONE);
                    }
                });
            }

            setRecyclerViewPadding();
            if (open) {
                searchStatus.setText(R.string.search_empty);
            } else {
                threadListLayoutCallback.getToolbar().closeSearchPhoneMode();
            }

            attachToolbarScroll(!(open || replyOpen));
        }
    }

    @SuppressLint("StringFormatMatches")
    //android studio doesn't like the nested getQuantityString and messes up, but nothing is wrong
    public void setSearchStatus(String query, boolean setEmptyText, boolean hideKeyboard) {
        if (hideKeyboard) {
            hideKeyboard(this);
        }

        if (setEmptyText) {
            searchStatus.setText(R.string.search_empty);
        }

        if (query != null) {
            int size = getDisplayingPosts().size();
            searchStatus.setText(getString(R.string.search_results,
                    getQuantityString(R.plurals.posts, size, size),
                    query
            ));
        }
    }

    public boolean canChildScrollUp() {
        if (replyOpen || searchOpen) {
            return true;
        }

        switch (postViewMode) {
            case LIST:
                if (getTopAdapterPosition() == 0) {
                    View top = layoutManager.findViewByPosition(0);
                    return top.getTop() != toolbarHeight();
                }
                break;
            case CARD:
                if (getTopAdapterPosition() == 0) {
                    View top = layoutManager.findViewByPosition(0);

                    if (top instanceof PostStubCell) {
                        // PostStubCell does not have grid_card_margin
                        return top.getTop() != toolbarHeight() + dp(1);
                    } else {
                        return top.getTop() != getDimen(R.dimen.grid_card_margin) + dp(1) + toolbarHeight();
                    }
                }
                break;
        }
        return true;
    }

    public boolean scrolledToBottom() {
        return getCompleteBottomAdapterPosition() == postAdapter.getItemCount() - 1;
    }

    public void smoothScrollNewPosts(int displayPosition) {
        if (layoutManager instanceof LinearLayoutManager) {
            ((LinearLayoutManager) layoutManager).scrollToPositionWithOffset(
                    displayPosition + 1,
                    //position + 1 for last seen view, dp(4) for it's height
                    recyclerView.getHeight() - recyclerView.getPaddingTop() - dp(4)
            );
        } else {
            Logger.wtf(TAG, "Layout manager is grid inside thread??");
        }
    }

    public void cleanup() {
        postAdapter.cleanup();
        reply.cleanup();
        openReply(false);
        openSearch(false);
        showingThread = null;
        lastPostCount = 0;
        noParty();
    }

    public List<Post> getDisplayingPosts() {
        return postAdapter.getDisplayList();
    }

    public ThumbnailView getThumbnail(PostImage postImage) {
        RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();

        for (int i = 0; i < layoutManager.getChildCount(); i++) {
            View view = layoutManager.getChildAt(i);
            if (view instanceof PostCellInterface) {
                PostCellInterface postView = (PostCellInterface) view;
                Post post = postView.getPost();

                if (!post.images.isEmpty()) {
                    for (PostImage image : post.images) {
                        if (image.equalUrl(postImage)) {
                            return postView.getThumbnailView(postImage);
                        }
                    }
                }
            }
        }
        return null;
    }

    public void scrollTo(int displayPosition, boolean smooth) {
        if (displayPosition < 0) {
            int bottom = postAdapter.getItemCount() - 1;
            int difference = Math.abs(bottom - getTopAdapterPosition());
            if (difference > MAX_SMOOTH_SCROLL_DISTANCE) {
                smooth = false;
            }

            if (smooth) {
                recyclerView.smoothScrollToPosition(bottom);
            } else {
                recyclerView.scrollToPosition(bottom);
                // No animation means no animation, wait for the layout to finish and skip all animations
                final RecyclerView.ItemAnimator itemAnimator = recyclerView.getItemAnimator();
                waitForLayout(recyclerView, view -> {
                    itemAnimator.endAnimations();
                    return true;
                });
            }
        } else {
            int scrollPosition = postAdapter.getScrollPosition(displayPosition);

            int difference = Math.abs(scrollPosition - getTopAdapterPosition());
            if (difference > MAX_SMOOTH_SCROLL_DISTANCE) {
                smooth = false;
            }

            if (smooth) {
                recyclerView.smoothScrollToPosition(scrollPosition);
            } else {
                recyclerView.scrollToPosition(scrollPosition);
                // No animation means no animation, wait for the layout to finish and skip all animations
                final RecyclerView.ItemAnimator itemAnimator = recyclerView.getItemAnimator();
                waitForLayout(recyclerView, view -> {
                    itemAnimator.endAnimations();
                    return true;
                });
            }
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

    public void selectPost(int post) {
        postAdapter.selectPost(post);
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

    @Override
    public ChanThread getThread() {
        return showingThread;
    }

    @Override
    public void showImageReencodingWindow(boolean supportsReencode) {
        threadListLayoutCallback.showImageReencodingWindow(supportsReencode);
    }

    public int[] getIndexAndTop() {
        int index = 0;
        int top = 0;
        if (recyclerView.getLayoutManager().getChildCount() > 0) {
            View topChild = recyclerView.getLayoutManager().getChildAt(0);

            index = ((RecyclerView.LayoutParams) topChild.getLayoutParams()).getViewLayoutPosition();

            RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) topChild.getLayoutParams();
            top = layoutManager.getDecoratedTop(topChild) - params.topMargin - recyclerView.getPaddingTop();
        }

        return new int[]{index, top};
    }

    private void attachToolbarScroll(boolean attach) {
        if (threadListLayoutCallback.shouldToolbarCollapse()) {
            Toolbar toolbar = threadListLayoutCallback.getToolbar();
            if (attach) {
                toolbar.attachRecyclerViewScrollStateListener(recyclerView);
            } else {
                toolbar.detachRecyclerViewScrollStateListener(recyclerView);
                toolbar.collapseShow(true);
            }
        }
    }

    private void showToolbarIfNeeded() {
        if (threadListLayoutCallback.shouldToolbarCollapse()) {
            // Of coming back to focus from a dual controller, like the threadlistcontroller,
            // check if we should show the toolbar again (after the other controller made it hide).
            // It should show if the search or reply is open, or if the thread was scrolled at the
            // top showing an empty space.

            Toolbar toolbar = threadListLayoutCallback.getToolbar();
            if (searchOpen || replyOpen) {
                // force toolbar to show
                toolbar.collapseShow(true);
            } else {
                // check if it should show if it was scrolled at the top
                toolbar.checkToolbarCollapseState(recyclerView);
            }
        }
    }

    private void setFastScroll(boolean enabled) {
        if (!enabled) {
            if (fastScroller != null) {
                recyclerView.removeItemDecoration(fastScroller);
                fastScroller = null;
            }
        } else {
            if (fastScroller == null) {
                fastScroller = FastScrollerHelper.create(recyclerView);
            }
        }
        recyclerView.setVerticalScrollBarEnabled(!enabled);
    }

    private void setRecyclerViewPadding() {
        int defaultPadding = 0;
        if (postViewMode == ChanSettings.PostViewMode.CARD) {
            defaultPadding = dp(1);
        }

        int left = defaultPadding;
        int top = defaultPadding;
        int right = defaultPadding;
        int bottom = defaultPadding;

        if (replyOpen) {
            reply.measure(MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            );
            if (ChanSettings.moveInputToBottom.get()) {
                bottom += reply.getMeasuredHeight();
                top += toolbarHeight();
            } else {
                top += reply.getMeasuredHeight();
            }
        } else if (searchOpen) {
            searchStatus.measure(MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            );
            top += searchStatus.getMeasuredHeight();
        } else {
            top += toolbarHeight();
        }

        recyclerView.setPadding(left, top, right, bottom);
    }

    @Override
    public void updatePadding() {
        setRecyclerViewPadding();
    }

    public int toolbarHeight() {
        Toolbar toolbar = threadListLayoutCallback.getToolbar();
        return toolbar.getToolbarHeight();
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

    private int getCompleteBottomAdapterPosition() {
        switch (postViewMode) {
            case LIST:
                return ((LinearLayoutManager) layoutManager).findLastCompletelyVisibleItemPosition();
            case CARD:
                return ((GridLayoutManager) layoutManager).findLastCompletelyVisibleItemPosition();
        }
        return -1;
    }

    private Bitmap hat;

    private final RecyclerView.ItemDecoration PARTY = new RecyclerView.ItemDecoration() {
        @Override
        public void onDrawOver(Canvas c, RecyclerView parent, RecyclerView.State state) {
            if (hat == null) {
                hat = BitmapFactory.decodeResource(getResources(), R.drawable.partyhat);
            }

            for (int i = 0, j = parent.getChildCount(); i < j; i++) {
                View child = parent.getChildAt(i);
                if (child instanceof PostCellInterface) {
                    PostCellInterface postView = (PostCellInterface) child;
                    Post post = postView.getPost();
                    if (post.isOP && !post.images.isEmpty()) {
                        RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) child.getLayoutParams();
                        int top = child.getTop() + params.topMargin;
                        int left = child.getLeft() + params.leftMargin;
                        c.drawBitmap(hat,
                                left - parent.getPaddingLeft() - dp(25),
                                top - dp(80) - parent.getPaddingTop() + toolbarHeight(),
                                null
                        );
                    }
                }
            }
        }
    };

    private void party() {
        if (showingThread.getLoadable().site instanceof Chan4) {
            Calendar calendar = Calendar.getInstance();
            if (calendar.get(Calendar.MONTH) == Calendar.OCTOBER && calendar.get(Calendar.DAY_OF_MONTH) == 1) {
                recyclerView.addItemDecoration(PARTY);
            }
        }
    }

    private void noParty() {
        recyclerView.removeItemDecoration(PARTY);
    }

    public void onImageOptionsApplied(Reply modifiedReply, boolean filenameRemoved) {
        reply.onImageOptionsApplied(modifiedReply, filenameRemoved);
    }

    public interface ThreadListLayoutPresenterCallback {
        void showThread(Loadable loadable);

        void requestNewPostLoad();

        void onListScrolledToBottom();
    }

    public interface ThreadListLayoutCallback {
        void replyLayoutOpen(boolean open);

        Toolbar getToolbar();

        boolean shouldToolbarCollapse();

        void showImageReencodingWindow(boolean supportsReencode);

        boolean threadBackPressed();
    }
}
