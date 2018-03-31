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
import org.floens.chan.core.site.Site;
import org.floens.chan.core.repository.SiteRepository;
import org.floens.chan.core.site.SiteService;

import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

import javax.inject.Inject;

public class SitesSetupPresenter implements Observer {
    private final SiteService siteService;
    private final SiteRepository siteRepository;
    private final BoardManager boardManager;

    private Callback callback;
    private AddCallback addCallback;

    private SiteRepository.Sites sites;
    private List<Site> sitesShown = new ArrayList<>();

    @Inject
    public SitesSetupPresenter(SiteService siteService, SiteRepository siteRepository,
                               BoardManager boardManager) {
        this.siteService = siteService;
        this.siteRepository = siteRepository;
        this.boardManager = boardManager;
    }

    public void create(Callback callback) {
        this.callback = callback;

        sites = siteRepository.all();
        sites.addObserver(this);

        sitesShown.addAll(sites.getAllInOrder());

        updateSitesInUi();

        this.callback.setNextAllowed(!sitesShown.isEmpty());

        if (sitesShown.isEmpty()) {
            callback.presentIntro();
        }
    }

    public void destroy() {
        sites.deleteObserver(this);
    }

    @Override
    public void update(Observable o, Object arg) {
        if (o == sites) {
            sitesShown.clear();
            sitesShown.addAll(sites.getAllInOrder());
            updateSitesInUi();
        }
    }

    public void show() {
        updateSitesInUi();
    }

    public void move(int from, int to) {
        Site item = sitesShown.remove(from);
        sitesShown.add(to, item);
        saveOrder();
        updateSitesInUi();
    }

    public void onIntroDismissed() {
        if (sitesShown.isEmpty()) {
            callback.showHint();
        }
    }

    public void bindAddDialog(AddCallback addCallback) {
        this.addCallback = addCallback;
    }

    public void unbindAddDialog() {
        this.addCallback = null;
    }

    public boolean mayExit() {
        return sitesShown.size() > 0;
    }

    public void onShowDialogClicked() {
        callback.showAddDialog();
    }

    public void onAddClicked(String url) {
        siteService.addSite(url, new SiteService.SiteAddCallback() {
            @Override
            public void onSiteAdded(Site site) {
                siteAdded(site);
                if (addCallback != null) {
                    addCallback.dismissDialog();
                }
            }

            @Override
            public void onSiteAddFailed(String message) {
                if (addCallback != null) {
                    addCallback.showAddError(message);
                }
            }
        });
    }

    public void onDoneClicked() {
    }

    private void siteAdded(Site site) {
        sitesShown.add(site);
        saveOrder();

        updateSitesInUi();

        callback.setNextAllowed(!sitesShown.isEmpty());
    }

    public void onSiteCellSettingsClicked(Site site) {
        callback.openSiteConfiguration(site);
    }

    private void saveOrder() {
        siteService.updateOrdering(sitesShown);
    }

    private void updateSitesInUi() {
        List<SiteBoardCount> r = new ArrayList<>();
        for (Site site : sitesShown) {
            r.add(new SiteBoardCount(site, boardManager.getSiteSavedBoards(site).size()));
        }
        callback.setSites(r);
    }

    public class SiteBoardCount {
        public Site site;
        public int boardCount;

        public SiteBoardCount(Site site, int boardCount) {
            this.site = site;
            this.boardCount = boardCount;
        }
    }

    public interface Callback {
        void setSites(List<SiteBoardCount> sites);

        void presentIntro();

        void showHint();

        void showAddDialog();

        void setNextAllowed(boolean nextAllowed);

        void openSiteConfiguration(Site site);
    }

    public interface AddCallback {
        void showAddError(String error);

        void dismissDialog();
    }
}
