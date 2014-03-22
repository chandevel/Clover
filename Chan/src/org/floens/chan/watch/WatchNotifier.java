package org.floens.chan.watch;

import java.util.List;

import org.floens.chan.R;
import org.floens.chan.manager.PinnedManager;
import org.floens.chan.model.Pin;

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
        List<Pin> pins = PinnedManager.getInstance().getPins();

        int count = 0;
        int pinCount = 0;

        for (Pin pin : pins) {
            count += pin.getNewPostCount();
            pinCount++;
        }

        showNotification(count + " new posts in " + pinCount + " threads");
//        showNotification("WatchNotifier update");
    }

    private void showNotification(String text) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        Notification.Builder builder = new Notification.Builder(context);
        builder.setTicker(text);
        builder.setContentTitle(text);
        builder.setContentText(text);
        builder.setSmallIcon(R.drawable.ic_stat_notify);
        builder.setOnlyAlertOnce(false);

        nm.notify(NOTIFICATION_ID, builder.getNotification());
    }
}
