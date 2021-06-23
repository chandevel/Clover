package com.github.adamantcheese.chan.ui.widget

import android.view.View
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.utils.AndroidUtils.getString
import com.github.adamantcheese.chan.utils.BackgroundUtils
import com.google.android.material.snackbar.BaseTransientBottomBar.BaseCallback
import com.google.android.material.snackbar.Snackbar

object CancellableSnackbar {
    private var snackbar: Snackbar? = null

    @JvmStatic
    @Synchronized
    fun cleanup() {
        snackbar?.dismiss()
    }

    @JvmStatic
    fun showSnackbar(anchorView: View, message: String) {
        showSnackbar(anchorView, message, R.string.empty, null)
    }

    @JvmStatic
    fun showSnackbar(anchorView: View, msgResId: Int, actionResId: Int, actionCallback: View.OnClickListener?) {
        if (BackgroundUtils.isInForeground()) {
            BackgroundUtils.runOnMainThread {
                showSnackbarInternal(anchorView, getString(msgResId), getString(actionResId), actionCallback)
            }
        }
    }

    @JvmStatic
    fun showSnackbar(anchorView: View, message: String, actionResId: Int, actionCallback: View.OnClickListener?) {
        if (BackgroundUtils.isInForeground()) {
            BackgroundUtils.runOnMainThread {
                showSnackbarInternal(anchorView, message, getString(actionResId), actionCallback)
            }
        }
    }

    @Synchronized
    private fun showSnackbarInternal(anchorView: View, message: String, actionString: String, actionCallback: View.OnClickListener?) {
        cleanup()
        snackbar = Snackbar.make(anchorView, message, Snackbar.LENGTH_LONG).apply {
            addCallback(object : BaseCallback<Snackbar?>() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    snackbar?.removeCallback(this)
                    snackbar = null
                }
            })
            setAction(actionString, actionCallback)
            isGestureInsetBottomIgnored = true
            show()
        }
    }
}