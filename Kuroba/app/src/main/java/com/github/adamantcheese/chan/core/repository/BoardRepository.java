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
package com.github.adamantcheese.chan.core.repository;

import androidx.core.util.Pair;

import com.github.adamantcheese.chan.core.database.DatabaseBoardManager;
import com.github.adamantcheese.chan.core.database.DatabaseUtils;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.Boards;
import com.github.adamantcheese.chan.utils.Logger;

import java.util.*;

public class BoardRepository
        implements Observer {
    private boolean initialized = false;
    private final DatabaseBoardManager databaseBoardManager;

    private final SiteRepository.Sites allSites;

    private final SitesBoards allBoards = new SitesBoards();
    private final SitesBoards savedBoards = new SitesBoards();

    public BoardRepository(DatabaseBoardManager databaseBoardManager, SiteRepository siteRepository) {
        this.databaseBoardManager = databaseBoardManager;
        allSites = siteRepository.all();
    }

    public void initialize() {
        if (initialized) return;
        initialized = true;
        updateObservablesSync();

        allSites.addObserver(this);
    }

    @Override
    public void update(Observable o, Object arg) {
        if (o == allSites) {
            updateObservablesAsync();
        }
    }

    public void updateAvailableBoardsForSite(Site site, Boards availableBoards) {
        boolean changed = DatabaseUtils.runTask(databaseBoardManager.createAll(site, availableBoards));
        Logger.d(this, "updateAvailableBoardsForSite changed = " + changed);
        if (changed) {
            updateObservablesAsync();
        }
    }

    public Board getFromCode(Site site, String code) {
        for (SiteBoards siteBoards : allBoards.get()) {
            if (siteBoards.site.id() == site.id()) {
                for (Board board : siteBoards.boards) {
                    if (board.code.equals(code)) {
                        return board;
                    }
                }
                return null;
            }
        }

        return null;
    }

    public SitesBoards getAll() {
        return allBoards;
    }

    public SitesBoards getSavedObservable() {
        return savedBoards;
    }

    public List<SiteBoards> getSaved() {
        return savedBoards.get();
    }

    public Boards getSiteBoards(Site site) {
        for (SiteBoards item : allBoards.siteBoards) {
            if (item.site.id() == site.id()) {
                return item.boards;
            }
        }
        return new Boards();
    }

    public Boards getSiteSavedBoards(Site site) {
        for (SiteBoards item : savedBoards.siteBoards) {
            if (item.site.id() == site.id()) {
                return item.boards;
            }
        }
        return new Boards();
    }

    public void updateBoardOrders(Boards boards) {
        DatabaseUtils.runTaskAsync(databaseBoardManager.updateOrders(boards), (e) -> updateObservablesAsync());
    }

    public void setSaved(Board board, boolean saved) {
        board.saved = saved;
        board.order = saved ? board.order : 0;
        DatabaseUtils.runTaskAsync(databaseBoardManager.update(board), (e) -> updateObservablesAsync());
    }

    public void setAllSaved(Boards boards, boolean saved) {
        for (Board board : boards) {
            board.saved = saved;
        }
        DatabaseUtils.runTaskAsync(databaseBoardManager.updateAll(boards), (e) -> updateObservablesAsync());
    }

    private void updateObservablesSync() {
        updateWith(DatabaseUtils.runTask(databaseBoardManager.getBoardsForAllSitesOrdered(allSites.getAll())));
    }

    private void updateObservablesAsync() {
        DatabaseUtils.runTaskAsync(databaseBoardManager.getBoardsForAllSitesOrdered(allSites.getAll()),
                this::updateWith
        );
    }

    private void updateWith(List<Pair<Site, Boards>> databaseData) {
        List<SiteBoards> all = new ArrayList<>();
        List<SiteBoards> saved = new ArrayList<>();
        for (Pair<Site, Boards> item : databaseData) {
            all.add(new SiteBoards(item.first, item.second));

            Boards savedBoards = new Boards();
            for (Board board : item.second) {
                if (board.saved) savedBoards.add(board);
            }
            saved.add(new SiteBoards(item.first, savedBoards));
        }

        allBoards.set(all);
        savedBoards.set(saved);

        allBoards.notifyObservers();
        savedBoards.notifyObservers();
    }

    public static class SitesBoards
            extends Observable {
        private List<SiteBoards> siteBoards = new ArrayList<>();

        public void set(List<SiteBoards> siteBoards) {
            this.siteBoards = siteBoards;
            setChanged();
        }

        public List<SiteBoards> get() {
            return siteBoards;
        }
    }

    public static class SiteBoards {
        public final Site site;
        public final Boards boards;

        public SiteBoards(Site site, Boards boards) {
            this.site = site;
            this.boards = boards;
        }
    }
}
