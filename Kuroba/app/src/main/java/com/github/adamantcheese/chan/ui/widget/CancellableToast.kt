package com.github.adamantcheese.chan.ui.widget

import android.content.Context
import android.widget.Toast
import com.github.adamantcheese.chan.utils.AndroidUtils.getString
import com.github.adamantcheese.chan.utils.BackgroundUtils

class CancellableToast {
    private var toast: Toast? = null

    fun showToast(context: Context, message: String) {
        showToast(context, message, Toast.LENGTH_SHORT)
    }

    fun showToast(context: Context, msgResId: Int) {
        showToast(context, msgResId, Toast.LENGTH_SHORT)
    }

    fun showToast(context: Context, msgResId: Int, duration: Int) {
        showToast(context, getString(msgResId), duration)
    }

    fun showToast(context: Context, message: String, duration: Int) {
        BackgroundUtils.ensureMainThread()

        if (toast != null) {
            toast!!.cancel()
            toast = null
        }

        toast = Toast.makeText(context, message, duration).apply { show() }
    }
}