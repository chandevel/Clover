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
package org.floens.chan.core.watch;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.support.v4.app.NotificationCompat;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.core.ChanPreferences;
import org.floens.chan.core.model.Pin;
import org.floens.chan.core.model.Post;
import org.floens.chan.service.WatchService;
import org.floens.chan.ui.activity.BoardActivity;
import org.floens.chan.utils.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class WatchNotifier {
    private static final String TAG = "WatchNotifier";

    private final int NOTIFICATION_ID = 1;

    private final Context context;
    private final NotificationManager nm;

    public WatchNotifier(Context context) {
        this.context = context;
        nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    public void destroy() {
        nm.cancel(NOTIFICATION_ID);
    }

    public void update() {
        if (!WatchService.getActivityInForeground() && ChanPreferences.getWatchBackgroundEnabled()) {
            prepareNotification();
        }
    }

    public void onForegroundChanged() {
        if (WatchService.getActivityInForeground()) {
            nm.cancel(NOTIFICATION_ID);
        } else {

        }
    }

    public void onPausePinsClicked() {
        nm.cancel(NOTIFICATION_ID);

        List<Pin> watchingPins = ChanApplication.getPinnedManager().getWatchingPins();
        for (Pin pin : watchingPins) {
            pin.watching = false;
        }

        ChanApplication.getPinnedManager().onPinsChanged();
        ChanApplication.getPinnedManager().updateAll(); // Can be the last thing this app does
    }

    private void prepareNotification() {
        List<Pin> watchingPins = ChanApplication.getPinnedManager().getWatchingPins();

        List<Pin> pins = new ArrayList<Pin>();
        int newPostsCount = 0;
        int newQuotesCount = 0;
        List<Post> posts = new ArrayList<Post>();
        boolean makeSound = false;
        boolean show = false;

        for (Pin pin : watchingPins) {
            PinWatcher watcher = pin.getPinWatcher();
            if (watcher == null || pin.isError)
                continue;

            boolean add = false;

            if (pin.getNewPostsCount() > 0) {
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

            List<CharSequence> lines = new ArrayList<CharSequence>();
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

            showNotification(tickerText, title, tickerText, Integer.toString(newPostsCount), lines, makeSound);
        }
    }

    @SuppressWarnings("deprecation")
    private void showNotification(String tickerText, String title, String content, String contentInfo,
                                  List<CharSequence> lines, boolean makeSound) {

        Intent intent = new Intent(context, BoardActivity.class);
        intent.addCategory("android.intent.category.LAUNCHER");
        intent.setAction("android.intent.action.MAIN");
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        builder.setContentIntent(pendingIntent);

        builder.setTicker(tickerText);
        builder.setContentTitle(title);
        builder.setContentText(content);
        builder.setContentInfo(contentInfo);
        builder.setSmallIcon(R.drawable.ic_stat_notify);

        Intent pauseWatching = new Intent(context, WatchService.class);
        pauseWatching.putExtra("pause_pins", true);

        PendingIntent pauseWatchingPending = PendingIntent.getService(context, 0, pauseWatching,
                PendingIntent.FLAG_UPDATE_CURRENT);

        builder.addAction(R.drawable.ic_action_pause, context.getString(R.string.watch_pause_pins),
                pauseWatchingPending);

        if (makeSound) {
            builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
        }

        NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
        for (CharSequence line : lines.subList(Math.max(0, lines.size() - 10), lines.size())) {
            style.addLine(line);
        }
        style.setBigContentTitle(title);
        // style.setSummaryText(content);

        builder.setStyle(style);

        Logger.i(TAG, "Showing notification");
        nm.notify(NOTIFICATION_ID, builder.getNotification());
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
