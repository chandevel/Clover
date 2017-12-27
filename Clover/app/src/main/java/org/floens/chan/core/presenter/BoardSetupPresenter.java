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

    public void addClicked() {
        callback.showAddDialog();
    }

    public void bindAddDialog(AddCallback addCallback) {
        this.addCallback = addCallback;

        searchEntered(null);
    }

    public void unbindAddDialog() {
        this.addCallback = null;
        suggestions.clear();
        selectedSuggestions.clear();
    }

    public void onSuggestionClicked(BoardSuggestion suggestion) {
        suggestion.checked = !suggestion.checked;
        if (suggestion.checked) {
            selectedSuggestions.add(suggestion.getCode());
        } else {
            selectedSuggestions.remove(suggestion.getCode());
        }
    }

    public List<BoardSuggestion> getSuggestions() {
        return suggestions;
    }

    public void onAddDialogPositiveClicked() {
        int count = 0;

        if (site.boardsType() == Site.BoardsType.DYNAMIC) {
            for (Board board : boardManager.getSiteBoards(site)) {
                if (selectedSuggestions.contains(board.code)) {
                    boardManager.saveBoard(board);
                    boardManager.updateBoardOrder(board, savedBoards.size());
                    count++;
                }
            }
        } else {
            for (String suggestion : selectedSuggestions) {
                Board board = site.createBoard(suggestion, suggestion);
                boardManager.saveBoard(board);
                boardManager.updateBoardOrder(board, savedBoards.size());
                count++;
            }
        }

        updateSavedBoards();
        callback.setSavedBoards(savedBoards);
        callback.boardsWereAdded(count);
    }

    public void move(int from, int to) {
        Board item = savedBoards.remove(from);
        savedBoards.add(to, item);

        int min = Math.min(from, to);
        int max = Math.max(from, to);

        for (int i = min; i <= max; i++) {
            boardManager.updateBoardOrder(savedBoards.get(i), i);
        }

        callback.setSavedBoards(savedBoards);
    }

    public void remove(int position) {
        Board board = savedBoards.remove(position);
        boardManager.unsaveBoard(board);

        for (int i = position; i < savedBoards.size(); i++) {
            boardManager.updateBoardOrder(savedBoards.get(i), i);
        }

        updateSavedBoards();
        callback.setSavedBoards(savedBoards);

        callback.showRemovedSnackbar(board);
    }

    public void undoRemoveBoard(Board board) {
        boardManager.saveBoard(board);
        // TODO
        boardManager.updateBoardOrder(board, savedBoards.size());
        updateSavedBoards();
        callback.setSavedBoards(savedBoards);
    }

    public void done() {
        callback.finish();
    }

    public void searchEntered(String userQuery) {
        if (suggestionCall != null) {
            suggestionCall.cancel();
        }

        final String query = userQuery == null ? null :
                userQuery.replace("/", "").replace("\\", "");
        suggestionCall = BackgroundUtils.runWithExecutor(executor, () -> {
            List<BoardSuggestion> suggestions = new ArrayList<>();
            if (site.boardsType() == Site.BoardsType.DYNAMIC) {
                List<Board> siteBoards = boardManager.getSiteBoards(site);
                List<Board> allUnsavedBoards = new ArrayList<>();
                for (Board siteBoard : siteBoards) {
                    if (!siteBoard.saved) {
                        allUnsavedBoards.add(siteBoard);
                    }
                }

                List<Board> toSuggest;
                if (query == null || query.equals("")) {
                    toSuggest = new ArrayList<>(allUnsavedBoards.size());
                    for (Board b : allUnsavedBoards) {
                        if (b.workSafe) toSuggest.add(b);
                    }
                    for (Board b : allUnsavedBoards) {
                        if (!b.workSafe) toSuggest.add(b);
                    }
                } else {
                    toSuggest = BoardHelper.search(allUnsavedBoards, query);
                }

                for (Board board : toSuggest) {
                    BoardSuggestion suggestion = new BoardSuggestion(board);
                    suggestions.add(suggestion);
                }
            } else {
                if (query != null && !query.equals("")) {
                    suggestions.add(new BoardSuggestion(query));
                }
            }

            return suggestions;
        }, result -> {
            updateSuggestions(result);

            if (addCallback != null) {
                addCallback.updateSuggestions();
            }
        });
    }

    private void updateSavedBoards() {
        savedBoards = new ArrayList<>(boardManager.getSiteSavedBoards(site));
    }

    private void updateSuggestions(List<BoardSuggestion> suggestions) {
        this.suggestions = suggestions;
        for (BoardSuggestion suggestion : this.suggestions) {
            suggestion.checked = selectedSuggestions.contains(suggestion.getCode());
        }
    }

    public interface Callback {
        void showAddDialog();

        void setSavedBoards(List<Board> savedBoards);

        void showRemovedSnackbar(Board board);

        void finish();

        void boardsWereAdded(int count);
    }

    public interface AddCallback {
        void updateSuggestions();
    }

    public static class BoardSuggestion {
        private final Board board;
        private final String code;

        private boolean checked = false;

        BoardSuggestion(Board board) {
            this.board = board;
            this.code = board.code;
        }

        BoardSuggestion(String code) {
            this.board = null;
            this.code = code;
        }

        public String getName() {
            if (board != null) {
                return BoardHelper.getName(board);
            } else {
                return "/" + code + "/";
            }
        }

        public String getDescription() {
            if (board != null) {
                return BoardHelper.getDescription(board);
            } else {
                return "";
            }
        }

        public String getCode() {
            return code;
        }

        public boolean isChecked() {
            return checked;
        }

        public long getId() {
            return code.hashCode();
        }
    }
}
