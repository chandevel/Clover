package com.github.adamantcheese.chan.ui.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.core.manager.WatchManager;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

import javax.inject.Inject;

import static android.provider.Settings.System.DEFAULT_NOTIFICATION_URI;
import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

public class LastPageNotification {
    //random notification ID's, so one notification per thread
    private Random random = new Random();

    @Inject
    NotificationManager notificationManager;
    private String NOTIFICATION_ID_STR = "4";

    @Inject
    WatchManager watchManager;

    public LastPageNotification() {
        inject(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel alert = new NotificationChannel(NOTIFICATION_ID_STR,
                    "Last page notification",
                    NotificationManager.IMPORTANCE_HIGH
            );
            alert.setSound(DEFAULT_NOTIFICATION_URI,
                    new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                            .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
                            .build()
            );
            alert.enableLights(true);
            alert.setLightColor(Color.RED);
            notificationManager.createNotificationChannel(alert);
        }
    }

    public Notification getNotification(int pinId) {
        Intent intent = new Intent(getAppContext(), StartActivity.class);
        intent.setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setFlags(
                        Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                .putExtra("pin_id", pinId);

        PendingIntent pendingIntent =
                PendingIntent.getActivity(getAppContext(), random.nextInt(), intent, PendingIntent.FLAG_ONE_SHOT);
        DateFormat time = SimpleDateFormat.getTimeInstance(DateFormat.SHORT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getAppContext());
        builder.setSmallIcon(R.drawable.ic_stat_notify_alert)
                .setContentTitle(time.format(new Date()) + " - " + getString(R.string.thread_page_limit))
                .setContentText(watchManager.findPinById(pinId).loadable.title)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE)
                .setLights(Color.RED, 1000, 1000)
                .setAutoCancel(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(NOTIFICATION_ID_STR);
        }

        return builder.build();
    }
}
