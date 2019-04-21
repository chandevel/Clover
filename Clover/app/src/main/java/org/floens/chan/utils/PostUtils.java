package org.floens.chan.utils;

import android.annotation.SuppressLint;

import org.floens.chan.core.model.ChanThread;
import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.orm.PostHide;

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

    public static Set<Post> findPostWithReplies(int id, ChanThread thread) {
        Set<Post> postsSet = new HashSet<>();
        if (thread == null) {
            return postsSet;
        }

        findPostWithRepliesRecursive(id, thread, postsSet);
        return postsSet;
    }

    /**
     * Finds a post by it's id and then finds all posts that has replied to this post recursively
     */
    //TODO: do the searching on a background thread?
    private static void findPostWithRepliesRecursive(int id, ChanThread thread, Set<Post> postsSet) {
        for (Post post : thread.posts) {
            if (post.no == id && !postsSet.contains(post)) {
                postsSet.add(post);

                for (Integer replyId : post.repliesFrom) {
                    findPostWithRepliesRecursive(replyId, thread, postsSet);
                }
            }
        }
    }

    /**
     * For every already hidden post checks whether there is a post that replies to this hidden post.
     * Collects all hidden posts with their replies.
     * This function is VERY slow so it must be executed on the background thread
     * */
    public static List<PostHide> findHiddenPostsWithReplies(
            List<PostHide> hiddenPostsFirstIteration,
            List<Post> posts
    ) {
        @SuppressLint("UseSparseArrays")
        Map<Integer, PostHide> hiddenPostsFastLookupMap = new HashMap<>();
        @SuppressLint("UseSparseArrays")
        Map<Integer, Post> postsFastLookupMap = new HashMap<>();

        for (PostHide postHide : hiddenPostsFirstIteration) {
            hiddenPostsFastLookupMap.put(postHide.no, postHide);
        }

        for (Post post : posts) {
            postsFastLookupMap.put(post.no, post);
        }

        List<PostHide> newHiddenPosts = search(
                hiddenPostsFastLookupMap,
                postsFastLookupMap,
                posts);

        if (newHiddenPosts.isEmpty()) {
            return hiddenPostsFirstIteration;
        }

        List<PostHide> resultList = new ArrayList<>(hiddenPostsFirstIteration.size());
        resultList.addAll(hiddenPostsFirstIteration);
        resultList.addAll(newHiddenPosts);

        return resultList;
    }

    private static List<PostHide> search(
            Map<Integer, PostHide> hiddenPostsFastLookupMap,
            Map<Integer, Post> postsFastLookupMap,
            List<Post> posts
    ) {
        Set<PostHide> newHiddenPosts = new HashSet<>();

        for (Post post : posts) {
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

                // posts is hidden no need to check the remaining replies
                break;
            }
        }

        return new ArrayList<>(newHiddenPosts);
    }

}
