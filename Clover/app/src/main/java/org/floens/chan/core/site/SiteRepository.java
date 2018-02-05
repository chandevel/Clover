package org.floens.chan.core.site;

import org.floens.chan.core.database.DatabaseManager;
import org.floens.chan.core.model.json.site.SiteConfig;
import org.floens.chan.core.settings.json.JsonSettings;
import org.floens.chan.core.model.orm.SiteModel;

import java.util.List;

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
}
