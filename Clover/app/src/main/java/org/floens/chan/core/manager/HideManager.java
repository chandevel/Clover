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

import org.floens.chan.ChanApplication;
import org.floens.chan.core.model.Hide;
import org.floens.chan.core.model.Post;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class HideManager {
    private final Map<String, Set<Integer>> hiddenThreadsByBoard = new HashMap<>();

    public HideManager() {
        updateHidden();
    }

    public void addHide(Post post) {
        Hide hide = new Hide(post.board, post.no);
        ChanApplication.getDatabaseManager().addHide(hide);
        addHideToCache(hide);
    }

    public boolean isHidden(Post post) {
        Set<Integer> boardHidden = hiddenThreadsByBoard.get(post.board);
        return boardHidden != null && boardHidden.contains(post.no);
    }

    private void addHideToCache(Hide hide) {
        Set<Integer> boardHidden = hiddenThreadsByBoard.get(hide.board);
        if (boardHidden == null) {
            boardHidden = new HashSet<>();
            hiddenThreadsByBoard.put(hide.board, boardHidden);
        }
        boardHidden.add(hide.no);
    }
    private void updateHidden() {
        hiddenThreadsByBoard.clear();
        for (Hide hide : ChanApplication.getDatabaseManager().getHidden()) {
            addHideToCache(hide);
        }
    }

    public void resetBoard(String board) {
        hiddenThreadsByBoard.remove(board);
        ChanApplication.getDatabaseManager().resetHides(board);
    }
}
