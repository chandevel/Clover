package com.github.adamantcheese.chan.core.manager

import com.github.adamantcheese.chan.ui.settings.SettingNotificationType
import com.github.adamantcheese.chan.utils.Logger
import io.reactivex.Flowable
import io.reactivex.processors.BehaviorProcessor

class SettingsNotificationManager {
    private val activeNotificationsSubject = BehaviorProcessor.createDefault(ActiveNotifications())

    @Synchronized
    fun notify(notificationType: SettingNotificationType) {
        val prev = activeNotificationsSubject.value!!

        if (prev.notifications.add(notificationType)) {
            Logger.d(TAG, "Added ${notificationType.name} notification")
            activeNotificationsSubject.onNext(prev)
        }
    }

    @Synchronized
    fun getNotificationByPriority(): SettingNotificationType? {
        val notifications = activeNotificationsSubject.value!!

        if (notifications.contains(SettingNotificationType.HasApkUpdate)) {
            return SettingNotificationType.HasApkUpdate
        }

        if (notifications.contains(SettingNotificationType.HasCrashLogs)) {
            return SettingNotificationType.HasCrashLogs
        }

        // Add new notifications here. Don't forget that order matters! The order affects priority.
        // For now "Apk update" has higher priority than "Crash log".

        return null
    }

    @Synchronized
    fun cancel(notificationType: SettingNotificationType) {
        val prev = activeNotificationsSubject.value!!

        if (prev.notifications.remove(notificationType)) {
            Logger.d(TAG, "Removed ${notificationType.name} notification")
            activeNotificationsSubject.onNext(prev)
        }
    }

    fun getSubject(): Flowable<ActiveNotifications> = activeNotificationsSubject

    data class ActiveNotifications(
            val notifications: MutableSet<SettingNotificationType> = mutableSetOf()
    ) {
        fun getOrDefault(notificationType: SettingNotificationType): SettingNotificationType {
            if (!contains(notificationType)) {
                return SettingNotificationType.Default
            }

            return notificationType
        }

        fun contains(notificationType: SettingNotificationType): Boolean {
            return notifications.contains(notificationType)
        }

        fun hasNotifications(): Boolean {
            return notifications.isNotEmpty()
        }
    }

    companion object {
        private const val TAG = "SettingsNotificationManager"
    }
}