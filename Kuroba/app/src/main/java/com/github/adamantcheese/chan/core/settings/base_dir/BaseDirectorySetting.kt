package com.github.adamantcheese.chan.core.settings.base_dir

import android.net.Uri
import com.github.adamantcheese.chan.core.settings.IntegerSetting
import com.github.adamantcheese.chan.core.settings.StringSetting
import com.github.adamantcheese.chan.core.settings.base_dir.BaseDirectorySetting.ActiveBaseDir.Companion.fromIntegerSetting

abstract class BaseDirectorySetting {
    abstract var activeBaseDir: IntegerSetting
    abstract val fileApiBaseDir: StringSetting
    abstract val safBaseDir: StringSetting

    fun isSafDirActive() = fromIntegerSetting(activeBaseDir) == ActiveBaseDir.SAFBaseDir
    fun isFileDirActive() = fromIntegerSetting(activeBaseDir) == ActiveBaseDir.FileBaseDir

    abstract fun setFileBaseDir(dir: String)
    abstract fun setSafBaseDir(dir: Uri)
    abstract fun resetFileDir()
    abstract fun resetSafDir()
    abstract fun resetActiveDir()

    /**
     * DO NOT CHANGE THE ORDER!!!
     * It will break the settings!!!
     * */
    enum class ActiveBaseDir {
        FileBaseDir,
        SAFBaseDir;

        companion object {
            fun fromIntegerSetting(setting: IntegerSetting): ActiveBaseDir {
                val ordinalToFind = setting.get()

                return values()
                        .firstOrNull { value -> value.ordinal == ordinalToFind }
                        ?: throw IllegalStateException("Couldn't find ActiveBaseDir with ordinal $ordinalToFind")
            }
        }
    }
}