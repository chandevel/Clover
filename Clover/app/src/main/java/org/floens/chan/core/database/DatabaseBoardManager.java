package org.floens.chan.core.database;

import com.j256.ormlite.stmt.QueryBuilder;

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

    public Callable<Board> createOrUpdate(final Board board) {
        return new Callable<Board>() {
            @Override
            public Board call() throws Exception {
                helper.boardsDao.createOrUpdate(board);

                return board;
            }
        };
    }

    public Callable<Void> update(final Board board) {
        return new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                helper.boardsDao.update(board);

                return null;
            }
        };
    }

    public Callable<Void> createAll(final List<Board> boards) {
        return new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                // TODO: optimize
                for (Board board : boards) {
                    QueryBuilder<Board, Integer> q = helper.boardsDao.queryBuilder();
                    q.where().eq("site", board.getSite().id())
                            .and().eq("value", board.code);
                    Board existing = q.queryForFirst();
                    if (existing != null) {
                        existing.update(board);
                        helper.boardsDao.update(existing);
                        board.update(existing);
                    } else {
                        helper.boardsDao.create(board);
                    }
                }

                return null;
            }
        };
    }

    public Callable<List<Board>> getSiteBoards(final Site site) {
        return new Callable<List<Board>>() {
            @Override
            public List<Board> call() throws Exception {
                List<Board> boards = helper.boardsDao.queryBuilder()
                        .where().eq("site", site.id())
                        .query();
                for (int i = 0; i < boards.size(); i++) {
                    Board board = boards.get(i);
                    board.site = site;
                }
                return boards;
            }
        };
    }

    public Callable<List<Board>> getSiteSavedBoards(final Site site) {
        return new Callable<List<Board>>() {
            @Override
            public List<Board> call() throws Exception {
                List<Board> boards = helper.boardsDao.queryBuilder()
                        .where().eq("site", site.id())
                        .and().eq("saved", true)
                        .query();
                for (int i = 0; i < boards.size(); i++) {
                    Board board = boards.get(i);
                    board.site = site;
                }
                return boards;
            }
        };
    }

    public Callable<List<Board>> getSavedBoards() {
        return new Callable<List<Board>>() {
            @Override
            public List<Board> call() throws Exception {
                List<Board> boards = null;
                try {
                    boards = helper.boardsDao.queryBuilder()
                            .where().eq("saved", true)
                            .query();
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
