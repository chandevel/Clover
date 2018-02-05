package org.floens.chan.core.database;

import com.j256.ormlite.stmt.PreparedUpdate;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.SelectArg;
import com.j256.ormlite.stmt.UpdateBuilder;

import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.site.Site;

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
        return () -> {
            QueryBuilder<Board, Integer> q = helper.boardsDao.queryBuilder();
            q.where().eq("site", board.getSite().id())
                    .and().eq("value", board.code);
            Board existing = q.queryForFirst();
            if (existing != null) {
                existing.updateExcudingUserFields(board);
                helper.boardsDao.update(existing);
                board.updateExcudingUserFields(existing);
            } else {
                helper.boardsDao.create(board);
            }

            return board;
        };
    }

    public Callable<Void> updateIncludingUserFields(final Board board) {
        return () -> {
            helper.boardsDao.update(board);

            return null;
        };
    }

    public Callable<Void> updateOrders(final List<Board> boards) {
        return () -> {
            SelectArg id = new SelectArg();
            SelectArg order = new SelectArg();

            UpdateBuilder<Board, Integer> updateBuilder = helper.boardsDao.updateBuilder();
            updateBuilder.where().eq("id", id);
            updateBuilder.updateColumnValue("order", order);
            PreparedUpdate<Board> statement = updateBuilder.prepare();

            for (int i = 0; i < boards.size(); i++) {
                Board board = boards.get(i);

                id.setValue(board.id);
                order.setValue(i);
                helper.boardsDao.update(statement);
            }

            return null;
        };
    }

    public Callable<Void> createAll(final List<Board> boards) {
        return () -> {
            // TODO: optimize
            for (Board board : boards) {
                QueryBuilder<Board, Integer> q = helper.boardsDao.queryBuilder();
                q.where().eq("site", board.getSite().id())
                        .and().eq("value", board.code);
                Board existing = q.queryForFirst();
                if (existing != null) {
                    existing.updateExcudingUserFields(board);
                    helper.boardsDao.update(existing);
                    board.updateExcudingUserFields(existing);
                } else {
                    helper.boardsDao.create(board);
                }
            }

            return null;
        };
    }

    public Callable<Board> getBoard(final Site site, final String code) {
        return () -> {
            Board board = helper.boardsDao.queryBuilder()
                    .where().eq("site", site.id())
                    .and().eq("value", code)
                    .queryForFirst();

            if (board != null) {
                board.site = site;
            }

            return board;
        };
    }

    public Callable<List<Board>> getSiteBoards(final Site site) {
        return () -> {
            List<Board> boards = helper.boardsDao.queryBuilder()
                    .where().eq("site", site.id())
                    .query();
            for (int i = 0; i < boards.size(); i++) {
                Board board = boards.get(i);
                board.site = site;
            }
            return boards;
        };
    }

    public Callable<List<Board>> getSiteSavedBoards(final Site site) {
        return () -> {
            List<Board> boards = helper.boardsDao.queryBuilder()
                    .where().eq("site", site.id())
                    .and().eq("saved", true)
                    .query();
            for (int i = 0; i < boards.size(); i++) {
                Board board = boards.get(i);
                board.site = site;
            }
            return boards;
        };
    }
}
