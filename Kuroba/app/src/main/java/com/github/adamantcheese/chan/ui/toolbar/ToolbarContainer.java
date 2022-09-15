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

import static android.text.TextUtils.isEmpty;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static com.github.adamantcheese.chan.ui.toolbar.ToolbarPresenter.AnimationStyle.FADE;
import static com.github.adamantcheese.chan.ui.toolbar.ToolbarPresenter.AnimationStyle.NONE;
import static com.github.adamantcheese.chan.ui.toolbar.ToolbarPresenter.AnimationStyle.POP;
import static com.github.adamantcheese.chan.ui.toolbar.ToolbarPresenter.AnimationStyle.PUSH;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrDrawable;
import static com.github.adamantcheese.chan.utils.AndroidUtils.removeFromParentView;
import static com.github.adamantcheese.chan.utils.AndroidUtils.updatePaddings;

import android.animation.*;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.ui.layout.SearchLayout;
import com.github.adamantcheese.chan.ui.theme.ArrowMenuDrawable;

import java.util.HashMap;
import java.util.Map;

/**
 * The container for the views created by the toolbar for the navigation items.
 * <p>
 * It will strictly only transition between two views. If a new view is set
 * and a transition is in progress, it is stopped before adding the new view.
 * <p>
 * For normal animations the previousView is the view that is animated away from, and the
 * currentView is the view where is animated to. The previousView is removed and cleared if the
 * animation finished.
 * <p>
 * Transitions are user-controlled animations that can be canceled of finished. For that the
 * currentView describes the view that was originally there, and the transitionView is the view
 * what is possibly transitioned to.
 * <p>
 * This is also the class that is responsible for the orientation and animation of the arrow-menu
 * drawable.
 */
