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
package org.floens.chan.controller.transition;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import org.floens.chan.controller.ControllerTransition;
import org.floens.chan.utils.AndroidUtils;

public class PushControllerTransition extends ControllerTransition {
    @Override
    public void perform() {
        AndroidUtils.waitForMeasure(to.view, new AndroidUtils.OnMeasuredCallback() {
            @Override
            public boolean onMeasured(View view) {
                Animator fromAlpha = ObjectAnimator.ofFloat(from.view, View.ALPHA, 1f, 0.7f);
                fromAlpha.setDuration(217);
                fromAlpha.setInterpolator(new AccelerateDecelerateInterpolator()); // new PathInterpolator(0.4f, 0f, 0.2f, 1f)

                Animator toAlpha = ObjectAnimator.ofFloat(to.view, View.ALPHA, 0f, 1f);
                toAlpha.setDuration(200);
                toAlpha.setInterpolator(new DecelerateInterpolator(2f));

                Animator toY = ObjectAnimator.ofFloat(to.view, View.TRANSLATION_Y, to.view.getHeight() * 0.08f, 0f);
                toY.setDuration(350);
                toY.setInterpolator(new DecelerateInterpolator(2.5f));

                toY.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        onCompleted();
                    }
                });

                AnimatorSet set = new AnimatorSet();
                set.playTogether(/*fromAlpha, */toAlpha, toY);
                set.start();
                return false;
            }
        });
    }
}
