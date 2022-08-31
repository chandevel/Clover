package com.github.adamantcheese.chan.core.database;

import androidx.core.util.Pair;

import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.SiteModel;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.Boards;
import com.j256.ormlite.stmt.*;

import java.util.*;
import java.util.concurrent.Callable;

public class DatabaseBoardManager {
    DatabaseHelper helper;

    public DatabaseBoardManager(DatabaseHelper helper) {
        this.helper = helper;
    }

    public Callable<Void> update(final Board board) {
        return () -> {
            helper.getBoardDao().update(board);

            return null;
        };
    }

    public Callable<Void> updateAll(final Boards boards) {
        return () -> {
            for (Board board : boards) {
                helper.getBoardDao().update(board);
            }

            return null;
        };
    }

    public Callable<Void> updateOrders(final Boards boards) {
        return () -> {
            SelectArg id = new SelectArg();
            SelectArg order = new SelectArg();

            UpdateBuilder<Board, Integer> updateBuilder = helper.getBoardDao().updateBuilder();
            updateBuilder.where().eq("id", id);
            updateBuilder.updateColumnValue("order", order);
            PreparedUpdate<Board> statement = updateBuilder.prepare();

            for (int i = 0; i < boards.size(); i++) {
                Board board = boards.get(i);

                id.setValue(board.id);
                order.setValue(i);
                helper.getBoardDao().update(statement);
            }

            return null;
        };
    }

    public Callable<Boolean> createAll(final Site site, final Boards boards) {
        return () -> {
            List<Board> allFromDb = helper.getBoardDao().queryForEq("site", site.id());
            Map<String, Board> byCodeFromDb = new HashMap<>();
            for (Board board : allFromDb) {
                byCodeFromDb.put(board.code, board);
                board.site = site;
            }

            Boards toCreate = new Boards();
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
                    helper.getBoardDao().create(board);
                }
            }

            if (!toUpdate.isEmpty()) {
                for (Pair<Board, Board> pair : toUpdate) {
                    Board dbBoard = pair.first;
                    Board newPropertiesBoard = pair.second;

                    dbBoard.updateExcludingUserFields(newPropertiesBoard);
                    helper.getBoardDao().update(dbBoard);
                }
            }

            return !toCreate.isEmpty() || !toUpdate.isEmpty();
        };
    }

    public Callable<List<Pair<Site, Boards>>> getBoardsForAllSitesOrdered(List<Site> sites) {
        return () -> {
            // Query the orders of the sites.
            QueryBuilder<SiteModel, Integer> q = helper.getSiteModelDao().queryBuilder();
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
            List<Board> allBoards = helper.getBoardDao().queryBuilder().where().in("site", siteIds).query();

            // Map the boards from siteId to a list of boards.
            Map<Integer, Site> sitesById = new HashMap<>();
            for (Site site : sites) {
                sitesById.put(site.id(), site);
            }
            Map<Integer, Boards> bySite = new HashMap<>();
            for (Board board : allBoards) {
                board.site = sitesById.get(board.siteId);

                Boards boards = bySite.get(board.siteId);
                if (boards == null) {
                    boards = new Boards();
                    bySite.put(board.siteId, boards);
                }
                boards.add(board);
            }

            // And map the site to the board, and order these boards.
            List<Pair<Site, Boards>> res = new ArrayList<>();
            for (Site site : sitesOrdered) {
                Boards siteBoards = bySite.get(site.id());
                if (siteBoards == null) siteBoards = new Boards();
                Collections.sort(siteBoards, (lhs, rhs) -> lhs.order - rhs.order);
                res.add(new Pair<>(site, siteBoards));
            }
            return res;
        };
    }

    public Callable<Boards> getSiteSavedBoards(final Site site) {
        return () -> {
            List<Board> boards =
                    helper.getBoardDao().queryBuilder().where().eq("site", site.id()).and().eq("saved", true).query();
            for (int i = 0; i < boards.size(); i++) {
                Board board = boards.get(i);
                board.site = site;
            }
            return new Boards(boards);
        };
    }

    public Callable<Void> deleteBoards(Site site) {
        return () -> {
            DeleteBuilder<Board, Integer> builder = helper.getBoardDao().deleteBuilder();

            builder.where().eq("site", site.id());
            builder.delete();

            return null;
        };
    }
}
