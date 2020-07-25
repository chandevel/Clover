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
    ApkUpdate(R.color.md_green_500),

    /**
     * There is at least one crash log available notification
     * */
    CrashLog(R.color.md_red_400)
}