package org.floens.chan.core.database;

import android.annotation.SuppressLint;
import android.support.annotation.Nullable;

import com.j256.ormlite.stmt.DeleteBuilder;
import com.j256.ormlite.table.TableUtils;

import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.orm.PostHide;
import org.floens.chan.utils.Logger;
import org.floens.chan.utils.PostUtils;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

public class DatabaseHideManager {
    private static final String TAG = "DatabaseHideManager";

    private static final long POST_HIDE_TRIM_TRIGGER = 25000;
    private static final long POST_HIDE_TRIM_COUNT = 5000;

    private DatabaseManager databaseManager;
    private DatabaseHelper helper;

    public DatabaseHideManager(DatabaseManager databaseManager, DatabaseHelper helper) {
        this.databaseManager = databaseManager;
        this.helper = helper;
    }

    public Callable<Void> load() {
        return () -> {
            databaseManager.trimTable(helper.postHideDao, DatabaseHelper.POST_HIDE_TABLE_NAME,
                    POST_HIDE_TRIM_TRIGGER, POST_HIDE_TRIM_COUNT);

            return null;
        };
    }

    /**
     * Searches for hidden posts in the PostHide table then checks whether there are posts with a reply
     * to already hidden posts and if there are hides them as well.
     */
    public List<Post> filterHiddenPosts(List<Post> posts, int siteId, String board) {
        return databaseManager.runTask(() -> {
            List<Integer> postNoList = new ArrayList<>(posts.size());
            for (Post post : posts) {
                postNoList.add(post.no);
            }

            @SuppressLint("UseSparseArrays")
            Map<Integer, Post> postsFastLookupMap = new LinkedHashMap<>();
            for (Post post : posts) {
                postsFastLookupMap.put(post.no, post);
            }

            applyFiltersToReplies(posts, postsFastLookupMap);

            Map<Integer, PostHide> hiddenPostsFastLookupMap = getHiddenPosts(
                    siteId,
                    board,
                    postNoList);

            // find replies to hidden posts and add them to the PostHide table in the database
            // and to the hiddenPostsFastLookupMap
            hideRepliesToAlreadyHiddenPosts(postsFastLookupMap, hiddenPostsFastLookupMap);

            List<Post> resultList = new ArrayList<>();

            // filter out hidden posts
            for (Post post : postsFastLookupMap.values()) {
                if (post.filterRemove) {
                    // this post is already filtered by some custom filter
                    continue;
                }

                PostHide hiddenPost = findHiddenPost(hiddenPostsFastLookupMap, post, siteId, board);
                if (hiddenPost != null) {
                    if (hiddenPost.hide) {
                        // hide post

                        Post newPost = rebuildPostWithInheritedFilter(
                                post,
                                0,
                                true,
                                false,
                                hiddenPost.hideRepliesToThisPost);

                        resultList.add(newPost);
                    } else {
                        // remove post

                        if (post.isOP) {
                            // hide OP post only if the user hid the whole thread
                            if (!hiddenPost.wholeThread) {
                                resultList.add(post);
                            }
                        }
                    }
                } else {
                    // no record of hidden post in the DB
                    resultList.add(post);
                }
            }

            //return posts that are NOT hidden
            return resultList;
        });
    }

