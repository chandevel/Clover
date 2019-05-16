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
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;

import com.adamantcheese.github.chan.R;
import com.github.adamantcheese.chan.Chan;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.core.manager.WatchManager;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostLinkable;
import com.github.adamantcheese.chan.core.model.orm.Pin;
import com.github.adamantcheese.chan.core.settings.ChanSettings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import javax.inject.Inject;

import static android.provider.Settings.System.DEFAULT_NOTIFICATION_URI;
import static com.github.adamantcheese.chan.Chan.inject;

public class WatchNotification extends Service {
    private static final int NOTIFICATION_ID = 1;
    private static final int NOTIFICATION_ID_ALERT = 2;
    private static final String NOTIFICATION_NAME = "Watch notification";
    private static final String NOTIFICATION_NAME_ALERT = "Watch notification alert";
    private static final int SUBJECT_LENGTH = 6;
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(new NotificationChannel(String.valueOf(NOTIFICATION_ID), NOTIFICATION_NAME, NotificationManager.IMPORTANCE_LOW));
            NotificationChannel alert = new NotificationChannel(String.valueOf(NOTIFICATION_ID_ALERT), NOTIFICATION_NAME_ALERT, NotificationManager.IMPORTANCE_HIGH);
            alert.setSound(DEFAULT_NOTIFICATION_URI, new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                    .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
                    .build());
            alert.enableLights(true);
            alert.setLightColor((int) Long.parseLong(ChanSettings.watchLed.get(), 16));
            nm.createNotificationChannel(alert);
        }

        //prevent ongoing notifications somewhere here
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
            watchManager.pauseAll();
        } else {
            nm.notify(NOTIFICATION_ID, createNotification());
        }

        return START_STICKY;
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

            Collections.sort(postsForExpandedLines);
            List<CharSequence> expandedLines = new ArrayList<>();
            for (Post postForExpandedLine : postsForExpandedLines) {
                CharSequence prefix;
                if (postForExpandedLine.getTitle().length() <= SUBJECT_LENGTH) {
                    prefix = postForExpandedLine.getTitle();
                } else {
                    prefix = postForExpandedLine.getTitle().subSequence(0, SUBJECT_LENGTH);
                }

                CharSequence comment = postForExpandedLine.image() != null ? "(img) " : "";
                if (postForExpandedLine.comment.length() > 0) {
                    comment = TextUtils.concat(comment, postForExpandedLine.comment);
                }

                // Replace >>123456789 with >789 to shorten the notification
                // Also replace spoilered shit with █
                // All spans are deleted by the replaceAll call and you can't fix it easily so this will have to do
                Editable toFix = new SpannableStringBuilder(comment);
                PostLinkable[] spans = toFix.getSpans(0, comment.length(), PostLinkable.class);
                for (PostLinkable span : spans) {
                    if (span.type == PostLinkable.Type.SPOILER && !span.getSpoilerState()) {
                        int start = toFix.getSpanStart(span);
                        int end = toFix.getSpanEnd(span);

                        char[] chars = new char[end - start];
                        Arrays.fill(chars, '█');
                        String s = new String(chars);

                        toFix.replace(start, end, s);
                    }
                }
                comment = SHORTEN_NO_PATTERN.matcher(toFix).replaceAll(">$1");

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
        Intent intent = new Intent(this, StartActivity.class);
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setChannelId(String.valueOf(NOTIFICATION_ID_ALERT));
            }
        } else {
            builder.setSmallIcon(R.drawable.ic_stat_notify);
            builder.setPriority(NotificationCompat.PRIORITY_MIN);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setChannelId(String.valueOf(NOTIFICATION_ID));
            }
        }

        Intent pauseWatching = new Intent(this, WatchNotification.class);
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
}
