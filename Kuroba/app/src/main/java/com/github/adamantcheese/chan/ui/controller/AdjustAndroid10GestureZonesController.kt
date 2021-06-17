package com.github.adamantcheese.chan.ui.controller

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.view.View
import android.view.View.inflate
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.RelativeLayout
import androidx.annotation.RequiresApi
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.controller.Controller
import com.github.adamantcheese.chan.features.gesture_editor.AdjustAndroid10GestureZonesView
import com.github.adamantcheese.chan.features.gesture_editor.AttachSide
import com.github.adamantcheese.chan.features.gesture_editor.ExclusionZone
import com.github.adamantcheese.chan.utils.AndroidUtils.dp
import com.github.adamantcheese.chan.utils.AndroidUtils.getScreenOrientation


@RequiresApi(Build.VERSION_CODES.Q)
open class AdjustAndroid10GestureZonesController(
        context: Context,
        private var attachSide: AttachSide,
        private var skipZone: ExclusionZone?
) : Controller(context) {
    private lateinit var viewRoot: RelativeLayout
    private lateinit var adjustZonesView: AdjustAndroid10GestureZonesView
    private lateinit var addZoneButton: Button

    @JvmField
    var adjusted = false
    private var presenting = false

    private val globalLayoutListener = object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            adjustZonesView.viewTreeObserver.removeOnGlobalLayoutListener(this)

            onViewLaidOut()
        }
    }

    override fun onCreate() {
        super.onCreate()
        presenting = true

        view = inflate(context, R.layout.controller_adjust_android_ten_gesture_zones, null) as ViewGroup
        viewRoot = view.findViewById(R.id.view_root)
        adjustZonesView = view.findViewById(R.id.adjust_gesture_zones_view)
        addZoneButton = view.findViewById(R.id.add_zone_button)
        if (skipZone != null) {
            addZoneButton.setText(R.string.apply_options)
        }
        addZoneButton.setOnClickListener { adjustZonesView.onAddZoneButtonClicked() }

        adjustZonesView.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        adjustZonesView.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
    }

    private fun onViewLaidOut() {
        setButtonPosition(attachSide)

        adjustZonesView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        adjustZonesView.hide()
        adjustZonesView.show(
                attachSide,
                getScreenOrientation(),
                skipZone,
                adjustZonesView.width,
                adjustZonesView.height
        )

        adjustZonesView.setOnZoneAddedCallback {
            adjusted = true
            stopPresenting()
        }
    }

    private fun setButtonPosition(attachSide: AttachSide) {
        val prevLayoutParams = addZoneButton.layoutParams as RelativeLayout.LayoutParams

        when (attachSide) {
            AttachSide.Bottom -> {
                prevLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP)
                prevLayoutParams.topMargin = TOP_BUTTON_MARGIN
                prevLayoutParams.bottomMargin = 0
            }
            AttachSide.Top -> {
                prevLayoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                prevLayoutParams.topMargin = 0
                prevLayoutParams.bottomMargin = BOTTOM_BUTTON_MARGIN
            }
            else -> return
        }

        addZoneButton.layoutParams = prevLayoutParams
    }

    override fun onBack(): Boolean {
        if (presenting) {
            presenting = false
            stopPresenting()
            return true
        }

        return super.onBack()
    }

    override fun onDestroy() {
        super.onDestroy()

        presenting = false
        adjustZonesView.hide()
    }

    companion object {
        private val TOP_BUTTON_MARGIN = dp(64f)
        private val BOTTOM_BUTTON_MARGIN = dp(32f)
    }
}