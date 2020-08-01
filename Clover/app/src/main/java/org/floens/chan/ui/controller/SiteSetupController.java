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
package org.floens.chan.ui.controller;

import android.content.Context;

import androidx.annotation.NonNull;

import org.floens.chan.R;
import org.floens.chan.core.presenter.SiteSetupPresenter;
import org.floens.chan.core.settings.OptionsSetting;
import org.floens.chan.core.site.Site;
import org.floens.chan.core.site.SiteSetting;
import org.floens.chan.ui.settings.LinkSettingView;
import org.floens.chan.ui.settings.ListSettingView;
import org.floens.chan.ui.settings.SettingsController;
import org.floens.chan.ui.settings.SettingsGroup;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import static org.floens.chan.Chan.inject;

public class SiteSetupController extends SettingsController implements SiteSetupPresenter.Callback {
    @Inject
    SiteSetupPresenter presenter;

    private Site site;
    private LinkSettingView boardsLink;
    private LinkSettingView loginLink;

    public SiteSetupController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        inject(this);

        // Navigation
        navigation.setTitle(R.string.settings_screen);
        navigation.title = context.getString(R.string.setup_site_title, site.name());

        // View binding
        view = inflateRes(R.layout.settings_layout);
        content = view.findViewById(R.id.scrollview_content);

        // Preferences
        populatePreferences();

        // Presenter
        presenter.create(this, site);

        buildPreferences();
    }

    public void setSite(Site site) {
        this.site = site;
    }

    @Override
    public void onShow() {
        super.onShow();
        presenter.show();
    }

    @Override
    public void setBoardCount(int boardCount) {
        String boardsString = context.getResources().getQuantityString(
                R.plurals.board, boardCount, boardCount);
        String descriptionText = context.getString(
                R.string.setup_site_boards_description, boardsString);
        boardsLink.setDescription(descriptionText);
    }

    @Override
    public void setIsLoggedIn(boolean isLoggedIn) {
        String text = context.getString(isLoggedIn ?
                R.string.setup_site_login_description_enabled :
                R.string.setup_site_login_description_disabled);
        loginLink.setDescription(text);
    }

    @Override
    public void showSettings(List<SiteSetting> settings) {
        SettingsGroup group = new SettingsGroup("Additional settings");

        for (SiteSetting setting : settings) {
            if (setting.type == SiteSetting.Type.OPTIONS) {

                // Turn the SiteSetting for a list of options into a proper setting with a
                // name and a list of options, both given in the SiteSetting.
                OptionsSetting optionsSetting = (OptionsSetting) setting.setting;

                List<ListSettingView.Item<Enum>> items = new ArrayList<>();
                Enum[] settingItems = optionsSetting.getItems();
                for (int i = 0; i < settingItems.length; i++) {
                    String name = setting.optionNames.get(i);
                    Enum anEnum = settingItems[i];
                    items.add(new ListSettingView.Item<>(name, anEnum));
                }

                ListSettingView<?> v = getListSettingView(setting, optionsSetting, items);

                group.add(v);
            }
        }

        groups.add(group);
    }

    @SuppressWarnings("unchecked")
    @NonNull
    private ListSettingView<?> getListSettingView(
            SiteSetting setting,
            OptionsSetting optionsSetting,
            List<ListSettingView.Item<Enum>> items) {
        // we know it's an enum
        return (ListSettingView<?>) new ListSettingView(this,
                optionsSetting, setting.name, items);
    }

    @Override
    public void showLogin() {
        SettingsGroup login = new SettingsGroup(R.string.setup_site_group_login);

        loginLink = new LinkSettingView(
                this,
                context.getString(R.string.setup_site_login),
                "",
                v -> {
                    LoginController loginController = new LoginController(context);
                    loginController.setSite(site);
                    navigationController.pushController(loginController);
                }
        );

        login.add(loginLink);

        groups.add(login);
    }

    private void populatePreferences() {
        SettingsGroup general = new SettingsGroup(R.string.setup_site_group_general);

        boardsLink = new LinkSettingView(
                this,
                context.getString(R.string.setup_site_boards),
                "",
                v -> {
                    BoardSetupController boardSetupController = new BoardSetupController(context);
                    boardSetupController.setSite(site);
                    navigationController.pushController(boardSetupController);
                });
        general.add(boardsLink);

        groups.add(general);
    }
}
