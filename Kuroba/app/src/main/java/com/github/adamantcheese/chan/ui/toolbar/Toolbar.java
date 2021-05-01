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
package com.github.adamantcheese.chan.ui.toolbar;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.RecyclerView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.ui.theme.ArrowMenuDrawable;
import com.github.adamantcheese.chan.ui.theme.Theme;

import java.util.ArrayList;
import java.util.List;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getDimen;
import static com.github.adamantcheese.chan.utils.AndroidUtils.hideKeyboard;

public class Toolbar
        extends LinearLayout
        implements View.OnClickListener, ToolbarPresenter.Callback, ToolbarContainer.Callback {
    public static final int TOOLBAR_COLLAPSE_HIDE = 1000000;
    public static final int TOOLBAR_COLLAPSE_SHOW = -1000000;

    private final RecyclerView.OnScrollListener recyclerViewOnScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            if (!recyclerView.canScrollVertically(-1)) {
                setCollapse(TOOLBAR_COLLAPSE_SHOW, false);
            } else {
                processScrollCollapse(dy, false);
            }
        }

        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            if (recyclerView.getLayoutManager() != null && newState == RecyclerView.SCROLL_STATE_IDLE) {
                processRecyclerViewScroll(recyclerView);
            }
        }
    };

    private ToolbarPresenter presenter;

    private ImageView arrowMenuView;
    private ArrowMenuDrawable arrowMenuDrawable;

    private ToolbarContainer navigationItemContainer;

    private ToolbarCallback callback;
    private int lastScrollDeltaOffset;
    private int scrollOffset;
    private final List<ToolbarCollapseCallback> collapseCallbacks = new ArrayList<>();

    public Toolbar(Context context) {
        this(context, null);
    }

    public Toolbar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Toolbar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        setOrientation(HORIZONTAL);
        if (isInEditMode()) return;

        presenter = new ToolbarPresenter(this);

        //initView
        FrameLayout leftButtonContainer = new FrameLayout(getContext());
        addView(leftButtonContainer, WRAP_CONTENT, MATCH_PARENT);

        arrowMenuView = new ImageView(getContext());
        arrowMenuView.setOnClickListener(this);
        arrowMenuView.setFocusable(true);
        arrowMenuView.setScaleType(ImageView.ScaleType.CENTER);
        arrowMenuDrawable = new ArrowMenuDrawable();
        arrowMenuView.setImageDrawable(arrowMenuDrawable);
        arrowMenuView.setBackgroundResource(R.drawable.ripple_item_background);

        int toolbarSize = getDimen(context, R.dimen.toolbar_height);
        FrameLayout.LayoutParams leftButtonContainerLp =
                new FrameLayout.LayoutParams(toolbarSize, MATCH_PARENT, Gravity.CENTER_VERTICAL);
        leftButtonContainer.addView(arrowMenuView, leftButtonContainerLp);

        navigationItemContainer = new ToolbarContainer(getContext());
        addView(navigationItemContainer, new LayoutParams(0, MATCH_PARENT, 1f));

        navigationItemContainer.setCallback(this);
        navigationItemContainer.setArrowMenu(arrowMenuDrawable);

        if (getElevation() == 0f) {
            setElevation(dp(4f));
        }

        setBackgroundColor(getAttrColor(context, R.attr.colorPrimary));
    }

    public void setMenuDrawable(int resID) {
        arrowMenuView.setImageResource(resID);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        return isTransitioning() || super.dispatchTouchEvent(ev);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return true;
    }

    public int getToolbarHeight() {
        return getHeight() == 0 ? getLayoutParams().height : getHeight();
    }

    public void addCollapseCallback(ToolbarCollapseCallback callback) {
        collapseCallbacks.add(callback);
    }

    public void removeCollapseCallback(ToolbarCollapseCallback callback) {
        collapseCallbacks.remove(callback);
    }

    public void processScrollCollapse(int offset, boolean animated) {
        lastScrollDeltaOffset = offset;
        setCollapse(offset, animated);
    }

    public void collapseShow(boolean animated) {
        setCollapse(Toolbar.TOOLBAR_COLLAPSE_SHOW, animated);
    }

    public void setCollapse(int offset, boolean animated) {
        scrollOffset += offset;
        scrollOffset = Math.max(0, Math.min(getHeight(), scrollOffset));

        if (animated) {
            Interpolator slowdown = new DecelerateInterpolator(2f);
            animate().translationY(-scrollOffset).setInterpolator(slowdown).start();

            boolean collapse = scrollOffset > 0;
            for (ToolbarCollapseCallback c : collapseCallbacks) {
                c.onCollapseAnimation(collapse);
            }
        } else {
            animate().cancel();
            setTranslationY(-scrollOffset);

            for (ToolbarCollapseCallback c : collapseCallbacks) {
                c.onCollapseTranslation(scrollOffset / (float) getHeight());
            }
        }
    }

    public void attachRecyclerViewScrollStateListener(RecyclerView recyclerView) {
        recyclerView.addOnScrollListener(recyclerViewOnScrollListener);
    }

    public void detachRecyclerViewScrollStateListener(RecyclerView recyclerView) {
        recyclerView.removeOnScrollListener(recyclerViewOnScrollListener);
    }

    public void checkToolbarCollapseState(RecyclerView recyclerView) {
        processRecyclerViewScroll(recyclerView);
    }

    private void processRecyclerViewScroll(RecyclerView recyclerView) {
        View positionZero = recyclerView.getLayoutManager().findViewByPosition(0);
        boolean allowHide = positionZero == null || positionZero.getTop() < 0;
        if (allowHide || lastScrollDeltaOffset <= 0) {
            setCollapse(lastScrollDeltaOffset <= 0 ? TOOLBAR_COLLAPSE_SHOW : TOOLBAR_COLLAPSE_HIDE, true);
        } else {
            setCollapse(TOOLBAR_COLLAPSE_SHOW, true);
        }
    }

    public void openSearch() {
        presenter.openSearch();
    }

    public boolean closeSearch() {
        return presenter.closeSearch();
    }

    public boolean isTransitioning() {
        return navigationItemContainer.isTransitioning();
    }

    public void setNavigationItem(
            final boolean animate, final boolean pushing, final NavigationItem item, Theme theme
    ) {
        ToolbarPresenter.AnimationStyle animationStyle;
        if (!animate) {
            animationStyle = ToolbarPresenter.AnimationStyle.NONE;
        } else if (pushing) {
            animationStyle = ToolbarPresenter.AnimationStyle.PUSH;
        } else {
            animationStyle = ToolbarPresenter.AnimationStyle.POP;
        }

        presenter.set(item, theme, animationStyle);
    }

    public void beginTransition(NavigationItem newItem) {
        presenter.startTransition(newItem);
    }

    public void transitionProgress(float progress) {
        presenter.setTransitionProgress(progress);
    }

    public void finishTransition(boolean completed) {
        presenter.stopTransition(completed);
    }

    public void setCallback(ToolbarCallback callback) {
        this.callback = callback;
    }

    @Override
    public void onClick(View v) {
        if (v == arrowMenuView) {
            callback.onMenuOrBackClicked(arrowMenuDrawable.getProgress() == 1f);
        }
    }

    public ArrowMenuDrawable getArrowMenuDrawable() {
        return arrowMenuDrawable;
    }

    public void updateTitle(NavigationItem navigationItem) {
        presenter.update(navigationItem);
    }

    @Override
    public void showForNavigationItem(NavigationItem item, Theme theme, ToolbarPresenter.AnimationStyle animation) {
        navigationItemContainer.set(item, theme, animation);
    }

    @Override
    public void containerStartTransition(NavigationItem item, ToolbarPresenter.TransitionAnimationStyle animation) {
        navigationItemContainer.startTransition(item, animation);
    }

    @Override
    public void containerStopTransition(boolean didComplete) {
        navigationItemContainer.stopTransition(didComplete);
    }

    @Override
    public void containerSetTransitionProgress(float progress) {
        navigationItemContainer.setTransitionProgress(progress);
    }

    @Override
    public void searchInput(String input) {
        presenter.searchInput(input);
    }

    @Override
    public void onNavItemSet(NavigationItem item) {
        callback.onNavItemSet(item);
    }

    @Override
    public void onSearchVisibilityChanged(NavigationItem item, boolean visible) {
        callback.onSearchVisibilityChanged(item, visible);

        if (!visible) {
            hideKeyboard(navigationItemContainer);
        }
    }

    @Override
    public void onSearchInput(NavigationItem item, String input) {
        callback.onSearchEntered(item, input);
    }

    @Override
    public void updateViewForItem(NavigationItem item) {
        navigationItemContainer.update(item);
    }

    public interface ToolbarCallback {
        void onMenuOrBackClicked(boolean isArrow);

        void onSearchVisibilityChanged(NavigationItem item, boolean visible);

        void onSearchEntered(NavigationItem item, String entered);

        void onNavItemSet(NavigationItem item);
    }

    public interface ToolbarCollapseCallback {
        void onCollapseTranslation(float offset);

        void onCollapseAnimation(boolean collapse);
    }
}
