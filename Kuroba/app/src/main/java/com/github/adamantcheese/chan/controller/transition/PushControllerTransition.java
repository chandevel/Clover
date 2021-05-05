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
package com.github.adamantcheese.chan.controller.transition;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.core.view.OneShotPreDrawListener;

import com.github.adamantcheese.chan.controller.ControllerTransition;

public class PushControllerTransition
        extends ControllerTransition {
    @Override
    public void perform() {
        OneShotPreDrawListener.add(to.view, () -> {
            Animator toAlpha = ObjectAnimator.ofFloat(to.view, View.ALPHA, 0f, 1f);
            toAlpha.setInterpolator(new DecelerateInterpolator(2f));

            Animator toY = ObjectAnimator.ofFloat(to.view, View.TRANSLATION_Y, to.view.getHeight() * 0.08f, 0f);
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
        });
    }
}
