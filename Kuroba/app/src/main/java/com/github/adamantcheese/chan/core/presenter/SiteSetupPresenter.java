package com.github.adamantcheese.chan.core.presenter;

import static com.github.adamantcheese.chan.Chan.inject;

import com.github.adamantcheese.chan.core.database.DatabaseBoardManager;
import com.github.adamantcheese.chan.core.database.DatabaseUtils;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.SiteSetting;

import java.util.List;

import javax.inject.Inject;

public class SiteSetupPresenter {
    private final Callback callback;
    private final Site site;
    private final boolean hasLogin;

    @Inject
    DatabaseBoardManager databaseBoardManager;

    public SiteSetupPresenter(Callback callback, Site site) {
        this.callback = callback;
        this.site = site;

        inject(this);

        hasLogin = site.siteFeature(Site.SiteFeature.LOGIN);

        if (hasLogin) {
            callback.showLogin();
        }

        List<SiteSetting<?>> settings = site.settings();
        if (!settings.isEmpty()) {
            callback.showSettings(settings);
        }
    }

    public void show() {
        callback.setBoardCount(DatabaseUtils.runTask(databaseBoardManager.getSiteSavedBoards(site)).size());
        if (hasLogin) {
            callback.setIsLoggedIn(site.api().isLoggedIn());
        }
    }

    public interface Callback {
        void setBoardCount(int boardCount);

        void showLogin();

        void setIsLoggedIn(boolean isLoggedIn);

        void showSettings(List<SiteSetting<?>> settings);
    }
}
