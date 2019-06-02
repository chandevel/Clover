/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
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
package com.github.adamantcheese.chan.core.manager;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

import com.github.adamantcheese.chan.core.receiver.WakeUpdateReceiver;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.utils.Logger;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;

/**
 * Deals with background alarms specifically. No foreground stuff here.
 */
public class WakeManager {
    private static final String TAG = "WakeManager";

    private WakeLock wakeLock;

    private final AlarmManager alarmManager;
    private final PowerManager powerManager;

    private List<Wakeable> wakeableSet = new ArrayList<>();
    public static final Intent intent = new Intent(getAppContext(), WakeUpdateReceiver.class);
    private PendingIntent pendingIntent = PendingIntent.getBroadcast(getAppContext(), 1, intent, 0);
    private long lastBackgroundUpdateTime;

    @Inject
    public WakeManager() {
        alarmManager = (AlarmManager) getAppContext().getSystemService(Context.ALARM_SERVICE);
        powerManager = (PowerManager) getAppContext().getSystemService(Context.POWER_SERVICE);

        EventBus.getDefault().register(this);

        if (ChanSettings.watchBackground.get() && ChanSettings.watchEnabled.get()) {
            startAlarm();
        }
    }

    public void onBroadcastReceived() {
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
            if (ChanSettings.watchBackground.get() && ChanSettings.watchEnabled.get()) {
                startAlarm();
            } else {
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
        alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, 0, ChanSettings.watchBackgroundInterval.get(), pendingIntent);
        Logger.d(TAG, "Started background alarm with an interval of " + (ChanSettings.watchBackgroundInterval.get() / 1000 / 60) + " minutes");
    }

    private void stopAlarm() {
        alarmManager.cancel(pendingIntent);
        Logger.d(TAG, "Stopped background alarm");
    }

    /**
     * Want a wake lock? Request true. If a lock already exists it will be freed before acquiring a new one.
     * Don't need it any more? Request false.
     */
    public void manageLock(boolean lock) {
        if (lock) {
            if (wakeLock != null) {
                Logger.e(TAG, "Wakelock not null while trying to acquire one");
                if (wakeLock.isHeld()) {
                    wakeLock.release();
                }
                wakeLock = null;
            }
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Kuroba:WakeManagerUpdateLock");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(60 * 1000); //60 seconds max
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
