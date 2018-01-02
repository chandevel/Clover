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
import android.text.TextUtils;

import org.floens.chan.Chan;
import org.floens.chan.R;
import org.floens.chan.core.manager.WatchManager;
import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.orm.Pin;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.activity.BoardActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import javax.inject.Inject;

import static org.floens.chan.Chan.inject;

public class WatchNotifier extends Service {
    private static final String TAG = "WatchNotifier";
    private static final int NOTIFICATION_ID = 1;
    private static final PostAgeComparator POST_AGE_COMPARATOR = new PostAgeComparator();
    private static final int SUBJECT_LENGTH = 6;
    private static final String IMAGE_TEXT = "(img) ";
    private static final Pattern SHORTEN_NO_PATTERN = Pattern.compile(">>\\d+(?=\\d{3})(\\d{3})");

    private NotificationManager nm;

    @Inject
    WatchManager watchManager;

    @Override
    public IBinder onBind(final Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        inject(this);

        nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

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
        watchManager.pauseAll();
    }

    private Notification createNotification() {
        boolean notifyQuotesOnly = ChanSettings.watchNotifyMode.get().equals("quotes");
        boolean soundQuotesOnly = ChanSettings.watchSound.get().equals("quotes");

        List<Post> unviewedPosts = new ArrayList<>();
        List<Post> listQuoting = new ArrayList<>();
        List<Pin> pins = new ArrayList<>();
        List<Pin> subjectPins = new ArrayList<>();

        boolean light = false;
        boolean sound = false;
        boolean peek = false;

        for (Pin pin : watchManager.getWatchingPins()) {
            WatchManager.PinWatcher watcher = watchManager.getPinWatcher(pin);
            if (watcher == null || pin.isError) {
                continue;
            }

            pins.add(pin);

            if (notifyQuotesOnly) {
                unviewedPosts.addAll(watcher.getUnviewedQuotes());
                listQuoting.addAll(watcher.getUnviewedQuotes());
                if (watcher.getWereNewQuotes()) {
                    light = true;
                    sound = true;
                    peek = true;
                }
                if (pin.getNewQuoteCount() > 0) {
                    subjectPins.add(pin);
                }
            } else {
                unviewedPosts.addAll(watcher.getUnviewedPosts());
                listQuoting.addAll(watcher.getUnviewedQuotes());
                if (watcher.getWereNewPosts()) {
                    light = true;
                    if (!soundQuotesOnly) {
                        sound = true;
                        peek = true;
                    }
                }
                if (watcher.getWereNewQuotes()) {
                    sound = true;
                    peek = true;
                }
                if (pin.getNewPostCount() > 0) {
                    subjectPins.add(pin);
                }
            }
        }

        if (Chan.getInstance().getApplicationInForeground()) {
            light = false;
            sound = false;
        }

        if (!ChanSettings.watchPeek.get()) {
            peek = false;
        }

        return notifyAboutPosts(pins, subjectPins, unviewedPosts, listQuoting, notifyQuotesOnly, light, sound, peek);
    }

    private Notification notifyAboutPosts(List<Pin> pins, List<Pin> subjectPins, List<Post> unviewedPosts, List<Post> listQuoting,
                                          boolean notifyQuotesOnly, boolean light, boolean sound, boolean peek) {
        String title = getResources().getQuantityString(R.plurals.watch_title, pins.size(), pins.size());

        if (unviewedPosts.isEmpty()) {
            // Idle notification
            String message = getString(R.string.watch_idle);
            return get(title, message, null, false, false, false, false, null);
        } else {
            // New posts notification
            String message;
            List<Post> postsForExpandedLines;
            if (notifyQuotesOnly) {
                message = getResources().getQuantityString(R.plurals.watch_new_quotes, listQuoting.size(), listQuoting.size());
                postsForExpandedLines = listQuoting;
            } else {
                postsForExpandedLines = unviewedPosts;
                if (listQuoting.size() > 0) {
                    message = getResources().getQuantityString(R.plurals.watch_new_quoting, unviewedPosts.size(), unviewedPosts.size(), listQuoting.size());
                } else {
                    message = getResources().getQuantityString(R.plurals.watch_new, unviewedPosts.size(), unviewedPosts.size());
                }
            }

            Collections.sort(postsForExpandedLines, POST_AGE_COMPARATOR);
            List<CharSequence> expandedLines = new ArrayList<>();
            for (Post postForExpandedLine : postsForExpandedLines) {
                CharSequence prefix;
                if (postForExpandedLine.getTitle().length() <= SUBJECT_LENGTH) {
                    prefix = postForExpandedLine.getTitle();
                } else {
                    prefix = postForExpandedLine.getTitle().subSequence(0, SUBJECT_LENGTH);
                }

                String comment = postForExpandedLine.image != null ? IMAGE_TEXT : "";
                if (postForExpandedLine.comment.length() > 0) {
                    comment += postForExpandedLine.comment;
                }

                // Replace >>132456798 with >789 to shorten the notification
                comment = SHORTEN_NO_PATTERN.matcher(comment).replaceAll(">$1");

                expandedLines.add(prefix + ": " + comment);
            }

            Pin targetPin = null;
            if (subjectPins.size() == 1) {
                targetPin = subjectPins.get(0);
            }

            String smallText = TextUtils.join(", ", expandedLines);
            return get(message, smallText, expandedLines, light, sound, peek, true, targetPin);
        }
    }

    /**
     * Create a notification with the supplied parameters.
     * The style of the big notification is InboxStyle, a list of text.
     *
     * @param title         The title of the notification
     * @param smallText     The content of the small notification
     * @param expandedLines A list of lines for the big notification, or null if not shown
     * @param sound         Should the notification make a sound
     * @param peek          Peek the notification into the screen
     * @param alertIcon     Show the alert version of the icon
     * @param target        The target pin, or null to open the pinned pane on tap
     */
    private Notification get(String title, String smallText, List<CharSequence> expandedLines,
                             boolean light, boolean sound, boolean peek, boolean alertIcon, Pin target) {
        Intent intent = new Intent(this, BoardActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        intent.putExtra("pin_id", target == null ? -1 : target.id);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        if (sound || peek) {
            builder.setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE);
        }

        if (light) {
            long watchLed = Long.parseLong(ChanSettings.watchLed.get(), 16);
            if (watchLed >= 0) {
                builder.setLights((int) watchLed, 1000, 1000);
            }
        }

        builder.setContentIntent(pendingIntent);

        builder.setContentTitle(title);
        if (smallText != null) {
            builder.setContentText(smallText);
        }

        if (alertIcon || peek) {
            builder.setSmallIcon(R.drawable.ic_stat_notify_alert);
            builder.setPriority(NotificationCompat.PRIORITY_HIGH);
        } else {
            builder.setSmallIcon(R.drawable.ic_stat_notify);
            builder.setPriority(NotificationCompat.PRIORITY_MIN);
        }

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
            style.setBigContentTitle(title);
            builder.setStyle(style);
        }

        return builder.build();
    }

    private static class PostAgeComparator implements Comparator<Post> {
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
