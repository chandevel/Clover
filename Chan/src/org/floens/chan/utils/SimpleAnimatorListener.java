package org.floens.chan.utils;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;

/**
 * Extends AnimatorListener with no-op methods.
 */
public class SimpleAnimatorListener implements AnimatorListener {
    @Override
    public void onAnimationCancel(Animator animation) {
    }

    @Override
    public void onAnimationEnd(Animator animation) {
    }

    @Override
    public void onAnimationRepeat(Animator animation) {
    }

    @Override
    public void onAnimationStart(Animator animation) {
    }
}
