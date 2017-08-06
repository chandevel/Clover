/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.floens.chan.core.manager;

import org.floens.chan.core.database.DatabaseManager;
import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.site.Boards;
import org.floens.chan.core.site.Site;
import org.floens.chan.core.site.Sites;
import org.floens.chan.utils.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import de.greenrobot.event.EventBus;

/**
 * <p>Keeps track of {@link Board}s in the system.
 * <p>There are a few types of sites, those who provide a list of all boards known,
 * sites where users can create boards and have a very long list of known boards,
 * and those who don't provide a board list at all.
 * <p>We try to save as much info about boards as possible, this means that we try to save all
 * boards we encounter.
 * For sites with a small list of boards which does provide a board list api we save all those boards.
 * <p>All boards have a {@link Board#saved} flag indicating if it should be visible in the user's
 * favorite board list, along with a {@link Board#order} in which they appear.
 */
@Singleton
public class BoardManager {
    private static final String TAG = "BoardManager";

    private static final Comparator<Board> ORDER_SORT = new Comparator<Board>() {
        @Override
        public int compare(Board lhs, Board rhs) {
            return lhs.order - rhs.order;
        }
    };

    private static final Comparator<Board> NAME_SORT = new Comparator<Board>() {
        @Override
        public int compare(Board lhs, Board rhs) {
            return lhs.name.compareTo(rhs.name);
        }
    };

    private final DatabaseManager databaseManager;

    private final List<Board> savedBoards = new ArrayList<>();

    @Inject
    public BoardManager(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;

        loadBoards();

        fetchLimitedSitesTheirBoards();
    }

    public Board getForCode(Site site, String code) {
        if (site.boardsType() == Site.BoardsType.DYNAMIC) {
            for (Board board : getSavedBoards()) {
                if (board.site == site && board.code.equals(code)) {
                    return board;
                }
            }
            return null;
        } else {
            return Board.fromSiteNameCode(site, code, code);
        }
    }

    public List<Board> getSavedBoards() {
        return savedBoards;
    }

    public void saveBoard(Board board) {
        board.saved = true;

        board = databaseManager.runTaskSync(databaseManager.getDatabaseBoardManager().createOrUpdate(board));

        loadBoards();
    }

    public void unsaveBoard(Board board) {
        board.saved = false;

        board = databaseManager.runTaskSync(databaseManager.getDatabaseBoardManager().createOrUpdate(board));

        loadBoards();
    }

    private void loadBoards() {
        savedBoards.clear();
        savedBoards.addAll(databaseManager.runTaskSync(databaseManager.getDatabaseBoardManager().getSavedBoards()));

        EventBus.getDefault().post(new BoardsChangedMessage());
    }

    private void fetchLimitedSitesTheirBoards() {
        List<Site> sites = Sites.allSites();
        for (final Site site : sites) {
            if (site.boardsType() == Site.BoardsType.DYNAMIC) {
                site.boards(new Site.BoardsListener() {
                    @Override
                    public void onBoardsReceived(Boards boards) {
                        handleBoardsFetch(site, boards);
                    }
                });
            }
        }
    }

    private void handleBoardsFetch(Site site, Boards boards) {
        Logger.i(TAG, "Got boards for " + site.name());

        databaseManager.runTask(databaseManager.getDatabaseBoardManager().createAll(boards.boards));
    }

    /*private void appendBoards(Boards response) {
        List<Board> boardsToAddWs = new ArrayList<>();
        List<Board> boardsToAddNws = new ArrayList<>();

        for (int i = 0; i < response.boards.size(); i++) {
            Board serverBoard = response.boards.get(i);

            Board existing = getBoardByCode(serverBoard.code);
            if (existing != null) {
                existing.update(serverBoard);
            } else {
                serverBoard.saved = true;
                if (serverBoard.workSafe) {
                    boardsToAddWs.add(serverBoard);
                } else {
                    boardsToAddNws.add(serverBoard);
                }
            }
        }

        Collections.sort(boardsToAddWs, NAME_SORT);
        Collections.sort(boardsToAddNws, NAME_SORT);

        for (int i = 0; i < boardsToAddWs.size(); i++) {
            Board board = boardsToAddWs.get(i);
            board.order = boards.size();
            boards.add(board);
        }

        for (int i = 0; i < boardsToAddNws.size(); i++) {
            Board board = boardsToAddNws.get(i);
            board.order = boards.size();
            boards.add(board);
        }

        saveDatabase();
        update(true);
    }

    // Thread-safe
    private Board getBoardByCode(String code) {
        synchronized (boardsByCode) {
            return boardsByCode.get(code);
        }
    }

    public List<Board> getAllBoards() {
        return boards;
    }

    public List<Board> getSavedBoards() {
        return savedBoards;
    }

    public void flushOrderAndSaved() {
        saveDatabase();
        update(true);
    }*/

    /*private void update(boolean notify) {
        savedBoards.clear();
        savedBoards.addAll(filterSaved(boards));
        synchronized (boardsByCode) {
            boardsByCode.clear();
            for (Board board : boards) {
                boardsByCode.put(board.code, board);
            }
        }
        if (notify) {
            EventBus.getDefault().post(new BoardsChangedMessage());
        }
    }*/

    /*private void saveDatabase() {
        databaseManager.runTask(databaseManager.getDatabaseBoardManager().setBoards(boards));
    }*/

    /*private List<Board> filterSaved(List<Board> all) {
        List<Board> saved = new ArrayList<>(all.size());
        for (int i = 0; i < all.size(); i++) {
            Board board = all.get(i);
            if (board.saved) {
                saved.add(board);
            }
        }
        Collections.sort(saved, ORDER_SORT);
        return saved;
    }*/

    public static class BoardsChangedMessage {
    }
}
