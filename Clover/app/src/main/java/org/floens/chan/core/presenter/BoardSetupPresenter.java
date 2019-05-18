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
import org.floens.chan.core.repository.BoardRepository;
import org.floens.chan.core.site.Site;
import org.floens.chan.ui.helper.BoardHelper;
import org.floens.chan.utils.BackgroundUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.inject.Inject;

public class BoardSetupPresenter implements Observer {
    private BoardManager boardManager;

    private Callback callback;
    private AddCallback addCallback;

    private Site site;

    private List<Board> savedBoards;

    private BoardRepository.SitesBoards allBoardsObservable;

    private Executor executor = Executors.newSingleThreadExecutor();
    private BackgroundUtils.Cancelable suggestionCall;

    private List<BoardSuggestion> suggestions = new ArrayList<>();
    private List<String> selectedSuggestions = new LinkedList<>();

    private String suggestionsQuery = null;
    private final BoardSortingInfo boardSortingInfo = BoardSortingInfo.defaultSorting();

    @Inject
    public BoardSetupPresenter(BoardManager boardManager) {
        this.boardManager = boardManager;
    }

    public void create(Callback callback, Site site) {
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
                // Update the boards shown in the query.
                queryBoardsWithQueryAndShowInAddDialog();
            }
        }
    }

    public void addClicked() {
        callback.showAddDialog();
    }

    public void bindAddDialog(AddCallback addCallback) {
        this.addCallback = addCallback;

        queryBoardsWithQueryAndShowInAddDialog();
    }

    public void unbindAddDialog() {
        this.addCallback = null;
        suggestions.clear();
        selectedSuggestions.clear();
        suggestionsQuery = null;
    }

    public void onSelectAllClicked() {
        for (BoardSuggestion suggestion : suggestions) {
            suggestion.checked = true;
            selectedSuggestions.add(suggestion.getCode());
        }
        addCallback.suggestionsWereChanged();
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

        List<Board> boardsToSave = new ArrayList<>();

        if (site.boardsType().canList) {
            List<Board> siteBoards = boardManager.getSiteBoards(site);
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

    public void remove(int position) {
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
        suggestionsQuery = userQuery;
        queryBoardsWithQueryAndShowInAddDialog();
    }

    private void queryBoardsWithQueryAndShowInAddDialog() {
        if (suggestionCall != null) {
            suggestionCall.cancel();
        }

        final String query = suggestionsQuery == null ? null :
                suggestionsQuery.replace("/", "").replace("\\", "");
        suggestionCall = BackgroundUtils.runWithExecutor(executor, () -> {
            List<BoardSuggestion> suggestions = new ArrayList<>();
            if (site.boardsType().canList) {
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

            BoardSortingInfo localBoardSortingInfo;

            synchronized (boardSortingInfo) {
                localBoardSortingInfo = new BoardSortingInfo(boardSortingInfo);
            }

            return sortSuggestions(suggestions, localBoardSortingInfo);
        }, result -> {
            updateSuggestions(result);

            if (addCallback != null) {
                addCallback.suggestionsWereChanged();
            }
        });
    }

    private List<BoardSuggestion> sortSuggestions(
            List<BoardSuggestion> suggestions,
            BoardSortingInfo boardSortingInfo) {
        Collections.sort(suggestions, new BoardSuggestionComparator(boardSortingInfo));
        return suggestions;
    }

    private void updateSuggestions(List<BoardSuggestion> suggestions) {
        this.suggestions = suggestions;
        for (BoardSuggestion suggestion : this.suggestions) {
            suggestion.checked = selectedSuggestions.contains(suggestion.getCode());
        }
    }

    private void setOrder() {
        for (int i = 0; i < savedBoards.size(); i++) {
            Board b = savedBoards.get(i);
            b.order = i;
        }
    }

    public void updateSortingMode(BoardSortingInfo.BoardSortingMode sortingMode) {
        boardSortingInfo.setSortingMode(sortingMode);
        queryBoardsWithQueryAndShowInAddDialog();
    }

    public void updateSortingOrder(BoardSortingInfo.BoardSortingOrder sortingOrder) {
        boardSortingInfo.setSortingOrder(sortingOrder);
        queryBoardsWithQueryAndShowInAddDialog();
    }

    public interface Callback {
        void showAddDialog();

        void setSavedBoards(List<Board> savedBoards);

        void showRemovedSnackbar(Board board);

        void boardsWereAdded(int count);
    }

    public interface AddCallback {
        void suggestionsWereChanged();
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

    private static class BoardSuggestionComparator implements Comparator<BoardSuggestion> {
        public static final int SORT_BY_BOARD_CODE = 1 << 0;
        public static final int SORT_BY_BOARD_NAME = 1 << 1;
        public static final int SORT_ASCENDING = 1 << 8;
        public static final int SORT_DESCENDING = 1 << 9;

        private int options;

        public BoardSuggestionComparator(BoardSortingInfo boardSortingInfo) {
            options = boardSortingInfo.getSortingMode().value | boardSortingInfo.getSortingOrder().value;
        }

        @Override
        public int compare(BoardSuggestion o1, BoardSuggestion o2) {
            if (o1 == null && o2 == null) {
                return 0;
            } else if (o1 == null) {
                return -1;
            } else if (o2 == null) {
                return 1;
            }

            if (o1.board == null && o2.board == null) {
                return 0;
            } else if (o1.board == null) {
                return -1;
            }  else if (o2.board == null) {
                return 1;
            }

            String str1;
            String str2;

            if (((options & 0xFF) & SORT_BY_BOARD_CODE) != 0) {
                str1 = o1.code;
                str2 = o2.code;
            } else if (((options & 0xFF) & SORT_BY_BOARD_NAME) != 0) {
                str1 = o1.board.name;
                str2 = o2.board.name;
            } else {
                throw new IllegalStateException("Unknown options flag: " + (options & 0xFF));
            }

            int multiplier = (((options & 0xFF00) & SORT_ASCENDING) != 0) ? 1 : -1;
            return str1.compareTo(str2) * multiplier;
        }

    }

    public static class BoardSortingInfo {
        private BoardSortingMode sortingMode;
        private BoardSortingOrder sortingOrder;

        public BoardSortingInfo(BoardSortingMode sortingMode, BoardSortingOrder sortingOrder) {
            this.sortingMode = sortingMode;
            this.sortingOrder = sortingOrder;
        }

        public BoardSortingInfo(BoardSortingInfo other) {
            this.sortingMode = other.sortingMode;
            this.sortingOrder = other.sortingOrder;
        }

        public static BoardSortingInfo defaultSorting() {
            return new BoardSortingInfo(
                    BoardSortingMode.ByBoardCode,
                    BoardSortingOrder.Ascending);
        }

        public BoardSortingMode getSortingMode() {
            return sortingMode;
        }

        public BoardSortingOrder getSortingOrder() {
            return sortingOrder;
        }

        public void setSortingMode(BoardSortingMode sortingMode) {
            this.sortingMode = sortingMode;
        }

        public void setSortingOrder(BoardSortingOrder sortingOrder) {
            this.sortingOrder = sortingOrder;
        }

        public enum BoardSortingMode {
            ByBoardCode(BoardSuggestionComparator.SORT_BY_BOARD_CODE),
            ByBoardName(BoardSuggestionComparator.SORT_BY_BOARD_NAME);

            private int value;

            BoardSortingMode(int value) {
                this.value = value;
            }

            public int getValue() {
                return value;
            }
        }

        public enum BoardSortingOrder {
            Ascending(BoardSuggestionComparator.SORT_ASCENDING),
            Descending(BoardSuggestionComparator.SORT_DESCENDING);

            private int value;

            BoardSortingOrder(int value) {
                this.value = value;
            }

            public int getValue() {
                return value;
            }
        }
    }
}
