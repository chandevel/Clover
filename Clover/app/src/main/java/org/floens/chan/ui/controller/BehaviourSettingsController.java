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
import android.view.View;
import android.widget.Toast;

import org.floens.chan.R;
import org.floens.chan.core.database.DatabaseManager;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.helper.RefreshUIMessage;
import org.floens.chan.ui.settings.BooleanSettingView;
import org.floens.chan.ui.settings.IntegerSettingView;
import org.floens.chan.ui.settings.LinkSettingView;
import org.floens.chan.ui.settings.SettingsController;
import org.floens.chan.ui.settings.SettingsGroup;
import org.floens.chan.ui.settings.StringSettingView;

import de.greenrobot.event.EventBus;

import static org.floens.chan.Chan.injector;

public class BehaviourSettingsController extends SettingsController {
    public BehaviourSettingsController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigation.setTitle(R.string.settings_screen_behaviour);

        setupLayout();

        populatePreferences();

        buildPreferences();
    }

    private void populatePreferences() {
        // General group
        {
            SettingsGroup general = new SettingsGroup(R.string.settings_group_general);

            general.add(new BooleanSettingView(this,
                    ChanSettings.autoRefreshThread,
                    R.string.setting_auto_refresh_thread, 0));

            general.add(new BooleanSettingView(this, ChanSettings.confirmExit,
                    R.string.setting_confirm_exit, 0));

            requiresRestart.add(general.add(new BooleanSettingView(this,
                    ChanSettings.controllerSwipeable,
                    R.string.setting_controller_swipeable, 0)));

            setupClearThreadHidesSetting(general);

            groups.add(general);
        }

        // Reply group
        {
            SettingsGroup reply = new SettingsGroup(R.string.settings_group_reply);

            reply.add(new BooleanSettingView(this, ChanSettings.postPinThread,
                    R.string.setting_post_pin, 0));

            reply.add(new StringSettingView(this, ChanSettings.postDefaultName,
                    R.string.setting_post_default_name, R.string.setting_post_default_name));

            groups.add(reply);
        }

        // Post group
        {
            SettingsGroup post = new SettingsGroup(R.string.settings_group_post);

            requiresUiRefresh.add(post.add(new BooleanSettingView(this,
                    ChanSettings.textOnly,
                    R.string.setting_text_only, R.string.setting_text_only_description)));

            requiresUiRefresh.add(post.add(new BooleanSettingView(this,
                    ChanSettings.revealTextSpoilers,
                    R.string.settings_reveal_text_spoilers,
                    R.string.settings_reveal_text_spoilers_description)));

            requiresUiRefresh.add(post.add(new BooleanSettingView(this,
                    ChanSettings.anonymize,
                    R.string.setting_anonymize, 0)));

            requiresUiRefresh.add(post.add(new BooleanSettingView(this,
                    ChanSettings.anonymizeIds,
                    R.string.setting_anonymize_ids, 0)));

            requiresUiRefresh.add(post.add(new BooleanSettingView(this,
                    ChanSettings.showAnonymousName,
                    R.string.setting_show_anonymous_name, 0)));

            post.add(new BooleanSettingView(this,
                    ChanSettings.repliesButtonsBottom,
                    R.string.setting_buttons_bottom, 0));

            post.add(new BooleanSettingView(this,
                    ChanSettings.volumeKeysScrolling,
                    R.string.setting_volume_key_scrolling, 0));

            post.add(new BooleanSettingView(this,
                    ChanSettings.tapNoReply,
                    R.string.setting_tap_no_rely, 0));

            post.add(new BooleanSettingView(this,
                    ChanSettings.openLinkConfirmation,
                    R.string.setting_open_link_confirmation, 0));

            groups.add(post);
        }

        // Proxy group
        {
            SettingsGroup proxy = new SettingsGroup(R.string.settings_group_proxy);

            proxy.add(new BooleanSettingView(this, ChanSettings.proxyEnabled,
                    R.string.setting_proxy_enabled, 0));

            proxy.add(new StringSettingView(this, ChanSettings.proxyAddress,
                    R.string.setting_proxy_address, R.string.setting_proxy_address));

            proxy.add(new IntegerSettingView(this, ChanSettings.proxyPort,
                    R.string.setting_proxy_port, R.string.setting_proxy_port));

            groups.add(proxy);
        }
    }

    private void setupClearThreadHidesSetting(SettingsGroup post) {
        post.add(new LinkSettingView(this, R.string.setting_clear_thread_hides, 0, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO: don't do this here.
                DatabaseManager databaseManager = injector().instance(DatabaseManager.class);
                databaseManager.runTask(
                        databaseManager.getDatabaseHideManager().clearAllThreadHides());
                Toast.makeText(context, R.string.setting_cleared_thread_hides, Toast.LENGTH_LONG)
                        .show();
                EventBus.getDefault().post(new RefreshUIMessage("clearhides"));
            }
        }));
    }
}
