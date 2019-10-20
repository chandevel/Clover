package com.github.adamantcheese.chan.ui.settings.base_directory

import android.net.Uri
import com.github.adamantcheese.chan.BuildConfig
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.k1rakishou.fsaf.manager.base_directory.BaseDirectory
import java.io.File

class LocalThreadsBaseDirectory(
) : BaseDirectory(BuildConfig.DEBUG) {

    override fun getDirFile(): File? {
        val localThreadsPath = ChanSettings.localThreadLocation.get()
        if (localThreadsPath.isEmpty()) {
            return null
        }

        return File(localThreadsPath)
    }

    override fun getDirUri(): Uri? {
        val localThreadsSafPath = ChanSettings.localThreadsLocationUri.get()
        if (localThreadsSafPath.isEmpty()) {
            return null
        }

        return Uri.parse(localThreadsSafPath)
    }

}