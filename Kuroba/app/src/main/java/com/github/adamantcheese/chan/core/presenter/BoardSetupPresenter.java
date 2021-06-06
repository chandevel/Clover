/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
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
package com.github.adamantcheese.chan.core.presenter;

import android.text.TextUtils;

import com.github.adamantcheese.chan.core.manager.BoardManager;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.repository.BoardRepository;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.Boards;
import com.github.adamantcheese.chan.ui.helper.BoardHelper;
import com.github.adamantcheese.chan.utils.BackgroundUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;

import javax.inject.Inject;

public class BoardSetupPresenter
        implements Observer {
    private final BoardManager boardManager;

    private PresenterCallback callback;
    private LayoutCallback addCallback;

    private Site site;

    private Boards savedBoards;

    private BoardRepository.SitesBoards allBoardsObservable;

    private BackgroundUtils.Cancelable suggestionCall;

    @Inject
    public BoardSetupPresenter(BoardManager boardManager) {
        this.boardManager = boardManager;
    }

    public void create(PresenterCallback callback, Site site) {
        this.callback = callback;
        this.site = site;

        savedBoards = boardManager.getSiteSavedBoards(site);
        callback.setSavedBoards(savedBoards);

        allBoardsObservable = boardManager.getAllBoardsObservable();
        allBoardsObservable.addObserver(this);
    }

    public void destroy() {
        boardManager.updateBoardOrders(savedBoards);

        allBoardsObservable.deleteObserver(this);
    }

    @Override
    public void update(Observable o, Object arg) {
        if (o == allBoardsObservable) {
            if (addCallback != null) {
                searchEntered(null);
            }
        }
    }

    public void addClicked() {
        callback.showAddDialog();
    }

    public void bindAddDialog(LayoutCallback addCallback) {
        this.addCallback = addCallback;
        searchEntered(null);
    }

    public void unbindAddDialog() {
        this.addCallback = null;
    }

    public void onAddDialogPositiveClicked() {
        int count = 0;

        List<String> selectedSuggestions = addCallback.getCheckedSuggestions();
        Boards boardsToSave = new Boards();

        if (site.boardsType().canList) {
            Boards siteBoards = boardManager.getSiteBoards(site);
            Map<String, Board> siteBoardsByCode = new HashMap<>();
            for (Board siteBoard : siteBoards) {
                siteBoardsByCode.put(siteBoard.code, siteBoard);
            }
            for (String selectedSuggestion : selectedSuggestions) {
                Board board = siteBoardsByCode.get(selectedSuggestion);
                if (board != null) {
                    boardsToSave.add(board);
                    savedBoards.add(board);
                    count++;
                }
            }
        } else {
            for (String suggestion : selectedSuggestions) {
                Board board = site.createBoard(suggestion, suggestion);
                boardsToSave.add(board);
                savedBoards.add(board);
                count++;
            }
        }

        boardManager.setAllSaved(boardsToSave, true);

        setOrder();
        callback.setSavedBoards(savedBoards);
        callback.boardsWereAdded(count);
    }

    public void move(int from, int to) {
        Board item = savedBoards.remove(from);
        savedBoards.add(to, item);
        setOrder();

        callback.setSavedBoards(savedBoards);
    }

    public void removeBoard(int position) {
        Board board = savedBoards.remove(position);
        boardManager.setSaved(board, false);

        setOrder();
        callback.setSavedBoards(savedBoards);

        callback.showRemovedSnackbar(board);
    }

    public void undoRemoveBoard(Board board) {
        boardManager.setSaved(board, true);
        savedBoards.add(board.order, board);
        setOrder();
        callback.setSavedBoards(savedBoards);
    }

    public void searchEntered(String userQuery) {
        if (suggestionCall != null) {
            suggestionCall.cancel();
        }

        final String query = userQuery == null ? null : userQuery.replace("/", "").replace("\\", "");
        suggestionCall = BackgroundUtils.runWithExecutor(BackgroundUtils.backgroundService, () -> {
            List<BoardSuggestion> suggestions = new ArrayList<>();
            if (site.boardsType().canList) {
                Boards siteBoards = boardManager.getSiteBoards(site);
                Boards allUnsavedBoards = new Boards();
                for (Board siteBoard : siteBoards) {
                    if (!siteBoard.saved) {
                        allUnsavedBoards.add(siteBoard);
                    }
                }

                Boards toSuggest;
                if (TextUtils.isEmpty(query)) {
                    toSuggest = new Boards(allUnsavedBoards);
                } else {
                    toSuggest = BoardHelper.search(allUnsavedBoards, query);
                }

                for (Board board : toSuggest) {
                    BoardSuggestion suggestion = new BoardSuggestion(board);
                    suggestions.add(suggestion);
                }
            } else {
                if (!TextUtils.isEmpty(query)) {
                    suggestions.add(new BoardSuggestion(query));
                }
            }

            return suggestions;
        }, result -> {
            if (addCallback != null) {
                List<String> currentlySelectedSuggestions = addCallback.getCheckedSuggestions();
                for (BoardSuggestion suggestion : result) {
                    suggestion.checked = currentlySelectedSuggestions.contains(suggestion.code);
                }

                addCallback.suggestionsWereChanged(result);
            }
        });
    }

    private void setOrder() {
        for (int i = 0; i < savedBoards.size(); i++) {
            savedBoards.get(i).order = i;
        }
    }

    public interface PresenterCallback {
        void showAddDialog();

        void setSavedBoards(Boards savedBoards);

        void showRemovedSnackbar(Board board);

        void boardsWereAdded(int count);
    }

    public interface LayoutCallback {
        List<String> getCheckedSuggestions();

        void suggestionsWereChanged(List<BoardSuggestion> suggestions);
    }

    public static class BoardSuggestion {
        private final Board board;
        public final String code;

        public boolean checked = false;

        public BoardSuggestion(Board board) {
            this.board = board;
            this.code = board.code;
        }

        public BoardSuggestion(String code) {
            this.board = null;
            this.code = code;
        }

        public String getName() {
            if (board != null) {
                return board.getFormattedName();
            } else {
                return "/" + code + "/";
            }
        }

        public String getDescription() {
            return board == null ? "" : board.description;
        }

        public boolean isChecked() {
            return checked;
        }

        public long getId() {
            return code.hashCode();
        }
    }
}
