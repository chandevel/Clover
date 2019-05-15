package com.github.adamantcheese.chan.utils;

import android.annotation.SuppressLint;

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

    private PostUtils() {
    }

    public static Post findPostById(int id, ChanThread thread) {
        if (thread != null) {
            for (Post post : thread.posts) {
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
     * For every already hidden post checks whether there is a post that replies to this hidden post.
     * Collects all hidden posts with their replies.
     * This function is slow so it must be executed on the background thread
     * */
    public static List<PostHide> findHiddenPostsWithReplies(
            List<PostHide> hiddenPostsFirstIteration,
            Map<Integer, Post> postsFastLookupMap
    ) {
        @SuppressLint("UseSparseArrays")
        Map<Integer, PostHide> hiddenPostsFastLookupMap = new HashMap<>();

        for (PostHide postHide : hiddenPostsFirstIteration) {
            hiddenPostsFastLookupMap.put(postHide.no, postHide);
        }

        List<PostHide> newHiddenPosts = search(
                hiddenPostsFastLookupMap,
                postsFastLookupMap);

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
     * */
    private static List<PostHide> search(
            Map<Integer, PostHide> hiddenPostsFastLookupMap,
            Map<Integer, Post> postsFastLookupMap
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
                if (repliedToPost.isOP || toInheritBaseInfoFrom == null || !toInheritBaseInfoFrom.hideRepliesToThisPost) {
                    // skip if OP or if has a flag to not hide replies to this post
                    continue;
                }

                PostHide postHide = new PostHide();
                postHide.hide = toInheritBaseInfoFrom.hide;
                postHide.site = toInheritBaseInfoFrom.site;
                postHide.board = toInheritBaseInfoFrom.board;
                postHide.hideRepliesToThisPost = toInheritBaseInfoFrom.hideRepliesToThisPost;
                postHide.no = post.no;

                //always false because there may be only one OP
                postHide.wholeThread = false;

                hiddenPostsFastLookupMap.put(post.no, postHide);
                newHiddenPosts.add(postHide);

                // post is hidden no need to check the remaining replies
                break;
            }
        }

        return new ArrayList<>(newHiddenPosts);
    }

}
