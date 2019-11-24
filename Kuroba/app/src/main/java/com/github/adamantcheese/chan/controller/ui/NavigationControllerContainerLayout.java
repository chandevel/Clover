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
package com.github.adamantcheese.chan.controller.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.Scroller;

import androidx.core.view.ViewCompat;

import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.controller.NavigationController;

import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;

public class NavigationControllerContainerLayout
        extends FrameLayout {
    private NavigationController navigationController;

    private int slopPixels;

    /**
     * How many pixels we should move a finger to the right before we start moving the whole
     * controller to the right when in Phone layout. (The lower it is the easier it is to start
     * moving the controller which may make it harder to click other views)
     */
    private int minimalMovedPixels = dp(10);
    private int maxFlingPixels;

    private boolean swipeEnabled = true;

    // The event used in onInterceptTouchEvent to track the initial down event
    private MotionEvent interceptedEvent;

    // The tracking is blocked when the user has moved too much in the y direction
    private boolean blockTracking = false;

    // Is the top controller being tracked and moved
    private boolean tracking = false;

    // The controller being tracked, corresponds with tracking
    private Controller trackingController;

    // The controller behind the tracking controller
    private Controller behindTrackingController;

    // The position of the touch after tracking has started, used to calculate the total offset from
    private int trackStartPosition;

    // Tracks the motion when tracking
    private VelocityTracker velocityTracker;

    // Used to fling and scroll the tracking view
    private Scroller scroller;

    // Indicate if the controller should be popped after the animation ends
    private boolean finishTransitionAfterAnimation = false;

    // Paint, draw rect and position for drawing the shadow
    // The shadow is only drawn when tracking is true
    private Paint shadowPaint;
    private Rect shadowRect = new Rect();
    private int shadowPosition;

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
        slopPixels = viewConfiguration.getScaledTouchSlop();
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
        if (!swipeEnabled || tracking || navigationController.isBlockingInput()
                || !navigationController.getTop().navigation.swipeable || getBelowTop() == null)
        {
            return false;
        }

        int actionMasked = event.getActionMasked();

        if (actionMasked != MotionEvent.ACTION_DOWN && interceptedEvent == null) {
            // Action down wasn't called here, ignore
            return false;
        }

        switch (actionMasked) {
            case MotionEvent.ACTION_DOWN:
                interceptedEvent = MotionEvent.obtain(event);
                break;
            case MotionEvent.ACTION_MOVE: {
                float x = (event.getX() - interceptedEvent.getX());
                float y = (event.getY() - interceptedEvent.getY());

                if (Math.abs(y) >= slopPixels || interceptedEvent.getX() < dp(20)) {
                    blockTracking = true;
                }

                if (!blockTracking && x >= minimalMovedPixels && Math.abs(x) > Math.abs(y)) {
                    startTracking(event);

                    return true;
                }
                break;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                interceptedEvent.recycle();
                interceptedEvent = null;
                blockTracking = false;
                break;
            }
        }

        return false;
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        if (disallowIntercept) {
            if (interceptedEvent != null) {
                interceptedEvent.recycle();
                interceptedEvent = null;
            }
            blockTracking = false;
            if (tracking) {
                endTracking(false);
            }
        }

        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!tracking) {
            return false;
        }

        int translationX = Math.max(0, ((int) event.getX()) - trackStartPosition);
        setTopControllerTranslation(translationX);

        velocityTracker.addMovement(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                scroller.forceFinished(true);

                velocityTracker.addMovement(event);
                velocityTracker.computeCurrentVelocity(1000);
                int velocity = (int) velocityTracker.getXVelocity();

                if (translationX > 0) {
                    boolean doFlingAway = false;

                    if ((velocity > 0 && Math.abs(velocity) > dp(800) && Math.abs(velocity) < maxFlingPixels)
                            || translationX >= getWidth() * 3 / 4)
                    {
                        velocity = Math.max(dp(2000), velocity);

                        scroller.fling(translationX, 0, velocity, 0, 0, Integer.MAX_VALUE, 0, 0);

                        // Make sure the animation always goes past the end
                        if (scroller.getFinalX() < getWidth()) {
                            scroller.startScroll(translationX, 0, getWidth(), 0, 2000);
                        }

                        doFlingAway = true;
                    }

                    if (doFlingAway) {
                        startFlingAnimation(true);
                    } else {
                        scroller.forceFinished(true);
                        scroller.startScroll(translationX, 0, -translationX, 0, 250);
                        startFlingAnimation(false);
                    }
                } else {
                    // User swiped back to the left
                    endTracking(false);
                }

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

        if (tracking) {
            float alpha = Math.min(1f, Math.max(0f, 0.5f - (shadowPosition / (float) getWidth()) * 0.5f));
            shadowPaint.setColor(Color.argb((int) (alpha * 255f), 0, 0, 0));
            shadowRect.set(0, 0, shadowPosition, getHeight());
            canvas.drawRect(shadowRect, shadowPaint);
        }
    }

    private void startTracking(MotionEvent startEvent) {
        if (tracking) {
            throw new IllegalStateException("startTracking called but already tracking");
        }

        tracking = true;
        trackingController = navigationController.getTop();
        behindTrackingController = getBelowTop();

        interceptedEvent.recycle();
        interceptedEvent = null;

        trackStartPosition = (int) startEvent.getX();
        velocityTracker = VelocityTracker.obtain();
        velocityTracker.addMovement(startEvent);

        navigationController.beginSwipeTransition(trackingController, behindTrackingController);
    }

    private void endTracking(boolean finishTransition) {
        if (!tracking) {
            throw new IllegalStateException("endTracking called but was not tracking");
        }

        navigationController.endSwipeTransition(trackingController, behindTrackingController, finishTransition);
        tracking = false;
        trackingController = null;
        behindTrackingController = null;
    }

    private void startFlingAnimation(boolean finishTransitionAfterAnimation) {
        this.finishTransitionAfterAnimation = finishTransitionAfterAnimation;
        ViewCompat.postOnAnimation(this, flingRunnable);
    }

    private Runnable flingRunnable = new Runnable() {
        @Override
        public void run() {
            if (!tracking) {
                throw new IllegalStateException("fling animation running while not tracking");
            }

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
                endTracking(finishTransitionAfterAnimation);
            }
        }
    };

    private void setTopControllerTranslation(int translationX) {
        shadowPosition = translationX;
        trackingController.view.setTranslationX(translationX);
        navigationController.swipeTransitionProgress(translationX / (float) getWidth());
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
