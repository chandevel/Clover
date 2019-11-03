package com.github.adamantcheese.chan.ui.view.gesture_editor

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.adamantcheese.chan.utils.SafeRectF

class EditableZone {
    private var currentAttachSide = AttachSide.Left
    private var viewWidth = 0f
    private var viewHeight = 0f

    // This represent whether the whole zone is being moved by the user
    private var isMoving = false
    private var isResizing = false

    private val zoneDefaultWidth = AndroidUtils.dp(96f).toFloat()
    private val zoneDefaultHeight = AndroidUtils.dp(96f).toFloat()
    private val handleSize = AndroidUtils.dp(16f).toFloat()
    private val zone = SafeRectF(0f, 0f, 0f, 0f)
    private val handle = SafeRectF(0f, 0f, 0f, 0f)

    private val currentEditableZonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#50FF4C82")
    }

    private val addedZonesPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#50FFB55B")
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    fun init(attachSide: AttachSide, viewWidth: Int, viewHeight: Int) {
        var x = 0f
        var y = 0f

        this.viewWidth = viewWidth.toFloat()
        this.viewHeight = viewHeight.toFloat()

        check(this.viewHeight > 1f) { "viewHeight < 1f" }
        check(this.viewHeight > 1f) { "viewHeight < 1f" }

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

        currentAttachSide = attachSide
        zone.moveTo(x, y)
        zone.setSize(zoneDefaultWidth, zoneDefaultHeight)

        initHandle(attachSide)
    }

    fun draw(canvas: Canvas) {
        // Draw zone
        canvas.drawRect(zone.asRectF(), currentEditableZonePaint)

        // Draw the handle
        canvas.drawRect(handle.asRectF(), handlePaint)
    }

    fun onTouchStart(x: Float, y: Float) {
        isMoving = zone.contains(x, y)
        isResizing = handle.contains(x, y)

        check(!(isMoving && isResizing)) {
            "isMoving and isResizing are both true!"
        }
    }

    fun onMoving(dx: Float, dy: Float): Boolean {
        if (isMoving) {
            // We are moving the whole zone (this should probably only be used for testing)
            handle.moveBy(dx, dy)
            zone.moveBy(dx, dy)
            return true
        }

        if (isResizing) {
            onResizing(dx, dy)
            return true
        }

        return false
    }

    private fun onResizing(dx: Float, dy: Float) {
        val oldWidth = zone.width
        val oldHeight = zone.height

        when (currentAttachSide) {
            AttachSide.Left -> {
                if (zone.resizeWidth(oldWidth + dx)) {
                    handle.moveBy(dx, 0f)
                }

                if (zone.resizeHeight(oldHeight + dy)) {
                    handle.moveBy(0f, dy)
                }
            }
            AttachSide.Right -> {
                if (zone.resizeWidth(oldWidth - dx)) {
                    zone.moveBy(dx, 0f)
                    handle.moveBy(dx, 0f)
                }

                if (zone.resizeHeight(oldHeight + dy)) {
                    handle.moveBy(0f, dy)
                }
            }
            AttachSide.Top -> TODO()
            AttachSide.Bottom -> TODO()
        }
    }

    fun onTouchEnd() {
        isMoving = false
        isResizing = false
    }

    private fun initHandle(attachSide: AttachSide) {
        when (attachSide) {
            AttachSide.Left -> {
                handle.moveTo(
                        zone.right() + handleSize,
                        zone.bottom() + handleSize
                )
            }
            AttachSide.Right -> {
                handle.moveTo(
                        zone.left() - (handleSize * 2f),
                        zone.bottom() + handleSize
                )
            }
            AttachSide.Top -> {
//                handle.moveTo(
//                        zone.right() + handleSize,
//                        zone.top() + handleSize
//                )
            }
            AttachSide.Bottom -> {
//                handles[AttachSide.Bottom] = SafeRectF(
//                        zone.left() + halfWidth,
//                        zone.bottom() + handleSize,
//                        handleSize,
//                        handleSize
//                )
            }
        }

        handle.setSize(handleSize, handleSize)
    }

}