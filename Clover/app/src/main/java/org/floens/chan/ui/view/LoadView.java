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
package org.floens.chan.ui.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import org.floens.chan.utils.AnimationUtils;

/**
 * Container for a view with an ProgressBar. Toggles between the view and a
 * ProgressBar.
 */
public class LoadView extends FrameLayout {
    private int fadeDuration = 200;
    private boolean animateLayout;
    private boolean animateVertical;
    private int layoutAnimationDuration = 500;
    private Listener listener;

    public LoadView(Context context) {
        super(context);
    }

    public LoadView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LoadView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * Set the duration of the fades in ms
     *
     * @param fadeDuration the duration of the fade animation in ms
     */
    public void setFadeDuration(int fadeDuration) {
        this.fadeDuration = fadeDuration;
    }

    public void setLayoutAnimationDuration(int layoutAnimationDuration) {
        this.layoutAnimationDuration = layoutAnimationDuration;
    }

    public void setAnimateLayout(boolean animateLayout, boolean animateVertical) {
        this.animateLayout = animateLayout;
        this.animateVertical = animateVertical;
    }

    /**
     * Set a listener that gives a call when a view gets removed
     *
     * @param listener the listener
     */
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /**
     * Set the content of this container. It will fade the attached views out with the
     * new one. Set view to null to show the progressbar.
     *
     * @param newView the view or null for a progressbar.
     */
    public View setView(View newView) {
        return setView(newView, true);
    }

    /**
     * Set the content of this container. It will fade the attached views out with the
     * new one. Set view to null to show the progressbar.
     *
     * @param newView the view or null for a progressbar.
     * @param animate should it be animated
     */
    public View setView(View newView, boolean animate) {
        if (newView == null) {
            FrameLayout progressBar = new FrameLayout(getContext());
            progressBar.addView(new ProgressBar(getContext()), new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));
            newView = progressBar;
        }

        if (animate) {
            // Fade all attached views out
            for (int i = 0; i < getChildCount(); i++) {
                final View child = getChildAt(i);
                final ViewPropertyAnimator childAnimation = child.animate()
                        .setInterpolator(new LinearInterpolator())
                        .setDuration(fadeDuration)
                        .alpha(0f);
                childAnimation.setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationCancel(Animator animation) {
                        // Canceled because it is being animated in again.
                        // Don't let this listener call removeView on the in animation.
                        childAnimation.setListener(null);
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        // Animation ended without interruptions, remove listener for future animations.
                        childAnimation.setListener(null);
                        removeView(child);
                        if (listener != null) {
                            listener.onLoadViewRemoved(child);
                        }
                    }
                }).start();
            }

            // Assume no running animations
            if (newView.getAlpha() == 1f) {
                newView.setAlpha(0f);
            }

            // Fade our new view in
            newView.animate()
                    .setInterpolator(new LinearInterpolator())
                    .setDuration(fadeDuration)
                    .alpha(1f)
                    .start();

            // Assume view already attached to this view (fading out)
            if (newView.getParent() == null) {
                addView(newView, getLayoutParamsForView(newView));
            }

            // Animate this view its size to the new view size if the width or height is WRAP_CONTENT
            if (animateLayout && getChildCount() >= 2) {
                final int currentSize = animateVertical ? getHeight() : getWidth();
                int newSize = animateVertical ? newView.getHeight() : newView.getWidth();
                if (newSize == 0) {
                    if (animateVertical) {
                        if (newView.getLayoutParams().height == LayoutParams.WRAP_CONTENT) {
                            newView.measure(
                                    View.MeasureSpec.makeMeasureSpec(getWidth(), View.MeasureSpec.EXACTLY),
                                    View.MeasureSpec.UNSPECIFIED);
                            newSize = newView.getMeasuredHeight();
                        } else {
                            newSize = newView.getLayoutParams().height;
                        }
                    } else {
                        if (newView.getLayoutParams().width == LayoutParams.WRAP_CONTENT) {
                            newView.measure(
                                    View.MeasureSpec.UNSPECIFIED,
                                    View.MeasureSpec.makeMeasureSpec(getHeight(), View.MeasureSpec.EXACTLY));
                            newSize = newView.getMeasuredWidth();
                        } else {
                            newSize = newView.getLayoutParams().width;
                        }
                    }
                }

                AnimationUtils.animateLayout(animateVertical, this, currentSize, newSize, layoutAnimationDuration, true, null);
            }
        } else {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                child.animate().cancel();
                if (listener != null) {
                    listener.onLoadViewRemoved(child);
                }
            }
            removeAllViews();
            newView.animate().cancel();
            newView.setAlpha(1f);
            addView(newView, getLayoutParamsForView(newView));
        }

        return newView;
    }

    public LayoutParams getLayoutParamsForView(View view) {
        return new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    }

    public interface Listener {
        void onLoadViewRemoved(View view);
    }
}
