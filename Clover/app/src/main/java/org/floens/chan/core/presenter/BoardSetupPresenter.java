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
import org.floens.chan.ui.helper.BoardHelper;
import org.floens.chan.utils.BackgroundUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.inject.Inject;

public class BoardSetupPresenter {
    private BoardManager boardManager;

    private Callback callback;
    private AddCallback addCallback;

    private Site site;

    private List<Board> savedBoards;

    private Executor executor = Executors.newSingleThreadExecutor();
    private BackgroundUtils.Cancelable suggestionCall;

    private List<BoardSuggestion> suggestions = new ArrayList<>();
    private Set<String> selectedSuggestions = new HashSet<>();

    @Inject
    public BoardSetupPresenter(BoardManager boardManager) {
        this.boardManager = boardManager;
    }

    public void create(Callback callback, Site site) {
        this.callback = callback;
        this.site = site;

        updateSavedBoards();
        callback.setSavedBoards(savedBoards);
    }

    public void setAddCallback(AddCallback addCallback) {
        this.addCallback = addCallback;
        suggestions.clear();
        selectedSuggestions.clear();
    }

    public void addClicked() {
        callback.showAddDialog();
    }

    public void onSuggestionClicked(BoardSuggestion suggestion) {
        suggestion.checked = !suggestion.checked;
        String code = suggestion.board.code;
        if (suggestion.checked) {
            selectedSuggestions.add(code);
        } else {
            selectedSuggestions.remove(code);
        }
        addCallback.updateSuggestions();
    }

    public List<BoardSuggestion> getSuggestions() {
        return suggestions;
    }

    public void onAddDialogPositiveClicked() {
        for (BoardSuggestion suggestion : suggestions) {
            if (suggestion.checked) {
                boardManager.saveBoard(suggestion.board);
                updateSavedBoards();
                callback.setSavedBoards(savedBoards);
            }
        }
    }

    public void move(int from, int to) {
        Board item = savedBoards.remove(from);
        savedBoards.add(to, item);

        callback.setSavedBoards(savedBoards);
    }

    public void remove(int position) {
        Board board = savedBoards.remove(position);

        boardManager.unsaveBoard(board);

        updateSavedBoards();
        callback.setSavedBoards(savedBoards);
    }

    public void done() {
        callback.finish();
    }

    public void searchEntered(final String query) {
        if (suggestionCall != null) {
            suggestionCall.cancel();
        }

        suggestionCall = BackgroundUtils.runWithExecutor(executor, new Callable<List<BoardSuggestion>>() {
            @Override
            public List<BoardSuggestion> call() throws Exception {
                List<BoardSuggestion> suggestions = new ArrayList<>();
                if (site.boardsType() == Site.BoardsType.DYNAMIC) {
                    List<Board> siteBoards = boardManager.getSiteBoards(site);
                    List<Board> toSearch = new ArrayList<>();
                    for (Board siteBoard : siteBoards) {
                        if (!siteBoard.saved) {
                            toSearch.add(siteBoard);
                        }
                    }
                    List<Board> search = BoardHelper.search(toSearch, query);

                    for (Board board : search) {
                        BoardSuggestion suggestion = new BoardSuggestion(board);
                        suggestions.add(suggestion);
                    }
                } else {
                    // TODO
                    suggestions.add(new BoardSuggestion(null));
                }

                return suggestions;
            }
        }, new BackgroundUtils.BackgroundResult<List<BoardSuggestion>>() {
            @Override
            public void onResult(List<BoardSuggestion> result) {
                updateSuggestions(result);

                if (addCallback != null) {
                    addCallback.updateSuggestions();
                }
            }
        });
    }

    private void updateSavedBoards() {
        savedBoards = new ArrayList<>(boardManager.getSiteSavedBoards(site));
    }

    private void updateSuggestions(List<BoardSuggestion> suggestions) {
        this.suggestions = suggestions;
        for (BoardSuggestion suggestion : this.suggestions) {
            suggestion.checked = selectedSuggestions.contains(suggestion.board.code);
        }
    }

    public interface Callback {
        void showAddDialog();

        void setSavedBoards(List<Board> savedBoards);

        void finish();
    }

    public interface AddCallback {
        void updateSuggestions();
    }

    public static class BoardSuggestion {
        public final Board board;

        private boolean checked = false;

        public BoardSuggestion(Board board) {
            this.board = board;
        }

        public String getName() {
            return BoardHelper.getName(board);
        }

        public String getDescription() {
            return BoardHelper.getDescription(board);
        }

        public boolean isChecked() {
            return checked;
        }
    }
}
