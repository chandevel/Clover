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
package org.floens.chan.ui.toolbar;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.floens.chan.R;
import org.floens.chan.ui.drawable.ArrowMenuDrawable;
import org.floens.chan.ui.drawable.DropdownArrowDrawable;
import org.floens.chan.ui.layout.SearchLayout;
import org.floens.chan.utils.AndroidUtils;

import java.util.ArrayList;
import java.util.List;

import static org.floens.chan.utils.AndroidUtils.dp;
import static org.floens.chan.utils.AndroidUtils.getAttrColor;
import static org.floens.chan.utils.AndroidUtils.hideKeyboard;
import static org.floens.chan.utils.AndroidUtils.removeFromParentView;
import static org.floens.chan.utils.AndroidUtils.setRoundItemBackground;

public class Toolbar extends LinearLayout implements
        View.OnClickListener, ToolbarPresenter.Callback {
    public static final int TOOLBAR_COLLAPSE_HIDE = 1000000;
    public static final int TOOLBAR_COLLAPSE_SHOW = -1000000;

    private final RecyclerView.OnScrollListener recyclerViewOnScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            processScrollCollapse(dy);
        }

        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            if (recyclerView.getLayoutManager() != null &&
                    newState == RecyclerView.SCROLL_STATE_IDLE) {
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
    private List<ToolbarCollapseCallback> collapseCallbacks = new ArrayList<>();

    public Toolbar(Context context) {
        this(context, null);
    }

    public Toolbar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Toolbar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
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

    public void processScrollCollapse(int offset) {
        processScrollCollapse(offset, false);
    }

    public void processScrollCollapse(int offset, boolean animated) {
        lastScrollDeltaOffset = offset;
        setCollapse(offset, animated);
    }

    public void collapseShow(boolean animated) {
        setCollapse(Toolbar.TOOLBAR_COLLAPSE_SHOW, animated);
    }

    public void collapseHide(boolean animated) {
        setCollapse(Toolbar.TOOLBAR_COLLAPSE_HIDE, animated);
    }

    public void setCollapse(int offset, boolean animated) {
        scrollOffset += offset;
        scrollOffset = Math.max(0, Math.min(getHeight(), scrollOffset));

        if (animated) {
            animate().translationY(-scrollOffset)
                    .setDuration(300)
                    .setInterpolator(new DecelerateInterpolator(2f))
                    .start();

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

    public void setNavigationItem(final boolean animate, final boolean pushing, final NavigationItem item) {
        ToolbarPresenter.AnimationStyle animationStyle;
        if (!animate) {
            animationStyle = ToolbarPresenter.AnimationStyle.NONE;
        } else if (pushing) {
            animationStyle = ToolbarPresenter.AnimationStyle.PUSH;
        } else {
            animationStyle = ToolbarPresenter.AnimationStyle.POP;
        }

        presenter.set(item, animationStyle);
    }

    public void setArrowMenuIconShown(boolean show) {
        arrowMenuView.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    public void beginTransition(NavigationItem newItem) {
        presenter.startTransition(newItem, ToolbarPresenter.TransitionAnimationStyle.POP);
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

    public void setArrowMenuProgress(float progress) {
        arrowMenuDrawable.setProgress(progress);
    }

    public void setShowArrowMenu(boolean show) {
        arrowMenuView.setVisibility(show ? VISIBLE : GONE);
    }

    public ArrowMenuDrawable getArrowMenuDrawable() {
        return arrowMenuDrawable;
    }

    public void updateTitle(NavigationItem navigationItem) {
        presenter.update(navigationItem);
    }

    private void init() {
        setOrientation(HORIZONTAL);

        if (isInEditMode()) return;

        presenter = new ToolbarPresenter();
        presenter.create(this);

        initView();
    }

    private void initView() {
        FrameLayout leftButtonContainer = new FrameLayout(getContext());
        addView(leftButtonContainer, LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);

        arrowMenuView = new ImageView(getContext());
        arrowMenuView.setOnClickListener(this);
        arrowMenuView.setFocusable(true);
        arrowMenuView.setScaleType(ImageView.ScaleType.CENTER);
        arrowMenuDrawable = new ArrowMenuDrawable();
        arrowMenuView.setImageDrawable(arrowMenuDrawable);

        setRoundItemBackground(arrowMenuView);

        int toolbarSize = getResources().getDimensionPixelSize(R.dimen.toolbar_height);
        FrameLayout.LayoutParams leftButtonContainerLp = new FrameLayout.LayoutParams(
                toolbarSize, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER_VERTICAL);
        leftButtonContainer.addView(arrowMenuView, leftButtonContainerLp);

        navigationItemContainer = new ToolbarContainer(getContext());
        addView(navigationItemContainer, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));

        navigationItemContainer.setArrowMenu(arrowMenuDrawable);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (getElevation() == 0f) {
                setElevation(dp(4f));
            }
        }
    }

    @Override
    public void showForNavigationItem(
            NavigationItem item, ToolbarPresenter.AnimationStyle animation) {
        View view = createNavigationItemView(item);
        boolean arrow = item.hasBack || item.search;

        navigationItemContainer.set(view, arrow, animation);
    }

    @Override
    public void containerStartTransition(
            NavigationItem item, ToolbarPresenter.TransitionAnimationStyle animation) {
        View view = createNavigationItemView(item);
        boolean arrow = item.hasBack || item.search;

        navigationItemContainer.startTransition(view, arrow, animation);
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
    public void updateViewForItem(NavigationItem item, boolean current) {
        navigationItemContainer.update(item, current);
    }

    private LinearLayout createNavigationItemView(final NavigationItem item) {
        if (item.search) {
            return createSearchLayout(item);
        } else {
            return createNavigationLayout(item);
        }
    }

    @NonNull
    private LinearLayout createNavigationLayout(NavigationItem item) {
        @SuppressLint("InflateParams")
        LinearLayout menu = (LinearLayout) LayoutInflater.from(getContext()).inflate(R.layout.toolbar_menu, null);
        menu.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout titleContainer = menu.findViewById(R.id.title_container);

        final TextView titleView = menu.findViewById(R.id.title);
        titleView.setTypeface(AndroidUtils.ROBOTO_MEDIUM);
        titleView.setText(item.title);
        titleView.setTextColor(0xffffffff);

        if (item.middleMenu != null) {
            int arrowColor = getAttrColor(getContext(), R.attr.dropdown_light_color);
            int arrowPressedColor = getAttrColor(getContext(), R.attr.dropdown_light_pressed_color);
            Drawable drawable = new DropdownArrowDrawable(dp(12), dp(12), true, arrowColor, arrowPressedColor);
            titleView.setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null);
            titleView.setOnClickListener(v -> item.middleMenu.show(titleView));
        }

        TextView subtitleView = menu.findViewById(R.id.subtitle);
        if (!TextUtils.isEmpty(item.subtitle)) {
            ViewGroup.LayoutParams titleParams = titleView.getLayoutParams();
            titleParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            titleView.setLayoutParams(titleParams);
            subtitleView.setText(item.subtitle);
            subtitleView.setTextColor(0xffffffff);
            titleView.setPadding(titleView.getPaddingLeft(), dp(5f), titleView.getPaddingRight(), titleView.getPaddingBottom());
        } else {
            titleContainer.removeView(subtitleView);
        }

        if (item.rightView != null) {
            removeFromParentView(item.rightView);
            item.rightView.setPadding(0, 0, dp(16), 0);
            menu.addView(item.rightView, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        }

        if (item.menu != null) {
            removeFromParentView(item.menu);
            menu.addView(item.menu, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        }

        return menu;
    }

    @NonNull
    private LinearLayout createSearchLayout(NavigationItem item) {
        SearchLayout searchLayout = new SearchLayout(getContext());

        searchLayout.setCallback(input -> {
            presenter.searchInput(input);
        });

        if (item.searchText != null) {
            searchLayout.setText(item.searchText);
        }

        searchLayout.setHint(callback.getSearchHint(item));
        searchLayout.setPadding(dp(16), searchLayout.getPaddingTop(), searchLayout.getPaddingRight(), searchLayout.getPaddingBottom());

        return searchLayout;
    }

    public interface ToolbarCallback {
        void onMenuOrBackClicked(boolean isArrow);

        void onSearchVisibilityChanged(NavigationItem item, boolean visible);

        String getSearchHint(NavigationItem item);

        void onSearchEntered(NavigationItem item, String entered);
    }

    public static class SimpleToolbarCallback implements ToolbarCallback {
        @Override
        public void onMenuOrBackClicked(boolean isArrow) {
        }

        @Override
        public void onSearchVisibilityChanged(NavigationItem item, boolean visible) {
        }

        @Override
        public String getSearchHint(NavigationItem item) {
            return null;
        }

        @Override
        public void onSearchEntered(NavigationItem item, String entered) {
        }
    }

    public interface ToolbarCollapseCallback {
        void onCollapseTranslation(float offset);

        void onCollapseAnimation(boolean collapse);
    }
}
