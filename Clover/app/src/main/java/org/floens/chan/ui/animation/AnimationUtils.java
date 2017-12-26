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
package org.floens.chan.ui.animation;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.widget.TextView;

public class AnimationUtils {
    public static int interpolate(int a, int b, float x) {
        return (int) (a + (b - a) * x);
    }

    public static void animateTextColor(final TextView text, int to) {
        ValueAnimator animation = ValueAnimator.ofObject(new ArgbEvaluator(), text.getCurrentTextColor(), to);
        animation.addUpdateListener(a -> text.setTextColor((int) a.getAnimatedValue()));
        animation.start();
    }

    public static void animateBackgroundColorDrawable(final View view, int newColor) {
        int currentBackground = ((ColorDrawable) view.getBackground()).getColor();
        ValueAnimator animation = ValueAnimator.ofObject(new ArgbEvaluator(), currentBackground, newColor);
        animation.addUpdateListener(a -> view.setBackgroundColor((int) a.getAnimatedValue()));
        animation.start();
    }
}
