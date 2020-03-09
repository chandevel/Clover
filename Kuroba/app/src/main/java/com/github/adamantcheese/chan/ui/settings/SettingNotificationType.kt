package com.github.adamantcheese.chan.ui.settings

import androidx.annotation.ColorInt
import com.github.adamantcheese.chan.R

enum class SettingNotificationType(@ColorInt val notificationIconTintColor: Int) {
    /**
     * No active notification
     * */
    Default(android.R.color.transparent),

    /**
     * New apk update is available notification
     * */
    ApkUpdate(R.color.new_apk_update_icon_color),

    /**
     * There is at least one crash log available notification
     * */
    CrashLog(R.color.new_crash_log_icon_color)
}