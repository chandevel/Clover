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
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.core.manager.WatchManager;
import org.floens.chan.core.model.Pin;
import org.floens.chan.core.model.Post;
import org.floens.chan.core.watch.PinWatcher;
import org.floens.chan.ui.activity.BoardActivity;
import org.floens.chan.ui.activity.ChanActivity;
import org.floens.chan.ui.activity.StartActivity;
import org.floens.chan.utils.AndroidUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class WatchNotifier extends Service {
    private static final String TAG = "WatchNotifier";
    private static final int NOTIFICATION_ID = 1;
    private static final PostAgeComparer POST_AGE_COMPARER = new PostAgeComparer();

    private NotificationManager nm;
    private WatchManager wm;

    @Override
    public IBinder onBind(final Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        wm = ChanApplication.getWatchManager();

        startForeground(NOTIFICATION_ID, createNotification());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        nm.cancel(NOTIFICATION_ID);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getExtras() != null && intent.getExtras().getBoolean("pause_pins", false)) {
            pausePins();
        } else {
            updateNotification();
        }

        return START_STICKY;
    }

    public void updateNotification() {
        nm.notify(NOTIFICATION_ID, createNotification());
    }

    public void pausePins() {
        wm.pausePins();
    }

    private Notification createNotification() {
        boolean notifyQuotesOnly = ChanSettings.getWatchNotifyMode().equals("quotes");
        boolean soundQuotesOnly = ChanSettings.getWatchSound().equals("quotes");

        List<Post> list = new ArrayList<>();
        List<Post> listQuoting = new ArrayList<>();
        List<Pin> pins = new ArrayList<>();
        List<Pin> subjectPins = new ArrayList<>();

        boolean ticker = false;
        boolean sound = false;

        for (Pin pin : wm.getWatchingPins()) {
            PinWatcher watcher = pin.getPinWatcher();
            if (watcher == null || pin.isError)
                continue;

            pins.add(pin);

            if (notifyQuotesOnly) {
                list.addAll(watcher.getUnviewedQuotes());
                listQuoting.addAll(watcher.getUnviewedQuotes());
                if (watcher.getWereNewQuotes()) {
                    ticker = true;
                    sound = true;
                }
                if (pin.getNewQuoteCount() > 0) {
                    subjectPins.add(pin);
                }
            } else {
                list.addAll(watcher.getUnviewedPosts());
                listQuoting.addAll(watcher.getUnviewedQuotes());
                if (watcher.getWereNewPosts()) {
                    ticker = true;
                    if (!soundQuotesOnly) {
                        sound = true;
                    }
                }
                if (watcher.getWereNewQuotes()) {
                    sound = true;
                }
                if (pin.getNewPostCount() > 0) {
                    subjectPins.add(pin);
                }
            }
        }

        if (ChanApplication.getInstance().getApplicationInForeground()) {
            ticker = false;
            sound = false;
        }

        return notifyAboutPosts(pins, subjectPins, list, listQuoting, notifyQuotesOnly, ticker, sound);
    }

    private Notification notifyAboutPosts(List<Pin> pins, List<Pin> subjectPins, List<Post> list, List<Post> listQuoting,
                                          boolean notifyQuotesOnly, boolean makeTicker, boolean makeSound) {
        String title = getResources().getQuantityString(R.plurals.watch_title, pins.size(), pins.size());

        if (list.size() == 0) {
            // Idle notification
            String message = getString(R.string.watch_idle);
            return getNotificationFor(null, title, message, -1, null, false, false, null);
        } else {
            // New posts notification
            String message;
            List<Post> notificationList;
            if (notifyQuotesOnly) {
                message = getResources().getQuantityString(R.plurals.watch_new_quotes, listQuoting.size(), listQuoting.size());
                notificationList = listQuoting;
            } else {
                notificationList = list;
                if (listQuoting.size() > 0) {
                    message = getResources().getQuantityString(R.plurals.watch_new_quoting, list.size(), list.size(), listQuoting.size());
                } else {
                    message = getResources().getQuantityString(R.plurals.watch_new, list.size(), list.size());
                }
            }

            Collections.sort(notificationList, POST_AGE_COMPARER);
            List<CharSequence> lines = new ArrayList<>();
            for (Post post : notificationList) {
                CharSequence prefix = AndroidUtils.ellipsize(post.title, 18);

                CharSequence comment;
                if (post.comment.length() == 0) {
                    comment = "(image)";
                } else {
                    comment = post.comment;
                }

                lines.add(prefix + ": " + comment);
            }

            Pin subject = null;
            if (subjectPins.size() == 1) {
                subject = subjectPins.get(0);
            }

            String ticker = null;
            if (makeTicker) {
                ticker = message;
            }

            return getNotificationFor(ticker, title, message, -1, lines, makeTicker, makeSound, subject);
        }
    }

    /**
     * Create a notification with the supplied parameters.
     * The style of the big notification is InboxStyle, a list of text.
     *
     * @param tickerText    The tickertext to show, or null if no tickertext should be shown.
     * @param contentTitle  The title of the notification
     * @param contentText   The content of the small notification
     * @param contentNumber The contentInfo and number, or -1 if not shown
     * @param expandedLines A list of lines for the big notification, or null if not shown
     * @param makeSound     Should the notification make a sound
     * @param target        The target pin, or null to open the pinned pane on tap
     * @return
     */
    @SuppressWarnings("deprecation")
    private Notification getNotificationFor(String tickerText, String contentTitle, String contentText, int contentNumber,
                                            List<CharSequence> expandedLines, boolean light, boolean makeSound, Pin target) {
        Intent intent = new Intent(this, BoardActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        intent.putExtra("pin_id", target == null ? -1 : target.id);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        if (makeSound) {
            builder.setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE);
        }

        if (light) {
            long watchLed = ChanSettings.getWatchLed();
            if (watchLed >= 0) {
                builder.setLights((int) watchLed, 1000, 1000);
            }
        }


        builder.setContentIntent(pendingIntent);

        if (tickerText != null) {
            tickerText = tickerText.substring(0, Math.min(tickerText.length(), 50));
        }

        builder.setTicker(tickerText);
        builder.setContentTitle(contentTitle);
        builder.setContentText(contentText);

        if (contentNumber >= 0) {
            builder.setContentInfo(Integer.toString(contentNumber));
            builder.setNumber(contentNumber);
        }

        builder.setSmallIcon(R.drawable.ic_stat_notify);

        Intent pauseWatching = new Intent(this, WatchNotifier.class);
        pauseWatching.putExtra("pause_pins", true);

        PendingIntent pauseWatchingPending = PendingIntent.getService(this, 0, pauseWatching,
                PendingIntent.FLAG_UPDATE_CURRENT);

        builder.addAction(R.drawable.ic_action_pause, getString(R.string.watch_pause_pins),
                pauseWatchingPending);

        if (expandedLines != null) {
            NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
            for (CharSequence line : expandedLines.subList(Math.max(0, expandedLines.size() - 10), expandedLines.size())) {
                style.addLine(line);
            }
            style.setBigContentTitle(contentTitle);
            builder.setStyle(style);
        }

        return builder.getNotification();
    }

    private static class PostAgeComparer implements Comparator<Post> {
        @Override
        public int compare(Post lhs, Post rhs) {
            if (lhs.time < rhs.time) {
                return 1;
            } else if (lhs.time > rhs.time) {
                return -1;
            } else {
                return 0;
            }
        }
    }
}
