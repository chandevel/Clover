package com.github.adamantcheese.chan.core.repository;

import android.text.TextUtils;
import android.util.SparseArray;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.core.database.DatabaseBoardManager;
import com.github.adamantcheese.chan.core.database.DatabaseFilterManager;
import com.github.adamantcheese.chan.core.database.DatabaseHideManager;
import com.github.adamantcheese.chan.core.database.DatabaseLoadableManager;
import com.github.adamantcheese.chan.core.database.DatabasePinManager;
import com.github.adamantcheese.chan.core.database.DatabaseSavedReplyManager;
import com.github.adamantcheese.chan.core.database.DatabaseSiteManager;
import com.github.adamantcheese.chan.core.database.DatabaseUtils;
import com.github.adamantcheese.chan.core.model.orm.Filter;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.model.orm.SiteModel;
import com.github.adamantcheese.chan.core.settings.primitives.JsonSettings;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.utils.Logger;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Observable;

import static com.github.adamantcheese.chan.Chan.instance;
import static com.github.adamantcheese.chan.core.site.SiteRegistry.SITE_CLASSES;

public class SiteRepository {
    private final DatabaseSiteManager databaseSiteManager;
    private final Gson gson;
    private final Sites sitesObservable = new Sites();

    public Site forId(int id) {
        return sitesObservable.forId(id);
    }

    public SiteRepository(DatabaseSiteManager databaseSiteManager, Gson gson) {
        this.databaseSiteManager = databaseSiteManager;
        this.gson = gson;
    }

    public Sites all() {
        return sitesObservable;
    }

    public SiteModel byId(int id) {
        return DatabaseUtils.runTask(databaseSiteManager.byId(id));
    }

    public void updateUserSettings(Site site, JsonSettings jsonSettings) {
        SiteModel siteModel = byId(site.id());
        if (siteModel == null) throw new NullPointerException("siteModel == null");
        updateSiteUserSettingsAsync(siteModel, jsonSettings);
    }

    public void updateSiteUserSettingsAsync(SiteModel siteModel, JsonSettings jsonSettings) {
        siteModel.storeUserSettings(gson, jsonSettings);
        DatabaseUtils.runTaskAsync(databaseSiteManager.update(siteModel));
    }

    public Map<Integer, Integer> getOrdering() {
        return DatabaseUtils.runTask(databaseSiteManager.getOrdering());
    }

    public void updateSiteOrderingAsync(List<Site> sites) {
        List<Integer> ids = new ArrayList<>(sites.size());
        for (Site site : sites) {
            ids.add(site.id());
        }

        DatabaseUtils.runTaskAsync(databaseSiteManager.updateOrdering(ids), r -> {
            sitesObservable.wasReordered();
            sitesObservable.notifyObservers();
        });
    }

    public void initialize() {
        List<Site> sites = new ArrayList<>();

        List<SiteModel> models = DatabaseUtils.runTask(databaseSiteManager.getAll());

        for (SiteModel siteModel : models) {
            SiteConfigSettingsHolder holder;
            try {
                holder = instantiateSiteFromModel(siteModel);
            } catch (IllegalArgumentException e) {
                Logger.e(this, "instantiateSiteFromModel", e);
                break;
            }

            holder.site.initialize(siteModel.id, holder.settings);
            sites.add(holder.site);
        }

        sitesObservable.addAll(sites);

        for (Site site : sites) {
            site.postInitialize();
        }

        sitesObservable.notifyObservers();
    }

    public Site createFromClass(Class<? extends Site> siteClass) {
        Site site = instantiateSiteClass(siteClass);

        JsonSettings settings = new JsonSettings();

        //the index doesn't necessarily match the key value to get the class ID anymore since sites were removed
        int classId = SITE_CLASSES.keyAt(SITE_CLASSES.indexOfValue(site.getClass()));

        SiteModel model = createFromClass(classId, settings);

        site.initialize(model.id, settings);

        sitesObservable.add(site);

        site.postInitialize();

        sitesObservable.notifyObservers();

        return site;
    }