    private void hideRepliesToAlreadyHiddenPosts(
            Map<Integer, Post> postsFastLookupMap,
            Map<Integer, PostHide> hiddenPostsFastLookupMap
    ) throws SQLException {

        List<PostHide> newHiddenPosts = new ArrayList<>();

        for (Post post : postsFastLookupMap.values()) {
            if (hiddenPostsFastLookupMap.containsKey(post.no)) {
                continue;
            }

            for (Integer replyNo : post.repliesTo) {
                if (hiddenPostsFastLookupMap.containsKey(replyNo)) {
                    PostHide parentHiddenPost = hiddenPostsFastLookupMap.get(replyNo);
                    Post parentPost = postsFastLookupMap.get(replyNo);

                    if (!parentPost.filterReplies || !parentHiddenPost.hideRepliesToThisPost) {
                        continue;
                    }

                    PostHide newHiddenPost = new PostHide();
                    newHiddenPost.no = post.no;
                    newHiddenPost.site = parentHiddenPost.site;
                    newHiddenPost.board = parentHiddenPost.board;
                    newHiddenPost.wholeThread = false;
                    newHiddenPost.hideRepliesToThisPost = parentHiddenPost.hideRepliesToThisPost;
                    newHiddenPost.hide = parentHiddenPost.hide;

                    hiddenPostsFastLookupMap.put(newHiddenPost.no, newHiddenPost);
                    newHiddenPosts.add(newHiddenPost);

                    //post is already hidden no need to check other replies
                    break;
                }
            }
        }

        if (newHiddenPosts.isEmpty()) {
            return;
        }

        for (PostHide postHide : newHiddenPosts) {
            helper.postHideDao.createIfNotExists(postHide);
        }
    }

    private void applyFiltersToReplies(List<Post> posts, Map<Integer, Post> postsFastLookupMap) {
        for (Post post : posts) {
            if (post.isOP) {
                // skip the OP
                continue;
            }

            if (post.hasFilterParameters()) {
                if (post.filterRemove && post.filterStub) {
                    // wtf?
                    Logger.w(TAG, "Post has both filterRemove and filterStub flags");
                    continue;
                }

                applyPostFilterActionToChildPosts(post, postsFastLookupMap);
            }
        }
    }

    private Map<Integer, PostHide> getHiddenPosts(
            int siteId,
            String board,
            List<Integer> postNoList) throws SQLException {

        Set<PostHide> hiddenInDatabase = new HashSet<>(helper.postHideDao.queryBuilder().where()
                .in(PostHide.NO_COLUMN_NAME, postNoList)
                .and()
                .eq(PostHide.SITE_COLUMN_NAME, siteId)
                .and()
                .eq(PostHide.BOARD_COLUMN_NAME, board)
                .query());

        @SuppressLint("UseSparseArrays")
        Map<Integer, PostHide> hiddenMap = new HashMap<>();

        for (PostHide postHide : hiddenInDatabase) {
            hiddenMap.put(postHide.no, postHide);
        }

        return hiddenMap;
    }

    /**
     * Takes filter parameters from the post and assigns them to all posts in the current reply chain.
     * If some post already has another filter's parameters - does not overwrite them.
     * Returns a chain of hidden posts.
     * */
    private void applyPostFilterActionToChildPosts(Post parentPost, Map<Integer, Post> postsFastLookupMap) {
        if (postsFastLookupMap.isEmpty()) {
            return;
        }

        if (!parentPost.filterReplies) {
            // do nothing with replies if filtering is disabled for replies
            return;
        }

        // find all replies to the post recursively
        Set<Post> postWithAllReplies = PostUtils.findPostWithReplies(
                parentPost.no,
                new ArrayList<>(postsFastLookupMap.values()));

        Set<Integer> postNoWithAllReplies = new HashSet<>(postWithAllReplies.size());

        for (Post p : postWithAllReplies) {
            postNoWithAllReplies.add(p.no);
        }

        for (Integer no : postNoWithAllReplies) {
            if (no == parentPost.no) {
                // do nothing with the parent post
                continue;
            }

            Post childPost = postsFastLookupMap.get(no);
            if (childPost == null) {
                // cross-thread post
                continue;
            }

            // do not overwrite filter parameters from another filter
            if (!childPost.hasFilterParameters()) {
                Post newPost = rebuildPostWithInheritedFilter(
                        childPost,
                        parentPost.filterHighlightedColor,
                        parentPost.filterStub,
                        parentPost.filterRemove,
                        true);

                // assign the filter parameters to the child post
                postsFastLookupMap.put(no, newPost);

                postWithAllReplies.remove(childPost);
                postWithAllReplies.add(newPost);
            }
        }
    }


