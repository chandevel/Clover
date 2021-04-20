package com.github.adamantcheese.chan.core.settings.base_dir

import android.net.Uri
import android.os.Environment
import com.github.adamantcheese.chan.BuildConfig
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.core.settings.primitives.IntegerSetting
import com.github.adamantcheese.chan.core.settings.primitives.StringSetting
import com.github.adamantcheese.chan.core.settings.provider.SettingProvider
import com.github.adamantcheese.chan.utils.AndroidUtils.postToEventBus
import java.io.File

class SavedFilesBaseDirSetting(
        override var activeBaseDir: IntegerSetting,
        override val fileApiBaseDir: StringSetting,
        override val safBaseDir: StringSetting
) : BaseDirectorySetting() {

    constructor(settingProvider: SettingProvider<Any>) : this(
            IntegerSetting(
                    settingProvider,
                    "saved_files_active_base_dir_ordinal",
                    0
            ),
            StringSetting(
                    settingProvider,
                    "preference_image_save_location",
                    getDefaultSaveLocationDir()
            ),
            StringSetting(
                    settingProvider,
                    "preference_image_save_location_uri",
                    ""
            )
    )

    init {
        fileApiBaseDir.addCallback { _, _ ->
            postToEventBus(ChanSettings.SettingChanged(fileApiBaseDir))
        }

        safBaseDir.addCallback { _, _ ->
            postToEventBus(ChanSettings.SettingChanged(safBaseDir))
        }
    }

    override fun setFileBaseDir(dir: String) {
        fileApiBaseDir.setSyncNoCheck(dir)
        activeBaseDir.setSync(ActiveBaseDir.FileBaseDir.ordinal)
    }

    override fun setSafBaseDir(dir: Uri) {
        safBaseDir.setSyncNoCheck(dir.toString())
        activeBaseDir.setSync(ActiveBaseDir.SAFBaseDir.ordinal)
    }

    override fun resetFileDir() {
        fileApiBaseDir.setSyncNoCheck(getDefaultSaveLocationDir())
    }

    override fun resetSafDir() {
        safBaseDir.setSyncNoCheck("")
    }

    override fun resetActiveDir() {
        activeBaseDir.setSync(ActiveBaseDir.FileBaseDir.ordinal)
    }

    companion object {
        private const val FILES_DIR = "files"

        fun getDefaultSaveLocationDir(): String {
            @Suppress("DEPRECATION")
            return (Environment.getExternalStorageDirectory().toString()
                    + File.separator
                    + BuildConfig.APP_LABEL
                    + File.separator
                    + FILES_DIR)
        }
    }
}