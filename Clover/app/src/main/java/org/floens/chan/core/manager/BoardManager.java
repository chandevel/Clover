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

import com.android.volley.Response;
import com.android.volley.VolleyError;

import org.floens.chan.Chan;
import org.floens.chan.chan.ChanUrls;
import org.floens.chan.core.database.DatabaseManager;
import org.floens.chan.core.model.Board;
import org.floens.chan.core.net.BoardsRequest;
import org.floens.chan.utils.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.greenrobot.event.EventBus;

public class BoardManager implements Response.Listener<List<Board>>, Response.ErrorListener {
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

    private final List<Board> boards;
    private final List<Board> savedBoards = new ArrayList<>();
    private final Map<String, Board> boardsByCode = new HashMap<>();

    public BoardManager(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        boards = databaseManager.getBoards();

        if (boards.isEmpty()) {
            Logger.d(TAG, "Loading default boards");
            boards.addAll(getDefaultBoards());
            saveDatabase();
            update(true);
        } else {
            update(false);
        }

        Chan.getVolleyRequestQueue().add(new BoardsRequest(ChanUrls.getBoardsUrl(), this, this));
    }

    @Override
    public void onResponse(List<Board> response) {
        List<Board> boardsToAddWs = new ArrayList<>();
        List<Board> boardsToAddNws = new ArrayList<>();

        for (int i = 0; i < response.size(); i++) {
            Board serverBoard = response.get(i);

            Board existing = getBoardByCode(serverBoard.code);
            if (existing != null) {
                serverBoard.id = existing.id;
                serverBoard.saved = existing.saved;
                serverBoard.order = existing.order;
                boards.set(boards.indexOf(existing), serverBoard);
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

    @Override
    public void onErrorResponse(VolleyError error) {
        Logger.e(TAG, "Failed to get boards from server");
    }

    // Thread-safe
    public boolean getBoardExists(String code) {
        return getBoardByCode(code) != null;
    }

    // Thread-safe
    public Board getBoardByCode(String code) {
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
        databaseManager.setBoards(boards);
    }

    private List<Board> getDefaultBoards() {
        List<Board> list = new ArrayList<>();
        list.add(new Board("Technology", "g", true, true));
        list.add(new Board("Food & Cooking", "ck", true, true));
        list.add(new Board("Do It Yourself", "diy", true, true));
        list.add(new Board("Animals & Nature", "an", true, true));

        Collections.shuffle(list);

        return list;
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
