package com.github.adamantcheese.chan.core.settings

import com.github.adamantcheese.chan.utils.AndroidUtils
import com.github.adamantcheese.chan.utils.Logger


object ChanState {
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
    open fun hasNewApkUpdate() = hasNewApkUpdate
}