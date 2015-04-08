package org.floens.chan.ui.helper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;

import org.floens.chan.R;

import static org.floens.chan.utils.AndroidUtils.dp;


/**
 * An ItemDecorator and Touch listener that enabled the list to be reordered and items to be swiped away.
 * It isn't perfect, but works good for what is should do.
 */
public class SwipeListener extends RecyclerView.ItemDecoration implements RecyclerView.OnItemTouchListener {
    public enum Swipeable {
        NO,
        LEFT,
        RIGHT,
        BOTH;
    }

    private static final String TAG = "SwipeListener";
    private static final int MAX_SCROLL_SPEED = 14; // dp/s

    private final int slopPixels;
    private final int flingPixels;
    private final int maxFlingPixels;

    private final Context context;
    private Callback callback;
    private final RecyclerView recyclerView;
    private final LinearLayoutManager layoutManager;
    private final SwipeItemAnimator swipeItemAnimator;

    private VelocityTracker tracker;
    private boolean swiping;
    private float touchDownX;
    private float touchDownY;
    private float totalScrolled;
    private float touchDownOffsetX;
    private float offsetX;
    private View downView;

    private boolean dragging;
    private boolean somePositionChanged = false;
    private int dragPosition = -1;
    private float offsetY;
    private float touchDownOffsetY;

