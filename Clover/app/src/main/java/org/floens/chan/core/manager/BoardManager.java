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

import android.util.Pair;

import org.floens.chan.core.database.DatabaseManager;
import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.site.Site;
import org.floens.chan.core.site.Sites;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Observable;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * <p>Keeps track of {@link Board}s in the system.
 * <p>There are a few types of sites, those who provide a list of all boards known,
 * sites where users can create boards and have a very long list of known boards,
 * and those who don't provide a board list at all.
 * <p>We try to save as much info about boards as possible, this means that we try to save all
 * boards we encounter.
 * For sites with a small list of boards which does provide a board list api we save all those
 * boards.
 * <p>All boards have a {@link Board#saved} flag indicating if it should be visible in the user's
 * favorite board list, along with a {@link Board#order} in which they appear.
 */
@Singleton
public class BoardManager {
    private static final String TAG = "BoardManager";

    private static final Comparator<Board> ORDER_SORT = (lhs, rhs) -> lhs.order - rhs.order;

    private final DatabaseManager databaseManager;

    private final List<Pair<Site, List<Board>>> sitesWithSavedBoards = new ArrayList<>();
    private final SavedBoards savedBoardsObservable = new SavedBoards();

    @Inject
    public BoardManager(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;

        updateSavedBoardsAndNotify();
    }

    public void createAll(List<Board> boards) {
        databaseManager.runTask(
                databaseManager.getDatabaseBoardManager().createAll(boards));
    }

    /**
     * Get the board with the same {@code code} for the given site. The board does not need
     * to be saved.
     *
     * @param site the site
     * @param code the board code
     * @return the board code with the same site and board code, or {@code null} if not found.
     */
    public Board getBoard(Site site, String code) {
        return databaseManager.runTask(
                databaseManager.getDatabaseBoardManager().getBoard(site, code));
    }

    public List<Board> getSiteBoards(Site site) {
        return databaseManager.runTask(
                databaseManager.getDatabaseBoardManager().getSiteBoards(site));
    }

    public List<Board> getSiteSavedBoards(Site site) {
        List<Board> boards = databaseManager.runTask(
                databaseManager.getDatabaseBoardManager().getSiteSavedBoards(site));
        Collections.sort(boards, ORDER_SORT);
        return boards;
    }

    public SavedBoards getSavedBoardsObservable() {
        return savedBoardsObservable;
    }

    public void saveBoard(Board board) {
        setSaved(board, true);
    }

    public void unsaveBoard(Board board) {
        setSaved(board, false);
    }

    public void updateBoardOrders(List<Board> boards) {
        databaseManager.runTask(databaseManager.getDatabaseBoardManager()
                .updateOrders(boards));
        updateSavedBoardsAndNotify();
    }

    private void setSaved(Board board, boolean saved) {
        board.saved = saved;
        databaseManager.runTask(databaseManager.getDatabaseBoardManager().updateIncludingUserFields(board));
        updateSavedBoardsAndNotify();
    }

    private void updateSavedBoardsAndNotify() {
        sitesWithSavedBoards.clear();
        for (Site site : Sites.allSites()) {
            List<Board> siteBoards = getSiteSavedBoards(site);
            sitesWithSavedBoards.add(new Pair<>(site, siteBoards));
        }

        savedBoardsObservable.doNotify();
    }

    public class SavedBoards extends Observable {
        private void doNotify() {
            setChanged();
            notifyObservers();
        }

        public List<Pair<Site, List<Board>>> get() {
            return sitesWithSavedBoards;
        }
    }
}
