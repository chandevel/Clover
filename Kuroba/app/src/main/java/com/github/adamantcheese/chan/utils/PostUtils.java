package com.github.adamantcheese.chan.utils;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.model.ChanThread;
import com.github.adamantcheese.chan.core.model.Post;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class PostUtils {

    //https://programming.guide/java/formatting-byte-size-to-human-readable-format.html
    public static String getReadableFileSize(long bytes) {
        if (-1000 < bytes && bytes < 1000) {
            return bytes + " B";
        }
        CharacterIterator ci = new StringCharacterIterator("kMGTPE");
        while (bytes <= -999_950 || bytes >= 999_950) {
            bytes /= 1000;
            ci.next();
        }
        return String.format(Locale.ENGLISH, "%.1f %cB", bytes / 1000.0, ci.current());
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
