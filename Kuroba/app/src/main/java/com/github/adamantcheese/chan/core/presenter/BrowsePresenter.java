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

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.manager.BoardManager;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.repository.BoardRepository;
import com.github.adamantcheese.chan.core.site.Site;

import java.util.Observable;
import java.util.Observer;

import javax.inject.Inject;

public class BrowsePresenter
        implements Observer {
    private final DatabaseManager databaseManager;

    @Nullable
    private Callback callback;

    private boolean hadBoards;
    private Board currentBoard;

    private BoardRepository.SitesBoards savedBoardsObservable;

    @Inject
    public BrowsePresenter(DatabaseManager databaseManager, BoardManager boardManager) {
        this.databaseManager = databaseManager;

        savedBoardsObservable = boardManager.getSavedBoardsObservable();

        hadBoards = hasBoards();
    }

    public void create(Callback callback) {
        this.callback = callback;

        savedBoardsObservable.addObserver(this);
    }

    public void destroy() {
        this.callback = null;
        savedBoardsObservable.deleteObserver(this);
    }

    public Board currentBoard() {
        return currentBoard;
    }

    public void setBoard(Board board) {
        loadBoard(board);
    }

    public void loadWithDefaultBoard() {
        Board first = firstBoard();
        if (first != null) {
            loadBoard(first);
        }
    }

    public void onBoardsFloatingMenuSiteClicked(Site site) {
        if (callback != null) {
            callback.loadSiteSetup(site);
        }
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
        if (callback != null) {
            currentBoard = board;

            callback.loadBoard(getLoadableForBoard(board));
        }
    }

    public interface Callback {
        void loadBoard(Loadable loadable);

        void loadSiteSetup(Site site);
    }
}