public class ToolbarContainer
        extends FrameLayout {
    private Callback callback;

    private ArrowMenuDrawable arrowMenu;

    @Nullable
    private ItemView previousView;

    @Nullable
    private ItemView currentView;

    @Nullable
    private ItemView transitionView;

    @Nullable
    private ToolbarPresenter.TransitionAnimationStyle transitionAnimationStyle;

    private final Map<View, Animator> animatorSet = new HashMap<>();

    public ToolbarContainer(Context context) {
        this(context, null);
    }

    public ToolbarContainer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ToolbarContainer(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void setArrowMenu(ArrowMenuDrawable arrowMenu) {
        this.arrowMenu = arrowMenu;
    }

    public void set(NavigationItem item, ToolbarPresenter.AnimationStyle animation) {
        endAnimations();

        ItemView itemView = new ItemView(item);

        previousView = currentView;
        currentView = itemView;

        addView(itemView.view, new LayoutParams(MATCH_PARENT, MATCH_PARENT));

        if (getChildCount() > 2) {
            throw new IllegalArgumentException("More than 2 child views attached");
        }

        // Can't run the animation if there is no previous view
        // Otherwise just show it without an animation.
        if (animation != NONE && previousView != null) {
            setAnimation(itemView, previousView, animation);
        } else {
            if (previousView != null) {
                removeItem(previousView);
                previousView = null;
            }
        }

        setArrowProgress(1f, !currentView.item.hasArrow());

        itemView.attach();

        callback.onNavItemSet(item);
    }

    public void update(NavigationItem item) {
        ItemView viewForItem = null;

        if (currentView != null && item == currentView.item) {
            viewForItem = currentView;
        } else if (previousView != null && item == previousView.item) {
            viewForItem = previousView;
        } else if (transitionView != null && item == transitionView.item) {
            viewForItem = transitionView;
        }

        if (viewForItem != null) {
            viewForItem.updateNavigationView();
        }
    }

    public boolean isTransitioning() {
        return transitionView != null || previousView != null;
    }

    public void startTransition(NavigationItem item, ToolbarPresenter.TransitionAnimationStyle style) {
        if (transitionView != null) {
            throw new IllegalStateException("Already in transition mode");
        }

        endAnimations();

        ItemView itemView = new ItemView(item);

        transitionView = itemView;
        transitionAnimationStyle = style;
        addView(itemView.view, new LayoutParams(MATCH_PARENT, MATCH_PARENT));

        if (getChildCount() > 2) {
            throw new IllegalArgumentException("More than 2 child views attached");
        }

        itemView.attach();

        callback.onNavItemSet(item);
    }

    public void stopTransition(boolean didComplete) {
        if (transitionView == null) {
            throw new IllegalStateException("Not in transition mode");
        }

        if (didComplete) {
            removeItem(currentView);
            currentView = transitionView;
        } else {
            removeItem(transitionView);
        }
        transitionView = null;

        if (getChildCount() != 1) {
            throw new IllegalStateException("Not 1 view attached");
        }
    }

    public void setTransitionProgress(float progress) {
        if (transitionView == null) {
            throw new IllegalStateException("Not in transition mode");
        }

        transitionProgressAnimation(progress, transitionAnimationStyle);
    }

    private void endAnimations() {
        if (previousView != null) {
            endAnimation(previousView.view);
            if (previousView != null) {
                throw new IllegalStateException("Animation end did not remove view");
            }
        }

        if (currentView != null) {
            endAnimation(currentView.view);
        }
    }

    private void endAnimation(View view) {
        Animator a = animatorSet.remove(view);
        if (a != null) {
            a.end();
        }
    }

    private void setAnimation(ItemView view, ItemView previousView, ToolbarPresenter.AnimationStyle animationStyle) {
        if (animationStyle == PUSH || animationStyle == POP) {
            final boolean pushing = animationStyle == PUSH;

            // Previous animation
            ValueAnimator previousAnimation = getShortAnimator();
            previousAnimation.addUpdateListener(a -> {
                float value = (float) a.getAnimatedValue();
                setPreviousAnimationProgress(previousView.view, pushing, value);
            });
            previousAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animatorSet.remove(previousView.view);
                    removeItem(previousView);
                    ToolbarContainer.this.previousView = null;
                }
            });
            if (!pushing) previousAnimation.setStartDelay(100);
            animatorSet.put(previousView.view, previousAnimation);

            post(previousAnimation::start);

            // Current animation + arrow
            view.view.setAlpha(0f);
            ValueAnimator animation = getShortAnimator();
            animation.addUpdateListener(a -> {
                float value = (float) a.getAnimatedValue();
                setAnimationProgress(view.view, pushing, value);

                if (previousView.item.hasArrow() != currentView.item.hasArrow()) {
                    setArrowProgress(value, !currentView.item.hasArrow());
                }
            });
            animation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animatorSet.remove(view.view);
                }
            });
            if (!pushing) animation.setStartDelay(100);
            animatorSet.put(view.view, animation);

            post(animation::start);
        } else if (animationStyle == FADE) {
            // Previous animation
            ValueAnimator previousAnimation = ObjectAnimator.ofFloat(previousView.view, View.ALPHA, 1f, 0f);
            previousAnimation.setInterpolator(new LinearInterpolator());
            previousAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animatorSet.remove(previousView.view);
                    removeItem(previousView);
                    ToolbarContainer.this.previousView = null;
                }
            });
            animatorSet.put(previousView.view, previousAnimation);

            post(previousAnimation::start);

            // Current animation + arrow
            view.view.setAlpha(0f);
            ValueAnimator animation = ObjectAnimator.ofFloat(view.view, View.ALPHA, 0f, 1f);
            animation.setInterpolator(new LinearInterpolator());
            animation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animatorSet.remove(view.view);
                }
            });
            // A different animator for the arrow because that one needs the deceleration
            // interpolator.
            ValueAnimator arrow = ValueAnimator.ofFloat(0f, 1f);
            arrow.setInterpolator(new DecelerateInterpolator(2f));
            arrow.addUpdateListener(a -> {
                float value = (float) a.getAnimatedValue();
                if (previousView.item.hasArrow() != currentView.item.hasArrow()) {
                    setArrowProgress(value, !currentView.item.hasArrow());
                }
            });

            AnimatorSet animationAndArrow = new AnimatorSet();
            animationAndArrow.playTogether(animation, arrow);
            animationAndArrow.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animatorSet.remove(view.view);
                }
            });

            animatorSet.put(view.view, animationAndArrow);

            post(animationAndArrow::start);
        }
    }

    private void setPreviousAnimationProgress(View view, boolean pushing, float progress) {
        final float offset = dp(getContext(), 16);
        view.setTranslationY((pushing ? -offset : offset) * progress);
        view.setAlpha(1f - progress);
    }

    private void setAnimationProgress(View view, boolean pushing, float progress) {
        final float offset = dp(getContext(), 16);
        view.setTranslationY((pushing ? offset : -offset) * (1f - progress));
        view.setAlpha(progress);
    }

    private void setArrowProgress(float progress, boolean reverse) {
        if (reverse) {
            progress = 1f - progress;
        }
        progress = Math.max(0f, Math.min(1f, progress));

        arrowMenu.setProgress(progress);
    }

    private void transitionProgressAnimation(float progress, ToolbarPresenter.TransitionAnimationStyle style) {
        progress = Math.max(0f, Math.min(1f, progress));

        final float offset = dp(getContext(), 16);

        boolean pushing = style == ToolbarPresenter.TransitionAnimationStyle.PUSH;

        transitionView.view.setTranslationY((pushing ? offset : -offset) * (1f - progress));
        transitionView.view.setAlpha(progress);

        currentView.view.setTranslationY((pushing ? -offset : offset) * progress);
        currentView.view.setAlpha(1f - progress);

        if (transitionView.item.hasArrow() != currentView.item.hasArrow()) {
            setArrowProgress(progress, !transitionView.item.hasArrow());
        }
    }

    private ValueAnimator getShortAnimator() {
        final ValueAnimator animator = ObjectAnimator.ofFloat(0f, 1f);
        animator.setInterpolator(new DecelerateInterpolator(2f));
        return animator;
    }

    private void removeItem(ItemView item) {
        if (item == null) return;
        item.detach();
        removeView(item.view);
    }

    private class ItemView {
        final View view;
        final NavigationItem item;

        @Nullable
        private ToolbarMenuView menuView;

        public ItemView(NavigationItem item) {
            this.item = item;
            this.view = createNavigationView();
        }

        public void attach() {
            if (item.menu != null && menuView != null) {
                menuView.attach(item.menu);
            }
        }

        public void detach() {
            if (menuView != null) {
                menuView.detach();
            }
        }

        private void updateNavigationView() {
            if (!item.search) {
                detach();
                createOrUpdateNavLayout(true);
                attach();
            }
        }

        private View createNavigationView() {
            return item.search ? createSearchLayout() : createOrUpdateNavLayout(false);
        }

        @NonNull
        private View createOrUpdateNavLayout(boolean update) {
            if (update && view == null) throw new IllegalStateException("Attempting to update null view!");
            LinearLayout menu;
            if (update) {
                menu = (LinearLayout) view;
            } else {
                menu = (LinearLayout) inflate(getContext(), R.layout.toolbar_menu, null);
            }
            ConstraintLayout titleContainer = menu.findViewById(R.id.title_container);

            // Title
            final TextView titleView = menu.findViewById(R.id.title);
            titleView.setText(item.title);

            // Middle title with arrow and callback
            ImageView dropdown = menu.findViewById(R.id.dropdown);
            if (item.middleMenu != null) {
                dropdown.setVisibility(VISIBLE);
                titleContainer.setOnClickListener(v -> item.middleMenu.show(titleView));
                titleContainer.setBackground(getAttrDrawable(getContext(), R.attr.selectableItemBackground));
            } else {
                dropdown.setVisibility(GONE);
                titleContainer.setOnClickListener(null);
                titleContainer.setBackground(null);
            }

            // Possible subtitle.
            TextView subtitleView = menu.findViewById(R.id.subtitle);
            if (!isEmpty(item.subtitle)) {
                subtitleView.setText(item.subtitle);
                subtitleView.setVisibility(VISIBLE);
                ((ConstraintLayout.LayoutParams) subtitleView.getLayoutParams()).bottomToBottom = R.id.parent;
                updatePaddings(titleView, -1, -1, dp(getContext(), 5f), -1);
            } else {
                subtitleView.setVisibility(GONE);
                ((ConstraintLayout.LayoutParams) subtitleView.getLayoutParams()).bottomToBottom = 0;
                updatePaddings(titleView, -1, -1, 0, -1);
            }

            // Possible view shown at the right side.
            removeFromParentView(item.rightView);
            if (item.rightView != null) {
                item.rightView.setPadding(0, 0, (int) dp(getContext(), 16), 0);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT);
                menu.addView(item.rightView, lp);
            }

            // Possible menu with items.
            removeFromParentView(menuView);
            if (item.menu != null) {
                menuView = new ToolbarMenuView(getContext());

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT);
                menu.addView(this.menuView, lp);
            }

            return menu;
        }

        @NonNull
        private View createSearchLayout() {
            SearchLayout searchLayout =
                    (SearchLayout) LayoutInflater.from(getContext()).inflate(R.layout.toolbar_search, null, false);
            searchLayout.setCallback(new SearchLayout.SearchLayoutCallback() {
                @Override
                public void onSearchEntered(String entered) {
                    callback.searchInput(entered);
                }

                @Override
                public void onClearPressedWhenEmpty() {
                    callback.onClearPressedWhenEmpty();
                }
            });
            searchLayout.setText(item.searchText);
            return searchLayout;
        }
    }

    public interface Callback {
        void searchInput(String input);

        void onClearPressedWhenEmpty();

        void onNavItemSet(NavigationItem item);
    }
}
