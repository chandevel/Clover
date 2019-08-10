package com.github.adamantcheese.chan.core.saf.callback

import android.net.Uri

interface ChooserCallback {
    fun onResult(uri: Uri)
    fun onCancel(reason: String)
}