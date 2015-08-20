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
package org.floens.chan.utils;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

import java.util.HashMap;
import java.util.Map;

public class AnimationUtils {
    public static int interpolate(int a, int b, float x) {
        return (int) (a + (b - a) * x);
    }

    public static void setHeight(View view, boolean expand, boolean animated) {
        setHeight(view, expand, animated, -1);
    }

    public static void setHeight(View view, boolean expand, boolean animated, int knownWidth) {
        if (animated) {
            animateHeight(view, expand, knownWidth);
        } else {
            view.setVisibility(expand ? View.VISIBLE : View.GONE);
        }
    }

    private static Map<View, ValueAnimator> layoutAnimations = new HashMap<>();

    public static int animateHeight(final View view, boolean expand) {
        return animateHeight(view, expand, -1);
    }

    public static int animateHeight(final View view, final boolean expand, int knownWidth) {
        return animateHeight(view, expand, knownWidth, 300);
    }

    public static int animateHeight(final View view, final boolean expand, int knownWidth, int duration) {
        return animateHeight(view, expand, knownWidth, duration, null);
    }

    /**
     * Animate the height of a view by changing the layoutParams.height value.<br>
     * view.measure is used to figure out the height.
     * Use knownWidth when the width of the view has not been measured yet.<br>
     * You can call this even when a height animation is currently running, it will resolve any issues.<br>
     * <b>This does cause some lag on complex views because requestLayout is called on each frame.</b>
     */
    public static int animateHeight(final View view, final boolean expand, int knownWidth, int duration, final LayoutAnimationProgress progressCallback) {
        final int fromHeight;
        int toHeight;
        if (expand) {
            int width = knownWidth < 0 ? view.getWidth() : knownWidth;

            view.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.UNSPECIFIED);
            fromHeight = view.getHeight();
            toHeight = view.getMeasuredHeight();
        } else {
            fromHeight = view.getHeight();
            toHeight = 0;
        }

        animateLayout(true, view, fromHeight, toHeight, duration, true, progressCallback);

        return toHeight;
    }

    public static void animateLayout(final boolean vertical, final View view, final int from, final int to, int duration, final boolean wrapAfterwards, final LayoutAnimationProgress callback) {
        ValueAnimator running = layoutAnimations.remove(view);
        if (running != null) {
            running.cancel();
        }

        ValueAnimator valueAnimator = ValueAnimator.ofInt(from, to);
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int value = (int) animation.getAnimatedValue();
                // Looks better
                if (value == 1) {
                    value = 0;
                }
                if (vertical) {
                    view.getLayoutParams().height = value;
                } else {
                    view.getLayoutParams().width = value;
                }
                view.requestLayout();

                if (callback != null) {
                    callback.onLayoutAnimationProgress(view, vertical, from, to, value, animation.getAnimatedFraction());
                }
            }
        });

        valueAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                view.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (to > 0) {
                    if (wrapAfterwards) {
                        if (vertical) {
                            view.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                        } else {
                            view.getLayoutParams().width = ViewGroup.LayoutParams.WRAP_CONTENT;
                        }
                    }
                } else {
                    if (vertical) {
                        view.getLayoutParams().height = 0;
                    } else {
                        view.getLayoutParams().width = 0;
                    }
                    view.setVisibility(View.GONE);
                }
                view.requestLayout();

                layoutAnimations.remove(view);
            }
        });
        valueAnimator.setInterpolator(new DecelerateInterpolator(2f));
        valueAnimator.setDuration(duration);
        valueAnimator.start();

        layoutAnimations.put(view, valueAnimator);
    }

    public interface LayoutAnimationProgress {
        void onLayoutAnimationProgress(View view, boolean vertical, int from, int to, int value, float progress);
    }
}
