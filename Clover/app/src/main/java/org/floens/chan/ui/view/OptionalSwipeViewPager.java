package org.floens.chan.ui.view;

import android.content.Context;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.MotionEvent;

public class OptionalSwipeViewPager extends ViewPager {
    private boolean swipingEnabled;

    public OptionalSwipeViewPager(Context context) {
        super(context);
    }

    public OptionalSwipeViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return swipingEnabled && super.onTouchEvent(ev);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return swipingEnabled && super.onInterceptTouchEvent(ev);
    }

    public void setSwipingEnabled(boolean swipingEnabled) {
        this.swipingEnabled = swipingEnabled;
    }
}
