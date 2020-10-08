package com.github.adamantcheese.chan.core.manager

import android.annotation.SuppressLint
import androidx.annotation.ColorInt
import com.github.adamantcheese.chan.R
import org.greenrobot.eventbus.EventBus

object SettingsNotificationManager {
    @JvmStatic
    fun postNotification(notification: SettingNotification) {
        val currentType = EventBus.getDefault().getStickyEvent(SettingNotification::class.java)
        val newType = currentType?.addType(notification) ?: notification
        EventBus.getDefault().postSticky(newType)
    }

    @JvmStatic
    fun cancelNotification(notification: SettingNotification) {
        val currentType = EventBus.getDefault().getStickyEvent(SettingNotification::class.java)
        val newType = currentType?.removeType(notification) ?: SettingNotification.Default
        EventBus.getDefault().postSticky(newType)
    }

    @SuppressLint("ResourceAsColor")
    enum class SettingNotification(var typeFlags: Int, @param:ColorInt var notificationIconTintColor: Int) {
        /**
         * No active notification
         */
        Default(0, android.R.color.transparent),

        /**
         * New apk update is available notification
         */
        ApkUpdate(1, R.color.md_green_500),

        /**
         * There is at least one crash log available notification
         */
        CrashLog(2, R.color.md_red_400),

        /**
         * There are both ApkUpdate and CrashLog notifications; use crashlog color
         */
        Both(3, R.color.md_red_400);

        fun addType(add: SettingNotification): SettingNotification {
            return fromTypeInt(typeFlags or add.typeFlags)
        }

        fun removeType(remove: SettingNotification): SettingNotification {
            return fromTypeInt(typeFlags and remove.typeFlags.inv())
        }

        companion object {
            private fun fromTypeInt(typeFlags: Int): SettingNotification {
                for (n in values()) {
                    if (n.typeFlags == typeFlags) {
                        return n
                    }
                }
                return Default
            }
        }

    }
}