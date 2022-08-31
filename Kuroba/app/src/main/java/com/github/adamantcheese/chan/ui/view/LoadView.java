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
package com.github.adamantcheese.chan.ui.view;

import static com.github.adamantcheese.chan.utils.AndroidUtils.removeFromParentView;

import android.animation.*;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Container for a view with an ProgressBar. Toggles between the view and a
 * ProgressBar.
 */
public class LoadView
        extends FrameLayout {
    private int fadeDuration = 300;
    private Listener listener;

    private AnimatorSet animatorSet = new AnimatorSet();

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
     * new one.
     *
     * @param newView the view.
     */
    public View setView(@NonNull View newView) {
        return setView(newView, true);
    }

    /**
     * Set the content of this container. It will fade the attached views out with the
     * new one.
     *
     * @param newView the view.
     * @param animate should it be animated
     */
    public View setView(@NonNull View newView, boolean animate) {
        if (animate) {
            // Fast forward possible pending animations (keeping the views attached.)
            animatorSet.cancel();
            animatorSet = new AnimatorSet();

            // Fade all attached views out.
            // If the animation is canceled, the views are not removed. If the animation ends,
            // the views are removed.
            List<Animator> animators = new ArrayList<>();
            for (int i = 0; i < getChildCount(); i++) {
                final View child = getChildAt(i);

                // We don't add a listener to remove the view we also animate in.
                if (child == newView) continue;

                ObjectAnimator objectAnimator = ObjectAnimator.ofFloat(child, View.ALPHA, 0f);
                objectAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationCancel(Animator animation) {
                        // If canceled, don't remove the view, but re-run the animation.
                        animation.removeListener(this);
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        removeAndCallListener(child);
                    }
                });
                animators.add(objectAnimator);
            }

            if (newView.getParent() != this) {
                if (newView.getParent() != null) {
                    removeFromParentView(newView);
                }
                addView(newView);
            }

            // Assume no running animations
            if (newView.getAlpha() == 1f) {
                newView.setAlpha(0f);
            }

            // Fade our new view in
            ObjectAnimator objectAnimator = ObjectAnimator.ofFloat(newView, View.ALPHA, 1f);
            animators.add(objectAnimator);

            animatorSet.setDuration(fadeDuration);
            animatorSet.playTogether(animators);

            final AnimatorSet currentAnimatorSet = animatorSet;

            // Postponing the animation to the next frame delays the animation until the heavy
            // view setup is done. If you start the animation immediately, it will jump because
            // the first frame is nowhere near 16ms. We rather have a bit of a delay instead of
            // a broken jumping animation.
            post(() -> {
                // The AnimatorSet is replaced with a new one, if it was changed between the
                // previous frame and now.
                if (animatorSet == currentAnimatorSet) {
                    animatorSet.start();
                }
            });
        } else {
            // Fast forward possible pending animations (end, so also remove them).
            animatorSet.end();

            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                removeAndCallListener(child);
            }

            animatorSet = new AnimatorSet();

            newView.setAlpha(1f);
            addView(newView);
        }

        return newView;
    }

    protected void removeAndCallListener(View child) {
        removeView(child);
        if (listener != null) {
            listener.onLoadViewRemoved(child);
        }
    }

    public interface Listener {
        void onLoadViewRemoved(View view);
    }
}
