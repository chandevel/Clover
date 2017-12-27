package org.floens.chan.core.site;

import org.floens.chan.core.database.DatabaseManager;
import org.floens.chan.core.model.json.site.SiteConfig;
import org.floens.chan.core.model.json.site.SiteUserSettings;
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
        return databaseManager.runTaskSync(databaseManager.getDatabaseSiteManager().getAll());
    }

    public SiteModel create(SiteConfig config, SiteUserSettings userSettings) {
        SiteModel siteModel = new SiteModel();
        siteModel.storeConfigFields(config, userSettings);
        databaseManager.runTaskSync(databaseManager.getDatabaseSiteManager().add(siteModel));
        return siteModel;
    }

    public void setId(SiteModel siteModel, int id) {
        databaseManager.runTaskSync(databaseManager.getDatabaseSiteManager()
                .updateId(siteModel, id));
    }
}
