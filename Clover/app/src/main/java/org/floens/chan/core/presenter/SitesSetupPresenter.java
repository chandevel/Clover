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
import org.floens.chan.core.site.SiteService;
import org.floens.chan.core.site.Sites;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

public class SitesSetupPresenter {
    private SiteService siteService;
    private BoardManager boardManager;

    private Callback callback;
    private AddCallback addCallback;

    private List<Site> sites = new ArrayList<>();

    @Inject
    public SitesSetupPresenter(SiteService siteService, BoardManager boardManager) {
        this.siteService = siteService;
        this.boardManager = boardManager;
    }

    public void create(Callback callback) {
        this.callback = callback;

        sites.addAll(Sites.allSites());

        this.callback.setAddedSites(sites);

        this.callback.setNextAllowed(!sites.isEmpty());

        if (sites.isEmpty()) {
            callback.presentIntro();
        }
    }

    public void show() {
        callback.setAddedSites(sites);
    }

    public void onIntroDismissed() {
        if (sites.isEmpty()) {
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
        return sites.size() > 0;
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

    public int getSiteBoardCount(Site site) {
        return boardManager.getSiteSavedBoards(site).size();
    }

    private void siteAdded(Site site) {
        sites.clear();
        sites.addAll(Sites.allSites());

        callback.setAddedSites(sites);

        callback.setNextAllowed(!sites.isEmpty());
    }

    public void onSiteCellSettingsClicked(Site site) {
        callback.openSiteConfiguration(site);
    }

    public interface Callback {
        void presentIntro();

        void showHint();

        void showAddDialog();

        void setAddedSites(List<Site> sites);

        void setNextAllowed(boolean nextAllowed);

        void openSiteConfiguration(Site site);
    }

    public interface AddCallback {
        void showAddError(String error);

        void dismissDialog();
    }
}
