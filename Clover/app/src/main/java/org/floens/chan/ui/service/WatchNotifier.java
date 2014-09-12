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
import android.media.RingtoneManager;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.core.manager.WatchManager;
import org.floens.chan.core.model.Pin;
import org.floens.chan.core.model.Post;
import org.floens.chan.core.watch.PinWatcher;
import org.floens.chan.ui.activity.BoardActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class WatchNotifier extends Service {
    private static final String TAG = "WatchNotifier";
    private static final int NOTIFICATION_ID = 1;

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

        startForeground(NOTIFICATION_ID, getIdleNotification());
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
        Notification notification = createNotification();
        if (notification != null) {
            nm.notify(NOTIFICATION_ID, notification);
        } else {
            nm.notify(NOTIFICATION_ID, getIdleNotification());
        }
    }

    public void pausePins() {
        wm.pausePins();
    }

    private Notification createNotification() {
        List<Pin> watchingPins = wm.getWatchingPins();

        List<Pin> pins = new ArrayList<>();
        int newPostsCount = 0;
        int newQuotesCount = 0;
        List<Post> posts = new ArrayList<>();
        boolean makeSound = false;
        boolean show = false;
        boolean wereNewPosts = false;

        for (Pin pin : watchingPins) {
            PinWatcher watcher = pin.getPinWatcher();
            if (watcher == null || pin.isError)
                continue;

            boolean add = false;

            if (pin.getNewPostsCount() > 0) {
                if (watcher.getWereNewPosts()) {
                    wereNewPosts = true;
                }
                newPostsCount += pin.getNewPostsCount();
                for (Post p : watcher.getNewPosts()) {
                    p.title = pin.loadable.title;
                    posts.add(p);
                }

                show = true;
                add = true;
            }

            if (pin.getNewQuoteCount() > 0) {
                newQuotesCount += pin.getNewQuoteCount();
                show = true;
                add = true;
            }

            if (watcher.getWereNewQuotes()) {
                makeSound = true;
            }

            if (add) {
                pins.add(pin);
            }
        }

        if (show) {
            String title = newPostsCount + " new post" + (newPostsCount != 1 ? "s" : "");
            if (newQuotesCount > 0) {
                title += ", " + newQuotesCount + " quoting you";
            }

            String tickerText = title + " in ";
            if (pins.size() == 1) {
                tickerText += pins.get(0).loadable.title;
            } else {
                tickerText += pins.size() + " thread" + (pins.size() != 1 ? "s" : "");
            }

            Collections.sort(posts, new PostAgeComparer());

            List<CharSequence> lines = new ArrayList<>();
            for (Post post : posts) {
                CharSequence comment;
                if (post.comment.length() == 0) {
                    comment = "(image)";
                } else {
                    comment = post.comment;
                }

                if (pins.size() > 1) {
                    lines.add(post.title + ": " + comment);
                } else {
                    lines.add(comment);
                }
            }

            Pin targetPin = null;
            if (pins.size() == 1) {
                targetPin = pins.get(0);
            }

            boolean showTickerText = !ChanApplication.getInstance().getApplicationInForeground() && wereNewPosts;
            return getNotificationFor(showTickerText ? tickerText : null, title, tickerText, newPostsCount, lines, makeSound, targetPin);
        } else {
            return null;
        }
    }

    private Notification getIdleNotification() {
        List<Pin> watchingPins = wm.getWatchingPins();
        int s = watchingPins.size();
        String message = "Watching " + s + " thread" + (s != 1 ? "s" : "");

        return getNotificationFor(null, message, message, -1, null, false, null);
    }

    @SuppressWarnings("deprecation")
    private Notification getNotificationFor(String tickerText, String title, String content, int count,
                                            List<CharSequence> lines, boolean makeSound, Pin targetPin) {

        Intent intent = new Intent(this, BoardActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        intent.putExtra("pin_id", targetPin == null ? -1 : targetPin.id);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setContentIntent(pendingIntent);

        if (tickerText != null) {
            tickerText = tickerText.substring(0, Math.min(tickerText.length(), 50));
        }

        builder.setTicker(tickerText);
        builder.setContentTitle(title);
        builder.setContentText(content);

        if (count >= 0) {
            builder.setContentInfo(Integer.toString(count));
            builder.setNumber(count);
        }

        builder.setSmallIcon(R.drawable.ic_stat_notify);

        Intent pauseWatching = new Intent(this, WatchNotifier.class);
        pauseWatching.putExtra("pause_pins", true);

        PendingIntent pauseWatchingPending = PendingIntent.getService(this, 0, pauseWatching,
                PendingIntent.FLAG_UPDATE_CURRENT);

        builder.addAction(R.drawable.ic_action_pause, getString(R.string.watch_pause_pins),
                pauseWatchingPending);

        if (makeSound) {
            builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
        }

        if (lines != null) {
            NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
            for (CharSequence line : lines.subList(Math.max(0, lines.size() - 10), lines.size())) {
                style.addLine(line);
            }
            style.setBigContentTitle(title);
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
