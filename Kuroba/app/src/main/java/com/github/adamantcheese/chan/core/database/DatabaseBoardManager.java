package com.github.adamantcheese.chan.core.database;

import android.annotation.SuppressLint;
import android.util.Pair;

import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.SiteModel;
import com.github.adamantcheese.chan.core.site.Site;
import com.j256.ormlite.stmt.DeleteBuilder;
import com.j256.ormlite.stmt.PreparedUpdate;
import com.j256.ormlite.stmt.QueryBuilder;
import com.j256.ormlite.stmt.SelectArg;
import com.j256.ormlite.stmt.UpdateBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;

public class DatabaseBoardManager {
    private static final String TAG = "DatabaseBoardManager";

    @Inject
    DatabaseHelper helper;

    public DatabaseBoardManager() {
        inject(this);
    }

    public Callable<Board> createOrUpdate(final Board board) {
        return () -> {
            QueryBuilder<Board, Integer> q = helper.boardsDao.queryBuilder();
            q.where().eq("site", board.site.id()).and().eq("value", board.code);
            Board existing = q.queryForFirst();
            if (existing != null) {
                existing.updateExcludingUserFields(board);
                helper.boardsDao.update(existing);
                board.updateExcludingUserFields(existing);
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

    public Callable<Void> updateIncludingUserFields(final List<Board> boards) {
        return () -> {
            for (Board board : boards) {
                helper.boardsDao.update(board);
            }

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

    public Callable<Boolean> createAll(final Site site, final List<Board> boards) {
        return () -> {
            List<Board> allFromDb = helper.boardsDao.queryForEq("site", site.id());
            Map<String, Board> byCodeFromDb = new HashMap<>();
            for (Board board : allFromDb) {
                byCodeFromDb.put(board.code, board);
                board.site = site;
            }

            List<Board> toCreate = new ArrayList<>();
            List<Pair<Board, Board>> toUpdate = new ArrayList<>();
            for (Board board : boards) {
                if (byCodeFromDb.containsKey(board.code)) {
                    Board dbBoard = byCodeFromDb.get(board.code);
                    if (!dbBoard.equals(board)) {
                        toUpdate.add(new Pair<>(dbBoard, board));
                    }
                } else {
                    toCreate.add(board);
                }
            }

            if (!toCreate.isEmpty()) {
                for (Board board : toCreate) {
                    helper.boardsDao.create(board);
                }
            }

            if (!toUpdate.isEmpty()) {
                for (Pair<Board, Board> pair : toUpdate) {
                    Board dbBoard = pair.first;
                    Board newPropertiesBoard = pair.second;

                    dbBoard.updateExcludingUserFields(newPropertiesBoard);
                    helper.boardsDao.update(dbBoard);
                }
            }

            return !toCreate.isEmpty() || !toUpdate.isEmpty();
        };
    }

    public Callable<Board> getBoard(final Site site, final String code) {
        return () -> {
            Board board = helper.boardsDao.queryBuilder()
                    .where()
                    .eq("site", site.id())
                    .and()
                    .eq("value", code)
                    .queryForFirst();

            if (board != null) {
                board.site = site;
            }

            return board;
        };
    }

    @SuppressLint("UseSparseArrays")
    public Callable<List<Pair<Site, List<Board>>>> getBoardsForAllSitesOrdered(List<Site> sites) {
        return () -> {
            // Query the orders of the sites.
            QueryBuilder<SiteModel, Integer> q = helper.siteDao.queryBuilder();
            q.selectColumns("id", "order");
            List<SiteModel> modelsWithOrder = q.query();
            Map<Integer, Integer> ordering = new HashMap<>();
            for (SiteModel siteModel : modelsWithOrder) {
                ordering.put(siteModel.id, siteModel.order);
            }

            List<Site> sitesOrdered = new ArrayList<>(sites);
            // Sort the given sites array with these orders.
            Collections.sort(sitesOrdered, (lhs, rhs) -> ordering.get(lhs.id()) - ordering.get(rhs.id()));

            // Query all boards belonging to any of these sites.
            List<Integer> siteIds = new ArrayList<>(sitesOrdered.size());
            for (Site site : sitesOrdered) {
                siteIds.add(site.id());
            }
            List<Board> allBoards = helper.boardsDao.queryBuilder().where().in("site", siteIds).query();

            // Map the boards from siteId to a list of boards.
            Map<Integer, Site> sitesById = new HashMap<>();
            for (Site site : sites) {
                sitesById.put(site.id(), site);
            }
            Map<Integer, List<Board>> bySite = new HashMap<>();
            for (Board board : allBoards) {
                board.site = sitesById.get(board.siteId);

                List<Board> boards = bySite.get(board.siteId);
                if (boards == null) {
                    boards = new ArrayList<>();
                    bySite.put(board.siteId, boards);
                }
                boards.add(board);
            }

            // And map the site to the board, and order these boards.
            List<Pair<Site, List<Board>>> res = new ArrayList<>();
            for (Site site : sitesOrdered) {
                List<Board> siteBoards = bySite.get(site.id());
                if (siteBoards == null) siteBoards = new ArrayList<>();
                Collections.sort(siteBoards, (lhs, rhs) -> lhs.order - rhs.order);
                res.add(new Pair<>(site, siteBoards));
            }
            return res;
        };
    }

    public Callable<List<Board>> getSiteBoards(final Site site) {
        return () -> {
            List<Board> boards = helper.boardsDao.queryBuilder().where().eq("site", site.id()).query();
            for (int i = 0; i < boards.size(); i++) {
                Board board = boards.get(i);
                board.site = site;
            }
            return boards;
        };
    }

    public Callable<List<Board>> getSiteSavedBoards(final Site site) {
        return () -> {
            List<Board> boards =
                    helper.boardsDao.queryBuilder().where().eq("site", site.id()).and().eq("saved", true).query();
            for (int i = 0; i < boards.size(); i++) {
                Board board = boards.get(i);
                board.site = site;
            }
            return boards;
        };
    }

    public Callable<Void> deleteBoards(Site site) {
        return () -> {
            DeleteBuilder<Board, Integer> builder = helper.boardsDao.deleteBuilder();

            builder.where().eq("site", site.id());
            builder.delete();

            return null;
        };
    }
}
