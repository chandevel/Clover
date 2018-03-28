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
package org.floens.chan.ui.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;

import org.floens.chan.R;

import de.greenrobot.event.EventBus;

import static org.floens.chan.utils.AndroidUtils.getAppContext;

public class SavingNotification extends Service {
    public static final String DONE_TASKS_KEY = "done_tasks";
    public static final String TOTAL_TASKS_KEY = "total_tasks";
    private static final String CANCEL_KEY = "cancel";

    private static final int NOTIFICATION_ID = 2;

    private NotificationManager notificationManager;

    private boolean inForeground = false;
    private int doneTasks;
    private int totalTasks;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        notificationManager.cancel(NOTIFICATION_ID);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getExtras() != null) {
            Bundle extras = intent.getExtras();

            if (extras.getBoolean(CANCEL_KEY)) {
                EventBus.getDefault().post(new SavingCancelRequestMessage());
            } else {
                doneTasks = extras.getInt(DONE_TASKS_KEY);
                totalTasks = extras.getInt(TOTAL_TASKS_KEY);

                if (!inForeground) {
                    startForeground(NOTIFICATION_ID, getNotification());
                    inForeground = true;
                } else {
                    notificationManager.notify(NOTIFICATION_ID, getNotification());
                }
            }
        }

        return START_STICKY;
    }

    private Notification getNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getAppContext());
        builder.setSmallIcon(R.drawable.ic_stat_notify);
        builder.setContentTitle(getString(R.string.image_save_notification_downloading));
        builder.setContentText(getString(R.string.image_save_notification_cancel));
        builder.setProgress(totalTasks, doneTasks, false);
        builder.setContentInfo(doneTasks + "/" + totalTasks);

        Intent intent = new Intent(this, SavingNotification.class);
        intent.putExtra(CANCEL_KEY, true);
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(pendingIntent);

        return builder.build();
    }

    public static class SavingCancelRequestMessage {
    }
}
