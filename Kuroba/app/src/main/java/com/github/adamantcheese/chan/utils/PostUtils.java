package com.github.adamantcheese.chan.utils;

import android.annotation.SuppressLint;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.model.ChanThread;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.PostHide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PostUtils {

    @SuppressLint("DefaultLocale")
    public static String getReadableFileSize(long bytes) {
        //Nice stack overflow copy-paste, but it's been updated to be more correct
        //https://programming.guide/java/formatting-byte-size-to-human-readable-format.html
        //@formatter:off
        String s = bytes < 0 ? "-" : "";
        long b = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
        return b < 1000L ? bytes + " B"
                : b < 999_950L ? String.format("%s%.1f kB", s, b / 1e3)
                : (b /= 1000) < 999_950L ? String.format("%s%.1f MB", s, b / 1e3)
                : (b /= 1000) < 999_950L ? String.format("%s%.1f GB", s, b / 1e3)
                : (b /= 1000) < 999_950L ? String.format("%s%.1f TB", s, b / 1e3)
                : (b /= 1000) < 999_950L ? String.format("%s%.1f PB", s, b / 1e3)
                : String.format("%s%.1f EB", s, b / 1e6);
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

    /**
     * For every already hidden post checks whether there is a post that replies to this hidden post.
     * Collects all hidden posts with their replies.
     * This function is slow so it must be executed on the background thread
     */
    public static List<PostHide> findHiddenPostsWithReplies(
            List<PostHide> hiddenPostsFirstIteration, Map<Integer, Post> postsFastLookupMap
    ) {
        @SuppressLint("UseSparseArrays")
        Map<Integer, PostHide> hiddenPostsFastLookupMap = new HashMap<>();

        for (PostHide postHide : hiddenPostsFirstIteration) {
            hiddenPostsFastLookupMap.put(postHide.no, postHide);
        }

        List<PostHide> newHiddenPosts = search(hiddenPostsFastLookupMap, postsFastLookupMap);

        if (newHiddenPosts.isEmpty()) {
            return hiddenPostsFirstIteration;
        }

        List<PostHide> resultList = new ArrayList<>(hiddenPostsFirstIteration.size());
        resultList.addAll(hiddenPostsFirstIteration);
        resultList.addAll(newHiddenPosts);

        return resultList;
    }

    /**
     * For every post checks whether it has a reply to already hidden post and adds that post to the
     * hidden posts list if it has. Checks for some flags to decide whether that post should be hidden or not.
     */
    private static List<PostHide> search(
            Map<Integer, PostHide> hiddenPostsFastLookupMap, Map<Integer, Post> postsFastLookupMap
    ) {
        Set<PostHide> newHiddenPosts = new HashSet<>();

        for (Post post : postsFastLookupMap.values()) {
            // skip if already hidden
            if (hiddenPostsFastLookupMap.get(post.no) != null) {
                continue;
            }

            // enumerate all replies for every post
            for (Integer replyTo : post.repliesTo) {
                Post repliedToPost = postsFastLookupMap.get(replyTo);
                if (repliedToPost == null) {
                    // probably a cross-thread post
                    continue;
                }

                PostHide toInheritBaseInfoFrom = hiddenPostsFastLookupMap.get(replyTo);
                if (repliedToPost.isOP || toInheritBaseInfoFrom == null
                        || !toInheritBaseInfoFrom.hideRepliesToThisPost) {
                    // skip if OP or if has a flag to not hide replies to this post
                    continue;
                }

                PostHide postHide = PostHide.hidePost(post, false, toInheritBaseInfoFrom.hide, true);
                hiddenPostsFastLookupMap.put(post.no, postHide);
                newHiddenPosts.add(postHide);

                // post is hidden no need to check the remaining replies
                break;
            }
        }

        return new ArrayList<>(newHiddenPosts);
    }
}
