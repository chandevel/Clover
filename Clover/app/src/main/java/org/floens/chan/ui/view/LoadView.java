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
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import java.util.HashMap;
import java.util.Map;

/**
 * Container for a view with an ProgressBar. Toggles between the view and a
 * ProgressBar.
 */
public class LoadView extends FrameLayout {
    private int fadeDuration = 200;
    private Map<View, AnimatorSet> animatorsIn = new HashMap<>();
    private Map<View, AnimatorSet> animatorsOut = new HashMap<>();

    public LoadView(Context context) {
        super(context);
        init();
    }

    public LoadView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LoadView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setView(null, false);
    }

    public void setFadeDuration(int fadeDuration) {
        this.fadeDuration = fadeDuration;
    }

    /**
     * Set the content of this container. It will fade the old one out with the
     * new one. Set view to null to show the progressbar.
     *
     * @param view the view or null for a progressbar.
     */
    public void setView(View view) {
        setView(view, true);
    }

    public void setView(View newView, boolean animate) {
        // Passing null means showing a progressbar
        if (newView == null) {
            FrameLayout progressBar = new FrameLayout(getContext());
            progressBar.addView(new ProgressBar(getContext()), new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));
            newView = progressBar;
        }

        if (animate) {
            // Readded while still running a add/remove animation for the new view
            // This also removes the new view from this view
            AnimatorSet out = animatorsOut.remove(newView);
            if (out != null) {
                out.cancel();
            }

            AnimatorSet in = animatorsIn.remove(newView);
            if (in != null) {
                in.cancel();
            }

            // Add fade out animations for all remaining view
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child != null) {
                    AnimatorSet inSet = animatorsIn.remove(child);
                    if (inSet != null) {
                        inSet.cancel();
                    }

                    if (!animatorsOut.containsKey(child)) {
                        animateViewOut(child);
                    }
                }
            }

            addView(newView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

            // Fade view in
            if (newView.getAlpha() == 1f) {
                newView.setAlpha(0f);
            }
            animateViewIn(newView);
        } else {
            for (AnimatorSet set : animatorsIn.values()) {
                set.cancel();
            }
            animatorsIn.clear();

            for (AnimatorSet set : animatorsOut.values()) {
                set.cancel();
            }
            animatorsOut.clear();

            removeAllViews();
            addView(newView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }
    }

    private void animateViewOut(final View view) {
        // Cancel any fade in animations
        AnimatorSet fadeIn = animatorsIn.remove(view);
        if (fadeIn != null) {
            fadeIn.cancel();
        }

        final AnimatorSet set = new AnimatorSet();
        set.setDuration(fadeDuration);
        if (fadeDuration > 0) {
            set.setStartDelay(50);
        }
        set.setInterpolator(new LinearInterpolator());
        set.play(ObjectAnimator.ofFloat(view, View.ALPHA, 0f));
        animatorsOut.put(view, set);
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                removeView(view);
                animatorsOut.remove(set);
            }
        });
        set.start();
    }

    private void animateViewIn(View view) {
        final AnimatorSet set = new AnimatorSet();
        set.setDuration(fadeDuration);
        set.setInterpolator(new LinearInterpolator());
        set.play(ObjectAnimator.ofFloat(view, View.ALPHA, 1f));
        animatorsIn.put(view, set);
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                animatorsIn.remove(set);
            }
        });
        set.start();
    }
}