    private SiteModel createFromClass(int classID, JsonSettings userSettings) {
        SiteModel siteModel = new SiteModel();
        siteModel.classID = classID;
        siteModel.storeUserSettings(gson, userSettings);
        DatabaseUtils.runTask(databaseSiteManager.add(siteModel));

        return siteModel;
    }

    private SiteConfigSettingsHolder instantiateSiteFromModel(SiteModel siteModel) {
        return new SiteConfigSettingsHolder(instantiateSiteClass(siteModel.classID), siteModel.loadConfig(gson));
    }

    @NonNull
    private Site instantiateSiteClass(int classId) {
        Class<? extends Site> clazz = SITE_CLASSES.get(classId);
        if (clazz == null) {
            throw new IllegalArgumentException("Unknown class id: " + classId);
        }
        return instantiateSiteClass(clazz);
    }

    @NonNull
    public Site instantiateSiteClass(Class<? extends Site> clazz) {
        try {
            return clazz.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalArgumentException();
        }
    }

    public void removeSite(Site site) {
        DatabaseUtils.runTask(() -> {
            removeFilters(site);
            instance(DatabaseBoardManager.class).deleteBoards(site).call();

            List<Loadable> siteLoadables = instance(DatabaseLoadableManager.class).getLoadables(site).call();
            if (!siteLoadables.isEmpty()) {
                instance(DatabasePinManager.class).deletePinsFromLoadables(siteLoadables).call();
                instance(DatabaseLoadableManager.class).deleteLoadables(siteLoadables).call();
            }

            instance(DatabaseSavedReplyManager.class).deleteSavedReplies(site).call();
            instance(DatabaseHideManager.class).deleteThreadHides(site).call();
            instance(DatabaseSiteManager.class).deleteSite(site).call();
            return null;
        });
    }

    private void removeFilters(Site site)
            throws Exception {
        List<Filter> filtersToDelete = new ArrayList<>();
        DatabaseFilterManager databaseFilterManager = instance(DatabaseFilterManager.class);

        for (Filter filter : databaseFilterManager.getFilters().call()) {
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

        databaseFilterManager.deleteFilters(filtersToDelete).call();
    }

    public class Sites
            extends Observable {
        private List<Site> sites = Collections.unmodifiableList(new ArrayList<>());
        private SparseArray<Site> sitesById = new SparseArray<>();

        public Site forId(int id) {
            return sitesById.get(id);
        }

        public List<Site> getAll() {
            return new ArrayList<>(sites);
        }

        public List<Site> getAllInOrder() {
            Map<Integer, Integer> ordering = getOrdering();

            List<Site> ordered = new ArrayList<>(sites);
            //noinspection ConstantConditions
            Collections.sort(
                    ordered,
                    (lhs, rhs) -> lhs == null || rhs == null ? 0 : ordering.get(lhs.id()) - ordering.get(rhs.id())
            );

            return ordered;
        }

        private void addAll(@NonNull List<Site> all) {
            List<Site> copy = new ArrayList<>(sites);
            copy.addAll(all);
            resetSites(copy);
            setChanged();
        }

        private void add(@NonNull Site site) {
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

        private void resetSites(@NonNull List<Site> newSites) {
            sites = Collections.unmodifiableList(newSites);
            SparseArray<Site> byId = new SparseArray<>(newSites.size());
            for (Site newSite : newSites) {
                byId.put(newSite.id(), newSite);
            }
            sitesById = byId;
        }
    }

    private static class SiteConfigSettingsHolder {
        @NonNull
        Site site;
        @NonNull
        JsonSettings settings;

        public SiteConfigSettingsHolder(@NonNull Site site, @NonNull JsonSettings settings) {
            this.site = site;
            this.settings = settings;
        }
    }
}
