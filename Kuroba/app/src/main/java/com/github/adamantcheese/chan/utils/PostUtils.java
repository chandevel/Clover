package com.github.adamantcheese.chan.utils;

import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.github.adamantcheese.chan.core.model.*;
import com.github.adamantcheese.chan.core.net.NetUtils;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.*;

import okhttp3.*;

public class PostUtils {

    //https://programming.guide/java/formatting-byte-size-to-human-readable-format.html
    // ALL sites use KiB, displayed more formally as KB, not the SI unit kB
    public static String getReadableFileSize(long bytes) {
        long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
        if (absB < 1024) {
            return bytes + " B";
        }
        long value = absB;
        CharacterIterator ci = new StringCharacterIterator("KMGTPE");
        for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
            value >>= 10;
            ci.next();
        }
        value *= Long.signum(bytes);
        return String.format(Locale.ENGLISH, "%.1f %ciB", value / 1024.0, ci.current());
    }

    public static Post findPostById(int id, @Nullable ChanThread thread) {
        if (thread != null) {
            for (Post post : thread.getPosts()) {
                if (post.no == id) {
                    return post;
                }
            }
        }
        return null;
    }

    public static Set<Post> findPostWithReplies(int id, List<Post> posts) {
        Set<Post> postsSet = new HashSet<>();
        findPostWithRepliesRecursive(id, posts, postsSet);
        return postsSet;
    }

    /**
     * Finds a post by it's id and then finds all posts that has replied to this post recursively
     */
    private static void findPostWithRepliesRecursive(int id, List<Post> posts, Set<Post> postsSet) {
        for (Post post : posts) {
            if (post.no == id && !postsSet.contains(post)) {
                postsSet.add(post);
                for (Integer replyId : post.repliesFrom) {
                    findPostWithRepliesRecursive(replyId, posts, postsSet);
                }
            }
        }
    }

    /**
     * Generate a post image summary, but also check for metadata titles and update the text in a dialog or textview.
     * One of dialog or textview needs to not be null. If textview is null, the dialog's message field will attempt to be used.
     *
     * @param image        The image to generate a summary for.
     * @param existingText Any existing text; if null, a new spannable string builder will be used.
     * @param dialog       The dialog we're pumping this info into
     * @param textView     An optional text view to write into
     */
    public static void generatePostImageSummaryAndSetTextViewWithUpdates(
            @NonNull PostImage image,
            @Nullable CharSequence existingText,
            @NonNull AlertDialog dialog,
            @Nullable TextView textView
    ) {
        StringBuilder text = new StringBuilder(existingText == null ? "" : existingText);
        text.append("Filename: ").append(image.filename).append(".").append(image.extension);
        if ("webm".equalsIgnoreCase(image.extension)) {
            // check webms for extra titles, async
            // this is a super simple example of what the embedding engine does, basically
            String checking = "\nChecking for metadata titles…";
            text.append(checking);
            Call call = NetUtils.applicationClient.newCall(new Request.Builder().url(image.imageUrl).build());
            call.enqueue(new Callback() {
                @Override
                public void onFailure(
                        @NotNull Call call, @NotNull IOException e
                ) {
                    int index = text.toString().indexOf(checking);
                    text.replace(index, index + checking.length(), "");
                    // update on main thread, this is an OkHttp thread
                    BackgroundUtils.runOnMainThread(() -> ((TextView) (textView == null
                            ? dialog.findViewById(android.R.id.message)
                            : textView)).setText(text));
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response)
                        throws IOException {
                    int index = text.toString().indexOf(checking);
                    String replaceText = ""; // clears out text if nothing found

                    byte[] bytes = new byte[2048];
                    response.body().source().read(bytes);
                    response.close();
                    for (int i = 0; i < bytes.length - 1; i++) {
                        if (((bytes[i] & 0xFF) << 8 | bytes[i + 1] & 0xFF) == 0x7ba9) {
                            byte len = (byte) (bytes[i + 2] ^ 0x80);
                            // i is the position of the length bytes, which are 2 bytes
                            // 1 after that is the actual string start
                            replaceText = "\nMetadata title: " + new String(bytes, i + 2 + 1, len);
                            break;
                        }
                    }
                    text.replace(index, index + checking.length(), replaceText);
                    // update on main thread, this is an OkHttp thread
                    BackgroundUtils.runOnMainThread(() -> ((TextView) (textView == null
                            ? dialog.findViewById(android.R.id.message)
                            : textView)).setText(text));
                }
            });
            dialog.setOnDismissListener(dialog1 -> call.cancel());
        }
        if (image.isInlined) {
            text.append("\nLinked file");
        } else {
            text.append("\nDimensions: ").append(image.imageWidth).append("x").append(image.imageHeight);
        }

        if (image.size > 0) {
            text.append("\nSize: ").append(getReadableFileSize(image.size));
        }

        if (image.spoiler()) {
            text.append("\nSpoilered");
        }

        dialog.setMessage(text);
    }
}
