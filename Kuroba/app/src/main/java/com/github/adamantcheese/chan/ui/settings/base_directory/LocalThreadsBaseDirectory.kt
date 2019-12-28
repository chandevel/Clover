package com.github.adamantcheese.chan.ui.settings.base_directory

import android.net.Uri
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.k1rakishou.fsaf.manager.base_directory.BaseDirectory
import java.io.File

class LocalThreadsBaseDirectory : BaseDirectory() {

    override fun getDirFile(): File? {
        return File(ChanSettings.localThreadLocation.fileApiBaseDir.get())
    }

    override fun getDirUri(): Uri? {
        return Uri.parse(ChanSettings.localThreadLocation.safBaseDir.get())
    }

    override fun currentActiveBaseDirType(): ActiveBaseDirType {
        return when {
            ChanSettings.localThreadLocation.isSafDirActive() -> ActiveBaseDirType.SafBaseDir
            ChanSettings.localThreadLocation.isFileDirActive() -> ActiveBaseDirType.JavaFileBaseDir
            else -> throw IllegalStateException("LocalThreadsBaseDirectory: No active base directory!!!")
        }
    }
}