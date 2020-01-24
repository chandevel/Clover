package com.github.adamantcheese.chan.core.repository;

import android.text.TextUtils;
import android.util.Pair;
import android.util.SparseArray;

import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.model.json.site.SiteConfig;
import com.github.adamantcheese.chan.core.model.orm.Filter;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.model.orm.SiteModel;
import com.github.adamantcheese.chan.core.settings.json.JsonSettings;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.SiteRegistry;
import com.github.adamantcheese.chan.utils.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Observable;

import javax.inject.Inject;

public class SiteRepository {
    private static final String TAG = "SiteRepository";

    private DatabaseManager databaseManager;
    private Sites sitesObservable = new Sites();

    public Site forId(int id) {
        return sitesObservable.forId(id);
    }

    @Inject
    public SiteRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public Sites all() {
        return sitesObservable;
    }

    public SiteModel byId(int id) {
        return databaseManager.runTask(databaseManager.getDatabaseSiteManager().byId(id));
    }

    public void setId(SiteModel siteModel, int id) {
        databaseManager.runTask(databaseManager.getDatabaseSiteManager().updateId(siteModel, id));
    }

    public void updateSiteUserSettingsAsync(SiteModel siteModel, JsonSettings jsonSettings) {
        siteModel.storeUserSettings(jsonSettings);
        databaseManager.runTaskAsync(databaseManager.getDatabaseSiteManager().update(siteModel));
    }

    public Map<Integer, Integer> getOrdering() {
        return databaseManager.runTask(databaseManager.getDatabaseSiteManager().getOrdering());
    }

    public void updateSiteOrderingAsync(List<Site> sites) {
        List<Integer> ids = new ArrayList<>(sites.size());
        for (Site site : sites) {
            ids.add(site.id());
        }

        databaseManager.runTaskAsync(databaseManager.getDatabaseSiteManager().updateOrdering(ids), r -> {
            sitesObservable.wasReordered();
            sitesObservable.notifyObservers();
        });
    }

    public void initialize() {
        List<Site> sites = new ArrayList<>();

        List<SiteModel> models = databaseManager.runTask(databaseManager.getDatabaseSiteManager().getAll());

        for (SiteModel siteModel : models) {
            SiteConfigSettingsHolder holder;
            try {
                holder = instantiateSiteFromModel(siteModel);
            } catch (IllegalArgumentException e) {
                Logger.e(TAG, "instantiateSiteFromModel", e);
                break;
            }

            Site site = holder.site;
            SiteConfig config = holder.config;
            JsonSettings settings = holder.settings;

            site.initialize(siteModel.id, config, settings);

            sites.add(site);
        }

        sitesObservable.addAll(sites);

        for (Site site : sites) {
            site.postInitialize();
        }

        sitesObservable.notifyObservers();
    }

    public Site createFromClass(Class<? extends Site> siteClass) {
        Site site = instantiateSiteClass(siteClass);

        SiteConfig config = new SiteConfig();
        JsonSettings settings = new JsonSettings();

        //the index doesn't necessarily match the key value to get the class ID anymore since sites were removed
        config.classId = SiteRegistry.SITE_CLASSES.keyAt(SiteRegistry.SITE_CLASSES.indexOfValue(site.getClass()));
        config.external = false;

        SiteModel model = createFromClass(config, settings);

        site.initialize(model.id, config, settings);

        sitesObservable.add(site);

        site.postInitialize();

        sitesObservable.notifyObservers();

        return site;
    }

    private SiteModel createFromClass(SiteConfig config, JsonSettings userSettings) {
        SiteModel siteModel = new SiteModel();
        siteModel.storeConfig(config);
        siteModel.storeUserSettings(userSettings);
        databaseManager.runTask(databaseManager.getDatabaseSiteManager().add(siteModel));

        return siteModel;
    }

    private SiteConfigSettingsHolder instantiateSiteFromModel(SiteModel siteModel) {
        Pair<SiteConfig, JsonSettings> configFields = siteModel.loadConfigFields();
        SiteConfig config = configFields.first;
        JsonSettings settings = configFields.second;

        return new SiteConfigSettingsHolder(instantiateSiteClass(config.classId), config, settings);
    }