    /**
     * Rebuilds a child post with custom filter parameters
     * */
    private Post rebuildPostWithInheritedFilter(
            Post childPost,
            int filterHighlightedColor,
            boolean filterStub,
            boolean filterRemove,
            boolean filterReplies
    ) {
        return new Post.Builder()
                .board(childPost.board)
                .posterId(childPost.id)
                .opId(childPost.opId)
                .id(childPost.no)
                .op(childPost.isOP)
                .replies(childPost.getReplies())
                .images(childPost.getImagesCount())
                .uniqueIps(childPost.getUniqueIps())
                .sticky(childPost.isSticky())
                .archived(childPost.isArchived())
                .lastModified(childPost.getLastModified())
                .closed(childPost.isClosed())
                .subject(childPost.subject)
                .name(childPost.name)
                .comment(childPost.comment.toString())
                .tripcode(childPost.tripcode)
                .setUnixTimestampSeconds(childPost.time)
                .images(childPost.images)
                .moderatorCapcode(childPost.capcode)
                .httpIcons(childPost.httpIcons)
                .filter(filterHighlightedColor, filterStub, filterRemove, filterReplies)
                .isSavedReply(childPost.isSavedReply)
                .spans(childPost.subjectSpan, childPost.nameTripcodeIdCapcodeSpan)
                .linkables(childPost.linkables)
                .repliesTo(childPost.repliesTo)
                .build();
    }

    @Nullable
    private PostHide findHiddenPost(
            Map<Integer, PostHide> hiddenPostsFastLookupMap,
            Post post,
            int siteId,
            String board
    ) {
        if (hiddenPostsFastLookupMap.isEmpty()) {
            return null;
        }

        PostHide potentiallyHiddenPost = hiddenPostsFastLookupMap.get(post.no);
        if (potentiallyHiddenPost == null) {
            return null;
        }

        if (potentiallyHiddenPost.site == siteId && potentiallyHiddenPost.board.equals(board)) {
            return potentiallyHiddenPost;
        }

        return null;
    }

    public Callable<Void> addThreadHide(PostHide hide) {
        return () -> {
            if (contains(hide)) {
                return null;
            }

            helper.postHideDao.createIfNotExists(hide);

            return null;
        };
    }

    public Callable<Void> addPostsHide(List<PostHide> hideList) {
        return () -> {
            for (PostHide postHide : hideList) {
                if (contains(postHide)) {
                    continue;
                }

                helper.postHideDao.createIfNotExists(postHide);
            }

            return null;
        };
    }

    public Callable<Void> removeThreadHide(PostHide hide) {
        return () -> {
            helper.postHideDao.delete(hide);

            return null;
        };
    }

    public Callable<Void> removePostsHide(List<PostHide> hideList) {
        return () -> {
            for (PostHide postHide : hideList) {
                DeleteBuilder<PostHide, Integer> deleteBuilder = helper.postHideDao.deleteBuilder();

                deleteBuilder.where()
                        .eq(PostHide.NO_COLUMN_NAME, postHide.no)
                        .and()
                        .eq(PostHide.SITE_COLUMN_NAME, postHide.site)
                        .and()
                        .eq(PostHide.BOARD_COLUMN_NAME, postHide.board);

                deleteBuilder.delete();
            }

            return null;
        };
    }

    private boolean contains(PostHide hide) throws SQLException {
        PostHide inDb = helper.postHideDao.queryBuilder().where()
                .eq(PostHide.NO_COLUMN_NAME, hide.no)
                .and()
                .eq(PostHide.SITE_COLUMN_NAME, hide.site)
                .and()
                .eq(PostHide.BOARD_COLUMN_NAME, hide.board)
                .queryForFirst();

        //if this thread is already hidden - do nothing
        return inDb != null;
    }

    public Callable<Void> clearAllThreadHides() {
        return () -> {
            TableUtils.clearTable(helper.getConnectionSource(), PostHide.class);

            return null;
        };
    }
}
