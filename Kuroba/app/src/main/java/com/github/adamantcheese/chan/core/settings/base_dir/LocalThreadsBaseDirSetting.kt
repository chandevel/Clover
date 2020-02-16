package com.github.adamantcheese.chan.core.settings.base_dir

import android.net.Uri
import android.os.Environment
import com.github.adamantcheese.chan.core.manager.ThreadSaveManager
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.core.settings.IntegerSetting
import com.github.adamantcheese.chan.core.settings.SettingProvider
import com.github.adamantcheese.chan.core.settings.StringSetting
import com.github.adamantcheese.chan.utils.AndroidUtils.getApplicationLabel
import com.github.adamantcheese.chan.utils.AndroidUtils.postToEventBus
import java.io.File

class LocalThreadsBaseDirSetting(
        override var activeBaseDir: IntegerSetting,
        override val fileApiBaseDir: StringSetting,
        override val safBaseDir: StringSetting
) : BaseDirectorySetting() {

    constructor(settingProvider: SettingProvider) : this(
            IntegerSetting(
                    settingProvider,
                    "local_threads_active_base_dir_ordinal",
                    0
            ),
            StringSetting(
                    settingProvider,
                    "local_threads_location",
                    getDefaultLocalThreadsLocation()
            ),
            StringSetting(
                    settingProvider,
                    "local_threads_location_uri",
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
        fileApiBaseDir.setSyncNoCheck(getDefaultLocalThreadsLocation())
    }

    override fun resetSafDir() {
        safBaseDir.setSyncNoCheck("")
    }

    override fun resetActiveDir() {
        activeBaseDir.setSync(ActiveBaseDir.FileBaseDir.ordinal)
    }

    companion object {
        fun getDefaultLocalThreadsLocation(): String {
            return (Environment.getExternalStorageDirectory().toString()
                    + File.separator
                    + getApplicationLabel()
                    + File.separator
                    + ThreadSaveManager.SAVED_THREADS_DIR_NAME)
        }
    }
}