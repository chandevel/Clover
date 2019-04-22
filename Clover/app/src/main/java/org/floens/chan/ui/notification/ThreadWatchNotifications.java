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
    private static final String CHANNEL_ID_WATCH_NORMAL = "watch:normal";
    private static final String CHANNEL_ID_WATCH_MENTION = "watch:mention";
    private static final int NOTIFICATION_ID_WATCH_NORMAL = 0x10000;
    private static final int NOTIFICATION_ID_WATCH_NORMAL_MASK = 0xffff;
    private static final int NOTIFICATION_ID_WATCH_MENTION = 0x20000;
    private static final int NOTIFICATION_ID_WATCH_MENTION_MASK = 0xffff;

    private static final String POST_COMMENT_IMAGE_PREFIX = "(img) ";
    private static final Pattern POST_COMMENT_SHORTEN_NO_PATTERN =
            Pattern.compile(">>\\d+(?=\\d{4})(\\d{4})");

    @Inject
    public ThreadWatchNotifications(Context applicationContext) {
        super(applicationContext);
    }

    public void showForWatchers(List<WatchManager.PinWatcher> pinWatchers) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            showPinSummaries(pinWatchers);
        } else {
            // legacy notification
        }
    }

    public void hide() {

    }

    @TargetApi(Build.VERSION_CODES.O)
    private void showPinSummaries(List<WatchManager.PinWatcher> pinWatchers) {
        ensureChannels();

        for (WatchManager.PinWatcher pinWatcher : pinWatchers) {
            if (!pinWatcher.requiresNotificationUpdate()) {
                continue;
            }

            // Normal thread posts.
            if (!pinWatcher.getUnviewedPosts().isEmpty()) {
                NotificationCompat.Builder builder =
                        new NotificationCompat.Builder(applicationContext,
                                CHANNEL_ID_WATCH_NORMAL);

                NotificationCompat.MessagingStyle messagingStyle =
                        new NotificationCompat.MessagingStyle("");

                builder.setSmallIcon(R.drawable.ic_stat_notify);
                if (pinWatcher.getThumbnailBitmap() != null) {
                    builder.setLargeIcon(pinWatcher.getThumbnailBitmap());
                }

                String subTitle = "(" + pinWatcher.getUnviewedPosts().size() + ")";
                messagingStyle.setConversationTitle(pinWatcher.getTitle() + " " + subTitle);
                messagingStyle.setGroupConversation(true);
                addPostsToMessagingStyle(messagingStyle, pinWatcher.getUnviewedPosts());
                builder.setStyle(messagingStyle);

                setNotificationIntent(builder);

                int id = NOTIFICATION_ID_WATCH_NORMAL +
                        (pinWatcher.getPinId() & NOTIFICATION_ID_WATCH_NORMAL_MASK);
                notificationManager.notify(id, builder.build());
            }

            // Posts that mention you.
            if (!pinWatcher.getUnviewedQuotes().isEmpty()) {
                NotificationCompat.Builder builder =
                        new NotificationCompat.Builder(applicationContext,
                                CHANNEL_ID_WATCH_MENTION);

                NotificationCompat.MessagingStyle messagingStyle =
                        new NotificationCompat.MessagingStyle("");

                builder.setSmallIcon(R.drawable.ic_stat_notify_alert);
                builder.setSubText("Mentions");
                if (pinWatcher.getThumbnailBitmap() != null) {
                    builder.setLargeIcon(pinWatcher.getThumbnailBitmap());
                }

                String subTitle = "(" + pinWatcher.getUnviewedQuotes().size() + " mentions)";
                messagingStyle.setConversationTitle(pinWatcher.getTitle() + " " + subTitle);
                messagingStyle.setGroupConversation(true);
                addPostsToMessagingStyle(messagingStyle, pinWatcher.getUnviewedQuotes());
                builder.setStyle(messagingStyle);

                setNotificationIntent(builder);

                int id = NOTIFICATION_ID_WATCH_MENTION +
                        (pinWatcher.getPinId() & NOTIFICATION_ID_WATCH_MENTION_MASK);
                notificationManager.notify(id, builder.build());
            }

            pinWatcher.hadNotificationUpdate();
        }
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

    @TargetApi(Build.VERSION_CODES.O)
    private void setNotificationIntent(NotificationCompat.Builder builder) {
        Intent intent = new Intent(applicationContext, BoardActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP |
                Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        builder.setContentIntent(pendingIntent);
    }

    @TargetApi(Build.VERSION_CODES.O)
    private void ensureChannels() {
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
}
