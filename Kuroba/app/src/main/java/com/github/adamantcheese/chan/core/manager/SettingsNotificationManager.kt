package com.github.adamantcheese.chan.core.manager

import android.annotation.SuppressLint
import com.github.adamantcheese.chan.ui.settings.SettingNotificationType
import io.reactivex.Flowable
import io.reactivex.processors.BehaviorProcessor

class SettingsNotificationManager {
    private val activeNotificationsSubject = BehaviorProcessor.createDefault(ActiveNotifications())

    init {
        initSubject()
    }

    /**
     * This is a singleton class so we don't care about the disposable since we will never should
     * dispose of this stream
     * */
    @SuppressLint("CheckResult")
    private fun initSubject() {
        activeNotificationsSubject
                .subscribe({
                    // Do nothing
                }, { error ->
                    throw RuntimeException("$TAG Uncaught exception!!! " +
                            "activeNotificationsSubject is in error state now!!! " +
                            "This should not happen!!!, original error = " + error.message)
                }, {
                    throw RuntimeException(
                            "$TAG activeNotificationsSubject stream has completed!!! " +
                                    "This should not happen!!!"
                    )
                })
    }

    @Synchronized
    fun notify(notificationType: SettingNotificationType) {
        val prev = activeNotificationsSubject.value!!

        if (prev.notifications.add(notificationType)) {
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
            activeNotificationsSubject.onNext(prev)
        }
    }

    fun getSubject(): Flowable<ActiveNotifications> = activeNotificationsSubject
            // Do not emit anything if previous ActiveNotifications are the same as new ones
            .distinctUntilChanged()

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