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


import org.floens.chan.core.site.Site;
import org.floens.chan.core.site.SiteManager;
import org.floens.chan.core.site.Sites;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import static org.floens.chan.Chan.getGraph;

public class SiteSetupPresenter {
    @Inject
    SiteManager siteManager;

    private Callback callback;

    private List<Site> sites = new ArrayList<>();

    @Inject
    public SiteSetupPresenter() {
        getGraph().inject(this);
    }

    public void create(Callback callback) {
        this.callback = callback;

        sites.addAll(Sites.allSites());

        this.callback.setAddedSites(sites);

        this.callback.setNextAllowed(!sites.isEmpty(), false);
    }

    public boolean mayExit() {
        return false;
    }

    public void onUrlSubmitClicked(String url) {
        callback.goToUrlSubmittedState();

        siteManager.addSite(url, new SiteManager.SiteAddCallback() {
            @Override
            public void onSiteAdded(Site site) {
                siteAdded(site);
            }

            @Override
            public void onSiteAddFailed(String message) {
                callback.showUrlHint(message);
            }
        });
    }

    public void onNextClicked() {
        if (!sites.isEmpty()) {
            callback.moveToSavedBoards();
        }
    }

    private void siteAdded(Site site) {
        sites.clear();
        sites.addAll(Sites.allSites());

        callback.setAddedSites(sites);
        callback.runSiteAddedAnimation(site);

        callback.setNextAllowed(!sites.isEmpty(), true);
    }

    private int counter;

    public interface Callback {
        void goToUrlSubmittedState();

        void runSiteAddedAnimation(Site site);

        void setAddedSites(List<Site> sites);

        void setNextAllowed(boolean nextAllowed, boolean animate);

        void showUrlHint(String text);

        void moveToSavedBoards();
    }
}
