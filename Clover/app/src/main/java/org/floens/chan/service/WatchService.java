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
package org.floens.chan.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import org.floens.chan.ChanApplication;
import org.floens.chan.core.ChanPreferences;
import org.floens.chan.core.model.Pin;
import org.floens.chan.core.watch.WatchNotifier;
import org.floens.chan.utils.Logger;

import java.util.List;

public class WatchService extends Service {
    private static final String TAG = "WatchService";

    private static final long FOREGROUND_INTERVAL = 10000L;

    private static WatchService instance;
    private static boolean activityInForeground = false;

    private Thread loadThread;
    private boolean running = true;
    private WatchNotifier watchNotifier;

    public static void onActivityStart() {
        activityInForeground = true;
        if (instance != null) {
            instance.onActivityInForeground();
        }
    }

    public static void onActivityStop() {
        activityInForeground = false;
        if (instance != null) {
            instance.onActivityInBackground();
        }
    }

    public static boolean getActivityInForeground() {
        return activityInForeground;
    }

    public static void updateRunningState(Context context) {
        if (ChanPreferences.getWatchEnabled()) {
            if (ChanApplication.getPinnedManager().getWatchingPins().size() == 0) {
                if (getRunning()) {
                    disable(context);
                }
            } else {
                if (!getRunning()) {
                    enable(context);
                }
            }
        } else {
            if (getRunning()) {
                disable(context);
            }
        }
    }

    public static void enable(Context context) {
        if (!getRunning()) {
            context.startService(new Intent(context, WatchService.class));
        }
    }

    public static void disable(Context context) {
        if (getRunning()) {
            context.stopService(new Intent(context, WatchService.class));

            List<Pin> pins = ChanApplication.getPinnedManager().getWatchingPins();
            for (Pin pin : pins) {
                pin.destroyWatcher();
            }

            instance.watchNotifier.destroy();
        }
    }

    public static boolean getRunning() {
        return instance != null;
    }

    public static void onPinWatcherResult() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                ChanApplication.getPinnedManager().onPinsChanged();
                if (instance != null) {
                    instance.watchNotifier.update();
                }
            }
        });
    }

    @Override
    public void onCreate() {
        super.onCreate();

        instance = this;

        watchNotifier = new WatchNotifier(this);

        Logger.i(TAG, "WatchService created");

        startThread();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        instance = null;

        running = false;
        if (loadThread != null) {
            loadThread.interrupt();
        }

        Logger.i(TAG, "WatchService destroyed");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getExtras() != null && intent.getExtras().getBoolean("pause_pins", false)) {
            if (watchNotifier != null) {
                watchNotifier.onPausePinsClicked();
            }
        }

        return START_STICKY;
    }

    private void startThread() {
        running = true;

        if (loadThread == null) {
            loadThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (running) {
                        Logger.d(TAG, "Loadthread loop");

                        update();

                        if (!running)
                            return;

                        long timeout = activityInForeground ? FOREGROUND_INTERVAL : getBackgroundTimeout();
                        if (timeout < 0L) {
                            Logger.d(TAG, "Waiting for interrupt...");
                            try {
                                Object o = new Object();
                                synchronized (o) {
                                    o.wait();
                                }
                            } catch (InterruptedException e) {
                                Logger.d(TAG, "Interrupted!");
                            }
                        } else {
                            try {
                                Thread.sleep(timeout);
                            } catch (InterruptedException e) {
                                Logger.d(TAG, "Interrupted!");
                            }
                        }
                    }
                }
            });

            loadThread.start();
        }
    }

    private void onActivityInForeground() {
        if (loadThread != null) {
            loadThread.interrupt();
        }
        watchNotifier.onForegroundChanged();
    }

    private void onActivityInBackground() {
        watchNotifier.onForegroundChanged();
    }

    /**
     * Returns the sleep time the user specified for background iteration
     *
     * @return the sleep time in ms, or -1 if background reloading is disabled
     */
    private long getBackgroundTimeout() {
        if (ChanPreferences.getWatchBackgroundEnabled()) {
            return ChanPreferences.getWatchBackgroundTimeout();
        } else {
            return -1;
        }
    }

    private void update() {
        List<Pin> pins = ChanApplication.getPinnedManager().getWatchingPins();
        for (Pin pin : pins) {
            pin.updateWatch();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
