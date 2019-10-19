package com.github.adamantcheese.chan.ui.settings.base_directory

import android.net.Uri
import com.github.k1rakishou.fsaf.manager.base_directory.BaseDirectory
import java.io.File

class FilesBaseDirectory(
        dirUri: Uri?,
        dirFile: File?
) : BaseDirectory(dirUri, dirFile)