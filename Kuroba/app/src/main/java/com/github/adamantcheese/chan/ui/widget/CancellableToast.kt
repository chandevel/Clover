package com.github.adamantcheese.chan.ui.widget

import android.widget.Toast
import com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext
import com.github.adamantcheese.chan.utils.AndroidUtils.getString
import com.github.adamantcheese.chan.utils.BackgroundUtils

class CancellableToast {
    private var toast: Toast? = null

    fun showToast(message: String) {
        showToast(message, Toast.LENGTH_SHORT)
    }

    fun showToast(msgResId: Int) {
        showToast(getString(msgResId), Toast.LENGTH_SHORT)
    }

    fun showToast(msgResId: Int, duration: Int) {
        showToast(getString(msgResId), duration)
    }

    fun showToast(message: String, duration: Int) {
        BackgroundUtils.ensureMainThread()

        if (toast != null) {
            toast!!.cancel()
            toast = null
        }

        toast = Toast.makeText(getAppContext(), message, duration).apply { show() }
    }
}