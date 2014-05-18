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
package org.floens.chan.ui;

import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;

/**
 * Contains the TranslateAnimation's for a horizontal scrolling ViewFlipper.
 */
public class ViewFlipperAnimations {
    public static TranslateAnimation BACK_IN;
    public static TranslateAnimation BACK_OUT;
    public static TranslateAnimation NEXT_IN;
    public static TranslateAnimation NEXT_OUT;

    static {
        // Setup the static TranslateAnimations for the ViewFlipper
        BACK_IN = new TranslateAnimation(Animation.RELATIVE_TO_PARENT, -1f, Animation.RELATIVE_TO_PARENT, 0f, 0, 0f, 0,
                0f);
        BACK_IN.setInterpolator(new AccelerateDecelerateInterpolator());
        BACK_IN.setDuration(300);

        BACK_OUT = new TranslateAnimation(Animation.RELATIVE_TO_PARENT, 0f, Animation.RELATIVE_TO_PARENT, 1f, 0, 0f, 0,
                0f);
        BACK_OUT.setInterpolator(new AccelerateDecelerateInterpolator());
        BACK_OUT.setDuration(300);

        NEXT_IN = new TranslateAnimation(Animation.RELATIVE_TO_PARENT, 1f, Animation.RELATIVE_TO_PARENT, 0f, 0, 0f, 0,
                0f);
        NEXT_IN.setInterpolator(new AccelerateDecelerateInterpolator());
        NEXT_IN.setDuration(300);

        NEXT_OUT = new TranslateAnimation(Animation.RELATIVE_TO_PARENT, 0f, Animation.RELATIVE_TO_PARENT, -1f, 0, 0f,
                0, 0f);
        NEXT_OUT.setInterpolator(new AccelerateDecelerateInterpolator());
        NEXT_OUT.setDuration(300);
    }
}
