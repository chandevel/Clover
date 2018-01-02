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
import android.widget.LinearLayout;

import org.floens.chan.R;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.activity.StartActivity;
import org.floens.chan.ui.helper.RefreshUIMessage;
import org.floens.chan.ui.settings.BooleanSettingView;
import org.floens.chan.ui.settings.IntegerSettingView;
import org.floens.chan.ui.settings.SettingView;
import org.floens.chan.ui.settings.SettingsController;
import org.floens.chan.ui.settings.SettingsGroup;
import org.floens.chan.ui.settings.StringSettingView;

import de.greenrobot.event.EventBus;

public class AdvancedSettingsController extends SettingsController {
    private static final String TAG = "AdvancedSettingsController";

    private boolean needRestart;
    private SettingView newCaptcha;
    private SettingView enableReplyFab;
    private SettingView neverHideToolbar;
    private SettingView controllersSwipeable;

    public AdvancedSettingsController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigation.setTitle(R.string.settings_screen_advanced);

        view = inflateRes(R.layout.settings_layout);
        content = (LinearLayout) view.findViewById(R.id.scrollview_content);

        populatePreferences();

        buildPreferences();

        ChanSettings.settingsOpenCounter.set(5);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (needRestart) {
            ((StartActivity) context).restart();
        }
    }

    @Override
    public void onPreferenceChange(SettingView item) {
        super.onPreferenceChange(item);

        if (item == enableReplyFab || item == newCaptcha || item == neverHideToolbar || item == controllersSwipeable) {
            needRestart = true;
        } else {
            EventBus.getDefault().post(new RefreshUIMessage("postui"));
        }
    }

    private void populatePreferences() {
        SettingsGroup settings = new SettingsGroup(R.string.settings_group_advanced);

        newCaptcha = settings.add(new BooleanSettingView(this, ChanSettings.postNewCaptcha, R.string.setting_use_new_captcha, R.string.setting_use_new_captcha_description));
        settings.add(new BooleanSettingView(this, ChanSettings.saveOriginalFilename, R.string.setting_save_original_filename, 0));
        controllersSwipeable = settings.add(new BooleanSettingView(this, ChanSettings.controllerSwipeable, R.string.setting_controller_swipeable, 0));
        settings.add(new BooleanSettingView(this, ChanSettings.shareUrl, R.string.setting_share_url, R.string.setting_share_url_description));
        settings.add(new BooleanSettingView(this, ChanSettings.networkHttps, R.string.setting_network_https, R.string.setting_network_https_description));
        enableReplyFab = settings.add(new BooleanSettingView(this, ChanSettings.enableReplyFab, R.string.setting_enable_reply_fab, R.string.setting_enable_reply_fab_description));
        settings.add(new BooleanSettingView(this, ChanSettings.anonymize, R.string.setting_anonymize, 0));
        settings.add(new BooleanSettingView(this, ChanSettings.anonymizeIds, R.string.setting_anonymize_ids, 0));
        settings.add(new BooleanSettingView(this, ChanSettings.showAnonymousName, R.string.setting_show_anonymous_name, 0));
        settings.add(new BooleanSettingView(this, ChanSettings.revealImageSpoilers, R.string.settings_reveal_image_spoilers, 0));
        settings.add(new BooleanSettingView(this, ChanSettings.revealTextSpoilers, R.string.settings_reveal_text_spoilers, R.string.settings_reveal_text_spoilers_description));
        settings.add(new BooleanSettingView(this, ChanSettings.repliesButtonsBottom, R.string.setting_buttons_bottom, 0));
        settings.add(new BooleanSettingView(this, ChanSettings.confirmExit, R.string.setting_confirm_exit, 0));
        settings.add(new BooleanSettingView(this, ChanSettings.tapNoReply, R.string.setting_tap_no_rely, 0));
        settings.add(new BooleanSettingView(this, ChanSettings.volumeKeysScrolling, R.string.setting_volume_key_scrolling, 0));
        settings.add(new BooleanSettingView(this, ChanSettings.postFullDate, R.string.setting_post_full_date, 0));
        settings.add(new BooleanSettingView(this, ChanSettings.postFileInfo, R.string.setting_post_file_info, 0));
        settings.add(new BooleanSettingView(this, ChanSettings.postFilename, R.string.setting_post_filename, 0));
        neverHideToolbar = settings.add(new BooleanSettingView(this, ChanSettings.neverHideToolbar, R.string.setting_never_hide_toolbar, 0));

        groups.add(settings);

        SettingsGroup proxy = new SettingsGroup(R.string.settings_group_proxy);
        proxy.add(new BooleanSettingView(this, ChanSettings.proxyEnabled, R.string.setting_proxy_enabled, 0));
        proxy.add(new StringSettingView(this, ChanSettings.proxyAddress, R.string.setting_proxy_address, R.string.setting_proxy_address));
        proxy.add(new IntegerSettingView(this, ChanSettings.proxyPort, R.string.setting_proxy_port, R.string.setting_proxy_port));
        groups.add(proxy);
    }
}
