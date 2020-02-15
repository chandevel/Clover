package com.github.adamantcheese.chan.ui.settings

import androidx.annotation.ColorInt
import com.github.adamantcheese.chan.R

enum class SettingNotificationType(@ColorInt val notificationIconTintColor: Int) {
    Default(android.R.color.transparent),
    HasApkUpdate(R.color.new_apk_update_icon_color),
    HasCrashLogs(R.color.new_crash_log_icon_color)
}