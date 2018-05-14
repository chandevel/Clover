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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.floens.chan.R;
import org.floens.chan.ui.drawable.ArrowMenuDrawable;

import java.util.HashMap;
import java.util.Map;

import static org.floens.chan.utils.AndroidUtils.dp;

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
 * Transitions are user-controlled animations that can be cancelled of finished. For that the
 * currentView describes the view that was originally there, and the transitionView is the view
 * what is possibly transitioned to.
 * <p>
 * This is also the class that is responsible for the orientation and animation of the arrow-menu
 * drawable.
 */
public class ToolbarContainer extends FrameLayout {
    private ArrowMenuDrawable arrowMenu;

    private View previousView;
    private boolean previousArrow;
    private View currentView;
    private boolean currentArrow;
    private View transitionView;
    private boolean transitionArrow;
    private ToolbarPresenter.TransitionAnimationStyle transitionAnimationStyle;

    private Map<View, Animator> animatorSet = new HashMap<>();

    public ToolbarContainer(Context context) {
        this(context, null);
    }

    public ToolbarContainer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ToolbarContainer(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setArrowMenu(ArrowMenuDrawable arrowMenu) {
        this.arrowMenu = arrowMenu;
    }

    public void set(View view, boolean arrow, ToolbarPresenter.AnimationStyle animation) {
        if (transitionView != null) {
            throw new IllegalStateException("Currently in transition mode");
        }

        endAnimations();

        previousView = currentView;
        previousArrow = currentArrow;
        currentView = view;
        currentArrow = arrow;

        addView(view, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        if (getChildCount() > 2) {
            throw new IllegalArgumentException("More than 2 child views attached");
        }

        // Can't run the animation if there is no previous view
        // Otherwise just show it without an animation.
        if (animation != ToolbarPresenter.AnimationStyle.NONE && previousView != null) {
            setAnimation(view, previousView, animation);
        } else {
            if (previousView != null) {
                removeView(previousView);
                previousView = null;
            }
        }

        if (animation == ToolbarPresenter.AnimationStyle.NONE) {
            setArrowProgress(1f, !currentArrow);
        }
    }

    public void update(NavigationItem item, boolean current) {
        // TODO: clean up
        View view = current ? currentView : (previousView != null ? previousView : transitionView);
        if (view != null) {
            TextView titleView = view.findViewById(R.id.title);
            if (titleView != null) {
                titleView.setText(item.title);
            }

            if (!TextUtils.isEmpty(item.subtitle)) {
                TextView subtitleView = view.findViewById(R.id.subtitle);
                if (subtitleView != null) {
                    subtitleView.setText(item.subtitle);
                }
            }
        }
    }

    public boolean isTransitioning() {
        return transitionView != null || previousView != null;
    }

    public void startTransition(
            View view, boolean arrow, ToolbarPresenter.TransitionAnimationStyle style) {
        if (transitionView != null) {
            throw new IllegalStateException("Already in transition mode");
        }

        endAnimations();

        transitionView = view;
        transitionArrow = arrow;
        transitionAnimationStyle = style;
        addView(view, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        if (getChildCount() > 2) {
            throw new IllegalArgumentException("More than 2 child views attached");
        }
    }

    public void stopTransition(boolean didComplete) {
        if (transitionView == null) {
            throw new IllegalStateException("Not in transition mode");
        }

        if (didComplete) {
            removeView(currentView);
            currentView = transitionView;
            currentArrow = transitionArrow;
            transitionView = null;
        } else {
            removeView(transitionView);
            transitionView = null;
        }

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
            endAnimation(previousView);
            if (previousView != null) {
                throw new IllegalStateException("Animation end did not remove view");
            }
        }

        if (currentView != null) {
            endAnimation(currentView);
        }
    }

    private void endAnimation(View view) {
        Animator a = animatorSet.remove(view);
        if (a != null) {
            a.end();
        }
    }

    private void setAnimation(View view, View previousView,
                              ToolbarPresenter.AnimationStyle animationStyle) {
        if (animationStyle == ToolbarPresenter.AnimationStyle.PUSH ||
                animationStyle == ToolbarPresenter.AnimationStyle.POP) {
            final boolean pushing = animationStyle == ToolbarPresenter.AnimationStyle.PUSH;

            // Previous animation
            ValueAnimator previousAnimation = getShortAnimator();
            previousAnimation.addUpdateListener(a -> {
                float value = (float) a.getAnimatedValue();
                setPreviousAnimationProgress(previousView, pushing, value);
            });
            previousAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animatorSet.remove(previousView);
                    removeView(previousView);
                    ToolbarContainer.this.previousView = null;
                }
            });
            if (!pushing) previousAnimation.setStartDelay(100);
            animatorSet.put(previousView, previousAnimation);

            post(previousAnimation::start);

            // Current animation + arrow
            view.setAlpha(0f);
            ValueAnimator animation = getShortAnimator();
            animation.addUpdateListener(a -> {
                float value = (float) a.getAnimatedValue();
                setAnimationProgress(view, pushing, value);

                if (previousArrow != currentArrow) {
                    setArrowProgress(value, !currentArrow);
                }
            });
            animation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animatorSet.remove(view);
                }
            });
            if (!pushing) animation.setStartDelay(100);
            animatorSet.put(view, animation);

            post(animation::start);
        } else if (animationStyle == ToolbarPresenter.AnimationStyle.FADE) {
            // Previous animation
            ValueAnimator previousAnimation =
                    ObjectAnimator.ofFloat(previousView, View.ALPHA, 1f, 0f);
            previousAnimation.setDuration(300);
            previousAnimation.setInterpolator(new LinearInterpolator());
            previousAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animatorSet.remove(previousView);
                    removeView(previousView);
                    ToolbarContainer.this.previousView = null;
                }
            });
            animatorSet.put(previousView, previousAnimation);

            post(previousAnimation::start);

            // Current animation + arrow
            view.setAlpha(0f);
            ValueAnimator animation = ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f);
            animation.setDuration(300);
            animation.setInterpolator(new LinearInterpolator());
            animation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animatorSet.remove(view);
                }
            });
            // A different animator for the arrow because that one needs the deceleration
            // interpolator.
            ValueAnimator arrow = ValueAnimator.ofFloat(0f, 1f);
            arrow.setDuration(300);
            arrow.setInterpolator(new DecelerateInterpolator(2f));
            arrow.addUpdateListener(a -> {
                float value = (float) a.getAnimatedValue();
                if (previousArrow != currentArrow) {
                    setArrowProgress(value, !currentArrow);
                }
            });

            AnimatorSet animationAndArrow = new AnimatorSet();
            animationAndArrow.playTogether(animation, arrow);
            animationAndArrow.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animatorSet.remove(view);
                }
            });

            animatorSet.put(view, animationAndArrow);

            post(animationAndArrow::start);
        }
    }

    private void setPreviousAnimationProgress(View view, boolean pushing, float progress) {
        final int offset = dp(16);
        view.setTranslationY((pushing ? -offset : offset) * progress);
        view.setAlpha(1f - progress);
    }

    private void setAnimationProgress(View view, boolean pushing, float progress) {
        final int offset = dp(16);
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

    private void transitionProgressAnimation(
            float progress, ToolbarPresenter.TransitionAnimationStyle style) {
        progress = Math.max(0f, Math.min(1f, progress));

        final int offset = dp(16);

        boolean pushing = style == ToolbarPresenter.TransitionAnimationStyle.PUSH;

        transitionView.setTranslationY((pushing ? offset : -offset) * (1f - progress));
        transitionView.setAlpha(progress);

        currentView.setTranslationY((pushing ? -offset : offset) * progress);
        currentView.setAlpha(1f - progress);

        if (transitionArrow != currentArrow) {
            setArrowProgress(progress, !transitionArrow);
        }
    }

    private ValueAnimator getShortAnimator() {
        final ValueAnimator animator = ObjectAnimator.ofFloat(0f, 1f);
        animator.setDuration(300);
        animator.setInterpolator(new DecelerateInterpolator(2f));
        return animator;
    }
}
