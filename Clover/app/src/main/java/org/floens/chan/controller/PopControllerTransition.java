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
package org.floens.chan.controller;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

public class PopControllerTransition extends ControllerTransition {
    @Override
    public void perform() {
        Animator toAlpha = ObjectAnimator.ofFloat(to.view, View.ALPHA, to.view.getAlpha(), 1f);
        toAlpha.setInterpolator(new DecelerateInterpolator()); // new PathInterpolator(0f, 0f, 0.2f, 1f)
        toAlpha.setDuration(250);

        Animator fromY = ObjectAnimator.ofFloat(from.view, View.Y, 0f, from.view.getHeight() * 0.05f);
        fromY.setInterpolator(new AccelerateInterpolator(2.5f));
        fromY.setDuration(250);

        fromY.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                onCompleted();
            }
        });

        Animator fromAlpha = ObjectAnimator.ofFloat(from.view, View.ALPHA, from.view.getAlpha(), 0f);
        fromAlpha.setInterpolator(new AccelerateInterpolator(2f));
        fromAlpha.setStartDelay(100);
        fromAlpha.setDuration(150);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(toAlpha, fromY, fromAlpha);
        set.start();
    }
}
