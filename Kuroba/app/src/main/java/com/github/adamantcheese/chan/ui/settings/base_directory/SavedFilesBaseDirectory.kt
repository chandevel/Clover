package com.github.adamantcheese.chan.ui.settings.base_directory

import android.net.Uri
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.k1rakishou.fsaf.manager.base_directory.BaseDirectory
import java.io.File

class SavedFilesBaseDirectory : BaseDirectory() {

    override fun getDirFile(): File? {
        val path = ChanSettings.saveLocation.fileApiBaseDir.get()
        if (path.isEmpty()) {
            return null
        }

        return File(path)
    }

    override fun getDirUri(): Uri? {
        val path = ChanSettings.saveLocation.safBaseDir.get()
        if (path.isEmpty()) {
            return null
        }

        return Uri.parse(path)
    }

    override fun currentActiveBaseDirType(): ActiveBaseDirType {
        return when {
            ChanSettings.saveLocation.isSafDirActive() -> ActiveBaseDirType.SafBaseDir
            ChanSettings.saveLocation.isFileDirActive() -> ActiveBaseDirType.JavaFileBaseDir
            else -> throw IllegalStateException("SavedFilesBaseDirectory: No active base directory!!!")
        }
    }
}