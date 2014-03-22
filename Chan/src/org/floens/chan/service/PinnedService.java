package org.floens.chan.service;

import java.util.List;

import org.floens.chan.manager.PinnedManager;
import org.floens.chan.model.Pin;
import org.floens.chan.utils.ChanPreferences;
import org.floens.chan.utils.Logger;
import org.floens.chan.watch.WatchNotifier;

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
    private final WatchNotifier watchNotifier;

    public static void onActivityStart() {
        Logger.test("onActivityStart");
        activityInForeground = true;
    }

    public static void onActivityStop() {
        Logger.test("onActivityStop");
        activityInForeground = false;
    }

    public static void startStopAccordingToSettings(Context context) {
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
                PinnedManager.getInstance().onPinsChanged();
            }
        });
    }

    public PinnedService() {
        watchNotifier = new WatchNotifier(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();

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
                        update();

                        long timeout = activityInForeground ? FOREGROUND_INTERVAL : BACKGROUND_INTERVAL;

                        if (!running) return;

                        try {
                            Thread.sleep(timeout);
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                }
            });

            loadThread.start();
            Toast.makeText(getApplicationContext(), "Service thread started", Toast.LENGTH_SHORT).show();
        }
    }

    private void update() {
        List<Pin> pins = PinnedManager.getInstance().getPins();
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




