package com.github.adamantcheese.chan.ui.settings.base_directory

import android.net.Uri
import com.github.adamantcheese.chan.BuildConfig
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.k1rakishou.fsaf.manager.base_directory.BaseDirectory
import java.io.File

class SavedFilesBaseDirectory(
) : BaseDirectory(BuildConfig.DEBUG) {

    override fun getDirFile(): File? {
        val saveLocationPath = ChanSettings.saveLocation.get()
        if (saveLocationPath.isEmpty()) {
            return null
        }

        return File(saveLocationPath)
    }

    override fun getDirUri(): Uri? {
        val saveLocationSafPath = ChanSettings.saveLocationUri.get()
        if (saveLocationSafPath.isEmpty()) {
            return null
        }

        return Uri.parse(saveLocationSafPath)
    }

}