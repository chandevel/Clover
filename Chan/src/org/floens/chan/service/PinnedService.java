package org.floens.chan.service;

import java.util.List;

import org.floens.chan.R;
import org.floens.chan.manager.PinnedManager;
import org.floens.chan.model.Pin;
import org.floens.chan.utils.Logger;

import android.app.Notification;
import android.app.NotificationManager;
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

    public static void onActivityStart() {
        Logger.test("onActivityStart");
        activityInForeground = true;
    }

    public static void onActivityStop() {
        Logger.test("onActivityStop");
        activityInForeground = false;
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
        loadThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }

                while (running) {
                    doUpdates();

                    long timeout = activityInForeground ? FOREGROUND_INTERVAL : BACKGROUND_INTERVAL;

                    try {
                        Thread.sleep(timeout);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        loadThread.start();
    }

    private void doUpdates() {
        List<Pin> pins = PinnedManager.getInstance().getPins();
        for (Pin pin : pins) {
            pin.updateWatch();
        }
    }

    public static void callOnPinsChanged() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                PinnedManager.getInstance().onPinsChanged();
            }
        });
    }

    @SuppressWarnings("deprecation")
    private void showNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        Notification.Builder builder = new Notification.Builder(this);
        builder.setTicker(text);
        builder.setContentTitle(text);
        builder.setContentText(text);
        builder.setSmallIcon(R.drawable.ic_stat_notify);

        nm.notify(1, builder.getNotification());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}




