package org.floens.chan.core.presenter;

import org.floens.chan.core.database.DatabaseManager;
import org.floens.chan.core.site.Site;

import javax.inject.Inject;

public class SiteSetupPresenter {
    private Callback callback;
    private Site site;
    private DatabaseManager databaseManager;

    @Inject
    public SiteSetupPresenter(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void create(Callback callback, Site site) {
        this.callback = callback;
        this.site = site;
    }

    public void show() {
        setBoardCount(callback, site);
    }

    private void setBoardCount(Callback callback, Site site) {
        callback.setBoardCount(
                databaseManager.runTaskSync(
                        databaseManager.getDatabaseBoardManager().getSiteSavedBoards(site)
                ).size()
        );
    }

    public interface Callback {
        void setBoardCount(int boardCount);
    }
}
