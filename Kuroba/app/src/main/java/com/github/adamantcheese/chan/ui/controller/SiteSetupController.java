/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
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
package com.github.adamantcheese.chan.ui.controller;

import android.content.Context;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.presenter.SiteSetupPresenter;
import com.github.adamantcheese.chan.core.settings.primitives.BooleanSetting;
import com.github.adamantcheese.chan.core.settings.primitives.OptionsSetting;
import com.github.adamantcheese.chan.core.settings.primitives.StringSetting;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.SiteSetting;
import com.github.adamantcheese.chan.ui.controller.settings.SettingsController;
import com.github.adamantcheese.chan.ui.settings.BooleanSettingView;
import com.github.adamantcheese.chan.ui.settings.LinkSettingView;
import com.github.adamantcheese.chan.ui.settings.ListSettingView;
import com.github.adamantcheese.chan.ui.settings.ListSettingView.Item;
import com.github.adamantcheese.chan.ui.settings.SettingView;
import com.github.adamantcheese.chan.ui.settings.SettingsGroup;
import com.github.adamantcheese.chan.ui.settings.StringSettingView;

import java.util.ArrayList;
import java.util.List;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getQuantityString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

public class SiteSetupController
        extends SettingsController
        implements SiteSetupPresenter.Callback {
    SiteSetupPresenter presenter;

    private final Site site;
    private LinkSettingView boardsLink;
    private LinkSettingView loginLink;

    public SiteSetupController(Context context, Site site) {
        super(context);
        this.site = site;
        presenter = new SiteSetupPresenter(this, site);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Navigation
        navigation.setTitle(R.string.settings_screen);
        navigation.title = getString(R.string.setup_site_title, site.name());
    }

    @Override
    public void onShow() {
        super.onShow();
        presenter.show();
    }

    @Override
    public void setBoardCount(int boardCount) {
        String boardsString = getQuantityString(R.plurals.board, boardCount, boardCount);
        String descriptionText = getString(R.string.setup_site_boards_description, boardsString);
        boardsLink.setDescription(descriptionText);
    }

    @Override
    public void setIsLoggedIn(boolean isLoggedIn) {
        String text = getString(isLoggedIn
                ? R.string.setup_site_login_description_enabled
                : R.string.setup_site_login_description_disabled);
        loginLink.setDescription(text);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void showSettings(List<SiteSetting<?>> settings) {
        SettingsGroup group = new SettingsGroup("Additional settings");

        for (SiteSetting<?> setting : settings) {
            SettingView generated;

            switch (setting.type) {
                case OPTIONS:
                    // Turn the SiteSetting for a list of options into a proper setting with a
                    // name and a list of options, both given in the SiteSetting.
                    OptionsSetting optionsSetting = (OptionsSetting) setting.setting;

                    List<Item<Enum>> items = new ArrayList<>();
                    Enum[] settingItems = optionsSetting.getItems();
                    for (int i = 0; i < settingItems.length; i++) {
                        String name = setting.optionNames.get(i);
                        Enum anEnum = settingItems[i];
                        if (name != null) {
                            items.add(new Item<>(name, anEnum));
                        }
                    }

                    //noinspection unchecked
                    generated = new ListSettingView<>(this, optionsSetting, setting.name, items);
                    break;
                case STRING:
                    // Turn the SiteSetting for a string setting into a proper setting with a name and input
                    generated = new StringSettingView(this,
                            (StringSetting) setting.setting,
                            setting.name,
                            setting.name,
                            null
                    );
                    break;
                case BOOLEAN:
                    generated = new BooleanSettingView(this, (BooleanSetting) setting.setting, setting.name, null);
                    break;
                default:
                    throw new UnsupportedOperationException("Not implemented yet for this setting type!");
            }

            group.add(generated);
        }

        groups.add(group);
    }

    @Override
    public void showLogin() {
        SettingsGroup login = new SettingsGroup(R.string.setup_site_group_login);

        loginLink = new LinkSettingView(this, getString(R.string.setup_site_login), "", (v, sv) -> {
            LoginController loginController = new LoginController(context, site);
            navigationController.pushController(loginController);
        });

        login.add(loginLink);

        groups.add(login);
    }

    @Override
    protected void populatePreferences() {
        SettingsGroup general = new SettingsGroup(R.string.setup_site_group_general);

        boardsLink = new LinkSettingView(this, getString(R.string.setup_site_boards), "", (v, sv) -> {
            BoardSetupController boardSetupController = new BoardSetupController(context);
            boardSetupController.setSite(site);
            navigationController.pushController(boardSetupController);
        });
        general.add(boardsLink);

        groups.add(general);
    }
}
