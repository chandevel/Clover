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

import android.content.Context;
import android.widget.Toast;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.core.manager.BoardManager;
import com.github.adamantcheese.chan.core.repository.SiteRepository;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.utils.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

public class SitesSetupPresenter
        implements Observer {
    private final Context context;

    @Inject
    private SiteRepository siteRepository;
    @Inject
    private BoardManager boardManager;

    private final Callback callback;

    private final SiteRepository.Sites sites;
    private final List<Site> sitesShown = new ArrayList<>();

    public SitesSetupPresenter(Context context, Callback callback) {
        inject(this);
        this.context = context;
        this.callback = callback;

        sites = siteRepository.all();
        sites.addObserver(this);

        sitesShown.addAll(sites.getAllInOrder());

        updateSitesInUi();
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

    public void move(int from, int to) {
        Site item = sitesShown.remove(from);
        sitesShown.add(to, item);
        saveOrder();
        updateSitesInUi();
    }

    public void onAddClicked(Class<? extends Site> siteClass) {
        Site newSite = siteRepository.createFromClass(siteClass);

        sitesShown.add(newSite);
        saveOrder();

        updateSitesInUi();
    }

    private void saveOrder() {
        siteRepository.updateSiteOrderingAsync(sitesShown);
    }

    private void updateSitesInUi() {
        List<SiteBoardCount> r = new ArrayList<>();
        for (Site site : sitesShown) {
            r.add(new SiteBoardCount(site, boardManager.getSiteSavedBoards(site).size()));
        }
        callback.setSites(r);
    }

    public void removeSite(Site site) {
        try {
            siteRepository.removeSite(site);
            ((StartActivity) context).restartApp();
        } catch (Throwable error) {
            Logger.e(this, "Could not delete site: " + site.name(), error);
            String message = getString(R.string.could_not_remove_site_error_message, site.name(), error.getMessage());
            showToast(context, message, Toast.LENGTH_LONG);
        }
    }

    public static class SiteBoardCount {
        public Site site;
        public int boardCount;

        public SiteBoardCount(Site site, int boardCount) {
            this.site = site;
            this.boardCount = boardCount;
        }
    }

    public interface Callback {
        void setSites(List<SiteBoardCount> sites);
    }
}
