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

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.Chan;
import com.github.adamantcheese.chan.core.receiver.WakeUpdateReceiver;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.chan.utils.StringUtils;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * Deals with background alarms specifically. No foreground stuff here.
 */
public class WakeManager {
    private static WakeManager instance;
    private final Map<Object, WakeLock> wakeLocks = new HashMap<>();

    private final AlarmManager alarmManager;
    private final PowerManager powerManager;

    private final Set<Wakeable> wakeableSet = new HashSet<>();
    private final PendingIntent pendingIntent =
            PendingIntent.getBroadcast(getAppContext(), 1, new Intent(getAppContext(), WakeUpdateReceiver.class), 0);
    // allow the wake manager to run at construction time; this will be used in the first onEvent call
    private long lastBackgroundUpdateTime = System.currentTimeMillis() - ChanSettings.watchBackgroundInterval.get();
    private boolean alarmRunning;

    public static WakeManager getInstance() {
        if (instance == null) {
            instance = new WakeManager();
        }

        return instance;
    }

    private WakeManager() {
        alarmManager = (AlarmManager) getAppContext().getSystemService(Context.ALARM_SERVICE);
        powerManager = (PowerManager) getAppContext().getSystemService(Context.POWER_SERVICE);

        EventBus.getDefault().register(this);

        if (ChanSettings.watchBackground.get() && ChanSettings.watchEnabled.get()) {
            startAlarm();
        }
    }

    public void onBroadcastReceived(boolean doCheck) {
        long currentTime = System.currentTimeMillis();
        Logger.d(this, "Alarm trigger @ " + StringUtils.getTimeDefaultLocale(currentTime));
        if (doCheck && currentTime - lastBackgroundUpdateTime < ChanSettings.watchBackgroundInterval.get()) {
            Logger.d(this, "Early; previous @ " + StringUtils.getTimeDefaultLocale(lastBackgroundUpdateTime));
        } else {
            lastBackgroundUpdateTime = currentTime;
            for (Wakeable wakeable : wakeableSet) {
                wakeable.onWake();
            }
        }
    }

    @Subscribe
    public void onEvent(ChanSettings.SettingChanged<?> settingChanged) {
        if (settingChanged.setting == ChanSettings.watchBackground
                || settingChanged.setting == ChanSettings.watchEnabled) {
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

    // Called when the app changes foreground state
    @Subscribe
    public void onEvent(Chan.ForegroundChangedMessage message) {
        if (message.inForeground) onBroadcastReceived(true);
    }

    public void registerWakeable(Wakeable wakeable) {
        boolean needsStart = wakeableSet.isEmpty();
        Logger.d(this, "Registered " + wakeable.getClass().toString());
        wakeableSet.add(wakeable);
        if (!alarmRunning && needsStart) {
            startAlarm();
        }
    }

    public void unregisterWakeable(Wakeable wakeable) {
        Logger.d(this, "Unregistered " + wakeable.getClass().toString());
        wakeableSet.remove(wakeable);
        if (alarmRunning && wakeableSet.isEmpty()) {
            stopAlarm();
        }
    }

    private void startAlarm() {
        if (!alarmRunning) {
            alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    0,
                    ChanSettings.watchBackgroundInterval.get(),
                    pendingIntent
            );
            Logger.i(this,
                    "Started background alarm with an interval of "
                            + MILLISECONDS.toMinutes(ChanSettings.watchBackgroundInterval.get()) + " minutes"
            );
            alarmRunning = true;
        }
    }

    private void stopAlarm() {
        if (alarmRunning) {
            alarmManager.cancel(pendingIntent);
            Logger.i(this, "Stopped background alarm");
            alarmRunning = false;
        }
    }

    /**
     * Want a wake lock? Request true. If a lock already exists it will be freed before acquiring a new one.
     * Don't need it any more? Request false.
     * <p>
     * Do be warned that wakelocks in this method aren't reference counted, so you can manage true a bunch but managed false once and the wakelock is gone.
     * The locker object is to prevent duplicate wakelocks from being generated for the same object.
     */
    public void manageLock(boolean lock, Object locker) {
        WakeLock wakeLock = wakeLocks.get(locker);
        if (lock) {
            if (wakeLock != null) {
                Logger.e(this, "Wakelock not null while trying to acquire one");
                wakeLock.release();
                wakeLocks.remove(locker);
            }

            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    BuildConfig.APP_LABEL + ":WakeManagerUpdateLock:" + Object.class.getSimpleName()
            );
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(MINUTES.toMillis(1));
            wakeLocks.put(locker, wakeLock);
        } else {
            if (wakeLock == null) {
                Logger.e(this, "Wakelock null while trying to release it");
            } else {
                wakeLock.release();
                wakeLocks.remove(locker);
            }
        }
    }

    public interface Wakeable {
        void onWake();
    }
}
