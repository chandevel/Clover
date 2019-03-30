package org.floens.chan.core.database;

import android.util.SparseArray;

import com.j256.ormlite.table.TableUtils;

import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.orm.PostHide;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

public class DatabaseHideManager {
    private static final long POST_HIDE_TRIM_TRIGGER = 250;
    private static final long POST_HIDE_TRIM_COUNT = 50;

    private DatabaseManager databaseManager;
    private DatabaseHelper helper;

    private final SparseArray<List<PostHide>> hides = new SparseArray<>();

    public DatabaseHideManager(DatabaseManager databaseManager, DatabaseHelper helper) {
        this.databaseManager = databaseManager;
        this.helper = helper;
    }

    public Callable<Void> load() {
        return () -> {
            databaseManager.trimTable(helper.postHideDao, DatabaseHelper.POST_HIDE_TABLE_NAME,
                    POST_HIDE_TRIM_TRIGGER, POST_HIDE_TRIM_COUNT);

            synchronized (hides) {
                hides.clear();

                List<PostHide> postHides = helper.postHideDao.queryForAll();
                for (PostHide hide : postHides) {
                    List<PostHide> hidesForId = hides.get(hide.no);
                    if (hidesForId == null) {
                        hidesForId = new ArrayList<>(1);
                        hides.put(hide.no, hidesForId);
                    }

                    hidesForId.add(hide);
                }
            }

            return null;
        };
    }

    /**
     * Returns true if the given post is hidden. If the Post is OP returns true only if the user hid
     * the thread from a catalog, otherwise returns false.
     * <p>
     * This method is thread-safe, and doesn't need to be called through
     * {@link DatabaseManager#runTask(Callable)}.
     *
     * @param post The Post to check if it is hidden.
     * @return {@code true} if hidden, {@code false} otherwise.
     */
    public boolean isHidden(Post post) {
        synchronized (hides) {
            if (hides.get(post.no) == null) {
                return false;
            }

            for (PostHide postHide : hides.get(post.no)) {
                if (postHide.equalsPost(post)) {
                    if (post.isOP) {
                        //hide OP post only if the user hid the whole thread
                        return postHide.wholeThread;
                    } else {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public Callable<Void> addThreadHide(PostHide hide) {
        return () -> {
            helper.postHideDao.create(hide);

            synchronized (hides) {
                List<PostHide> hidesForId = hides.get(hide.no);
                if (hidesForId == null) {
                    hidesForId = new ArrayList<>(1);
                    hides.put(hide.no, hidesForId);
                }

                hidesForId.add(hide);
            }

            return null;
        };
    }

    public Callable<Void> addPostsHide(List<PostHide> hideList) {
        return () -> {
            for (PostHide postHide : hideList) {
                helper.postHideDao.create(postHide);
            }

            synchronized (hides) {
                for (PostHide postHide : hideList) {
                    List<PostHide> hidesForId = hides.get(postHide.no);
                    if (hidesForId == null) {
                        hidesForId = new ArrayList<>(1);
                        hides.put(postHide.no, hidesForId);
                    }

                    hidesForId.add(postHide);
                }
            }

            return null;
        };
    }

    public Callable<Void> removeThreadHide(PostHide hide) {
        return () -> {
            helper.postHideDao.delete(hide);

            synchronized (hides) {
                List<PostHide> hidesForId = hides.get(hide.no);
                if (hidesForId != null) {
                    hidesForId.remove(hide);
                }
            }

            return null;
        };
    }

    public Callable<Void> removePostsHide(List<PostHide> hideList) {
        return () -> {
            for (PostHide postHide : hideList) {
                helper.postHideDao.delete(postHide);
            }

            synchronized (hides) {
                for (PostHide postHide : hideList) {
                    List<PostHide> hidesForId = hides.get(postHide.no);
                    if (hidesForId != null) {
                        hidesForId.remove(postHide);
                    }
                }
            }

            return null;
        };
    }

    public Callable<Void> clearAllThreadHides() {
        return () -> {
            TableUtils.clearTable(helper.getConnectionSource(), PostHide.class);

            synchronized (hides) {
                hides.clear();
            }

            return null;
        };
    }
}
