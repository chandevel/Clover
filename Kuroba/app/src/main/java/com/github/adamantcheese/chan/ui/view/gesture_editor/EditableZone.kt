package com.github.adamantcheese.chan.ui.view.gesture_editor

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.adamantcheese.chan.utils.SafeRectF

class EditableZone {
    private var currentAttachSide = AttachSide.Center
    private var viewWidth = 0f
    private var viewHeight = 0f
    private var isMoving = false

    private val zoneDefaultWidth = AndroidUtils.dp(96f).toFloat()
    private val zoneDefaultHeight = AndroidUtils.dp(96f).toFloat()
    private val handleSize = AndroidUtils.dp(16f).toFloat()
    private val zone = SafeRectF(0f, 0f, 0f, 0f)
    private val handles = mutableMapOf<AttachSide, SafeRectF>()

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
            AttachSide.Center -> {
                x = (viewWidth / 2f) - (zoneDefaultWidth / 2f)
                y = (viewHeight / 2f) - (zoneDefaultWidth / 2f)
            }
        }

        currentAttachSide = attachSide
        zone.moveTo(x, y)
        zone.resize(zoneDefaultWidth, zoneDefaultHeight)

        initHandles()
    }

    fun draw(canvas: Canvas) {
        // Draw zone
        canvas.drawRect(zone.asRectF(), currentEditableZonePaint)

        // Draw handles
        for ((attachSide, zoneRect) in handles) {
            if (attachSide == currentAttachSide) {
                continue
            }

            canvas.drawRect(zoneRect.asRectF(), handlePaint)
        }
    }

    fun onTouchStart(x: Float, y: Float) {
        isMoving = zone.contains(x, y)
    }

    fun onMoving(dx: Float, dy: Float) {
        if (!isMoving) {
            return
        }

        // We are moving the whole zone (this should probably only be used for testing)
        handles.forEach { (_, handle) ->
            handle.moveBy(dx, dy)
        }

        zone.moveBy(dx, dy)
    }

    fun onTouchEnd() {
        isMoving = false
    }

    private fun initHandles() {
        check(handles.isEmpty()) { "Handles are already initialized!!!" }

        for (side in AttachSide.values()) {
            if (side == currentAttachSide) {
                continue
            }

            val halfWidth = (zone.width / 2f) - (handleSize / 2f)
            val halfHeight = (zone.height / 2f) - (handleSize / 2f)

            when (side) {
                AttachSide.Left -> {
                    handles[AttachSide.Left] = SafeRectF(
                            zone.left() - (handleSize * 2f),
                            zone.top() + halfHeight,
                            handleSize,
                            handleSize
                    )
                }
                AttachSide.Right -> {
                    handles[AttachSide.Right] = SafeRectF(
                            zone.right() + handleSize,
                            zone.top() + halfHeight,
                            handleSize,
                            handleSize
                    )
                }
                AttachSide.Top -> {
                    handles[AttachSide.Top] = SafeRectF(
                            zone.left() + halfWidth,
                            zone.top() - (handleSize * 2f),
                            handleSize,
                            handleSize
                    )
                }
                AttachSide.Bottom -> {
                    handles[AttachSide.Bottom] = SafeRectF(
                            zone.left() + halfWidth,
                            zone.bottom() + handleSize,
                            handleSize,
                            handleSize
                    )
                }
                else -> {
                    // Do nothing with center
                }
            }

        }
    }

}