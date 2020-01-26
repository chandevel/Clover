package com.github.adamantcheese.chan.ui.controller

import android.content.Context
import android.view.View.inflate
import android.view.ViewGroup
import android.view.ViewTreeObserver
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.controller.Controller
import com.github.adamantcheese.chan.ui.view.gesture_editor.AdjustAndroid10GestureZonesView
import com.github.adamantcheese.chan.ui.view.gesture_editor.AttachSide

class AdjustAndroid10GestureZonesController(context: Context) : Controller(context) {
    private lateinit var adjustZonesView: AdjustAndroid10GestureZonesView
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
        adjustZonesView = view.findViewById(R.id.adjust_gesture_zones_view)
        adjustZonesView.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
    }

    private fun onViewLaidOut() {
        adjustZonesView.show(AttachSide.Right)
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

}