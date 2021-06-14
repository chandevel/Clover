package com.github.adamantcheese.chan.features.gesture_editor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import com.github.adamantcheese.chan.Chan.inject
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.utils.AndroidUtils.getRes
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.Q)
class AdjustAndroid10GestureZonesView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), GestureZoneEditorTouchHandlerCallbacks {
    private val gestureZoneEditorTouchHandler: GestureZoneEditorTouchHandler
    private val addedZones = mutableMapOf<Int, MutableSet<ExclusionZone>>()
    private var orientation: Int = -1
    private var shown: Boolean = false
    private var onZoneAddedCallback: (() -> Unit)? = null
    private var currentEditableZone: EditableZone? = null

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = getRes().getColor(R.color.gestures_zone_view_background, null)
    }
    private val addedZonesPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = getRes().getColor(R.color.gestures_zone_view_added_zones, null)
    }
    private val currentEditableZonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = getRes().getColor(R.color.gestures_zone_view_current_zone, null)
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    init {
        setWillNotDraw(false)

        gestureZoneEditorTouchHandler = GestureZoneEditorTouchHandler(this)
    }

    fun show(
            attachSide: AttachSide,
            orientation: Int,
            skipZone: ExclusionZone?,
            measuredWith: Int,
            measuredHeight: Int
    ) {
        this.orientation = orientation
        Android10GesturesExclusionZonesHolder.fillZones(addedZones, skipZone)

        currentEditableZone = EditableZone().apply {
            val editableZoneParams = if (skipZone == null) {
                null
            } else {
                skipZone.checkValid()

                EditableZone.EditableZoneParams(
                        skipZone.left.toFloat(),
                        skipZone.top.toFloat(),
                        (skipZone.right - skipZone.left).toFloat(),
                        (skipZone.bottom - skipZone.top).toFloat()
                )
            }

            init(attachSide, measuredWith, measuredHeight, editableZoneParams)
        }

        shown = true
        invalidate()
    }

    fun hide() {
        orientation = -1
        onZoneAddedCallback = null
        currentEditableZone = null
        addedZones.clear()
        shown = false

        invalidate()
    }

    fun onAddZoneButtonClicked() {
        val editableZone = checkNotNull(currentEditableZone) { "currentEditableZone is null" }

        Android10GesturesExclusionZonesHolder.addZone(
                orientation,
                editableZone.getCurrentAttachSide(),
                editableZone.getCurrentZone().asRect()
        )

        onZoneAddedCallback?.invoke()
    }

    fun setOnZoneAddedCallback(func: () -> Unit) {
        this.onZoneAddedCallback = func
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!shown) {
            return super.onTouchEvent(event)
        }

        if (gestureZoneEditorTouchHandler.onTouchEvent(event)) {
            return true
        }

        return super.onTouchEvent(event)
    }

    override fun onTouchStart(x: Float, y: Float) {
        val editableZone = checkNotNull(currentEditableZone) { "currentEditableZone is null" }
        editableZone.onTouchStart(x, y)
    }

    override fun onTouchInProgress(x: Float, y: Float, dx: Float, dy: Float) {
        if (!shown) {
            return
        }

        val editableZone = checkNotNull(currentEditableZone) { "currentEditableZone is null" }
        if (editableZone.onTouchInProgress(x, y, dx, dy)) {
            invalidate()
        }
    }

    override fun onTouchEnd() {
        val editableZone = checkNotNull(currentEditableZone) { "currentEditableZone is null" }
        editableZone.onTouchEnd()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!shown) {
            return
        }

        // Draw the background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        // Draw already added zones
        addedZones[orientation]?.forEach { zone ->
            canvas.drawRect(zone.zoneRect, addedZonesPaint)
        }

        // Draw current editable zone with handles
        val editableZone = checkNotNull(currentEditableZone) { "currentEditableZone is null" }
        editableZone.draw(canvas, currentEditableZonePaint, handlePaint)
    }
}

/**
 * This represents a side of the phone screen where we want to edit a gesture exclusion zone.
 * So, if it's left that means we want to edit a zone on the left side of the screen, if it's top
 * then we want to edit the top zone, etc.
 * */
enum class AttachSide(val id: Int) {
    Left(0),
    Right(1),

    // the following are not currently used, as the application does not have any swipe up/down actions
    Top(2),
    Bottom(3);

    companion object {
        @JvmStatic
        fun fromInt(id: Int): AttachSide {
            return values().firstOrNull { side -> side.id == id }
                    ?: throw IllegalArgumentException("Unknown attach side id: $id")
        }
    }
}