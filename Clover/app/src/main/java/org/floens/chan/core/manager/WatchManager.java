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

import android.content.Context;
import android.content.Intent;

import org.floens.chan.ChanApplication;
import org.floens.chan.core.ChanPreferences;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Pin;
import org.floens.chan.ui.service.WatchNotifier;
import org.floens.chan.utils.Logger;
import org.floens.chan.utils.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class WatchManager implements ChanApplication.ForegroundChangedListener {
    private static final String TAG = "WatchManager";
    private static final int FOREGROUND_TIME = 10;

    private final Context context;
    private final List<PinListener> listeners = new ArrayList<>();
    private final List<Pin> pins;
    private ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private PendingTimer pendingTimer;

    public WatchManager(Context context) {
        this.context = context;

        pins = ChanApplication.getDatabaseManager().getPinned();

        ChanApplication.getInstance().addForegroundChangedListener(this);

        updateTimerState();
        updateNotificationServiceState();
    }

    /**
     * Look for a pin that has an loadable that is equal to the supplied
     * loadable.
     *
     * @param other
     * @return The pin whose loadable is equal to the supplied loadable, or null
     * if no pin was found.
     */
    public Pin findPinByLoadable(Loadable other) {
        for (Pin pin : pins) {
            if (pin.loadable.equals(other)) {
                return pin;
            }
        }

        return null;
    }

    public Pin findPinById(int id) {
        for (Pin pin : pins) {
            if (pin.id == id) {
                return pin;
            }
        }

        return null;
    }

    public List<Pin> getPins() {
        return pins;
    }

    public List<Pin> getWatchingPins() {
        if (ChanPreferences.getWatchEnabled()) {
            List<Pin> l = new ArrayList<>();

            for (Pin p : pins) {
                if (p.watching)
                    l.add(p);
            }

            return l;
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Add a pin
     *
     * @param pin
     * @return true if it was added, false if it wasn't (duplicated)
     */
    public boolean addPin(Pin pin) {
        // No duplicates
        for (Pin e : pins) {
            if (e.loadable.equals(pin.loadable)) {
                return false;
            }
        }

        pins.add(pin);
        ChanApplication.getDatabaseManager().addPin(pin);

        onPinsChanged();

        return true;
    }

    /**
     * Remove a pin
     *
     * @param pin
     */
    public void removePin(Pin pin) {
        pins.remove(pin);
        ChanApplication.getDatabaseManager().removePin(pin);
        pin.destroyWatcher();

        onPinsChanged();
    }

    /**
     * Update the pin in the database
     *
     * @param pin
     */
    public void updatePin(Pin pin) {
        ChanApplication.getDatabaseManager().updatePin(pin);

        onPinsChanged();
    }

    /**
     * Updates all the pins to the database. This will run in a new thread
     * because it can be an expensive operation. (this will be an huge headache
     * later on when we get concurrent problems)
     */
    public void updateDatabase() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                ChanApplication.getDatabaseManager().updatePins(pins);
            }
        }).start();
    }

    public void onPinViewed(Pin pin) {
        pin.getPinWatcher().onViewed();

        onPinsChanged();
    }

    public void addPinListener(PinListener l) {
        listeners.add(l);
    }

    public void removePinListener(PinListener l) {
        listeners.remove(l);
    }

    public void onPinsChanged() {
        for (PinListener l : listeners) {
            l.onPinsChanged();
        }

        updateTimerState();
        updateNotificationServiceState();
    }

    public void pausePins() {
        List<Pin> watchingPins = getWatchingPins();
        for (Pin pin : watchingPins) {
            pin.watching = false;
        }

        onPinsChanged();
        updateDatabase();
    }

    public void onWatchEnabledChanged(boolean watchEnabled) {
        updateNotificationServiceState(watchEnabled, getWatchBackgroundEnabled());
        updateTimerState(watchEnabled, getWatchBackgroundEnabled());
    }

    public void onBackgroundWatchingChanged(boolean backgroundEnabled) {
        updateNotificationServiceState(getWatchEnabled(), backgroundEnabled);
        updateTimerState(getWatchEnabled(), backgroundEnabled);
    }

    @Override
    public void onForegroundChanged(final boolean foreground) {
        updateNotificationServiceState();
        updateTimerState();
    }

    public boolean getWatchEnabled() {
        // getWatchingPins returns an empty list when ChanPreferences.getWatchEnabled() is false
        return getWatchingPins().size() > 0;
    }

    public boolean getWatchBackgroundEnabled() {
        return ChanPreferences.getWatchBackgroundEnabled();
    }

    private void updateNotificationServiceState() {
        updateNotificationServiceState(getWatchEnabled(), getWatchBackgroundEnabled());
    }

    private void updateNotificationServiceState(boolean watchEnabled, boolean backgroundEnabled) {
        if (watchEnabled && backgroundEnabled) {
            // Also calls onStartCommand, which updates the notification
            context.startService(new Intent(context, WatchNotifier.class));
        } else {
            context.stopService(new Intent(context, WatchNotifier.class));
        }
    }

    private void updateTimerState() {
        updateTimerState(getWatchEnabled(), getWatchBackgroundEnabled());
    }

    private void updateTimerState(boolean watchEnabled, boolean backgroundEnabled) {
        if (watchEnabled) {
            if (ChanApplication.getInstance().getApplicationInForeground()) {
                setTimer(FOREGROUND_TIME);
            } else {
                if (backgroundEnabled) {
                    setTimer(ChanPreferences.getWatchBackgroundTimeout());
                } else {
                    if (pendingTimer != null) {
                        pendingTimer.cancel();
                        pendingTimer = null;
                        Logger.d(TAG, "Canceled timer");
                    }
                }
            }
        } else {
            if (pendingTimer != null) {
                pendingTimer.cancel();
                pendingTimer = null;
                Logger.d(TAG, "Canceled timer");
            }
        }
    }

    private void setTimer(int time) {
        if (pendingTimer != null && pendingTimer.time == time) {
            return;
        }

        if (pendingTimer != null) {
            pendingTimer.cancel();
            pendingTimer = null;
            Logger.d(TAG, "Canceled timer");
        }

        ScheduledFuture scheduledFuture = executor.schedule(new Runnable() {
            @Override
            public void run() {
                Utils.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        timerFired();
                    }
                });
            }
        }, time, TimeUnit.SECONDS);
        pendingTimer = new PendingTimer(scheduledFuture, time);
        Logger.d(TAG, "Timer firing in " + time + " seconds");
    }

    private void timerFired() {
        Logger.d(TAG, "Timer fired");
        pendingTimer = null;

        for (Pin pin : getWatchingPins()) {
            pin.updateWatch();
        }

        updateTimerState();
    }

    public static interface PinListener {
        public void onPinsChanged();
    }

    private static class PendingTimer {
        public ScheduledFuture scheduledFuture;
        public int time;

        public PendingTimer(ScheduledFuture scheduledFuture, int time) {
            this.scheduledFuture = scheduledFuture;
            this.time = time;
        }

        public void cancel() {
            scheduledFuture.cancel(false);
        }
    }
}
