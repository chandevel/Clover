package org.floens.chan.core.site;

import org.floens.chan.core.database.DatabaseManager;
import org.floens.chan.core.model.json.site.SiteConfig;
import org.floens.chan.core.model.orm.SiteModel;
import org.floens.chan.core.settings.json.JsonSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

public class SiteRepository {
    private DatabaseManager databaseManager;

    @Inject
    public SiteRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public List<SiteModel> all() {
        return databaseManager.runTask(databaseManager.getDatabaseSiteManager().getAll());
    }

    public SiteModel byId(int id) {
        return databaseManager.runTask(databaseManager.getDatabaseSiteManager()
                .byId(id));
    }

    public SiteModel create(SiteConfig config, JsonSettings userSettings) {
        SiteModel siteModel = new SiteModel();
        siteModel.storeConfig(config);
        siteModel.storeUserSettings(userSettings);
        databaseManager.runTask(databaseManager.getDatabaseSiteManager().add(siteModel));
        return siteModel;
    }

    public void setId(SiteModel siteModel, int id) {
        databaseManager.runTask(databaseManager.getDatabaseSiteManager()
                .updateId(siteModel, id));
    }

    public void updateSiteUserSettingsAsync(SiteModel siteModel, JsonSettings jsonSettings) {
        siteModel.storeUserSettings(jsonSettings);
        databaseManager.runTaskAsync(databaseManager.getDatabaseSiteManager()
                .update(siteModel));
    }

    public Map<Integer, Integer> getOrdering() {
        return databaseManager.runTask(databaseManager.getDatabaseSiteManager().getOrdering());
    }

    public void updateSiteOrderingAsync(List<Site> sites, Runnable done) {
        List<Integer> ids = new ArrayList<>(sites.size());
        for (Site site : sites) {
            ids.add(site.id());
        }

        databaseManager.runTaskAsync(databaseManager.getDatabaseSiteManager().updateOrdering(ids),
                result -> done.run());
    }
}
