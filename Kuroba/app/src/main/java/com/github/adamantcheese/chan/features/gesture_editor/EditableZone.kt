package com.github.adamantcheese.chan.features.gesture_editor

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import com.github.adamantcheese.chan.utils.AndroidUtils.dp

class EditableZone {
    private var currentAttachSide = AttachSide.Left
    private var viewWidth = 0f
    private var viewHeight = 0f

    // This represent whether the whole zone is being moved by the user
    private var isMoving = false

    // This represent whether the zone is being resized by the user
    private var isResizing = false
    private var zoneOriginToTouchPosDeltaWhenMoving = PointF(0f, 0f)

    private val zoneDefaultWidth = dp(32f)
    private val zoneDefaultHeight = dp(32f)
    private val handleSize = dp(16f)
    private val zone = ScreenRectF(0f, 0f, MINIMUM_SIZE, MINIMUM_SIZE, MINIMUM_SIZE, MAXIMUM_SIZE)
    private val handle = ScreenRectF(0f, 0f, MINIMUM_SIZE, MINIMUM_SIZE, MINIMUM_SIZE, MINIMUM_SIZE)

    fun init(
            attachSide: AttachSide,
            viewWidth: Int,
            viewHeight: Int,
            editableZoneParams: EditableZoneParams?
    ) {
        check(viewWidth >= MINIMUM_SIZE) { "viewWidth (${viewWidth}) < $MINIMUM_SIZE" }
        check(viewHeight >= MINIMUM_SIZE) { "viewHeight (${viewHeight}) < $MINIMUM_SIZE" }

        this.viewWidth = viewWidth.toFloat()
        this.viewHeight = viewHeight.toFloat()
        this.currentAttachSide = attachSide

        var x: Float
        var y: Float
        var zoneWidth = zoneDefaultWidth
        var zoneHeight = zoneDefaultHeight

        if (editableZoneParams == null) {
            calculateDefaultZoneCoordinates(attachSide, viewHeight, viewWidth).also { (calcX, calcY) ->
                x = calcX
                y = calcY
            }
        } else {
            x = editableZoneParams.x
            y = editableZoneParams.y
            zoneWidth = editableZoneParams.width
            zoneHeight = editableZoneParams.height
        }

        zone.moveTo(x, y)
        zone.setSize(zoneWidth, zoneHeight)

        updateHandlePosition(attachSide)
        handle.setSize(handleSize, handleSize)
    }

    private fun calculateDefaultZoneCoordinates(
            attachSide: AttachSide,
            viewHeight: Int,
            viewWidth: Int
    ): Pair<Float, Float> {
        var x = 0f
        var y = 0f

        when (attachSide) {
            AttachSide.Left -> {
                x = 0f
                y = viewHeight / 2f
            }
            AttachSide.Right -> {
                x = viewWidth.toFloat() - zoneDefaultWidth
                y = viewHeight / 2f
            }
            AttachSide.Top -> {
                x = viewWidth / 2f
                y = 0f
            }
            AttachSide.Bottom -> {
                x = viewWidth / 2f
                y = viewHeight.toFloat() - zoneDefaultHeight
            }
        }

        return Pair(x, y)
    }

    fun draw(canvas: Canvas, currentEditableZonePaint: Paint, handlePaint: Paint) {
        // Draw zone
        canvas.drawRect(zone.asRectF(), currentEditableZonePaint)

        // Draw the handle
        canvas.drawRect(handle.asRectF(), handlePaint)
    }

    fun onTouchStart(x: Float, y: Float) {
        isMoving = zone.contains(x, y)
        if (isMoving) {
            // The distance between the origin of the zone and the current click position
            zoneOriginToTouchPosDeltaWhenMoving.set(
                    x - zone.x,
                    y - zone.y
            )
        }

        isResizing = handle.contains(x, y)
        check(!(isMoving && isResizing)) { "isMoving and isResizing are both true!" }
    }

