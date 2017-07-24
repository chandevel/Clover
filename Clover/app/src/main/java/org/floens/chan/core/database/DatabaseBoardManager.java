package org.floens.chan.core.database;

import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.site.Site;
import org.floens.chan.core.site.Sites;
import org.floens.chan.utils.Logger;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Callable;

public class DatabaseBoardManager {
    private static final String TAG = "DatabaseBoardManager";

    private DatabaseManager databaseManager;
    private DatabaseHelper helper;

    public DatabaseBoardManager(DatabaseManager databaseManager, DatabaseHelper helper) {
        this.databaseManager = databaseManager;
        this.helper = helper;
    }

    /**
     * Save the boards listed in the database.<br>
     * The boards need to either come from a {@link Site} or from {@link #getBoards(Site)}.
     *
     * @param boards boards to set or update
     * @return void
     */
    public Callable<Void> setBoards(final List<Board> boards) {
        return new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                for (Board b : boards) {
                    helper.boardsDao.createOrUpdate(b);
                }

                return null;
            }
        };
    }

    public Callable<List<Board>> getBoards(final Site site) {
        return new Callable<List<Board>>() {
            @Override
            public List<Board> call() throws Exception {
                List<Board> boards = null;
                try {
                    boards = helper.boardsDao.queryBuilder()
                            .where().eq("site", site.id())
                            .query();
                    for (int i = 0; i < boards.size(); i++) {
                        Board board = boards.get(i);
                        board.site = site;
                    }
                } catch (SQLException e) {
                    Logger.e(TAG, "Error getting boards from db", e);
                }

                return boards;
            }
        };
    }

    public Callable<List<Board>> getAllBoards() {
        return new Callable<List<Board>>() {
            @Override
            public List<Board> call() throws Exception {
                List<Board> boards = null;
                try {
                    boards = helper.boardsDao.queryForAll();
                    for (int i = 0; i < boards.size(); i++) {
                        Board board = boards.get(i);
                        board.site = Sites.forId(board.siteId);
                    }
                } catch (SQLException e) {
                    Logger.e(TAG, "Error getting boards from db", e);
                }
                return boards;
            }
        };
    }
}
