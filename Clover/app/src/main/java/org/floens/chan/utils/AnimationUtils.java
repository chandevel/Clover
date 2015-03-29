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

import android.view.View;
import android.view.animation.Animation;

import org.floens.chan.ui.animation.HeightAnimation;

public class AnimationUtils {
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

    public static void animateHeight(final View view, boolean expand) {
        animateHeight(view, expand, -1);
    }

    public static void animateHeight(final View view, boolean expand, int knownWidth) {
        if (view.getAnimation() == null && ((view.getHeight() > 0 && expand) || (view.getHeight() == 0 && !expand))) {
            return;
        }

        view.clearAnimation();
        HeightAnimation heightAnimation;
        if (expand) {
            int width = knownWidth < 0 ? view.getWidth() : knownWidth;

            view.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.UNSPECIFIED);
            heightAnimation = new HeightAnimation(view, 0, view.getMeasuredHeight(), 300);
        } else {
            heightAnimation = new HeightAnimation(view, view.getHeight(), 0, 300);
        }
        view.startAnimation(heightAnimation);
        view.getAnimation().setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                view.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animation animation) {

            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
    }
}
