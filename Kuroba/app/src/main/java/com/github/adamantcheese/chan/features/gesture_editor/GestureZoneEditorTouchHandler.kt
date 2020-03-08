package com.github.adamantcheese.chan.features.gesture_editor

import android.graphics.PointF
import android.view.MotionEvent

class GestureZoneEditorTouchHandler(
        private val callbacks: GestureZoneEditorTouchHandlerCallbacks
) {
    private var isMoving: Boolean = false
    private val prevMovePosition = PointF(0f, 0f)

    fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount != 1) {
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                prevMovePosition.set(event.x, event.y)
                callbacks.onTouchStart(event.x, event.y)
                isMoving = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isMoving) {
                    return false
                }

                val dx = event.x - prevMovePosition.x
                val dy = event.y - prevMovePosition.y

                callbacks.onTouchInProgress(event.x, event.y, dx, dy)
                prevMovePosition.set(event.x, event.y)
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                callbacks.onTouchEnd()
                isMoving = false
            }
        }

        return true
    }

}

interface GestureZoneEditorTouchHandlerCallbacks {
    fun onTouchStart(x: Float, y: Float)
    fun onTouchInProgress(x: Float, y: Float, dx: Float, dy: Float)
    fun onTouchEnd()
}