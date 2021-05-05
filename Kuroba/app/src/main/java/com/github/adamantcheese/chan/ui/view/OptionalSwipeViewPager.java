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

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.viewpager.widget.ViewPager;

import com.github.adamantcheese.chan.core.repository.BitmapRepository;

public class OptionalSwipeViewPager
        extends ViewPager {
    private boolean swipingEnabled;

    public OptionalSwipeViewPager(Context context) {
        this(context, null);
    }

    public OptionalSwipeViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (isInEditMode()) {
            setAdapter(new ViewPagerAdapter() {
                @Override
                public int getCount() {
                    return 1;
                }

                @Override
                public View getView(int position, ViewGroup parent) {
                    BitmapRepository.initialize(parent.getContext());
                    ImageView test = new ImageView(parent.getContext());
                    test.setImageBitmap(BitmapRepository.partyHat.bitmap);
                    return test;
                }
            });
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return swipingEnabled && super.onTouchEvent(ev);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        try {
            return swipingEnabled && super.onInterceptTouchEvent(ev);
        } catch (IllegalArgumentException ignored) {
            // Ignore pointer index out of range exceptions
            return false;
        }
    }

    public void setSwipingEnabled(boolean swipingEnabled) {
        this.swipingEnabled = swipingEnabled;
    }
}
