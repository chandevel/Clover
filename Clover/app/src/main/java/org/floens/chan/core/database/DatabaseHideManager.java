package org.floens.chan.core.database;

import com.j256.ormlite.stmt.DeleteBuilder;
import com.j256.ormlite.table.TableUtils;

import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.orm.ThreadHide;
import org.floens.chan.core.site.Site;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class DatabaseHideManager {
    private static final long THREAD_HIDE_TRIM_TRIGGER = 250;
    private static final long THREAD_HIDE_TRIM_COUNT = 50;

    private DatabaseManager databaseManager;
    private DatabaseHelper helper;

    private final Map<Integer, List<ThreadHide>> hides = new HashMap<>();

    public DatabaseHideManager(DatabaseManager databaseManager, DatabaseHelper helper) {
        this.databaseManager = databaseManager;
        this.helper = helper;
    }

    public Callable<Void> load() {
        return () -> {
            databaseManager.trimTable(helper.threadHideDao, "threadhide",
                    THREAD_HIDE_TRIM_TRIGGER, THREAD_HIDE_TRIM_COUNT);

            synchronized (hides) {
                hides.clear();

                List<ThreadHide> threadHides = helper.threadHideDao.queryForAll();
                for (ThreadHide hide : threadHides) {
                    List<ThreadHide> hidesForId = hides.get(hide.no);
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
     * Returns if the given post is hidden. The Post must be a OP of a thread.
     * <p>
     * This method is thread-safe, and doesn't need to be called through
     * {@link DatabaseManager#runTask(Callable)}.
     *
     * @param post The Post to check if it is hidden.
     * @return {@code true} if hidden, {@code false} otherwise.
     */
    public boolean isThreadHidden(Post post) {
        synchronized (hides) {
            if (hides.containsKey(post.no)) {
                for (ThreadHide threadHide : hides.get(post.no)) {
                    if (threadHide.equalsPost(post)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public Callable<Void> addThreadHide(ThreadHide hide) {
        return () -> {
            helper.threadHideDao.create(hide);

            synchronized (hides) {
                List<ThreadHide> hidesForId = hides.get(hide.no);
                if (hidesForId == null) {
                    hidesForId = new ArrayList<>(1);
                    hides.put(hide.no, hidesForId);
                }

                hidesForId.add(hide);
            }

            return null;
        };
    }

    public Callable<Void> removeThreadHide(ThreadHide hide) {
        return () -> {
            helper.threadHideDao.delete(hide);

            synchronized (hides) {
                List<ThreadHide> hidesForId = hides.get(hide.no);
                if (hidesForId != null) {
                    hidesForId.remove(hide);
                }
            }

            return null;
        };
    }

    public Callable<Void> clearAllThreadHides() {
        return () -> {
            TableUtils.clearTable(helper.getConnectionSource(), ThreadHide.class);

            synchronized (hides) {
                hides.clear();
            }

            return null;
        };
    }

    public Callable<Void> deleteThreadHides(Site site) {
        return () -> {
            DeleteBuilder<ThreadHide, Integer> builder = helper.threadHideDao.deleteBuilder();
            builder.where().eq("site", site.id());
            builder.delete();

            return null;
        };
    }
}
