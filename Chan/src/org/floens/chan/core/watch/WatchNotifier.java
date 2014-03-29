package org.floens.chan.core.watch;

import java.util.ArrayList;
import java.util.List;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.core.model.Pin;
import org.floens.chan.service.WatchService;
import org.floens.chan.ui.activity.BoardActivity;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

public class WatchNotifier {
    private final int NOTIFICATION_ID = 1;

    private final WatchService pinnedService;
    private final NotificationManager nm;

    private int lastNewPosts;

    public WatchNotifier(WatchService pinnedService) {
        this.pinnedService = pinnedService;
        nm = (NotificationManager) pinnedService.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    public void update() {
        if (!WatchService.getActivityInForeground()) {
            prepareNotification();
        }
    }

    public void onForegroundChanged() {
        if (WatchService.getActivityInForeground()) {
            nm.cancel(NOTIFICATION_ID);
        } else {

        }
    }

    private void prepareNotification() {
        List<Pin> pins = ChanApplication.getPinnedManager().getWatchingPins();

        int newPosts = 0;
        List<Pin> pinsWithNewPosts = new ArrayList<Pin>();

        for (Pin pin : pins) {
            if (pin.getNewPostCount() > 0) {
                newPosts += pin.getNewPostCount();
                pinsWithNewPosts.add(pin);
            }
        }

        boolean show = false;

        if (lastNewPosts != newPosts && newPosts > 0) {
            show = true;
        }
        lastNewPosts = newPosts;

        if (show) {
            String descriptor;
            if (pinsWithNewPosts.size() == 1) {
                descriptor = pinsWithNewPosts.get(0).loadable.title;
            } else {
                descriptor = pinsWithNewPosts.size() + " threads";
            }

            String content = newPosts + " new posts in " + descriptor;
            String title = "New posts";

            showNotification(content, title, content, Integer.toString(newPosts));
        }
    }

    @SuppressWarnings("deprecation")
    private void showNotification(String tickerText, String title, String content, String contentInfo) {
        Intent intent = new Intent(pinnedService, BoardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pending = PendingIntent.getActivity(pinnedService, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder builder = new Notification.Builder(pinnedService);
        builder.setContentIntent(pending);

        builder.setTicker(tickerText);
        builder.setContentTitle(title);
        builder.setContentText(content);
        builder.setContentInfo(contentInfo);
        builder.setSmallIcon(R.drawable.ic_stat_notify);

        nm.notify(NOTIFICATION_ID, builder.getNotification());
    }
}
