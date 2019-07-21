package org.floens.chan.ui.notification;

import android.annotation.TargetApi;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.v4.app.NotificationCompat;

import org.floens.chan.R;
import org.floens.chan.core.manager.WatchManager;
import org.floens.chan.core.model.Post;
import org.floens.chan.ui.activity.BoardActivity;

import java.util.List;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ThreadWatchNotifications extends NotificationHelper {
    public static final String CHANNEL_ID_WATCH_NORMAL = "watch:normal";
    public static final String CHANNEL_ID_WATCH_MENTION = "watch:mention";
    private static final int NOTIFICATION_ID_WATCH_NORMAL = 0x10000;
    private static final int NOTIFICATION_ID_WATCH_NORMAL_MASK = 0xffff;
    private static final int NOTIFICATION_ID_WATCH_MENTION = 0x20000;
    private static final int NOTIFICATION_ID_WATCH_MENTION_MASK = 0xffff;

    private static final String POST_COMMENT_IMAGE_PREFIX = "(img) ";
    private static final Pattern POST_COMMENT_SHORTEN_NO_PATTERN =
            Pattern.compile(">>\\d+(?=\\d{4})(\\d{4})");

    private int pendingIntentCounter = 0;

    @Inject
    public ThreadWatchNotifications(Context applicationContext) {
        super(applicationContext);
    }

    public void showForWatchers(List<WatchManager.PinWatcher> pinWatchers) {
        showPinSummaries(pinWatchers);
    }

    public void hideAll() {
        notificationManager.cancelAll();
    }

    @TargetApi(Build.VERSION_CODES.O)
    private void showPinSummaries(List<WatchManager.PinWatcher> pinWatchers) {
        if (isOreo()) {
            ensureChannels();
        }

        for (WatchManager.PinWatcher pinWatcher : pinWatchers) {
            if (!pinWatcher.requiresNotificationUpdate()) {
                continue;
            }

            // Normal thread posts.
            int normalId = NOTIFICATION_ID_WATCH_NORMAL +
                    (pinWatcher.getPinId() & NOTIFICATION_ID_WATCH_NORMAL_MASK);
            if (!pinWatcher.getUnviewedPosts().isEmpty()) {
                NotificationCompat.Builder builder =
                        buildMessagingStyleNotification(pinWatcher, pinWatcher.getUnviewedPosts(),
                                false, CHANNEL_ID_WATCH_NORMAL);
                notificationManager.notify(normalId, builder.build());
            } else {
                notificationManager.cancel(normalId);
            }

            // Posts that mention you.
            int mentionId = NOTIFICATION_ID_WATCH_MENTION +
                    (pinWatcher.getPinId() & NOTIFICATION_ID_WATCH_MENTION_MASK);
            if (!pinWatcher.getUnviewedQuotes().isEmpty()) {
                NotificationCompat.Builder builder =
                        buildMessagingStyleNotification(pinWatcher, pinWatcher.getUnviewedQuotes(),
                                true, CHANNEL_ID_WATCH_MENTION);

                notificationManager.notify(mentionId, builder.build());
            } else {
                notificationManager.cancel(mentionId);
            }

            pinWatcher.hadNotificationUpdate();
        }
    }

    private NotificationCompat.Builder buildMessagingStyleNotification(
            WatchManager.PinWatcher pinWatcher, List<Post> posts, boolean mentions,
            String channelId) {
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(applicationContext, channelId);

        NotificationCompat.MessagingStyle messagingStyle =
                new NotificationCompat.MessagingStyle("");

        builder.setSmallIcon(!mentions ?
                R.drawable.ic_stat_notify : R.drawable.ic_stat_notify_alert);
        if (mentions) {
            builder.setSubText("Mentions");
        }
        if (pinWatcher.getThumbnailBitmap() != null) {
            builder.setLargeIcon(pinWatcher.getThumbnailBitmap());
        }
        if (mentions && !isOreo()) {
            builder.setDefaults(NotificationCompat.DEFAULT_SOUND |
                    NotificationCompat.DEFAULT_VIBRATE);
        }

        String subTitle;
        if (!mentions) {
            subTitle = "(" + posts.size() + ")";
        } else {
            subTitle = "(" + posts.size() + " mentions)";
        }
        messagingStyle.setConversationTitle(pinWatcher.getTitle() + " " + subTitle);
        messagingStyle.setGroupConversation(true);
        addPostsToMessagingStyle(messagingStyle, posts);
        builder.setStyle(messagingStyle);

        setNotificationIntent(builder, pinWatcher.getPinId());

        return builder;
    }

    private void addPostsToMessagingStyle(NotificationCompat.MessagingStyle messagingStyle,
                                          List<Post> unviewedPosts) {
        final int maxLines = 25;

        if (unviewedPosts.size() > maxLines) {
            unviewedPosts = unviewedPosts.subList(
                    unviewedPosts.size() - maxLines, unviewedPosts.size());
        }

        for (Post post : unviewedPosts) {
            String comment = post.image() != null ? POST_COMMENT_IMAGE_PREFIX : "";
            if (post.comment.length() > 0) {
                comment += post.comment;
            }

            // Replace >>132456798 with >6789 to shorten the notification
            comment = POST_COMMENT_SHORTEN_NO_PATTERN.matcher(comment)
                    .replaceAll(">$1");

            CharSequence name = post.nameTripcodeIdCapcodeSpan;
//            if (name.length() == 0) {
//                name = "Anonymous";
//            }
            messagingStyle.addMessage(comment, post.time, name);
        }
    }

    private void setNotificationIntent(NotificationCompat.Builder builder, int pinId) {
        Intent intent = new Intent(applicationContext, BoardActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP |
                Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        intent.putExtra("pin_id", pinId);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                applicationContext, ++pendingIntentCounter,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);

        builder.setContentIntent(pendingIntent);
    }

    @TargetApi(Build.VERSION_CODES.O)
    public void ensureChannels() {
        NotificationChannel normalChannel = new NotificationChannel(
                CHANNEL_ID_WATCH_NORMAL, "Thread updates",
                NotificationManager.IMPORTANCE_DEFAULT);
        normalChannel.setDescription("Normal posts for threads");
        notificationManager.createNotificationChannel(normalChannel);

        NotificationChannel mentionChannel = new NotificationChannel(
                CHANNEL_ID_WATCH_MENTION, "Thread mentions",
                NotificationManager.IMPORTANCE_HIGH);
        mentionChannel.setDescription("Posts were you were mentioned");
        mentionChannel.enableVibration(true);
        mentionChannel.enableLights(true);
        notificationManager.createNotificationChannel(mentionChannel);
    }

    private boolean isOreo() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
    }
}
