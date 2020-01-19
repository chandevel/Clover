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
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.Pair;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.core.manager.ThreadSaveManager;
import com.github.adamantcheese.chan.core.manager.WatchManager;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostLinkable;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.model.orm.Pin;
import com.github.adamantcheese.chan.core.model.orm.PinType;
import com.github.adamantcheese.chan.core.model.orm.SavedThread;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.settings.base_directory.LocalThreadsBaseDirectory;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.k1rakishou.fsaf.FileManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.inject.Inject;

import static android.provider.Settings.System.DEFAULT_NOTIFICATION_URI;
import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.core.settings.ChanSettings.NOTIFY_ONLY_QUOTES;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getQuantityString;

public class WatchNotification
        extends Service {
    private static final String TAG = "WatchNotification";
    private String NOTIFICATION_ID_STR = "1";
    private String NOTIFICATION_ID_ALERT_STR = "2";
    private int NOTIFICATION_ID = 1;
    private static final String NOTIFICATION_NAME = "Watch notification";
    private static final String NOTIFICATION_NAME_ALERT = "Watch notification alert";

    private static final Pattern SHORTEN_NO_PATTERN = Pattern.compile(">>\\d+(?=\\d{3})(\\d{3})");

    private int NOTIFICATION_LIGHT = 0x1;
    private int NOTIFICATION_SOUND = 0x2;
    private int NOTIFICATION_PEEK = 0x4;

    @Inject
    NotificationManager notificationManager;
    @Inject
    WatchManager watchManager;
    @Inject
    ThreadSaveManager threadSaveManager;
    @Inject
    FileManager fileManager;

    @Override
    public IBinder onBind(final Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        inject(this);

        ChanSettings.watchLastCount.set(0);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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

        Notification notification = createNotification();
        if (notification == null) {
            Logger.d(TAG, "onCreate() createNotification returned null");

            stopSelf();
            return;
        }

        // Do not remove this or the app will blow up on Android.Oreo and above
        startForeground(NOTIFICATION_ID, notification);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        notificationManager.cancel(NOTIFICATION_ID);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getExtras() != null && intent.getExtras().getBoolean("pause_pins", false)) {
            watchManager.pauseAll();
        } else {
            Notification notification = createNotification();
            if (notification == null) {
                Logger.d(TAG, "onStartCommand() createNotification returned null");

                stopSelf();
                return START_NOT_STICKY;
            }

            notificationManager.notify(NOTIFICATION_ID, notification);
        }
        return START_STICKY;
    }

    @Nullable
    private Notification createNotification() {
        boolean notifyQuotesOnly = ChanSettings.watchNotifyMode.get().equals(NOTIFY_ONLY_QUOTES);
        boolean soundQuotesOnly = ChanSettings.watchSound.get().equals(NOTIFY_ONLY_QUOTES);

        //A set of unviewed posts
        Set<Post> unviewedPosts = new HashSet<>();
        //A set of posts that quote the user
        Set<Post> listQuoting = new HashSet<>();
        //A list of pins that aren't errored or unwatched
        List<Pin> pins = new ArrayList<>();
        //A list of pins that had new posts in them, or had new quotes in them, depending on settings
        List<Pin> subjectPins = new ArrayList<>();
        // A list of pins that download threads
        List<Pin> threadDownloaderPins = new ArrayList<>();
        // Used for ThreadSaveManager
        HashMap<SavedThread, Pair<Loadable, List<Post>>> unviewedPostsByThread = new HashMap<>();

        int flags = 0;

        for (Pin pin : watchManager.getWatchingPins()) {
            WatchManager.PinWatcher watcher = watchManager.getPinWatcher(pin);
            if (watcher == null || pin.isError || pin.archived) {
                continue;
            }

            if (PinType.hasNoFlags(pin.pinType)) {
                Logger.d(TAG, "Pin " + pin.toString() + " has no flags");
                continue;
            }

            if (PinType.hasDownloadFlag(pin.pinType)) {
                SavedThread savedThread = watchManager.findSavedThreadByLoadableId(pin.loadable.id);
                if (savedThread != null && savedThread.isRunning()) {
                    // Just pass all the posts to the threadSaveManager. It will figure out new posts by itself
                    List<Post> allPosts = watcher.getPosts();
                    if (!allPosts.isEmpty()) {
                        // Add all posts to the map
                        unviewedPostsByThread.put(savedThread, new Pair<>(pin.loadable, allPosts));
                    }
                }

                if (savedThread != null) {
                    threadDownloaderPins.add(pin);
                }
            }

            if (PinType.hasWatchNewPostsFlag(pin.pinType) && pin.watching) {
                pins.add(pin);

                if (notifyQuotesOnly) {
                    unviewedPosts.addAll(watcher.getUnviewedQuotes());
                    listQuoting.addAll(watcher.getUnviewedQuotes());
                    if (watcher.getWereNewQuotes()) {
                        flags |= NOTIFICATION_LIGHT | NOTIFICATION_PEEK | NOTIFICATION_SOUND;
                    }
                    if (pin.getNewQuoteCount() > 0) {
                        subjectPins.add(pin);
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
                    if (pin.getNewPostCount() > 0) {
                        subjectPins.add(pin);
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

        if (unviewedPostsByThread.size() > 0) {
            updateSavedThreads(unviewedPostsByThread);
        }

        if (pins.isEmpty() && threadDownloaderPins.isEmpty()) {
            Logger.d(TAG, "Both pins or threadDownloaderPins are empty");
            return null;
        }

        return setupNotificationTextFields(pins,
                subjectPins,
                threadDownloaderPins,
                unviewedPosts,
                listQuoting,
                notifyQuotesOnly,
                flags
        );
    }

    private void updateSavedThreads(HashMap<SavedThread, Pair<Loadable, List<Post>>> allPostsByThread) {
        if (!fileManager.baseDirectoryExists(LocalThreadsBaseDirectory.class)) {
            Logger.d(TAG, "updateSavedThreads() LocalThreadsBaseDirectory does not exist");

            watchManager.stopSavingAllThread();
            return;
        }

        for (Map.Entry<SavedThread, Pair<Loadable, List<Post>>> entry : allPostsByThread.entrySet()) {
            Loadable loadable = entry.getValue().first;
            List<Post> posts = entry.getValue().second;

            if (!threadSaveManager.enqueueThreadToSave(loadable, posts)) {
                watchManager.stopSavingThread(loadable);
            }
        }
    }

    private Notification setupNotificationTextFields(
            List<Pin> pins,
            List<Pin> subjectPins,
            List<Pin> threadDownloaderPins,
            Set<Post> unviewedPosts,
            Set<Post> listQuoting,
            boolean notifyQuotesOnly,
            int flags
    ) {
        if (unviewedPosts.isEmpty()) {
            // Idle notification
            ChanSettings.watchLastCount.set(0);
            return buildNotification(formatNotificationTitle(pins.size(), threadDownloaderPins.size()),
                    Collections.singletonList(getString(R.string.watch_idle)),
                    0,
                    false,
                    false,
                    pins.size() > 0 ? pins.get(0) : null,
                    pins.size() > 0
            );
        } else {
            // New posts notification
            String message;
            Set<Post> postsForExpandedLines;
            if (notifyQuotesOnly) {
                message = formatNotificationTitleNewQuotes(listQuoting.size(), threadDownloaderPins.size());
                postsForExpandedLines = listQuoting;
            } else {
                postsForExpandedLines = unviewedPosts;
                if (listQuoting.size() > 0) {
                    message = formatNotificationTitleNewQuoting(unviewedPosts.size(),
                            listQuoting.size(),
                            threadDownloaderPins.size()
                    );
                } else {
                    message = formatNotificationTitleNewPosts(unviewedPosts.size(), threadDownloaderPins.size());
                }
            }

            List<Post> finalPosts = new ArrayList<>(postsForExpandedLines);
            Collections.sort(finalPosts);
            List<CharSequence> expandedLines = new ArrayList<>();
            for (Post postForExpandedLine : finalPosts) {
                CharSequence prefix;
                if (postForExpandedLine.getTitle().length() <= 6) {
                    prefix = postForExpandedLine.getTitle();
                } else {
                    prefix = postForExpandedLine.getTitle().subSequence(0, 6);
                }

                CharSequence comment = postForExpandedLine.image() != null ? "(img) " : "";
                if (postForExpandedLine.comment.length() > 0) {
                    // FIXME: this thing is pretty slow sometimes (50-200ms).
                    //  Can we replace it with something faster?
                    comment = TextUtils.concat(comment, postForExpandedLine.comment);
                }

                // Replace >>123456789 with >789 to shorten the notification
                // Also replace spoilered shit with █
                // All spans are deleted by the replaceAll call and you can't modify their ranges easily so this will have to do
                Editable toFix = new SpannableStringBuilder(comment);
                PostLinkable[] spans = toFix.getSpans(0, comment.length(), PostLinkable.class);
                for (PostLinkable span : spans) {
                    if (span.type == PostLinkable.Type.SPOILER) {
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

            boolean alert = ChanSettings.watchLastCount.get() < listQuoting.size();
            ChanSettings.watchLastCount.set(listQuoting.size());

            return buildNotification(message,
                    expandedLines,
                    flags,
                    alert,
                    ChanSettings.watchLastCount.get() > 0,
                    subjectPins.size() == 1 ? subjectPins.get(0) : null,
                    pins.size() > 0
            );
        }
    }

    private String formatNotificationTitleNewPosts(int unviewedPostsCount, int threadDownloaderPinsCount) {
        String watchNewQuotesTitle = getQuantityString(R.plurals.thread_new_posts,
                unviewedPostsCount,
                unviewedPostsCount,
                unviewedPostsCount
        );
        String downloadTitle =
                getQuantityString(R.plurals.download_title, threadDownloaderPinsCount, threadDownloaderPinsCount);

        if (unviewedPostsCount != 0 && threadDownloaderPinsCount == 0) {
            return watchNewQuotesTitle;
        } else if (unviewedPostsCount == 0 && threadDownloaderPinsCount != 0) {
            return downloadTitle;
        }

        return String.format("%s, %s", watchNewQuotesTitle, downloadTitle);
    }

    private String formatNotificationTitleNewQuotes(int listQuotingCount, int threadDownloaderPinsCount) {
        String watchNewQuotesTitle =
                getQuantityString(R.plurals.watch_new_quotes, listQuotingCount, listQuotingCount, listQuotingCount);
        String downloadTitle =
                getQuantityString(R.plurals.download_title, threadDownloaderPinsCount, threadDownloaderPinsCount);

        if (listQuotingCount != 0 && threadDownloaderPinsCount == 0) {
            return watchNewQuotesTitle;
        } else if (listQuotingCount == 0 && threadDownloaderPinsCount != 0) {
            return downloadTitle;
        }

        return String.format("%s, %s", watchNewQuotesTitle, downloadTitle);
    }

    private String formatNotificationTitleNewQuoting(
            int unviewedPostsCount, int listQuotingCount, int threadDownloaderPinsCount
    ) {
        String watchNewTitle = getQuantityString(R.plurals.watch_new_quoting,
                unviewedPostsCount,
                unviewedPostsCount,
                listQuotingCount
        );
        String downloadTitle =
                getQuantityString(R.plurals.download_title, threadDownloaderPinsCount, threadDownloaderPinsCount);

        if (unviewedPostsCount != 0 && threadDownloaderPinsCount == 0) {
            return watchNewTitle;
        } else if (unviewedPostsCount == 0 && threadDownloaderPinsCount != 0) {
            return downloadTitle;
        }

        return String.format("%s, %s", watchNewTitle, downloadTitle);
    }

    private String formatNotificationTitle(int pinsCount, int threadDownloaderPinsCount) {
        String watchTitle = getQuantityString(R.plurals.watch_title, pinsCount, pinsCount);
        String downloadTitle =
                getQuantityString(R.plurals.download_title, threadDownloaderPinsCount, threadDownloaderPinsCount);

        if (pinsCount != 0 && threadDownloaderPinsCount == 0) {
            return watchTitle;
        } else if (pinsCount == 0 && threadDownloaderPinsCount != 0) {
            return downloadTitle;
        }

        return String.format("%s, %s", watchTitle, downloadTitle);
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
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, alertIcon ? NOTIFICATION_ID_ALERT_STR : NOTIFICATION_ID_STR);
        builder.setContentTitle(title);
        builder.setContentText(TextUtils.join(", ", expandedLines));
        builder.setOngoing(true);

        //setup launch action, add pin if there's only one thread watching
        Intent intent = new Intent(this, StartActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        intent.putExtra("pin_id", target != null ? target.id : -1);
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
            builder.setSmallIcon(R.drawable.ic_stat_notify_alert);
            builder.setPriority(NotificationCompat.PRIORITY_HIGH);
        } else {
            builder.setSmallIcon(R.drawable.ic_stat_notify);
            builder.setPriority(NotificationCompat.PRIORITY_MIN);
        }

        if (hasWatchPins) {
            //setup the pause watch button
            Intent pauseWatching = new Intent(this, WatchNotification.class);
            pauseWatching.putExtra("pause_pins", true);
            PendingIntent pauseWatchingPending =
                    PendingIntent.getService(this, 0, pauseWatching, PendingIntent.FLAG_UPDATE_CURRENT);
            builder.addAction(R.drawable.ic_action_pause, getString(R.string.watch_pause_pins), pauseWatchingPending);
        }

        //setup the display in the notification
        NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
        for (CharSequence line : expandedLines.subList(Math.max(0, expandedLines.size() - 10), expandedLines.size())) {
            style.addLine(line);
        }
        style.setBigContentTitle(title);
        builder.setStyle(style);

        return builder.build();
    }
}
