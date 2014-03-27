package org.floens.chan.service;

import java.util.List;

import org.floens.chan.ChanApplication;
import org.floens.chan.core.ChanPreferences;
import org.floens.chan.core.model.Pin;
import org.floens.chan.core.watch.WatchNotifier;
import org.floens.chan.utils.Logger;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.Toast;

public class PinnedService extends Service {
    private static final long FOREGROUND_INTERVAL = 10000L;
    private static final long BACKGROUND_INTERVAL = 60000L;

    private static PinnedService instance;
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
            if (!getRunning()) {
                enable(context);
            }
        } else {
            if (getRunning()) {
                disable(context);
            }
        }
    }

    public static void enable(Context context) {
        if (!getRunning()) {
            context.startService(new Intent(context, PinnedService.class));
        }
    }

    public static void disable(Context context) {
        if (getRunning()) {
            context.stopService(new Intent(context, PinnedService.class));
        }
    }

    public static boolean getRunning() {
        return instance != null;
    }

    public static void callOnPinsChanged() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                ChanApplication.getPinnedManager().onPinsChanged();
            }
        });
    }

    @Override
    public void onCreate() {
        super.onCreate();

        watchNotifier = new WatchNotifier(this);

        instance = this;

        startThread();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        instance = null;

        running = false;
        if (loadThread != null) {
            loadThread.interrupt();
            Toast.makeText(getApplicationContext(), "Service thread interrupted", Toast.LENGTH_SHORT).show();
        }
    }

    private void startThread() {
        running = true;

        if (loadThread == null) {
            loadThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (running) {
                        Logger.test("Loadthread iteration");

                        update();

                        if (!running)
                            return;

                        long timeout = activityInForeground ? FOREGROUND_INTERVAL : BACKGROUND_INTERVAL;

                        try {
                            Thread.sleep(timeout);
                        } catch (InterruptedException e) {
                            if (!running) {
                                return;
                            }
                        }
                    }
                }
            });

            loadThread.start();
            Toast.makeText(getApplicationContext(), "Service thread started", Toast.LENGTH_SHORT).show();
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

    private void update() {
        List<Pin> pins = ChanApplication.getPinnedManager().getPins();
        for (Pin pin : pins) {
            pin.updateWatch();
        }

        watchNotifier.update();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
