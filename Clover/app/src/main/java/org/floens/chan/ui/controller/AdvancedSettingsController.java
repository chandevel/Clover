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

import android.app.Activity;
import android.content.Context;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.widget.LinearLayout;

import org.floens.chan.R;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.activity.StartActivity;
import org.floens.chan.ui.fragment.FolderPickFragment;
import org.floens.chan.ui.helper.RefreshUIMessage;
import org.floens.chan.ui.settings.BooleanSettingView;
import org.floens.chan.ui.settings.IntegerSettingView;
import org.floens.chan.ui.settings.LinkSettingView;
import org.floens.chan.ui.settings.SettingView;
import org.floens.chan.ui.settings.SettingsController;
import org.floens.chan.ui.settings.SettingsGroup;
import org.floens.chan.ui.settings.StringSettingView;

import java.io.File;

import de.greenrobot.event.EventBus;

public class AdvancedSettingsController extends SettingsController {
    private static final String TAG = "AdvancedSettingsController";

    private boolean needRestart;
    private LinkSettingView saveLocation;
    private SettingView forcePhoneLayoutSetting;
    private SettingView enableReplyFab;
    private SettingView postFullDate;
    private SettingView postFileInfo;
    private SettingView postFilename;
    private SettingView anonymize;
    private SettingView anonymizeIds;
    private SettingView tapNoReply;

    public AdvancedSettingsController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigationItem.title = string(R.string.settings_screen_advanced);

        view = inflateRes(R.layout.settings_layout);
        content = (LinearLayout) view.findViewById(R.id.scrollview_content);

        populatePreferences();

        buildPreferences();
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

        if (item == forcePhoneLayoutSetting || item == enableReplyFab) {
            needRestart = true;
        }

        if (item == postFullDate || item == postFileInfo || item == anonymize || item == anonymizeIds || item == tapNoReply || item == postFilename) {
            EventBus.getDefault().post(new RefreshUIMessage("postui"));
        }
    }

    private void populatePreferences() {
        SettingsGroup settings = new SettingsGroup(string(R.string.settings_group_advanced));

        // TODO change this to a presenting controller
        saveLocation = (LinkSettingView) settings.add(new LinkSettingView(this, string(R.string.setting_save_folder), null, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                File dir = new File(ChanSettings.saveLocation.get());
                if (!dir.mkdirs() && !dir.isDirectory()) {
                    new AlertDialog.Builder(context).setMessage(R.string.setting_save_folder_error_create_folder).show();
                } else {
                    FolderPickFragment frag = FolderPickFragment.newInstance(new FolderPickFragment.FolderPickListener() {
                        @Override
                        public void folderPicked(File path) {
                            ChanSettings.saveLocation.set(path.getAbsolutePath());
                            setSaveLocationDescription();
                        }
                    }, dir);
                    ((Activity) context).getFragmentManager().beginTransaction().add(frag, null).commit();
                }
            }
        }));
        setSaveLocationDescription();

        settings.add(new BooleanSettingView(this, ChanSettings.postNewCaptcha, string(R.string.setting_use_new_captcha), string(R.string.setting_use_new_captcha_description)));
        settings.add(new BooleanSettingView(this, ChanSettings.saveOriginalFilename, string(R.string.setting_save_original_filename), null));
        settings.add(new BooleanSettingView(this, ChanSettings.shareUrl, string(R.string.setting_share_url), string(R.string.setting_share_url_description)));
        settings.add(new BooleanSettingView(this, ChanSettings.networkHttps, string(R.string.setting_network_https), string(R.string.setting_network_https_description)));
        forcePhoneLayoutSetting = settings.add(new BooleanSettingView(this, ChanSettings.forcePhoneLayout, string(R.string.setting_force_phone_layout), null));
        enableReplyFab = settings.add(new BooleanSettingView(this, ChanSettings.enableReplyFab, string(R.string.setting_enable_reply_fab), string(R.string.setting_enable_reply_fab_description)));
        anonymize = settings.add(new BooleanSettingView(this, ChanSettings.anonymize, string(R.string.setting_anonymize), null));
        anonymizeIds = settings.add(new BooleanSettingView(this, ChanSettings.anonymizeIds, string(R.string.setting_anonymize_ids), null));
        settings.add(new BooleanSettingView(this, ChanSettings.repliesButtonsBottom, string(R.string.setting_buttons_bottom), null));
        settings.add(new BooleanSettingView(this, ChanSettings.confirmExit, string(R.string.setting_confirm_exit), null));
        tapNoReply = settings.add(new BooleanSettingView(this, ChanSettings.tapNoReply, string(R.string.setting_tap_no_rely), null));
        settings.add(new BooleanSettingView(this, ChanSettings.volumeKeysScrolling, string(R.string.setting_volume_key_scrolling), null));
        postFullDate = settings.add(new BooleanSettingView(this, ChanSettings.postFullDate, string(R.string.setting_post_full_date), null));
        postFileInfo = settings.add(new BooleanSettingView(this, ChanSettings.postFileInfo, string(R.string.setting_post_file_info), null));
        postFilename = settings.add(new BooleanSettingView(this, ChanSettings.postFilename, string(R.string.setting_post_filename), null));

        groups.add(settings);

        SettingsGroup proxy = new SettingsGroup(string(R.string.settings_group_proxy));
        proxy.add(new BooleanSettingView(this, ChanSettings.proxyEnabled, string(R.string.setting_proxy_enabled), null));
        proxy.add(new StringSettingView(this, ChanSettings.proxyAddress, string(R.string.setting_proxy_address), string(R.string.setting_proxy_address)));
        proxy.add(new IntegerSettingView(this, ChanSettings.proxyPort, string(R.string.setting_proxy_port), string(R.string.setting_proxy_port)));
        groups.add(proxy);
    }

    private void setSaveLocationDescription() {
        saveLocation.setDescription(ChanSettings.saveLocation.get());
    }
}