    private final Runnable scrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (dragging) {
                float scroll;
                boolean up = offsetY < recyclerView.getHeight() / 2f;
                if (up) {
                    scroll = Math.max(-dp(MAX_SCROLL_SPEED), (offsetY - recyclerView.getHeight() / 6f) * 0.1f);
                } else {
                    scroll = Math.min(dp(MAX_SCROLL_SPEED), (offsetY - recyclerView.getHeight() * 5f / 6f) * 0.1f);
                }

                if (up && scroll < 0f && layoutManager.findFirstCompletelyVisibleItemPosition() != 0) {
                    recyclerView.scrollBy(0, (int) scroll);
                } else if (!up && scroll > 0f && layoutManager.findLastCompletelyVisibleItemPosition() != recyclerView.getAdapter().getItemCount() - 1) {
                    recyclerView.scrollBy(0, (int) scroll);
                }

                if (scroll != 0) {
                    processDrag();
                    recyclerView.post(scrollRunnable);
                }
            }
        }
    };

    public SwipeListener(Context context, RecyclerView rv, Callback callback) {
        this.context = context;
        recyclerView = rv;
        this.callback = callback;

        layoutManager = new LinearLayoutManager(context);
        rv.setLayoutManager(layoutManager);
        swipeItemAnimator = new SwipeItemAnimator();
        swipeItemAnimator.setMoveDuration(250);
        rv.setItemAnimator(swipeItemAnimator);
        rv.addOnItemTouchListener(this);
        rv.addItemDecoration(this);

        ViewConfiguration viewConfiguration = ViewConfiguration.get(context);
        slopPixels = viewConfiguration.getScaledTouchSlop();
        flingPixels = viewConfiguration.getScaledMinimumFlingVelocity();
        maxFlingPixels = viewConfiguration.getScaledMaximumFlingVelocity();
    }

    @Override
    public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                totalScrolled = 0f;
                touchDownX = e.getRawX();
                touchDownY = e.getRawY();
                downView = rv.findChildViewUnder(e.getX(), e.getY());
                if (downView == null) {
                    // There can be gaps when a move animation is running
                    break;
                }

                // Dragging gets initiated immediately if the touch went down on the thumb area
                // Do not allow dragging when animations are running
                View thumbView = downView.findViewById(R.id.thumb);
                if (thumbView != null && e.getX() < rv.getPaddingLeft() + thumbView.getRight() && !swipeItemAnimator.isRunning()) {
                    int touchAdapterPos = rv.getChildAdapterPosition(downView);
                    if (touchAdapterPos < 0 || !callback.isMoveable(touchAdapterPos)) {
                        break;
                    }

                    dragging = true;
                    dragPosition = touchAdapterPos;

                    rv.post(scrollRunnable);

                    offsetY = e.getY();
                    touchDownOffsetY = offsetY - downView.getTop();

                    downView.setVisibility(View.INVISIBLE);
                    rv.invalidate();

                    return true;
                }

                // Didn't went down on the thumb area, start up the tracker
                if (tracker != null) {
                    Log.w(TAG, "Tracker was not null, recycling extra");
                    tracker.recycle();
                }
                tracker = VelocityTracker.obtain();
                tracker.addMovement(e);
                break;
            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    return true;
                }

                float deltaX = e.getRawX() - touchDownX;
                float deltaY = e.getRawY() - touchDownY;
                totalScrolled += Math.abs(deltaY);
                int adapterPosition = rv.getChildAdapterPosition(downView);
                if (adapterPosition < 0) {
                    break;
                }

                if (swiping) {
                    return true;
                } else {
                    // Logic to find out if a swipe should be initiated
                    Swipeable swipeable = callback.getSwipeable(adapterPosition);
                    if (swipeable != Swipeable.NO && Math.abs(deltaX) >= slopPixels && totalScrolled < slopPixels) {
                        boolean wasSwiped = false;
                        if (swipeable == Swipeable.BOTH) {
                            wasSwiped = true;
                        } else if (swipeable == Swipeable.LEFT && deltaX < -slopPixels) {
                            wasSwiped = true;
                        } else if (swipeable == Swipeable.RIGHT && deltaX > slopPixels) {
                            wasSwiped = true;
                        }

                        if (wasSwiped) {
                            swiping = true;
                            touchDownOffsetX = deltaX;
                            return true;
                        }
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                reset();
                break;
        }

        return false;
    }

    @Override
    public void onTouchEvent(RecyclerView rv, MotionEvent e) {
        if (swiping) {
            float deltaX = e.getRawX() - touchDownX;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_MOVE: {
                    tracker.addMovement(e);
                    offsetX = deltaX - touchDownOffsetX;
                    downView.setTranslationX(offsetX);
                    downView.setAlpha(Math.min(1f, Math.max(0f, 1f - (Math.abs(offsetX) / (float) downView.getWidth()))));
                    break;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    boolean reset = false;

                    int adapterPosition = rv.getChildAdapterPosition(downView);
                    if (adapterPosition < 0) {
                        reset = true;
                    } else if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                        tracker.addMovement(e);
                        tracker.computeCurrentVelocity(1000);
                        float xVelocity = tracker.getXVelocity();

                        if (Math.abs(xVelocity) > flingPixels && Math.abs(xVelocity) < maxFlingPixels &&
                                (xVelocity < 0) == (deltaX < 0) // Swiping in the same direction
                                ) {
                            SwipeItemAnimator.SwipeAnimationData data = new SwipeItemAnimator.SwipeAnimationData();
                            data.right = xVelocity > 0;

                            // Remove animations are linear, calculate the time here to mimic the fling speed
                            float timeLeft = (rv.getWidth() - Math.abs(offsetX)) / Math.abs(xVelocity);
                            timeLeft = Math.min(0.5f, timeLeft);
                            data.time = (long) (timeLeft * 1000f);
                            swipeItemAnimator.addRemoveData(downView, data);
                            callback.removeItem(rv.getChildAdapterPosition(downView));
                        } else {
                            reset = true;
                        }
                    } else {
                        reset = true;
                    }

                    // The item should be reset to its original alpha and position.
                    // Otherwise our SwipeItemAnimator will handle the swipe remove animation
                    if (reset) {
                        swipeItemAnimator.animateMove(rv.getChildViewHolder(downView), 0, 0, 0, 0);
                        swipeItemAnimator.runPendingAnimations();
                    }

                    reset();
                    break;
                }
            }
        } else if (dragging) {
            // Invalidate hover view
            recyclerView.invalidate();

            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_MOVE: {
                    offsetY = e.getY();

                    processDrag();

                    break;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    if (somePositionChanged) {
                        callback.movingDone();
                    }

                    RecyclerView.ViewHolder vh = recyclerView.getChildViewHolder(downView);
                    swipeItemAnimator.endAnimation(vh);
                    float floatingViewPos = offsetY - touchDownOffsetY - downView.getTop();
                    swipeItemAnimator.animateMove(vh, 0, (int) floatingViewPos, 0, 0);
                    swipeItemAnimator.runPendingAnimations();

                    reset();
                    break;
                }
            }
        }
    }

    private void processDrag() {
        float floatingViewPos = offsetY - touchDownOffsetY + downView.getHeight() / 2f;

        View viewAtPosition = null;

        // like findChildUnder, but without looking at the x axis
        for (int c = layoutManager.getChildCount(), i = c - 1; i >= 0; i--) {
            final View child = layoutManager.getChildAt(i);
            if (floatingViewPos >= child.getTop() && floatingViewPos <= child.getBottom()) {
                viewAtPosition = child;
                break;
            }
        }

        if (viewAtPosition == null) {
            return;
        }

        int touchAdapterPos = recyclerView.getChildAdapterPosition(viewAtPosition);
        if (touchAdapterPos < 0) {
            return;
        }

        int firstCompletelyVisible = layoutManager.findFirstCompletelyVisibleItemPosition();
        int lastCompletelyVisible = layoutManager.findLastCompletelyVisibleItemPosition();

        if (touchAdapterPos < firstCompletelyVisible || touchAdapterPos > lastCompletelyVisible) {
            return;
        }

        if ((touchAdapterPos > dragPosition && floatingViewPos > viewAtPosition.getTop() + viewAtPosition.getHeight() / 5f) ||
                (touchAdapterPos < dragPosition && floatingViewPos < viewAtPosition.getTop() + viewAtPosition.getHeight() * 4f / 5f)) {
            if (callback.isMoveable(touchAdapterPos)) {
                callback.moveItem(dragPosition, touchAdapterPos);
                dragPosition = touchAdapterPos;
                somePositionChanged = true;
            }
        }
    }

    @Override
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
        if (swiping && this.downView == view) {
            outRect.set((int) offsetX, 0, (int) -offsetX, 0);
        } else {
            outRect.set(0, 0, 0, 0);
        }
    }

    @Override
    public void onDraw(Canvas canvas, RecyclerView parent, RecyclerView.State state) {
        if (dragging) {
            for (int i = 0, c = layoutManager.getChildCount(); i < c; i++) {
                View child = layoutManager.getChildAt(i);
                if (child.getVisibility() != View.VISIBLE) {
                    child.setVisibility(View.VISIBLE);
                }
            }

            RecyclerView.ViewHolder vh = parent.findViewHolderForAdapterPosition(dragPosition);
            if (vh != null) {
                vh.itemView.setVisibility(View.INVISIBLE);
            }
        }
    }

    @Override
    public void onDrawOver(Canvas canvas, RecyclerView parent, RecyclerView.State state) {
        if (dragging) {
            RecyclerView.ViewHolder vh = parent.findViewHolderForAdapterPosition(dragPosition);
            if (vh != null) {
                int left = parent.getPaddingLeft();
                int top = (int) offsetY - (int) touchDownOffsetY;
                canvas.save();
                canvas.translate(left, top);
                vh.itemView.draw(canvas);
                canvas.restore();
            }
        }
    }

    private void reset() {
        if (tracker != null) {
            tracker.recycle();
            tracker = null;
        }
        downView = null;
        for (int i = 0, c = layoutManager.getChildCount(); i < c; i++) {
            View child = layoutManager.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) {
                child.setVisibility(View.VISIBLE);
            }
        }
        swiping = false;
        offsetX = 0f;
        dragging = false;
        dragPosition = -1;
        somePositionChanged = false;
    }

    public interface Callback {
        Swipeable getSwipeable(int position);

        void removeItem(int position);

        boolean isMoveable(int position);

        void moveItem(int from, int to);

        void movingDone();
    }
}
