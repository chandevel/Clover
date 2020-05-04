package com.github.adamantcheese.chan.utils;

import android.annotation.SuppressLint;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.model.ChanThread;
import com.github.adamantcheese.chan.core.model.Post;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PostUtils {

    @SuppressLint("DefaultLocale")
    public static String getReadableFileSize(long bytes) {
        //Nice stack overflow copy-paste, but it's been updated to be more correct
        //https://programming.guide/java/formatting-byte-size-to-human-readable-format.html
        //@formatter:off
        String s = bytes < 0 ? "-" : "";
        long b = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
        return    (b        ) < 1000L    ? bytes + " B"
                : (b        ) < 999_950L ? String.format("%s%.1f kB", s, b / 1e3)
                : (b /= 1000) < 999_950L ? String.format("%s%.1f MB", s, b / 1e3)
                : (b /= 1000) < 999_950L ? String.format("%s%.1f GB", s, b / 1e3)
                : (b /= 1000) < 999_950L ? String.format("%s%.1f TB", s, b / 1e3)
                : (b /= 1000) < 999_950L ? String.format("%s%.1f PB", s, b / 1e3)
                :                          String.format("%s%.1f EB", s, b / 1e6);
        //@formatter:on
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

                List<Integer> repliesFrom;

                synchronized (post.repliesFrom) {
                    repliesFrom = new ArrayList<>(post.repliesFrom);
                }

                for (Integer replyId : repliesFrom) {
                    findPostWithRepliesRecursive(replyId, posts, postsSet);
                }
            }
        }
    }
}
