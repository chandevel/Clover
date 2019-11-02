package com.github.adamantcheese.chan.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.widget.FrameLayout
import com.github.adamantcheese.chan.utils.AndroidUtils.dp

// TODO(gestures): uncomment
//@RequiresApi(Build.VERSION_CODES.P)
class AdjustAndroid10GestureZonesView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private val addedZones = mutableListOf<RectF>()
    private var shown: Boolean = false

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#5038FFB2")
    }

    private lateinit var currentEditableZone: EditableZone

    init {
        setWillNotDraw(false)
    }

    fun show(attachSide: AttachSide) {
        currentEditableZone = EditableZone().apply {
            init(attachSide, width, height)
        }

        shown = true
        invalidate()
    }

    fun hide() {
        shown = false
    }

    private fun onAddZoneButtonClicked() {

    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        check(shown) { "View is not shown but onDraw is called!" }

        // Draw the background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        // Draw current editable zone with handles
        currentEditableZone.draw(canvas)
    }

    class EditableZone {
        private var currentAttachSide = AttachSide.Center
        private val zoneDefaultWidth = dp(96f).toFloat()
        private val zoneDefaultHeight = dp(96f).toFloat()
        private val handleSize = dp(16f).toFloat()
        private var viewWidth = 0f
        private var viewHeight = 0f

        private val zone = RectF(0f, 0f, 0f, 0f)
        private val handles = mutableMapOf<AttachSide, RectF>()

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
            zone.set(x, y, x + zoneDefaultWidth, y + zoneDefaultHeight)

            check(zone.right > zone.left)
            check(zone.bottom > zone.top)

            initHandles()
        }

        private fun initHandles() {
            check(handles.isEmpty()) { "Handles are already initialized!!!" }

            for (side in AttachSide.values()) {
                if (side == currentAttachSide) {
                    continue
                }

                var x = 0f
                var y = 0f

                val halfWidth = ((zone.right - zone.left) / 2f) -
                        (handleSize / 2f)
                val halfHeight = ((zone.bottom - zone.top) / 2f) -
                        (handleSize / 2f)

                when (side) {
                    AttachSide.Left -> {
                        x = zone.left - (handleSize * 2f)
                        y = zone.top + halfHeight

                        handles[AttachSide.Left] = RectF(x, y, x + handleSize, y + handleSize)
                    }
                    AttachSide.Right -> {
                        x = zone.right + handleSize
                        y = zone.top + halfHeight

                        handles[AttachSide.Right] = RectF(x, y, x + handleSize, y + handleSize)
                    }
                    AttachSide.Top -> {
                        x = zone.left + halfWidth
                        y = zone.top - handleSize

                        handles[AttachSide.Top] = RectF(x, y, x + handleSize, y - handleSize)
                    }
                    AttachSide.Bottom -> {
                        x = zone.left + halfWidth
                        y = zone.bottom + (handleSize * 2f)

                        handles[AttachSide.Bottom] = RectF(x, y, x + handleSize, y - handleSize)
                    }
                    else -> {
                        // Do nothing with center
                    }
                }

            }
        }

        fun draw(canvas: Canvas) {
            // Draw zone
            canvas.drawRect(zone, currentEditableZonePaint)

            // Draw handles
            for ((attachSide, zoneRect) in handles) {
                if (attachSide == currentAttachSide) {
                    continue
                }

                canvas.drawRect(zoneRect, handlePaint)
            }
        }
    }

    enum class AttachSide {
        Center,
        Left,
        Right,
        Top,
        Bottom
    }
}