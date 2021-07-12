/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
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
package com.github.adamantcheese.chan.ui.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Build;
import android.os.IBinder;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.core.manager.WatchManager;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostLinkable;
import com.github.adamantcheese.chan.core.model.orm.Pin;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.settings.PersistableChanState;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import javax.inject.Inject;

import static android.provider.Settings.System.DEFAULT_NOTIFICATION_URI;
import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.core.settings.ChanSettings.WatchNotifyMode.NOTIFY_ONLY_QUOTES;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getNotificationManager;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getQuantityString;

public class WatchNotification
        extends Service {
    private static final String NOTIFICATION_ID_STR = "1";
    private static final String NOTIFICATION_ID_ALERT_STR = "2";
    private final int NOTIFICATION_ID = 1;
    private static final String NOTIFICATION_NAME = "Watch notification";
    private static final String NOTIFICATION_NAME_ALERT = "Watch notification alert";
    public static final String PAUSE_PINS_KEY = "pause_pins";

    private static final Pattern SHORTEN_NO_PATTERN = Pattern.compile(">>\\d+(?=\\d{3})(\\d{3})");

    private final int NOTIFICATION_LIGHT = 0x1;
    private final int NOTIFICATION_SOUND = 0x2;
    private final int NOTIFICATION_PEEK = 0x4;

    @Inject
    WatchManager watchManager;

    public static void setupChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = getNotificationManager();
            if (notificationManager.getNotificationChannel(NOTIFICATION_ID_STR) != null
                    && notificationManager.getNotificationChannel(NOTIFICATION_ID_ALERT_STR) != null) return;
            //notification channel for non-alerts
            notificationManager.createNotificationChannel(new NotificationChannel(NOTIFICATION_ID_STR,
                    NOTIFICATION_NAME,
                    NotificationManager.IMPORTANCE_MIN
            ));
            //notification channel for alerts
            NotificationChannel alert = new NotificationChannel(NOTIFICATION_ID_ALERT_STR,
                    NOTIFICATION_NAME_ALERT,
                    NotificationManager.IMPORTANCE_HIGH
            );
            alert.setSound(DEFAULT_NOTIFICATION_URI,
                    new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
                            .build()
            );
            alert.enableLights(true);
            alert.setLightColor(0xff91e466);
            notificationManager.createNotificationChannel(alert);
        }
    }

    @Override
    public IBinder onBind(final Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        inject(this);

        PersistableChanState.watchLastCount.set(0);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getNotificationManager().cancel(NOTIFICATION_ID);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        setupChannel();
        //start with a blank notification, to ensure it is made within 5 seconds
        startForeground(NOTIFICATION_ID,
                new NotificationCompat.Builder(this, NOTIFICATION_ID_STR).setSmallIcon(R.drawable.ic_stat_notify)
                        .setPriority(NotificationCompat.PRIORITY_MIN)
                        .setOngoing(true)
                        .build()
        );

        if (intent != null && intent.getExtras() != null && intent.getExtras().getBoolean(PAUSE_PINS_KEY, false)) {
            watchManager.pauseAll();
        }

        Notification notification = createNotification(); //this may take more than 5 seconds to generate
        if (notification == null) {
            Logger.d(this, "onStartCommand() createNotification returned null");
            stopSelf();
            return START_NOT_STICKY;
        }

        //replace the notification with the properly generated one
        startForeground(NOTIFICATION_ID, notification);
        return START_STICKY;
    }

    @Nullable
    private Notification createNotification() {
        boolean notifyQuotesOnly = ChanSettings.watchNotifyMode.get() == NOTIFY_ONLY_QUOTES;
        boolean soundQuotesOnly = ChanSettings.watchSound.get() == NOTIFY_ONLY_QUOTES;

        //A set of unviewed posts
        Set<Post> unviewedPosts = new HashSet<>();
        //A set of posts that quote the user
        Set<Post> listQuoting = new HashSet<>();
        //Count of pins that aren't errored or unwatched
        int pinCount = 0;
        //Lists of pins that had new posts or quotes in them, depending on settings
        List<Pin> newPostPins = new ArrayList<>();
        List<Pin> newQuotePins = new ArrayList<>();

        int flags = 0;

        for (Pin pin : watchManager.getWatchingPins()) {
            WatchManager.PinWatcher watcher = watchManager.getPinWatcher(pin);
            if (watcher == null || pin.isError || pin.archived) {
                continue;
            }

            if (pin.watching) {
                pinCount++;

                if (notifyQuotesOnly) {
                    unviewedPosts.addAll(watcher.getUnviewedQuotes());
                    listQuoting.addAll(watcher.getUnviewedQuotes());
                    if (watcher.getWereNewQuotes()) {
                        flags |= NOTIFICATION_LIGHT | NOTIFICATION_PEEK | NOTIFICATION_SOUND;
                    }
                    if (pin.getNewQuoteCount() > 0) {
                        newQuotePins.add(pin);
                    }
                } else {
                    unviewedPosts.addAll(watcher.getUnviewedPosts());
                    listQuoting.addAll(watcher.getUnviewedQuotes());
                    if (watcher.getWereNewPosts()) {
                        flags |= NOTIFICATION_LIGHT;
                        if (!soundQuotesOnly) {
                            flags |= NOTIFICATION_PEEK | NOTIFICATION_SOUND;
                        }
                    }
                    if (watcher.getWereNewQuotes()) {
                        flags |= NOTIFICATION_PEEK | NOTIFICATION_SOUND;
                    }
                    if (pin.getNewQuoteCount() > 0) {
                        newQuotePins.add(pin);
                    } else if (pin.getNewPostCount() > 0) {
                        newPostPins.add(pin);
                    }
                }
            }
        }

        if (BackgroundUtils.isInForeground()) {
            flags &= ~(NOTIFICATION_LIGHT);
            flags &= ~(NOTIFICATION_SOUND);
        }

        if (!ChanSettings.watchPeek.get()) {
            flags &= ~(NOTIFICATION_PEEK);
        }

        return setupNotificationTextFields(pinCount,
                newPostPins,
                newQuotePins,
                unviewedPosts,
                listQuoting,
                notifyQuotesOnly,
                flags
        );
    }

    private Notification setupNotificationTextFields(
            int pinCount,
            List<Pin> newPostPins,
            List<Pin> newQuotePins,
            Set<Post> unviewedPosts,
            Set<Post> listQuoting,
            boolean notifyQuotesOnly,
            int flags
    ) {
        String message;
        Set<Post> postsForExpandedLines;
        if (notifyQuotesOnly) {
            message = getQuantityString(R.plurals.watch_new_quotes, listQuoting.size());
            postsForExpandedLines = listQuoting;
        } else {
            postsForExpandedLines = unviewedPosts;
            if (listQuoting.size() > 0) {
                message = getQuantityString(R.plurals.new_posts, unviewedPosts.size()) + ", "
                        + getQuantityString(R.plurals.watch_new_quotes, listQuoting.size());
            } else {
                message = getQuantityString(R.plurals.new_posts, unviewedPosts.size());
            }
        }

        List<Post> finalPosts = new ArrayList<>(postsForExpandedLines);
        Collections.sort(finalPosts);
        finalPosts = finalPosts.subList(Math.max(0, finalPosts.size() - 10), finalPosts.size()); // last 10 posts

        List<CharSequence> expandedLines = new ArrayList<>();
        for (Post postForExpandedLine : finalPosts) {
            SpannableStringBuilder prefix;
            if (postForExpandedLine.getTitle().length() <= 6) {
                prefix = new SpannableStringBuilder(postForExpandedLine.getTitle());
            } else {
                prefix = new SpannableStringBuilder(postForExpandedLine.getTitle().subSequence(0, 6));
            }

            SpannableStringBuilder comment =
                    new SpannableStringBuilder(postForExpandedLine.image() != null ? "(img) " : "").append(
                            postForExpandedLine.comment == null ? "" : postForExpandedLine.comment);

            // Replace >>123456789 with >789 to shorten the notification
            // Also replace spoilered shit with █
            // All spans are deleted by the replaceAll call and you can't modify their ranges easily so this will have to do
            PostLinkable[] spans = comment.getSpans(0, comment.length(), PostLinkable.class);
            for (PostLinkable span : spans) {
                if (span.type == PostLinkable.Type.SPOILER) {
                    int start = comment.getSpanStart(span);
                    int end = comment.getSpanEnd(span);

                    char[] chars = new char[end - start];
                    Arrays.fill(chars, '█');
                    String s = new String(chars);

                    comment.replace(start, end, s);
                }
            }
            comment = new SpannableStringBuilder(SHORTEN_NO_PATTERN.matcher(comment).replaceAll(">$1"));

            expandedLines.add(prefix.append(": ").append(comment));
        }

        boolean alert = PersistableChanState.watchLastCount.get() < listQuoting.size();
        PersistableChanState.watchLastCount.set(listQuoting.size());

        return buildNotification(message,
                expandedLines,
                flags,
                alert,
                PersistableChanState.watchLastCount.get() > 0,
                newQuotePins.isEmpty() ? (newPostPins.isEmpty() ? null : newPostPins.get(0)) : newQuotePins.get(0),
                pinCount > 0
        );
    }

    /**
     * Create a notification with the supplied parameters.
     * The style of the big notification is InboxStyle, a list of text.
     *
     * @param title         The title of the notification
     * @param expandedLines A list of lines for the notification
     * @param flags         Flags for this notification (light, sound, peek)
     * @param alertIcon     Show the alert version of the icon
     * @param target        The target pin, or null to open the pinned pane on tap
     */
    private Notification buildNotification(
            String title,
            List<CharSequence> expandedLines,
            int flags,
            boolean alertIcon,
            boolean alertIconOverride,
            Pin target,
            boolean hasWatchPins
    ) {
        synchronized (this) {
            NotificationCompat.Builder builder =
                    new NotificationCompat.Builder(this, alertIcon ? NOTIFICATION_ID_ALERT_STR : NOTIFICATION_ID_STR);
            builder.setContentTitle(title).setContentText(TextUtils.join(", ", expandedLines)).setOngoing(true);

            //setup launch action, add pin if there's only one thread watching
            Intent intent = new Intent(this, StartActivity.class);
            intent.setAction(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP
                            | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    .putExtra("pin_id", target != null ? target.id : -1);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            builder.setContentIntent(pendingIntent);

            //setup lights, sound, and peek
            if ((flags & NOTIFICATION_SOUND) != 0 || (flags & NOTIFICATION_PEEK) != 0) {
                builder.setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE);
            }

            if ((flags & NOTIFICATION_LIGHT) != 0) {
                builder.setLights(0xff91e466, 1000, 1000);
            }

            //set the alert icon if necessary
            //if the last notification was an alert, continue it having that icon until it goes to zero
            //also keep the priority so it shows up in the status bar
            if (alertIcon || alertIconOverride) {
                builder.setSmallIcon(R.drawable.ic_stat_notify_alert).setPriority(NotificationCompat.PRIORITY_HIGH);
            } else {
                builder.setSmallIcon(R.drawable.ic_stat_notify).setPriority(NotificationCompat.PRIORITY_MIN);
            }

            if (hasWatchPins) {
                //setup the pause watch button
                Intent pauseWatching = new Intent(this, WatchNotification.class);
                pauseWatching.putExtra(PAUSE_PINS_KEY, true);
                PendingIntent pauseWatchIntent =
                        PendingIntent.getService(this, 0, pauseWatching, PendingIntent.FLAG_UPDATE_CURRENT);
                builder.addAction(R.drawable.ic_fluent_pause_24_filled,
                        getString(R.string.watch_pause_pins),
                        pauseWatchIntent
                );
            }

            //setup the display in the notification
            NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
            for (CharSequence line : expandedLines) {
                style.addLine(line);
            }
            style.setBigContentTitle(title);
            builder.setStyle(style);

            return builder.build();
        }
    }
}
