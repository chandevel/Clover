package com.github.adamantcheese.chan.core.settings.state

import com.github.adamantcheese.chan.core.settings.BooleanSetting
import com.github.adamantcheese.chan.core.settings.SharedPreferencesSettingProvider
import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.adamantcheese.chan.utils.Logger


object PersistableChanState {
    private const val TAG = "ChanState"
    private val hasNewApkUpdate: BooleanSetting

    init {
        try {
            val p = SharedPreferencesSettingProvider(AndroidUtils.getAppState())
            hasNewApkUpdate = BooleanSetting(p, "has_new_apk_update", false)
        } catch (error: Throwable) {
            Logger.e(TAG, "Error while initializing the state", error)
            throw error
        }
    }

    // Why? So it can be mocked in tests.
    @JvmStatic
    fun getHasNewApkUpdate(): Boolean = hasNewApkUpdate.get()

    @JvmStatic
    fun setHasNewApkUpdate(value: Boolean) = hasNewApkUpdate.set(value)

    @JvmStatic
    fun setHasNewApkUpdateSync(value: Boolean) = hasNewApkUpdate.setSync(value)
}