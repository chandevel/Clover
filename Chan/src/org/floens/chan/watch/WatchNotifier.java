package org.floens.chan.watch;

import org.floens.chan.R;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;

public class WatchNotifier {
    private final int NOTIFICATION_ID = 1;

    private final Context context;

    public WatchNotifier(Context context) {
        this.context = context;
    }

    public void update() {
        showNotification("Update!");
    }

    private void showNotification(String text) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        Notification.Builder builder = new Notification.Builder(context);
        builder.setTicker(text);
        builder.setContentTitle(text);
        builder.setContentText(text);
        builder.setSmallIcon(R.drawable.ic_stat_notify);

        nm.notify(NOTIFICATION_ID, builder.getNotification());
    }
}
