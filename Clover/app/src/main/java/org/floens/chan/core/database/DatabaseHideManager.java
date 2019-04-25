package org.floens.chan.core.database;

import android.support.annotation.Nullable;

import com.j256.ormlite.table.TableUtils;

import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.orm.PostHide;
import org.floens.chan.utils.Logger;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
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
            databaseManager.trimTable(helper.postHideDao, "posthide",
                    POST_HIDE_TRIM_TRIGGER, POST_HIDE_TRIM_COUNT);

            return null;
        };
    }

    /**
     * Searches for hidden posts in the PostHide table and then filters out all hidden posts.
     * This function may be called from the main thread without causing an exception but it will
     * hang the main thread.
     * */
    public List<Post> filterHiddenPosts(List<Post> posts, int siteId, String board) {
        return databaseManager.runTask(() -> {
            List<Integer> postNoList = new ArrayList<>(posts.size());

            for (Post post : posts) {
                postNoList.add(post.no);
            }

            try {
                // find hidden posts
                List<PostHide> hiddenPosts = helper.postHideDao.queryBuilder().where()
                        .in("no", postNoList)
                        .and()
                        .eq("site", siteId)
                        .and()
                        .eq("board", board)
                        .query();

                List<Post> resultList = new ArrayList<>();

                // filter out hidden posts
                for (Post post : posts) {
                    PostHide hiddenPost = findHiddenPost(hiddenPosts, post, siteId, board);

                    if (hiddenPost != null) {
                        if (post.isOP) {
                            //hide OP post only if the user hid the whole thread
                            if (!hiddenPost.wholeThread) {
                                resultList.add(post);
                            }
                        }
                    } else {
                        resultList.add(post);
                    }
                }

                //return posts that are NOT hidden
                return resultList;
            } catch (SQLException e) {
                Logger.e(TAG, "Unknown error while trying to load hidden posts", e);

                // do not filter out anything if could not load hidden posts
                return posts;
            }
        });
    }

    @Nullable
    private PostHide findHiddenPost(List<PostHide> hiddenPosts, Post post, int siteId, String board) {
        for (PostHide postHide : hiddenPosts) {
            if (post.no == postHide.no && siteId == postHide.site && board.equals(postHide.board)) {
                return postHide;
            }
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
                helper.postHideDao.delete(postHide);
            }

            return null;
        };
    }

    private boolean contains(PostHide hide) throws SQLException {
        PostHide inDb = helper.postHideDao.queryBuilder().where()
                .eq("no", hide.no)
                .and()
                .eq("site", hide.site)
                .and()
                .eq("board", hide.board)
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
