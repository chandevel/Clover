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

import org.floens.chan.core.database.DatabaseManager;
import org.floens.chan.core.manager.BoardManager;
import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.model.orm.Loadable;
import org.floens.chan.core.repository.BoardRepository;
import org.floens.chan.core.site.Site;

import java.util.Observable;
import java.util.Observer;

import javax.inject.Inject;

public class BrowsePresenter implements Observer {
    private final DatabaseManager databaseManager;
    private final BoardManager boardManager;
    private Callback callback;

	private boolean hadBoards;
    private Board currentBoard;

    private BoardRepository.SitesBoards savedBoardsObservable;

    @Inject
    public BrowsePresenter(DatabaseManager databaseManager, BoardManager boardManager) {
        this.databaseManager = databaseManager;
        this.boardManager = boardManager;

        savedBoardsObservable = boardManager.getSavedBoardsObservable();

        hadBoards = hasBoards();
    }

    public void create(Callback callback) {
        this.callback = callback;

        savedBoardsObservable.addObserver(this);
    }

    public void destroy() {
        savedBoardsObservable.deleteObserver(this);
    }

    public Board currentBoard() {
        return currentBoard;
    }

    public void setBoard(Board board) {
        loadBoard(board);
    }

    public void onBoardsFloatingMenuBoardClicked(Board board) {
        loadBoard(board);
    }

    public void loadWithDefaultBoard() {
        Board first = firstBoard();
        if (first != null) {
            loadBoard(first);
        }
    }

    public void onBoardsFloatingMenuSiteClicked(Site site) {
        callback.loadSiteSetup(site);
    }

    @Override
    public void update(Observable o, Object arg) {
        if (o == savedBoardsObservable) {
            if (!hadBoards && hasBoards()) {
                hadBoards = true;
                loadWithDefaultBoard();
            }
        }
    }

    private boolean hasBoards() {
        return firstBoard() != null;
    }

    private Board firstBoard() {
        for (BoardRepository.SiteBoards item : savedBoardsObservable.get()) {
            if (!item.boards.isEmpty()) {
                return item.boards.get(0);
            }
        }
        return null;
    }

    private Loadable getLoadableForBoard(Board board) {
        return databaseManager.getDatabaseLoadableManager().get(Loadable.forCatalog(board));
    }

    private void loadBoard(Board board) {
        currentBoard = board;
        callback.loadBoard(getLoadableForBoard(board));
        callback.showArchiveOption(board.site.boardFeature(Site.BoardFeature.ARCHIVE, board));
    }

    public interface Callback {
        void loadBoard(Loadable loadable);

        void loadSiteSetup(Site site);

        void showArchiveOption(boolean show);
    }
}
