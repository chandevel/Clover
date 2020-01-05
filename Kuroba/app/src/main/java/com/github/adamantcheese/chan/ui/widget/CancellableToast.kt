package com.github.adamantcheese.chan.ui.widget

import android.content.Context
import android.widget.Toast
import com.github.adamantcheese.chan.utils.BackgroundUtils

class CancellableToast {
    private var toast: Toast? = null

    fun showToast(context: Context?, message: String) {
        if (context == null) {
            return
        }

        BackgroundUtils.ensureMainThread()
        showToast(context, message, Duration.Short)
    }

    fun showToast(context: Context?, msgResId: Int) {
        if (context == null) {
            return
        }

        BackgroundUtils.ensureMainThread()
        showToast(context, context.getString(msgResId), Duration.Short)
    }

    fun showToast(context: Context?, msgResId: Int, duration: Duration) {
        if (context == null) {
            return
        }

        BackgroundUtils.ensureMainThread()
        showToast(context, context.getString(msgResId), duration)
    }

    fun showToast(context: Context?, message: String, duration: Duration) {
        if (context == null) {
            return
        }

        BackgroundUtils.ensureMainThread()

        if (toast != null) {
            toast!!.cancel()
            toast = null
        }

        val toastDuration = when (duration) {
            Duration.Short -> Toast.LENGTH_SHORT
            Duration.Long -> Toast.LENGTH_LONG
        }

        toast = Toast.makeText(context, message, toastDuration).apply { show() }
    }

    enum class Duration {
        Short,
        Long
    }
}