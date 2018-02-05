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

import org.floens.chan.R;
import org.floens.chan.core.presenter.SiteSetupPresenter;
import org.floens.chan.core.settings.OptionsSetting;
import org.floens.chan.core.settings.Setting;
import org.floens.chan.core.site.Site;
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
    public void showSettings(List<Setting<?>> settings) {
        SettingsGroup group = new SettingsGroup("Additional settings");

        for (Setting<?> setting : settings) {
            if (setting instanceof OptionsSetting) {
                OptionsSetting optionsSetting = (OptionsSetting) setting;

                List<ListSettingView.Item<Enum>> items = new ArrayList<>();
                for (Enum anEnum : optionsSetting.getItems()) {
                    items.add(new ListSettingView.Item<>(anEnum.name(), anEnum));
                }

                String name = optionsSetting.getItems()[0].getDeclaringClass().getSimpleName();
                ListSettingView<?> v = new ListSettingView(this,
                        optionsSetting, name, items);

                group.add(v);
            }
        }

        groups.add(group);
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
