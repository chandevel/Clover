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
package org.floens.chan.core.site;


import android.util.Pair;

import org.floens.chan.core.model.json.site.SiteConfig;
import org.floens.chan.core.model.json.site.SiteUserSettings;
import org.floens.chan.core.model.orm.SiteModel;
import org.floens.chan.core.site.sites.chan4.Chan4;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class SiteManager {
    private SiteRepository siteRepository;
    private SiteResolver resolver;

    private boolean initialized = false;
    private boolean addSiteForLegacy = false;

    @Inject
    public SiteManager(SiteRepository siteRepository,
                       SiteResolver resolver) {
        this.siteRepository = siteRepository;
        this.resolver = resolver;
    }

    public void addSite(String url, SiteAddCallback callback) {
        Site existing = resolver.findSiteForUrl(url);
        if (existing != null) {
            callback.onSiteAddFailed("site already added");
            return;
        }

        SiteResolver.SiteResolverResult resolve = resolver.resolveSiteForUrl(url);

        Class<? extends Site> siteClass;
        if (resolve.match == SiteResolver.SiteResolverResult.Match.BUILTIN) {
            siteClass = resolve.builtinResult;
        } else if (resolve.match == SiteResolver.SiteResolverResult.Match.EXTERNAL) {
            callback.onSiteAddFailed("external sites not hardcoded is not implemented yet");
            return;
        } else {
            callback.onSiteAddFailed("not a url");
            return;
        }

        Site site = instantiateSiteClass(siteClass);
        createNewSite(site);

        loadSites();

        callback.onSiteAdded(site);
    }

    /**
     * Called from the DatabaseHelper when upgrading to the tables with a site id.
     */
    public void addSiteForLegacy() {
        addSiteForLegacy = true;
    }

    public void initialize() {
        if (initialized) {
            throw new IllegalStateException("Already initialized");
        }

        if (addSiteForLegacy) {
            Site site = new Chan4();

            SiteConfig config = new SiteConfig();
            config.classId = Sites.SITE_CLASSES.indexOfValue(site.getClass());
            config.external = false;

            SiteModel model = siteRepository.create(config, new SiteUserSettings());
            siteRepository.setId(model, 0);
        }

        loadSites();
    }

    private void loadSites() {
        List<Site> sites = new ArrayList<>();

        for (SiteModel siteModel : siteRepository.all()) {
            sites.add(fromModel(siteModel));
        }

        Sites.initialize(sites);

        for (Site site : sites) {
            site.postInitialize();
        }
    }

    /**
     * Create a new site from the Site instance. This will insert the model for the site
     * into the database and calls initialize on the site instance.
     *
     * @param site the site to add.
     */
    private void createNewSite(Site site) {
        SiteConfig config = new SiteConfig();
        SiteUserSettings settings = new SiteUserSettings();

        config.classId = Sites.SITE_CLASSES.indexOfValue(site.getClass());
        config.external = false;

        siteRepository.create(config, settings);

        loadSites();
    }

    private Site fromModel(SiteModel siteModel) {
        Pair<SiteConfig, SiteUserSettings> configFields = siteModel.loadConfigFields();
        SiteConfig config = configFields.first;
        SiteUserSettings settings = configFields.second;

        Site site = instantiateSiteClass(config.classId);
        site.initialize(siteModel.id, config, settings);
        return site;
    }

    private Site instantiateSiteClass(int classId) {
        Class<? extends Site> clazz = Sites.SITE_CLASSES.get(classId);
        if (clazz == null) {
            throw new IllegalArgumentException("Unknown class id");
        }
        return instantiateSiteClass(clazz);
    }

    private Site instantiateSiteClass(Class<? extends Site> clazz) {
        Site site;
        //noinspection TryWithIdenticalCatches
        try {
            site = clazz.newInstance();
        } catch (InstantiationException e) {
            throw new IllegalArgumentException();
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException();
        }
        return site;
    }

    public interface SiteAddCallback {
        void onSiteAdded(Site site);

        void onSiteAddFailed(String message);
    }
}
