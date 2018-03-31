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

import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.repository.BoardRepository;
import org.floens.chan.core.site.Site;

import java.util.List;

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

    private final BoardRepository boardRepository;

    @Inject
    public BoardManager(BoardRepository boardRepository) {
        this.boardRepository = boardRepository;
    }

    public void initialize() {
        boardRepository.initialize();
    }

    public void updateAvailableBoardsForSite(Site site, List<Board> boards) {
        boardRepository.updateAvailableBoardsForSite(site, boards);
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
        return boardRepository.getFromCode(site, code);
    }

    public List<Board> getSiteBoards(Site site) {
        return boardRepository.getSiteBoards(site);
    }

    public List<Board> getSiteSavedBoards(Site site) {
        return boardRepository.getSiteSavedBoards(site);
    }

    public BoardRepository.SitesBoards getAllBoardsObservable() {
        return boardRepository.getAll();
    }

    public BoardRepository.SitesBoards getSavedBoardsObservable() {
        return boardRepository.getSaved();
    }

    public void updateBoardOrders(List<Board> boards) {
        boardRepository.updateBoardOrders(boards);
    }

    public void setSaved(Board board, boolean saved) {
        boardRepository.setSaved(board, saved);
    }

    public void setAllSaved(List<Board> boards, boolean saved) {
        boardRepository.setAllSaved(boards, saved);
    }
}
