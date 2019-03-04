package org.floens.chan.ui.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import org.floens.chan.R;
import org.floens.chan.core.manager.WatchManager;
import org.floens.chan.core.model.orm.Pin;
import org.floens.chan.ui.activity.BoardActivity;

import java.util.Random;

import javax.inject.Inject;

import static org.floens.chan.Chan.inject;
import static org.floens.chan.utils.AndroidUtils.getAppContext;

public class LastPageNotification extends Service {
    //random notification ID's, so one notification per thread
    private Random random = new Random();

    @Inject
    WatchManager watchManager;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        inject(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getExtras() != null) {
            Bundle extras = intent.getExtras();
            int pinId = extras.getInt("pin_id");

            //NotificationManagerCompat.from(getAppContext()).notify(random.nextInt(), getNotification(pinId));
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).notify(random.nextInt(), getNotification(pinId));
        }
        return START_STICKY;
    }

    private Notification getNotification(int pinId) {
        Pin pin = watchManager.findPinById(pinId);

        Intent intent = new Intent(this, BoardActivity.class);
        intent.setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                .putExtra("pin_id", pinId);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, random.nextInt(), intent, PendingIntent.FLAG_ONE_SHOT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setSmallIcon(R.drawable.ic_stat_notify_alert)
                .setContentTitle(getString(R.string.thread_page_limit))
                .setContentText(pin.loadable.title)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setTimeoutAfter(10 * 1000 * 60)
                .setAutoCancel(true);

        return builder.build();
    }
}
