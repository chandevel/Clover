/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.floens.chan.core.manager;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.core.settings.IntegerSetting;
import org.floens.chan.utils.Logger;

import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import de.greenrobot.event.EventBus;

import static org.floens.chan.utils.AndroidUtils.getAppContext;


/**
 * Deals with background alarms specifically. No foreground stuff here.
 */
@Singleton
public class WakeManager extends BroadcastReceiver {
    private static final String TAG = "WakeManager";

    public static final int BACKGROUND_INTERVAL = 15 * 60 * 1000;

    private static final String WAKELOCK_TAG = "Clover:WatchManagerUpdateLock";
    private static final long WAKELOCK_MAX_TIME = 60 * 1000;
    private WakeLock wakeLock;

    private final AlarmManager alarmManager;
    private final PowerManager powerManager;

    private Set<Wakeable> wakeableSet = new HashSet<>();
    public final Intent intent = new Intent("org.floens.chan.intent.action.WAKE_MANAGER_UPDATE");
    private long lastBackgroundUpdateTime;

    @Inject
    public WakeManager() {
        alarmManager = (AlarmManager) getAppContext().getSystemService(Context.ALARM_SERVICE);
        powerManager = (PowerManager) getAppContext().getSystemService(Context.POWER_SERVICE);

        EventBus.getDefault().register(this);

        if(ChanSettings.watchBackground.get() && ChanSettings.watchEnabled.get()) {
            Logger.d(TAG, "Starting background alarm");
            startAlarm();
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Logger.d(TAG, "Got message to wake all wakeables");
        if (System.currentTimeMillis() - lastBackgroundUpdateTime < 90 * 1000) { //wait 90 seconds between background updates
            Logger.w(TAG, "Background update broadcast ignored because it was requested too soon");
        } else {
            lastBackgroundUpdateTime = System.currentTimeMillis();
            for (Wakeable wakeable : wakeableSet) {
                wakeable.onWake();
            }
        }
    }

    public void onEvent(ChanSettings.SettingChanged<?> settingChanged) {
        if (settingChanged.setting == ChanSettings.watchBackground || settingChanged.setting == ChanSettings.watchEnabled) {
            if(ChanSettings.watchBackground.get() && ChanSettings.watchEnabled.get()) {
                Logger.d(TAG, "Starting background alarm");
                startAlarm();
            } else {
                Logger.d(TAG, "Stopping background alarm");
                stopAlarm();
            }
        } else if (settingChanged.setting == ChanSettings.watchBackgroundInterval) {
            stopAlarm();
            startAlarm();
        }
    }

    public void registerWakeable(Wakeable wakeable) {
        wakeableSet.add(wakeable);
    }

    public void unregisterWakeable(Wakeable wakeable) {
        wakeableSet.remove(wakeable);
    }

    private void startAlarm() {
        int interval = ChanSettings.watchBackgroundInterval.get();
        PendingIntent pendingIntent = PendingIntent.getBroadcast(getAppContext(), 1, intent, 0);
        alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, interval, interval, pendingIntent);
        Logger.d(TAG, "Scheduled with an interval of " + (interval / 1000 / 60) + " minutes");
    }

    private void stopAlarm() {
        PendingIntent pendingIntent = PendingIntent.getBroadcast(getAppContext(), 1, intent, 0);
        alarmManager.cancel(pendingIntent);
        Logger.d(TAG, "Unscheduled the repeating broadcast receiver");
    }

    //Want a lock? Request true. If a lock already exists it will be freed before acquiring a new one.
    //Don't need it any more? Request false.
    public void manageLock(boolean lock) {
        if (lock) {
            if (wakeLock != null) {
                Logger.e(TAG, "Wakelock not null while trying to acquire one");
                if (wakeLock.isHeld()) {
                    wakeLock.release();
                }
                wakeLock = null;
            }
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG);
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(WAKELOCK_MAX_TIME);
        } else {
            if (wakeLock == null) {
                Logger.e(TAG, "Wakelock null while trying to release it");
            } else {
                if (wakeLock.isHeld()) {
                    wakeLock.release();
                }
                wakeLock = null;
            }
        }
    }

    public interface Wakeable {
        void onWake();
    }
}
