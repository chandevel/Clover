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

import com.google.gson.Gson;

import org.floens.chan.core.database.DatabaseManager;
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
    @Inject
    DatabaseManager databaseManager;

    private SiteResolver resolver;

    private Gson gson = new Gson();

    @Inject
    public SiteManager() {
        resolver = new SiteResolver();
    }

    public void addSite(String url, SiteAddCallback callback) {
        SiteResolver.SiteResolverResult resolve = resolver.resolve(url);

        Class<? extends Site> siteClass;
        if (resolve.match == SiteResolver.SiteResolverResult.Match.BUILTIN) {
            siteClass = resolve.builtinResult;
        } else if (resolve.match == SiteResolver.SiteResolverResult.Match.EXTERNAL) {
            callback.onSiteAddFailed("external todo");
            return;
        } else {
            callback.onSiteAddFailed("not a url");
            return;
        }

        addSiteFromClass(siteClass, callback);
    }

    public void addSiteFromClass(Class<? extends Site> siteClass, SiteAddCallback callback) {
        Site site = instantiateSiteClass(siteClass);
        createNewSite(site);

        List<Site> newAllSites = new ArrayList<>(Sites.allSites());
        newAllSites.add(site);
        setAvailableSites(newAllSites);

        callback.onSiteAdded(site);
    }

    public void initialize() {
        List<Site> sites = loadSitesFromDatabase();

        if (sites.isEmpty()) {
            Site site = new Chan4();

            SiteConfig config = new SiteConfig();
            config.classId = Sites.SITE_CLASSES.indexOfValue(site.getClass());
            config.external = false;

            SiteUserSettings userSettings = new SiteUserSettings();

            SiteModel siteModel = new SiteModel();
            storeConfigFields(siteModel, config, userSettings);
            siteModel = databaseManager.runTaskSync(databaseManager.getDatabaseSiteManager().add(siteModel));
            databaseManager.runTaskSync(databaseManager.getDatabaseSiteManager().updateId(siteModel, 0));

            sites.add(site);
        }

        setAvailableSites(sites);
    }

    private void setAvailableSites(List<Site> newAllSites) {
        Sites.initialize(newAllSites);
    }

    private List<Site> loadSitesFromDatabase() {
        List<Site> sites = new ArrayList<>();

        List<SiteModel> siteModels = databaseManager.runTaskSync(databaseManager.getDatabaseSiteManager().getAll());

        for (SiteModel siteModel : siteModels) {
            Pair<SiteConfig, SiteUserSettings> configPair = loadConfigFields(siteModel);
            SiteConfig config = configPair.first;
            SiteUserSettings userSettings = configPair.second;

            Site site = loadSiteFromModel(siteModel, config, userSettings);
            sites.add(site);
        }

        return sites;
    }

    /**
     * Create a new site from the Site instance. This will insert the model for the site
     * into the database and calls initialize on the site instance.
     * @param site the site to add.
     */
    private void createNewSite(Site site) {
        SiteConfig config = new SiteConfig();
        config.classId = Sites.SITE_CLASSES.indexOfValue(site.getClass());
        config.external = false;

        SiteUserSettings userSettings = new SiteUserSettings();

        SiteModel siteModel = createNewSiteModel(site, config, userSettings);

        initializeSite(site, siteModel.id, config, userSettings);
    }

    private SiteModel createNewSiteModel(Site site, SiteConfig config, SiteUserSettings userSettings) {
        SiteModel siteModel = new SiteModel();
        storeConfigFields(siteModel, config, userSettings);
        siteModel = databaseManager.runTaskSync(databaseManager.getDatabaseSiteManager().add(siteModel));
        return siteModel;
    }

    private void storeConfigFields(SiteModel siteModel, SiteConfig config, SiteUserSettings userSettings) {
        siteModel.configuration = gson.toJson(config);
        siteModel.userSettings = gson.toJson(userSettings);
    }

    private Pair<SiteConfig, SiteUserSettings> loadConfigFields(SiteModel siteModel) {
        SiteConfig config = gson.fromJson(siteModel.configuration, SiteConfig.class);
        SiteUserSettings userSettings = gson.fromJson(siteModel.userSettings, SiteUserSettings.class);
        return Pair.create(config, userSettings);
    }

    private Site loadSiteFromModel(SiteModel model, SiteConfig config, SiteUserSettings userSettings) {
        Site site = instantiateSiteFromConfig(config);
        return initializeSite(site, model.id, config, userSettings);
    }

    private Site initializeSite(Site site, int id, SiteConfig config, SiteUserSettings userSettings) {
        site.initialize(id, config, userSettings);
        return site;
    }

    private Site instantiateSiteFromConfig(SiteConfig siteConfig) {
        int siteClassId = siteConfig.classId;
        Class<? extends Site> clazz = Sites.SITE_CLASSES.get(siteClassId);
        if (clazz == null) {
            throw new IllegalArgumentException();
        }
        return instantiateSiteClass(clazz);
    }

    /**
     * Create the instance of the site class. Catches any reflection exceptions as runtime
     * exceptions.
     * @param clazz the class to instantiate
     * @return the instantiated clas
     * @throws IllegalArgumentException on reflection exceptions, should never happen.
     */
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

    public static class SiteAlreadyAdded extends IllegalArgumentException {
    }

    public interface SiteAddCallback {
        void onSiteAdded(Site site);

        void onSiteAddFailed(String message);
    }
}
