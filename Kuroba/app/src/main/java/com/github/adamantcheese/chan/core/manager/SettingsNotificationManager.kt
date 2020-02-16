package com.github.adamantcheese.chan.core.manager

import com.github.adamantcheese.chan.ui.settings.SettingNotificationType
import com.github.adamantcheese.chan.utils.Logger
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.processors.BehaviorProcessor

class SettingsNotificationManager {
    private val notifications: MutableSet<SettingNotificationType> = mutableSetOf()

    /**
     * A reactive stream that is being used to notify observer about [notifications] changes
     * */
    private val activeNotificationsSubject = BehaviorProcessor.createDefault(Unit)

    /**
     * If [notifications] doesn't contain [notificationType] yet, then notifies
     * all observers that there is a new notification
     * */
    @Synchronized
    fun notify(notificationType: SettingNotificationType) {
        if (notifications.add(notificationType)) {
            Logger.d(TAG, "Added ${notificationType.name} notification")
            activeNotificationsSubject.onNext(Unit)
        }
    }

    @Synchronized
    fun getNotificationByPriority(): SettingNotificationType? {
        if (contains(SettingNotificationType.ApkUpdate)) {
            return SettingNotificationType.ApkUpdate
        }

        if (contains(SettingNotificationType.CrashLogs)) {
            return SettingNotificationType.CrashLogs
        }

        // Add new notifications here. Don't forget that order matters! The order affects priority.
        // For now "Apk update" has higher priority than "Crash log".

        return null
    }

    @Synchronized
    fun hasNotifications(notificationType: SettingNotificationType): Boolean {
        return contains(notificationType)
    }

    @Synchronized
    fun notificationsCount(): Int = notifications.count()

    @Synchronized
    fun getOrDefault(notificationType: SettingNotificationType): SettingNotificationType {
        if (!contains(notificationType)) {
            return SettingNotificationType.Default
        }

        return notificationType
    }

    @Synchronized
    fun count(): Int = notifications.size

    @Synchronized
    fun contains(notificationType: SettingNotificationType): Boolean {
        return notifications.contains(notificationType)
    }

    /**
     * If [notifications] contains [notificationType], then notifies all observers that this
     * notification has been canceled
     * */
    @Synchronized
    fun cancel(notificationType: SettingNotificationType) {
        if (notifications.remove(notificationType)) {
            Logger.d(TAG, "Removed ${notificationType.name} notification")
            activeNotificationsSubject.onNext(Unit)
        }
    }

    /**
     * Use this to observe current notification state. Duplicates checks and everything else is done
     * internally so you don't have to worry that you will get the same state twice. All updates
     * come on main thread so there is no need to worry about that as well.
     * */
    fun listenForNotificationUpdates(): Flowable<Unit> = activeNotificationsSubject
            .onBackpressureBuffer()
            .observeOn(AndroidSchedulers.mainThread())

    companion object {
        private const val TAG = "SettingsNotificationManager"
    }
}