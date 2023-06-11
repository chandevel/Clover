package com.github.adamantcheese.chan.features.notifications;

import static android.provider.Settings.System.DEFAULT_NOTIFICATION_URI;
import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getNotificationManager;

import android.app.*;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.core.manager.WatchManager;
import com.github.adamantcheese.chan.core.model.orm.Pin;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.inject.Inject;

public class LastPageNotification
        extends JobService {
    public static final String PIN_ID_KEY = "pin_id";
    public static final String NOTIFY_KEY = "notify";
    private static final String NOTIFICATION_ID_STR = "4";

    @Inject
    WatchManager watchManager;

    public LastPageNotification() {
        inject(this);
    }

    public static void setupChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (getNotificationManager().getNotificationChannel(NOTIFICATION_ID_STR) != null) return;
            NotificationChannel alert = new NotificationChannel(NOTIFICATION_ID_STR,
                    "Last page notification",
                    NotificationManager.IMPORTANCE_HIGH
            );
            alert.setSound(DEFAULT_NOTIFICATION_URI,
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                            .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
                            .build()
            );
            alert.enableLights(true);
            alert.setLightColor(Color.RED);
            getNotificationManager().createNotificationChannel(alert);
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        setupChannel();
        int pinId = params.getExtras().getInt(PIN_ID_KEY);
        boolean notify = params.getExtras().getInt(NOTIFY_KEY) == 1;
        Pin forPin = watchManager.findPinById(pinId);
        try {
            if (notify) {
                getNotificationManager().notify(forPin.loadable.no, getNotification(forPin, pinId));
            } else {
                getNotificationManager().cancel(forPin.loadable.no);
            }
        } catch (Exception ignored) {}
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }

    public Notification getNotification(Pin pin, int pinId) {
        Intent intent = new Intent(getAppContext(), StartActivity.class);
        intent
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                .putExtra("pin_id", pinId);

        PendingIntent pendingIntent = PendingIntent.getActivity(getAppContext(),
                pin.loadable.no,
                intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE
        );
        String time = SimpleDateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(new Date());

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getAppContext(), NOTIFICATION_ID_STR);
        builder
                .setSmallIcon(R.drawable.ic_stat_notify_alert)
                .setContentTitle(time + " - " + getString(R.string.thread_page_limit))
                .setContentText(pin.loadable.title)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE)
                .setLights(Color.RED, 1000, 1000)
                .setAutoCancel(true);

        return builder.build();
    }
}
