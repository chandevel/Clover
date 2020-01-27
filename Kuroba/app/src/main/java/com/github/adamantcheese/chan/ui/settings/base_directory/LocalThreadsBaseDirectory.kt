package com.github.adamantcheese.chan.ui.settings.base_directory

import android.net.Uri
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.k1rakishou.fsaf.manager.base_directory.BaseDirectory
import java.io.File

class LocalThreadsBaseDirectory : BaseDirectory() {

    override fun getDirFile(): File? {
        val path = ChanSettings.localThreadLocation.fileApiBaseDir.get()
        if (path.isEmpty()) {
            return null
        }

        return File(path)
    }

    override fun getDirUri(): Uri? {
        val path = ChanSettings.localThreadLocation.safBaseDir.get()
        if (path.isEmpty()) {
            return null
        }

        return Uri.parse(path)
    }

    override fun currentActiveBaseDirType(): ActiveBaseDirType {
        return when {
            ChanSettings.localThreadLocation.isSafDirActive() -> ActiveBaseDirType.SafBaseDir
            ChanSettings.localThreadLocation.isFileDirActive() -> ActiveBaseDirType.JavaFileBaseDir
            else -> throw IllegalStateException("LocalThreadsBaseDirectory: No active base directory!!!")
        }
    }
}