package org.floens.chan.service;

import java.util.List;

import org.floens.chan.manager.PinnedManager;
import org.floens.chan.model.Pin;
import org.floens.chan.utils.Logger;
import org.floens.chan.watch.WatchNotifier;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

public class PinnedService extends Service {
    private static final long FOREGROUND_INTERVAL = 10000L;
    private static final long BACKGROUND_INTERVAL = 60000L;

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

    public PinnedService() {
        watchNotifier = new WatchNotifier(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        running = false;
    }

    private void start() {
        running = true;

        if (loadThread == null) {
            loadThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (running) {
                        update();

                        long timeout = activityInForeground ? FOREGROUND_INTERVAL : BACKGROUND_INTERVAL;

                        try {
                            Thread.sleep(timeout);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    loadThread = null;
                }
            });

            loadThread.start();
        }
    }

    private void update() {
        List<Pin> pins = PinnedManager.getInstance().getPins();
        for (Pin pin : pins) {
            pin.updateWatch();
        }

        watchNotifier.update();
    }

    public static void callOnPinsChanged() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                PinnedManager.getInstance().onPinsChanged();
            }
        });
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}




