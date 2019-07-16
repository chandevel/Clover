package org.floens.chan.ui.notification;

import android.app.NotificationManager;
import android.content.Context;

public class NotificationHelper {
    protected final Context applicationContext;
    protected final NotificationManager notificationManager;

    public NotificationHelper(Context applicationContext) {
        this.applicationContext = applicationContext;

        notificationManager = (NotificationManager) applicationContext.
                getSystemService(Context.NOTIFICATION_SERVICE);
    }
}
