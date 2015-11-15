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
package org.floens.chan.controller.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.Scroller;

import org.floens.chan.controller.Controller;
import org.floens.chan.controller.NavigationController;

public class NavigationControllerContainerLayout extends FrameLayout {
    private NavigationController navigationController;

    private int slopPixels;
    private int flingPixels;
    private int maxFlingPixels;

    private boolean swipeEnabled = true;

    private MotionEvent downEvent;
    private boolean dontStartSwiping = false;
    private MotionEvent swipeStartEvent;
    private VelocityTracker velocityTracker;
    private Scroller scroller;
    private boolean popAfterSwipe = false;
    private Paint shadowPaint;
    private Rect shadowRect = new Rect();
    private boolean drawShadow;
    private int swipePosition;
    private boolean swiping = false;

    private Controller swipingController;

    public NavigationControllerContainerLayout(Context context) {
        super(context);
        init();
    }

    public NavigationControllerContainerLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public NavigationControllerContainerLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        ViewConfiguration viewConfiguration = ViewConfiguration.get(getContext());
        slopPixels = (int) (viewConfiguration.getScaledTouchSlop() * 0.5f);
        flingPixels = viewConfiguration.getScaledMinimumFlingVelocity();
        maxFlingPixels = viewConfiguration.getScaledMaximumFlingVelocity();

        scroller = new Scroller(getContext());

        shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    public void setSwipeEnabled(boolean swipeEnabled) {
        this.swipeEnabled = swipeEnabled;
    }

    public void setNavigationController(NavigationController navigationController) {
        this.navigationController = navigationController;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (!swipeEnabled || swiping) {
            return false;
        }

        int actionMasked = event.getActionMasked();

        if (actionMasked != MotionEvent.ACTION_DOWN && downEvent == null) {
            // Action down wasn't called here, ignore
            return false;
        }

        if (!navigationController.getTop().navigationItem.swipeable) {
            return false;
        }

        if (getBelowTop() == null) {
            // Cannot swipe now
            return false;
        }

        switch (actionMasked) {
            case MotionEvent.ACTION_DOWN:
//                Logger.test("onInterceptTouchEvent down");
                downEvent = MotionEvent.obtain(event);
                break;
            case MotionEvent.ACTION_MOVE: {
//                Logger.test("onInterceptTouchEvent move");
                float x = (event.getX() - downEvent.getX());
                float y = (event.getY() - downEvent.getY());

                if (Math.abs(y) >= slopPixels) {
//                    Logger.test("dontStartSwiping = true");
                    dontStartSwiping = true;
                }

                if (!dontStartSwiping && Math.abs(x) > Math.abs(y) && x >= slopPixels && !navigationController.isBlockingInput()) {
//                    Logger.test("Start tracking swipe");
                    downEvent.recycle();
                    downEvent = null;

                    swipeStartEvent = MotionEvent.obtain(event);
                    velocityTracker = VelocityTracker.obtain();
                    velocityTracker.addMovement(event);

                    swiping = true;

                    return true;
                }
                break;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
//                Logger.test("onInterceptTouchEvent cancel/up");
                downEvent.recycle();
                downEvent = null;
                dontStartSwiping = false;
                break;
            }
        }

        return false;
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        if (disallowIntercept) {
            if (downEvent != null) {
                downEvent.recycle();
                downEvent = null;
            }
            dontStartSwiping = false;
        }

        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // This touch wasn't initiated with onInterceptTouchEvent
        if (swipeStartEvent == null) {
            return false;
        }

        if (swipingController == null) {
            // Start of swipe

//            Logger.test("Start of swipe");

            swipingController = navigationController.getTop();
            drawShadow = true;

//            long start = Time.startTiming();

            Controller below = getBelowTop();
            navigationController.beginSwipeTransition(swipingController, below);

//            Time.endTiming("attach", start);
        }

        float translationX = Math.max(0, event.getX() - swipeStartEvent.getX());
        setTopControllerTranslation((int) translationX);

        velocityTracker.addMovement(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
//                Logger.test("onTouchEvent cancel or up");

                scroller.forceFinished(true);

                velocityTracker.addMovement(event);
                velocityTracker.computeCurrentVelocity(1000);
                float velocity = velocityTracker.getXVelocity();

                if (translationX > 0f) {
                    boolean isFling = false;

                    if (velocity > 0f && Math.abs(velocity) > flingPixels && Math.abs(velocity) < maxFlingPixels) {
                        scroller.fling((int) translationX, 0, (int) velocity, 0, 0, Integer.MAX_VALUE, 0, 0);

                        if (scroller.getFinalX() >= getWidth()) {
                            isFling = true;
                        }
                    }

                    if (isFling) {
//                        Logger.test("Flinging with velocity = " + velocity);
                        popAfterSwipe = true;
                        ViewCompat.postOnAnimation(this, flingRunnable);
                    } else {
//                        Logger.test("Snapping back!");
                        scroller.forceFinished(true);
                        scroller.startScroll((int) translationX, 0, -((int) translationX), 0, 300);
                        popAfterSwipe = false;
                        ViewCompat.postOnAnimation(this, flingRunnable);
                    }
                } else {
                    finishSwipe();
                }

                swipeStartEvent.recycle();
                swipeStartEvent = null;

                velocityTracker.recycle();
                velocityTracker = null;

                break;
            }
        }

        return true;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        if (drawShadow) {
            float alpha = Math.min(1f, Math.max(0f, 0.5f - (swipePosition / (float) getWidth()) * 0.5f));
            shadowPaint.setColor(Color.argb((int) (alpha * 255f), 0, 0, 0));
            shadowRect.set(0, 0, swipePosition, getHeight());
            canvas.drawRect(shadowRect, shadowPaint);
        }
    }

    private void finishSwipe() {
        Controller below = getBelowTop();

        navigationController.endSwipeTransition(swipingController, below, popAfterSwipe);
        swipingController = null;
        drawShadow = false;
        swiping = false;
    }

    private Runnable flingRunnable = new Runnable() {
        @Override
        public void run() {
            boolean finished = false;

            if (scroller.computeScrollOffset()) {
                float translationX = scroller.getCurrX();

                setTopControllerTranslation((int) translationX);

                // The view is not visible anymore. End it before the fling completely finishes.
                if (translationX >= getWidth()) {
                    finished = true;
                }
            } else {
                finished = true;
            }

            if (!finished) {
                ViewCompat.postOnAnimation(NavigationControllerContainerLayout.this, flingRunnable);
            } else {
                finishSwipe();
            }
        }
    };

    private void setTopControllerTranslation(int translationX) {
        swipePosition = translationX;
        swipingController.view.setTranslationX(swipePosition);
        navigationController.swipeTransitionProgress(swipePosition / (float) getWidth());
        invalidate();
    }

    private Controller getBelowTop() {
        if (navigationController.childControllers.size() >= 2) {
            return navigationController.childControllers.get(navigationController.childControllers.size() - 2);
        } else {
            return null;
        }
    }
}
