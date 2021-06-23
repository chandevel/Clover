package com.github.adamantcheese.chan.ui.widget

import android.content.Context
import android.widget.Toast
import com.github.adamantcheese.chan.utils.AndroidUtils.getString
import com.github.adamantcheese.chan.utils.BackgroundUtils

object CancellableToast {
    private var toast: Toast? = null

    @JvmStatic
    @Synchronized
    fun cleanup() {
        toast?.cancel()
        toast = null
    }

    @JvmStatic
    fun showToast(context: Context, message: String) {
        showToast(context, message, Toast.LENGTH_SHORT)
    }

    @JvmStatic
    fun showToast(context: Context, msgResId: Int) {
        showToast(context, msgResId, Toast.LENGTH_SHORT)
    }

    @JvmStatic
    fun showToast(context: Context, msgResId: Int, duration: Int) {
        showToast(context, getString(msgResId), duration)
    }

    @JvmStatic
    fun showToast(context: Context, message: String, duration: Int) {
        if (BackgroundUtils.isInForeground()) {
            BackgroundUtils.runOnMainThread {
                showToastInternal(context, message, duration)
            }
        }
    }

    @Synchronized
    private fun showToastInternal(context: Context, message: String, duration: Int) {
        cleanup()
        toast = Toast.makeText(context, message, duration).apply { show() }
    }
}