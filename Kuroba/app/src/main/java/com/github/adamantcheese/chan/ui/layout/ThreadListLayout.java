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
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.ChanThread;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.presenter.ReplyPresenter;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.core.repository.BitmapRepository.ResourceBitmap;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.settings.ChanSettings.PostViewMode;
import com.github.adamantcheese.chan.core.site.archives.ExternalSiteArchive;
import com.github.adamantcheese.chan.core.site.sites.chan4.Chan4;
import com.github.adamantcheese.chan.ui.adapter.PostAdapter;
import com.github.adamantcheese.chan.ui.adapter.PostsFilter;
import com.github.adamantcheese.chan.ui.cell.PostCell;
import com.github.adamantcheese.chan.ui.cell.PostCellInterface;
import com.github.adamantcheese.chan.ui.cell.ThreadStatusCell;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.ui.toolbar.Toolbar;
import com.github.adamantcheese.chan.ui.view.FastScroller;
import com.github.adamantcheese.chan.ui.view.FastScrollerHelper;
import com.github.adamantcheese.chan.ui.view.ThumbnailView;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.chan.utils.RecyclerUtils;
import com.github.adamantcheese.chan.utils.RecyclerUtils.RecyclerViewPosition;

import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import static com.github.adamantcheese.chan.core.settings.ChanSettings.PostViewMode.GRID;
import static com.github.adamantcheese.chan.core.settings.ChanSettings.PostViewMode.LIST;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getDimen;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getQuantityString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.hideKeyboard;
import static com.github.adamantcheese.chan.utils.AndroidUtils.isTablet;
import static com.github.adamantcheese.chan.utils.AndroidUtils.updatePaddings;

/**
 * A layout that wraps around a {@link RecyclerView} and a {@link ReplyLayout} to manage showing and replying to posts.
 */
