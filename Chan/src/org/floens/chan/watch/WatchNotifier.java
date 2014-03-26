package org.floens.chan.watch;

import java.util.List;

import org.floens.chan.R;
import org.floens.chan.activity.BoardActivity;
import org.floens.chan.manager.PinnedManager;
import org.floens.chan.model.Pin;
import org.floens.chan.service.PinnedService;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

public class WatchNotifier {
    private final int NOTIFICATION_ID = 1;

    private final PinnedService pinnedService;
    private final NotificationManager nm;

    public WatchNotifier(PinnedService pinnedService) {
        this.pinnedService = pinnedService;
        nm = (NotificationManager) pinnedService.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    public void update() {
        List<Pin> pins = PinnedManager.getInstance().getPins();

        int count = 0;
        int pinCount = 0;

        for (Pin pin : pins) {
            count += pin.getNewPostCount();
            pinCount++;
        }

        if (!PinnedService.getActivityInForeground()) {
            showNotification(count + " new posts in " + pinCount + " threads\n" + System.currentTimeMillis());
        }
    }

    public void onForegroundChanged() {
        if (PinnedService.getActivityInForeground()) {
            nm.cancel(NOTIFICATION_ID);
        } else {

        }
    }

    @SuppressWarnings("deprecation")
    private void showNotification(String text) {
        Intent intent = new Intent(pinnedService, BoardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pending = PendingIntent.getActivity(pinnedService, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder builder = new Notification.Builder(pinnedService);
        builder.setContentIntent(pending);

        builder.setTicker(text);
        builder.setContentTitle(text);
        builder.setContentText(text);
        builder.setSmallIcon(R.drawable.ic_stat_notify);

        nm.notify(NOTIFICATION_ID, builder.getNotification());
    }
}





