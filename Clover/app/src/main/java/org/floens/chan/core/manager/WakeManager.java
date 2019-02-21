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
import org.floens.chan.utils.Logger;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

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

    private final AlarmManager alarmManager;
    private final PowerManager powerManager;

    private WakeLock wakeLock;
    private Map<Intent, Wakeable> intentWakeableMap = new HashMap<>();
    private Map<Intent, PendingIntent> intentPendingIntentMap = new HashMap<>();

    private FilterPinManager filterPinManager;

    @Inject
    public WakeManager(FilterPinManager filterPinManager) {
        alarmManager = (AlarmManager) getAppContext().getSystemService(Context.ALARM_SERVICE);
        powerManager = (PowerManager) getAppContext().getSystemService(Context.POWER_SERVICE);
        //basically just to ensure a filter pin manager is made so it can register/unregister itself
        this.filterPinManager = filterPinManager;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            intentWakeableMap.get(intent).onWake(context, intent);
        } catch (NullPointerException e) {
            Logger.wtf(TAG, "Attempted to call onWake on null object, no wakeable object " +
                    "mapped to intent or null object mapped to intent");
            throw e;
        }
    }

    //Register the given intent to be associated with a wakeable, and schedule it to be updated every background interval
    public void registerWakeable(Intent intent, Wakeable wakeable) {
        intentWakeableMap.put(intent, wakeable);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(getAppContext(), 1, intent, 0);
        intentPendingIntentMap.put(intent, pendingIntent);
        int interval = ChanSettings.watchBackgroundInterval.get();
        Logger.d(TAG, "Scheduled for an inexact repeating broadcast receiver with an interval of " + (interval / 1000 / 60) + " minutes");
        alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, interval, interval, pendingIntent);
    }

    //Unregister the given intent and cancel background interval scheduling
    public void unregisterWakeable(Intent intent) {
        intentWakeableMap.remove(intent);
        PendingIntent pendingIntent = intentPendingIntentMap.remove(intent);
        Logger.d(TAG, "Unscheduled the repeating broadcast receiver");
        alarmManager.cancel(pendingIntent);
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
        void onWake(Context context, Intent intent);
    }
}
