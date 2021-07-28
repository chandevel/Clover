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
package com.github.adamantcheese.chan.ui.layout;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.drawerlayout.widget.DrawerLayout;

import com.github.adamantcheese.chan.R;

import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;

public class DrawerWidthAdjustingLayout
        extends DrawerLayout {
    public DrawerWidthAdjustingLayout(Context context) {
        super(context);
    }

    public DrawerWidthAdjustingLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DrawerWidthAdjustingLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);

        View drawer = findViewById(R.id.drawer);

        float width = Math.min(widthSize - dp(getContext(), 56), dp(getContext(), 56) * 6);
        if (drawer.getLayoutParams().width != width) {
            drawer.getLayoutParams().width = (int) width;
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
