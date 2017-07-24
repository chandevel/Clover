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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import de.greenrobot.event.EventBus;

/**
 * Keeps track of {@link Board}s that the user has "saved" to their list.
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
    private final Site defaultSite;

    private final List<Board> boards;
    private final List<Board> savedBoards = new ArrayList<>();
    private final Map<String, Board> boardsByCode = new HashMap<>();

    @Inject
    public BoardManager(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        defaultSite = Sites.defaultSite();
        boards = databaseManager.runTaskSync(databaseManager.getDatabaseBoardManager().getBoards(defaultSite));

        if (boards.isEmpty()) {
            update(false);
        }
    }

    private void appendBoards(Boards response) {
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
    }

    private void update(boolean notify) {
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
    }

    private void saveDatabase() {
        databaseManager.runTask(databaseManager.getDatabaseBoardManager().setBoards(boards));
    }

    private List<Board> filterSaved(List<Board> all) {
        List<Board> saved = new ArrayList<>(all.size());
        for (int i = 0; i < all.size(); i++) {
            Board board = all.get(i);
            if (board.saved) {
                saved.add(board);
            }
        }
        Collections.sort(saved, ORDER_SORT);
        return saved;
    }

    public static class BoardsChangedMessage {
    }
}
