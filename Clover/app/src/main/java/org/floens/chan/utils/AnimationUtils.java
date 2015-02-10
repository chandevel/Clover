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

import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.widget.ImageView;

public class AnimationUtils {
    /**
     * On your start view call startView.getGlobalVisibleRect(startBounds)
     * and on your end container call endContainer.getGlobalVisibleRect(endBounds, globalOffset);<br>
     * startBounds and endBounds will be adjusted appropriately and the starting scale will be returned.
     *
     * @param startBounds  your startBounds
     * @param endBounds    your endBounds
     * @param globalOffset your globalOffset
     * @return the starting scale
     */
    public static float calculateBoundsAnimation(Rect startBounds, Rect endBounds, Point globalOffset) {
        startBounds.offset(-globalOffset.x, -globalOffset.y);
        endBounds.offset(-globalOffset.x, -globalOffset.y);

        float startScale;
        if ((float) endBounds.width() / endBounds.height() > (float) startBounds.width() / startBounds.height()) {
            // Extend start bounds horizontally
            startScale = (float) startBounds.height() / endBounds.height();
            float startWidth = startScale * endBounds.width();
            float deltaWidth = (startWidth - startBounds.width()) / 2;
            startBounds.left -= deltaWidth;
            startBounds.right += deltaWidth;
        } else {
            // Extend start bounds vertically
            startScale = (float) startBounds.width() / endBounds.width();
            float startHeight = startScale * endBounds.height();
            float deltaHeight = (startHeight - startBounds.height()) / 2;
            startBounds.top -= deltaHeight;
            startBounds.bottom += deltaHeight;
        }

        return startScale;
    }

    public static void adjustImageViewBoundsToDrawableBounds(ImageView imageView, Rect bounds) {
        float[] f = new float[9];
        imageView.getImageMatrix().getValues(f);
        bounds.left += f[Matrix.MTRANS_X];
        bounds.top += f[Matrix.MTRANS_Y];
        bounds.right = (bounds.left + (int) (imageView.getDrawable().getIntrinsicWidth() * f[Matrix.MSCALE_X]));
        bounds.bottom = (bounds.top + (int) (imageView.getDrawable().getIntrinsicHeight() * f[Matrix.MSCALE_Y]));
    }
}
