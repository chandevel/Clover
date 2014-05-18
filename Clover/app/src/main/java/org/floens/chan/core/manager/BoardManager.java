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

import org.floens.chan.ChanApplication;
import org.floens.chan.chan.ChanUrls;
import org.floens.chan.core.model.Board;
import org.floens.chan.core.net.BoardsRequest;
import org.floens.chan.utils.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class BoardManager {
    private static final String TAG = "BoardManager";
    private static final Comparator<Board> savedOrder = new Comparator<Board>() {
        @Override
        public int compare(Board lhs, Board rhs) {
            return lhs.order < rhs.order ? -1 : 1;
        }
    };

    private List<Board> allBoards;

    private final List<String> savedKeys = new ArrayList<String>();
    private final List<String> savedValues = new ArrayList<String>();

    public BoardManager() {
        loadBoards();
        loadFromServer();
    }

    public List<Board> getAllBoards() {
        return allBoards;
    }

    public List<Board> getSavedBoards() {
        List<Board> saved = new ArrayList<Board>(allBoards.size());

        for (Board b : allBoards) {
            if (b.saved)
                saved.add(b);
        }

        Collections.sort(saved, savedOrder);

        return saved;
    }

    public List<String> getSavedKeys() {
        return savedKeys;
    }

    public List<String> getSavedValues() {
        return savedValues;
    }

    public boolean getBoardExists(String board) {
        for (Board e : getAllBoards()) {
            if (e.value.equals(board)) {
                return true;
            }
        }

        return false;
    }

    public String getBoardKey(String value) {
        for (Board e : allBoards) {
            if (e.value.equals(value)) {
                return e.key;
            }
        }

        return null;
    }

    public void updateSavedBoards() {
        ChanApplication.getDatabaseManager().updateBoards(allBoards);
        reloadSavedKeysValues();
    }

    private void reloadSavedKeysValues() {
        List<Board> saved = getSavedBoards();

        savedKeys.clear();
        for (Board board : saved) {
            savedKeys.add(board.key);
        }

        savedValues.clear();
        for (Board board : saved) {
            savedValues.add(board.value);
        }
    }

    private void storeBoards() {
        Logger.d(TAG, "Storing boards in database");

        for (Board test : allBoards) {
            if (test.saved) {
                Logger.w(TAG, "Board with value " + test.value + " saved");
            }
        }

        ChanApplication.getDatabaseManager().setBoards(allBoards);
    }

    private void loadBoards() {
        allBoards = ChanApplication.getDatabaseManager().getBoards();
        if (allBoards.size() == 0) {
            Logger.d(TAG, "Loading default boards");
            allBoards = getDefaultBoards();
            storeBoards();
        }

        reloadSavedKeysValues();
    }

    private void setBoardsFromServer(List<Board> list) {
        boolean changed = false;
        for (Board serverBoard : list) {
            boolean has = false;
            for (Board b : allBoards) {
                if (b.valueEquals(serverBoard)) {
                    has = true;
                    break;
                }
            }

            if (!has) {
                Logger.d(TAG, "Adding unknown board: " + serverBoard.value);
                allBoards.add(serverBoard);
                changed = true;
            }
        }

        if (changed) {
            storeBoards();
        }
    }

    private void loadFromServer() {
        ChanApplication.getVolleyRequestQueue().add(
                new BoardsRequest(ChanUrls.getBoardsUrl(), new Response.Listener<List<Board>>() {
                    @Override
                    public void onResponse(List<Board> data) {
                        Logger.i(TAG, "Got boards from server");
                        setBoardsFromServer(data);
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Logger.e(TAG, "Failed to get boards from server");
                    }
                })
        );
    }

    private List<Board> getDefaultBoards() {
        List<Board> list = new ArrayList<Board>();
        list.add(new Board("Technology", "g", true, true));
        list.add(new Board("Video Games", "v", true, true));
        list.add(new Board("Anime & Manga", "a", true, true));
        list.add(new Board("Comics & Cartoons", "co", true, true));
        list.add(new Board("International", "int", true, true));
        return list;
    }
}
