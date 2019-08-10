package com.github.adamantcheese.chan.core.saf.callback

import android.content.Intent

interface StartActivityCallbacks {
    fun myStartActivityForResult(intent: Intent, requestCode: Int): Boolean
}