    /**
     * Returns true if the current touch is either moving the zone or resizing it
     * */
    fun onTouchInProgress(x: Float, y: Float, dx: Float, dy: Float): Boolean {
        if (isMoving) {
            // We are moving the whole zone
            val newX = x - zoneOriginToTouchPosDeltaWhenMoving.x
            val newY = y - zoneOriginToTouchPosDeltaWhenMoving.y

            onMoving(newY, newX)
            updateHandlePosition(currentAttachSide)

            return true
        }

        if (isResizing) {
            // We are resizing the zone by moving the handle
            onResizing(dx, dy)
            updateHandlePosition(currentAttachSide)

            return true
        }

        return false
    }

    fun onTouchEnd() {
        isMoving = false
        isResizing = false
    }

    fun getCurrentAttachSide(): AttachSide = currentAttachSide
    fun getCurrentZone(): ScreenRectF = zone

    private fun onMoving(newY: Float, newX: Float) {
        when (currentAttachSide) {
            AttachSide.Left,
            AttachSide.Right -> {
                zone.moveTo(zone.x, newY)

                if (zone.top() < 0f) {
                    zone.moveTo(zone.x, 0f)
                }
                if (zone.bottom() > viewHeight) {
                    zone.moveTo(zone.x, viewHeight - zone.height)
                }
            }
            AttachSide.Top,
            AttachSide.Bottom -> {
                zone.moveTo(newX, zone.y)

                if (zone.left() < 0f) {
                    zone.moveTo(0f, zone.y)
                }
                if (zone.right() > viewWidth) {
                    zone.moveTo(viewWidth - zone.width, zone.y)
                }
            }
        }
    }

    private fun onResizing(dx: Float, dy: Float) {
        when (currentAttachSide) {
            AttachSide.Left -> {
                val targetHeight = zone.height + dy
                val realNewHeight = targetHeight.coerceIn(MINIMUM_SIZE, MAXIMUM_SIZE)

                zone.setHeight(realNewHeight)
            }
            AttachSide.Right -> {
                val targetHeight = zone.height + dy
                val realNewHeight = targetHeight.coerceIn(MINIMUM_SIZE, MAXIMUM_SIZE)

                zone.setHeight(realNewHeight)
            }
            AttachSide.Top -> {
                val targetWidth = zone.width - dx
                val realNewWidth = targetWidth.coerceIn(MINIMUM_SIZE, MAXIMUM_SIZE)

                zone.moveTo((zone.x + dx) - (realNewWidth - targetWidth), zone.y)
                zone.setWidth(realNewWidth)
            }
            AttachSide.Bottom -> {
                val targetWidth = zone.width - dx
                val realNewWidth = targetWidth.coerceIn(MINIMUM_SIZE, MAXIMUM_SIZE)

                zone.moveTo((zone.x + dx) - (realNewWidth - targetWidth), zone.y)
                zone.setWidth(realNewWidth)
            }
        }
    }

    private fun updateHandlePosition(attachSide: AttachSide) {
        when (attachSide) {
            AttachSide.Left -> {
                handle.moveTo(zone.right() + handleSize, zone.bottom() + handleSize)
            }
            AttachSide.Right -> {
                handle.moveTo(zone.left() - (handleSize * 2f), zone.bottom() + handleSize)
            }
            AttachSide.Top -> {
                handle.moveTo(zone.left() - (handleSize * 2f), zone.bottom() + handleSize)
            }
            AttachSide.Bottom -> {
                handle.moveTo(zone.left() - (handleSize * 2f), zone.top() - (handleSize * 2f))
            }
        }
    }

    data class EditableZoneParams(
            val x: Float,
            val y: Float,
            val width: Float,
            val height: Float
    )

    companion object {
        val MINIMUM_SIZE = dp(16f)

        // Android limitation
        val MAXIMUM_SIZE = dp(200f)
    }
}