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
package com.github.adamantcheese.chan.ui.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.github.adamantcheese.chan.R;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getNotificationManager;
import static com.github.adamantcheese.chan.utils.AndroidUtils.postToEventBus;

public class SavingNotification
        extends Service {
    public static final String DONE_TASKS_KEY = "done_tasks";
    public static final String FAILED_TASKS_KEY = "failed_tasks";
    public static final String TOTAL_TASKS_KEY = "total_tasks";
    private static final String CANCEL_KEY = "cancel";

    private static String NOTIFICATION_ID_STR = "3";
    private int NOTIFICATION_ID = 3;

    public static void setupChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (getNotificationManager().getNotificationChannel(NOTIFICATION_ID_STR) != null) return;
            getNotificationManager().createNotificationChannel(new NotificationChannel(NOTIFICATION_ID_STR,
                    "Save notification",
                    NotificationManager.IMPORTANCE_LOW
            ));
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        setupChannel();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getNotificationManager().cancel(NOTIFICATION_ID);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //start with a blank notification, to ensure it is made within 5 seconds
        startForeground(NOTIFICATION_ID, new NotificationCompat.Builder(this, NOTIFICATION_ID_STR).build());
        if (intent != null && intent.getExtras() != null) {
            Bundle extras = intent.getExtras();
            if (extras.getBoolean(CANCEL_KEY)) {
                postToEventBus(new SavingCancelRequestMessage());
                stopSelf();
                return START_NOT_STICKY;
            } else {
                int doneTasks = extras.getInt(DONE_TASKS_KEY);
                int failedTasks = extras.getInt(FAILED_TASKS_KEY);
                int totalTasks = extras.getInt(TOTAL_TASKS_KEY);
                //replace the notification with the generated one
                startForeground(NOTIFICATION_ID, getNotification(doneTasks, failedTasks, totalTasks));
                return START_STICKY;
            }
        }
        stopSelf();
        return START_NOT_STICKY;
    }

    private Notification getNotification(int done, int failed, int total) {
        Intent intent = new Intent(this, SavingNotification.class);
        intent.putExtra(CANCEL_KEY, true);
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_ID_STR);
        builder.setSmallIcon(R.drawable.ic_stat_notify)
                .setContentTitle(getString(R.string.image_save_notification_downloading))
                .setContentText(getString(R.string.image_save_notification_cancel))
                .setProgress(total, done + failed, false)
                .setContentInfo(done + "/" + failed + "/" + total)
                .setContentIntent(pendingIntent)
                .setOngoing(true);

        return builder.build();
    }

    public static class SavingCancelRequestMessage {}
}
