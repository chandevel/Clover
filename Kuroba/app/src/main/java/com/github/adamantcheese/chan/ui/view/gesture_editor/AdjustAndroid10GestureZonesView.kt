package com.github.adamantcheese.chan.ui.view.gesture_editor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout

// TODO(gestures): uncomment
//@RequiresApi(Build.VERSION_CODES.P)
class AdjustAndroid10GestureZonesView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), GestureZoneEditorTouchHandlerCallbacks {
    private val gestureZoneEditorTouchHandler: GestureZoneEditorTouchHandler
    private val addedZones = mutableListOf<RectF>()
    private var shown: Boolean = false

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#5038FFB2")
    }

    private lateinit var currentEditableZone: EditableZone

    init {
        setWillNotDraw(false)

        gestureZoneEditorTouchHandler = GestureZoneEditorTouchHandler(this)
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

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        check(shown) { "View is not shown but onTouchEvent is called!" }

        if (gestureZoneEditorTouchHandler.onTouchEvent(event)) {
            return true
        }

        return super.onTouchEvent(event)
    }

    override fun onTouchStart(x: Float, y: Float) {
        currentEditableZone.onTouchStart(x, y)
    }

    override fun onMoving(dx: Float, dy: Float) {
        check(shown) { "View is not shown but onMoving is called!" }

        currentEditableZone.onMoving(dx, dy)
        invalidate()
    }

    override fun onTouchEnd() {
        currentEditableZone.onTouchEnd()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        check(shown) { "View is not shown but onDraw is called!" }

        // Draw the background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        // Draw current editable zone with handles
        currentEditableZone.draw(canvas)
    }
}

enum class AttachSide {
    Center, // For tests
    Left,
    Right,
    Top,
    Bottom
}