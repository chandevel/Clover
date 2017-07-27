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
package org.floens.chan.core.presenter;


import org.floens.chan.core.manager.BoardManager;
import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.site.Site;
import org.floens.chan.core.site.Sites;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import static org.floens.chan.Chan.getGraph;

public class BoardSetupPresenter {
    private Callback callback;

    private final List<Site> sites = new ArrayList<>();

    @Inject
    BoardManager boardManager;

    private List<Board> savedBoards;

    @Inject
    public BoardSetupPresenter() {
        getGraph().inject(this);
    }

    public void create(Callback callback) {
        this.callback = callback;

        sites.addAll(Sites.allSites());

        loadSavedBoards();
        callback.setSavedBoards(savedBoards);
    }

    public void addFromSuggestion(BoardSuggestion suggestion) {
        Board board = Board.fromSiteNameCode(suggestion.site, suggestion.key, suggestion.key);

        boardManager.saveBoard(board);

        loadSavedBoards();
        callback.setSavedBoards(savedBoards);
    }

    public void move(int from, int to) {
        Board item = savedBoards.remove(from);
        savedBoards.add(to, item);

        callback.setSavedBoards(savedBoards);
    }

    public void remove(int position) {
        Board board = savedBoards.remove(position);

        boardManager.unsaveBoard(board);

        loadSavedBoards();
        callback.setSavedBoards(savedBoards);
    }

    public List<BoardSuggestion> getSuggestionsForQuery(String query) {
        List<BoardSuggestion> suggestions = new ArrayList<>();

        for (Site site : sites) {
            suggestions.add(new BoardSuggestion(query, site));
        }

        return suggestions;
    }

    private void loadSavedBoards() {
        savedBoards = new ArrayList<>(boardManager.getSavedBoards());
    }

    public interface Callback {
        void setSavedBoards(List<Board> savedBoards);
    }

    public static class BoardSuggestion {
        public final String key;
        public final Site site;

        public BoardSuggestion(String key, Site site) {
            this.key = key;
            this.site = site;
        }
    }
}