public class ThreadListLayout
        extends FrameLayout
        implements ReplyLayout.ReplyLayoutCallback, SwipeRefreshLayout.OnRefreshListener {
    public static final int MAX_SMOOTH_SCROLL_DISTANCE = 20;

    private ReplyLayout reply;
    private TextView searchStatus;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerView;
    private FastScroller fastScroller;
    private PostAdapter postAdapter;
    private ChanThread showingThread;
    private ThreadListLayoutPresenterCallback callback;
    private ThreadListLayoutCallback threadListLayoutCallback;
    private boolean replyOpen;
    private PostViewMode postViewMode;
    private int spanCount = 2;
    private boolean searchOpen;

    private final RecyclerView.OnScrollListener scrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            if (showingThread != null) {
                RecyclerViewPosition indexTop = RecyclerUtils.getIndexAndTop(recyclerView);

                showingThread.getLoadable().listViewIndex = indexTop.index;
                showingThread.getLoadable().listViewTop = indexTop.top;

                if (!recyclerView.canScrollVertically(1)) {
                    // As requested by the RecyclerView, make sure that the adapter isn't changed
                    // while in a layout pass. Postpone to the next frame.
                    recyclerView.post(() -> ThreadListLayout.this.callback.onListScrolledToBottom());
                }

                if (!(showingThread.getLoadable().site instanceof ExternalSiteArchive)) {
                    callback.updateDatabaseLoadable();
                }
            }
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
        swipeRefresh = findViewById(R.id.swipe_refresh);
        recyclerView = findViewById(R.id.recycler_view);

        // allows the recycler view to have intertia, but when the drawer is attempted to be swiped open
        // the recycler scroll stops and passes the event up to the drawer to allow drag-open
        swipeRefresh.setLegacyRequestDisallowInterceptTouchEventEnabled(true);
        recyclerView.setNestedScrollingEnabled(false);

        swipeRefresh.setOnRefreshListener(this);

        if (!isInEditMode() && ChanSettings.moveInputToBottom.get()) {
            LayoutParams params = (LayoutParams) reply.getLayoutParams();
            params.gravity = Gravity.BOTTOM;
            reply.setLayoutParams(params);
        }

        // View setup
        reply.setCallback(this);
        if (!isInEditMode()) {
            searchStatus.setTypeface(ThemeHelper.getTheme().mainFont);
        }
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

        postAdapter = new PostAdapter(recyclerView,
                postAdapterCallback,
                postCellCallback,
                statusCellCallback,
                ThemeHelper.getTheme()
        );
        recyclerView.setAdapter(postAdapter);
        recyclerView.addOnScrollListener(scrollListener);

        setFastScroll(false);

        attachToolbarScroll(true);

        updatePaddings(reply, 0, 0, ChanSettings.moveInputToBottom.get() ? 0 : toolbarHeight(), 0);
        updatePaddings(searchStatus, -1, -1, searchStatus.getPaddingTop() + toolbarHeight(), -1);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (postViewMode != GRID || recyclerView.getLayoutManager() == null) return;

        int gridCountSetting = isInEditMode() ? 3 : ChanSettings.getBoardColumnCount();
        boolean compactMode;
        if (gridCountSetting > 0) {
            // Set count
            spanCount = gridCountSetting;
            compactMode = (getMeasuredWidth() / spanCount) < dp(getContext(), 120);
        } else {
            // Auto
            spanCount = Math.max(1,
                    Math.round((float) getMeasuredWidth() / getDimen(getContext(), R.dimen.grid_card_width))
            );
            compactMode = false;
        }

        postAdapter.setCompact(compactMode);
        ((GridLayoutManager) recyclerView.getLayoutManager()).setSpanCount(spanCount);
    }

    public void setPostViewMode(PostViewMode postViewMode) {
        if (this.postViewMode != postViewMode) {
            this.postViewMode = postViewMode;
            recyclerView.setAdapter(null);
            postAdapter.setPostViewMode(postViewMode);

            RecyclerView.LayoutManager layoutManager = null;
            switch (postViewMode) {
                case LIST:
                    layoutManager = new LinearLayoutManager(getContext()) {
                        @Override
                        public boolean requestChildRectangleOnScreen(
                                @NonNull RecyclerView parent,
                                @NonNull View child,
                                @NonNull Rect rect,
                                boolean immediate,
                                boolean focusedChildVisible
                        ) {
                            return false;
                        }
                    };
                    setBackgroundColor(getAttrColor(getContext(), R.attr.backcolor));
                    break;
                case GRID:
                    layoutManager = new GridLayoutManager(null, spanCount, GridLayoutManager.VERTICAL, false) {
                        @Override
                        public boolean requestChildRectangleOnScreen(
                                @NonNull RecyclerView parent,
                                @NonNull View child,
                                @NonNull Rect rect,
                                boolean immediate,
                                boolean focusedChildVisible
                        ) {
                            return false;
                        }
                    };
                    setBackgroundColor(getAttrColor(getContext(), R.attr.backcolor_secondary));
                    break;
            }
            setRecyclerViewPadding();
            recyclerView.setLayoutManager(layoutManager);

            // in order for dividers to appear correctly, we have to clear out the adapter and set it again
            // see PostAdapter's onattachedtorecyclerview method
            postAdapter.setPostViewMode(postViewMode);
            recyclerView.setAdapter(null);
            recyclerView.setAdapter(postAdapter);
        }
    }

    public void showPosts(ChanThread thread, PostsFilter filter, boolean initial) {
        showingThread = thread;
        if (initial) {
            reply.getPresenter().bindLoadable(thread.getLoadable());
            RecyclerView.LayoutManager prevManager = recyclerView.getLayoutManager();
            recyclerView.setLayoutManager(null);
            recyclerView.setLayoutManager(prevManager);

            int index = thread.getLoadable().listViewIndex;
            int top = thread.getLoadable().listViewTop;

            switch (postViewMode) {
                case LIST:
                    ((LinearLayoutManager) recyclerView.getLayoutManager()).scrollToPositionWithOffset(index, top);
                    break;
                case GRID:
                    ((GridLayoutManager) recyclerView.getLayoutManager()).scrollToPositionWithOffset(index, top);
                    break;
            }

            party();
            santa();
        }

        setFastScroll(true);

        showError(null);
        postAdapter.setThread(thread, filter);
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
        if (replyOpen) {
            reply.focusComment();
        }
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
        if (postAdapter.showStatusView()) {
            postAdapter.notifyItemChanged(postAdapter.getItemCount() - 1, error);
        }
    }

    public void openSearch(boolean open) {
        if (searchOpen != open) {
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
                threadListLayoutCallback.getToolbar().closeSearch();
            }

            attachToolbarScroll(!(open || replyOpen));
        }
    }

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

        setRecyclerViewPadding();
    }

    public void smoothScrollNewPosts(int displayPosition) {
        if (recyclerView.getLayoutManager() instanceof LinearLayoutManager) {
            ((LinearLayoutManager) recyclerView.getLayoutManager()).scrollToPositionWithOffset(
                    //position + 1 to fully render the post after last viewed post (actually scrolls to top of post after post after last viewed)
                    displayPosition + 1,
                    recyclerView.getHeight() - recyclerView.getPaddingTop()
            );
        } else {
            Logger.wtf(this, "Layout manager is grid inside thread??");
        }
    }

    public void cleanup() {
        postAdapter.cleanup();
        reply.cleanup();
        openReply(false);
        if (showingThread.getLoadable().isThreadMode()) {
            openSearch(false);
        }
        showingThread = null;
        recyclerView.removeItemDecoration(PARTY);
        recyclerView.removeItemDecoration(SANTA);
    }

    public List<Post> getDisplayingPosts() {
        return postAdapter.getDisplayList();
    }

    public PostViewMode getPostViewMode() {
        return postViewMode;
    }

    public ThumbnailView getThumbnail(PostImage postImage) {
        RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
        if (layoutManager == null) return null;
        for (int i = 0; i < layoutManager.getChildCount(); i++) {
            View view = layoutManager.getChildAt(i);
            if (view instanceof PostCellInterface) {
                PostCellInterface postView = (PostCellInterface) view;
                Post post = postView.getPost();

                for (PostImage image : post.images) {
                    if (image.equals(postImage)) {
                        return postView.getThumbnailView(postImage);
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
                if (recyclerView.getItemAnimator() != null) {
                    recyclerView.post(recyclerView.getItemAnimator()::endAnimations);
                }
            }
        } else {
            int difference = Math.abs(displayPosition - getTopAdapterPosition());
            if (difference > MAX_SMOOTH_SCROLL_DISTANCE) {
                smooth = false;
            }

            if (smooth) {
                recyclerView.smoothScrollToPosition(displayPosition);
            } else {
                recyclerView.scrollToPosition(displayPosition);
                // No animation means no animation, wait for the layout to finish and skip all animations
                if (recyclerView.getItemAnimator() != null) {
                    recyclerView.post(recyclerView.getItemAnimator()::endAnimations);
                }
            }
        }
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

    @Override
    public ChanThread getThread() {
        return showingThread;
    }

    @Override
    public void showImageReencodingWindow() {
        threadListLayoutCallback.showImageReencodingWindow();
    }

    public RecyclerViewPosition getIndexAndTop() {
        return RecyclerUtils.getIndexAndTop(recyclerView);
    }

    private boolean shouldToolbarCollapse() {
        return !isTablet() && !ChanSettings.neverHideToolbar.get();
    }

    private void attachToolbarScroll(boolean attach) {
        if (shouldToolbarCollapse()) {
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
        if (shouldToolbarCollapse()) {
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
        int defaultPadding = postViewMode == GRID ? dp(1) : 0;
        int recyclerTop = defaultPadding + toolbarHeight();
        int recyclerBottom = defaultPadding;
        //reply view padding calculations (before measure)
        if (ChanSettings.moveInputToBottom.get()) {
            reply.setPadding(0, 0, 0, 0);
        } else {
            if (!replyOpen && searchOpen) {
                reply.setPadding(0, searchStatus.getMeasuredHeight(), 0, 0); // (2)
            } else {
                reply.setPadding(0, toolbarHeight(), 0, 0); // (1)
            }
        }

        //measurements
        if (replyOpen) {
            reply.measure(MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            );
        }
        if (searchOpen) {
            searchStatus.measure(MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            );
        }

        //recycler view padding calculations
        if (replyOpen) {
            if (ChanSettings.moveInputToBottom.get()) {
                recyclerBottom += reply.getMeasuredHeight();
            } else {
                recyclerTop += reply.getMeasuredHeight();
                recyclerTop -= toolbarHeight(); // reply has built-in padding for the toolbar height when input at top
            }
        }
        if (searchOpen) {
            recyclerTop += searchStatus.getMeasuredHeight(); //search status has built-in padding for the toolbar height
            recyclerTop -= toolbarHeight();
        }
        recyclerView.setPadding(defaultPadding, recyclerTop, defaultPadding, recyclerBottom);

        swipeRefresh.setProgressViewOffset(
                false,
                // hide the refresh
                recyclerTop - swipeRefresh.getProgressCircleDiameter(),
                // 40 pixels away from the top of all the stuff that could add to the padding
                recyclerTop + dp(40)
        );

        //reply view padding calculations (after measure)
        if (ChanSettings.moveInputToBottom.get()) {
            reply.setPadding(0, 0, 0, 0);
        } else {
            if (replyOpen && searchOpen) {
                reply.setPadding(0, searchStatus.getMeasuredHeight(), 0, 0); // (2)
            } else {
                reply.setPadding(0, toolbarHeight(), 0, 0); // (1)
            }
        }

        recyclerView.invalidateItemDecorations();
    }

    @Override
    public void updatePadding() {
        setRecyclerViewPadding();
    }

    @Override
    public boolean isViewingCatalog() {
        return threadListLayoutCallback.isViewingCatalog();
    }

    public int toolbarHeight() {
        return threadListLayoutCallback.getToolbar().getToolbarHeight();
    }

    private int getTopAdapterPosition() {
        switch (postViewMode) {
            case LIST:
                return ((LinearLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition();
            case GRID:
                return ((GridLayoutManager) recyclerView.getLayoutManager()).findFirstVisibleItemPosition();
        }
        return -1;
    }

    private final RecyclerView.ItemDecoration PARTY = new HatItemDecoration(BitmapRepository.partyHat);
    private final RecyclerView.ItemDecoration SANTA = new HatItemDecoration(BitmapRepository.xmasHat);

    private void party() {
        if (showingThread.getLoadable().site instanceof Chan4) {
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"));
            if (calendar.get(Calendar.MONTH) == Calendar.OCTOBER && calendar.get(Calendar.DAY_OF_MONTH) == 1) {
                recyclerView.addItemDecoration(PARTY);
            }
        }
    }

    private void santa() {
        if (showingThread.getLoadable().site instanceof Chan4) {
            Calendar calendar = Calendar.getInstance();
            if (calendar.get(Calendar.MONTH) == Calendar.DECEMBER && calendar.get(Calendar.DAY_OF_MONTH) == 25) {
                recyclerView.addItemDecoration(SANTA);
            }
        }
    }

    @Override
    public void onRefresh() {
        callback.requestData();
    }

    public void hideSwipeRefreshLayout() {
        swipeRefresh.setRefreshing(false);
    }

    public void refreshUI() {
        recyclerView.setAdapter(null);
        recyclerView.getRecycledViewPool().clear();
        recyclerView.setAdapter(postAdapter);
    }

    /**
     * Positions a hat bitmap over the top-left corner of post cells
     */
    private class HatItemDecoration
            extends RecyclerView.ItemDecoration {
        public final ResourceBitmap bitmapWrapper;

        public HatItemDecoration(ResourceBitmap bitmapWrapper) {
            this.bitmapWrapper = bitmapWrapper;
        }

        @Override
        public void onDrawOver(@NonNull Canvas c, RecyclerView parent, @NonNull RecyclerView.State state) {
            // precalculations to speed everything up a bit
            float bitmapXCenter = bitmapWrapper.bitmap.getScaledWidth(c) * bitmapWrapper.centerX;
            float bitmapYCenter = bitmapWrapper.bitmap.getScaledHeight(c) * bitmapWrapper.centerY;
            // if in list mode, move it over slightly to align with the thumbnail
            int thumbnailAdjustment = (postViewMode == LIST ? dp(ChanSettings.fontSize.get() - 7) : 0);

            for (int i = 0, j = parent.getChildCount(); i < j; i++) {
                View child = parent.getChildAt(i);
                if (child instanceof PostCellInterface) {
                    PostCellInterface postView = (PostCellInterface) child;
                    Post post = postView.getPost();
                    if (post.isOP && !post.images.isEmpty()) {
                        MarginLayoutParams postParams = (MarginLayoutParams) child.getLayoutParams();
                        c.drawBitmap(bitmapWrapper.bitmap,
                                child.getLeft() + postParams.leftMargin - parent.getPaddingLeft() - bitmapXCenter
                                        + thumbnailAdjustment,

                                child.getTop() + postParams.topMargin - parent.getPaddingTop() - bitmapYCenter
                                        + thumbnailAdjustment
                                        // the recycler's top padding must be accounted for because of layout changes
                                        // no special cases for reply/search open since those are taken care of in setRecyclerViewPadding
                                        + parent.getPaddingTop(),
                                null
                        );
                    }
                }
            }
        }
    }

    public void onImageOptionsApplied() {
        reply.onImageOptionsApplied();
    }

    public void onImageOptionsComplete() {
        reply.onImageOptionsComplete();
    }

    public boolean isReplyLayoutOpen() {
        return replyOpen;
    }

    public interface ThreadListLayoutPresenterCallback {
        void showThread(Loadable loadable);

        void requestData();

        void requestNewPostLoad();

        void onListScrolledToBottom();

        void updateDatabaseLoadable();
    }

    public interface ThreadListLayoutCallback {
        void replyLayoutOpen(boolean open);

        Toolbar getToolbar();

        void showImageReencodingWindow();

        boolean threadBackPressed();

        boolean isViewingCatalog();
    }
}