    private Site instantiateSiteClass(int classId) {
        Class<? extends Site> clazz = SiteRegistry.SITE_CLASSES.get(classId);
        if (clazz == null) {
            throw new IllegalArgumentException("Unknown class id");
        }
        return instantiateSiteClass(clazz);
    }

    private Site instantiateSiteClass(Class<? extends Site> clazz) {
        Site site;
        try {
            site = clazz.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalArgumentException();
        }
        return site;
    }

    public void removeSite(Site site) {
        databaseManager.runTask(() -> {
            removeFilters(site);
            databaseManager.getDatabaseBoardManager().deleteBoards(site).call();

            List<Loadable> siteLoadables = databaseManager.getDatabaseLoadableManager().getLoadables(site).call();
            if (!siteLoadables.isEmpty()) {
                databaseManager.getDatabaseSavedThreadManager().deleteSavedThreads(siteLoadables).call();
                databaseManager.getDatabasePinManager().deletePinsFromLoadables(siteLoadables).call();
                databaseManager.getDatabaseHistoryManager().deleteHistory(siteLoadables).call();
                databaseManager.getDatabaseLoadableManager().deleteLoadables(siteLoadables).call();
            }

            databaseManager.getDatabaseSavedReplyManager().deleteSavedReplies(site).call();
            databaseManager.getDatabaseHideManager().deleteThreadHides(site).call();
            databaseManager.getDatabaseSiteManager().deleteSite(site).call();
            return null;
        });
    }

    private void removeFilters(Site site)
            throws Exception {
        List<Filter> filtersToDelete = new ArrayList<>();

        for (Filter filter : databaseManager.getDatabaseFilterManager().getFilters().call()) {
            if (filter.allBoards || TextUtils.isEmpty(filter.boards)) {
                continue;
            }

            for (String uniqueId : filter.boards.split(",")) {
                String[] split = uniqueId.split(":");
                if (split.length == 2 && Integer.parseInt(split[0]) == site.id()) {
                    filtersToDelete.add(filter);
                    break;
                }
            }
        }

        databaseManager.getDatabaseFilterManager().deleteFilters(filtersToDelete).call();
    }

    public class Sites
            extends Observable {
        private List<Site> sites = Collections.unmodifiableList(new ArrayList<>());
        private SparseArray<Site> sitesById = new SparseArray<>();

        public Site forId(int id) {
            Site s = sitesById.get(id);
            if (s == null) {
                throw new IllegalArgumentException("No site with id (" + id + ")");
            }
            return s;
        }

        public List<Site> getAll() {
            return new ArrayList<>(sites);
        }

        public List<Site> getAllInOrder() {
            Map<Integer, Integer> ordering = getOrdering();

            List<Site> ordered = new ArrayList<>(sites);
            Collections.sort(ordered, (lhs, rhs) -> ordering.get(lhs.id()) - ordering.get(rhs.id()));

            return ordered;
        }

        private void addAll(List<Site> all) {
            List<Site> copy = new ArrayList<>(sites);
            copy.addAll(all);
            resetSites(copy);
            setChanged();
        }

        private void add(Site site) {
            List<Site> copy = new ArrayList<>(sites);
            copy.add(site);
            resetSites(copy);
            setChanged();
        }

        // We don't keep the order ourselves here, that's the task of listeners. Do notify the
        // listeners.
        private void wasReordered() {
            setChanged();
        }

        private void resetSites(List<Site> newSites) {
            sites = Collections.unmodifiableList(newSites);
            SparseArray<Site> byId = new SparseArray<>(newSites.size());
            for (Site newSite : newSites) {
                byId.put(newSite.id(), newSite);
            }
            sitesById = byId;
        }
    }

    private class SiteConfigSettingsHolder {
        Site site;
        SiteConfig config;
        JsonSettings settings;

        public SiteConfigSettingsHolder(Site site, SiteConfig config, JsonSettings settings) {
            this.site = site;
            this.config = config;
            this.settings = settings;
        }
    }
}
