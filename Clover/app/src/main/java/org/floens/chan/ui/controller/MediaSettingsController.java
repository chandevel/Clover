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
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.settings.BooleanSettingView;
import org.floens.chan.ui.settings.LinkSettingView;
import org.floens.chan.ui.settings.ListSettingView;
import org.floens.chan.ui.settings.SettingView;
import org.floens.chan.ui.settings.SettingsController;
import org.floens.chan.ui.settings.SettingsGroup;

import java.util.ArrayList;
import java.util.List;

import de.greenrobot.event.EventBus;

import static org.floens.chan.utils.AndroidUtils.getString;

public class MediaSettingsController extends SettingsController {
    // Special setting views
    private BooleanSettingView boardFolderSetting;
    private BooleanSettingView threadFolderSetting;
    private LinkSettingView saveLocation;
    private ListSettingView<ChanSettings.MediaAutoLoadMode> imageAutoLoadView;
    private ListSettingView<ChanSettings.MediaAutoLoadMode> videoAutoLoadView;

    public MediaSettingsController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        EventBus.getDefault().register(this);

        navigation.setTitle(R.string.settings_screen_media);

        setupLayout();

        populatePreferences();

        buildPreferences();

        onPreferenceChange(imageAutoLoadView);

        threadFolderSetting.setEnabled(ChanSettings.saveBoardFolder.get());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onPreferenceChange(SettingView item) {
        super.onPreferenceChange(item);

        if (item == imageAutoLoadView) {
            updateVideoLoadModes();
        } else if (item == boardFolderSetting) {
            updateThreadFolderSetting();
        }
    }

    public void onEvent(ChanSettings.SettingChanged setting) {
        if (setting.setting == ChanSettings.saveLocation) {
            updateSaveLocationSetting();
        }
    }

    private void populatePreferences() {
        // Media group
        {
            SettingsGroup media = new SettingsGroup(R.string.settings_group_media);

            setupSaveLocationSetting(media);

            boardFolderSetting = (BooleanSettingView) media.add(new BooleanSettingView(this,
                    ChanSettings.saveBoardFolder,
                    R.string.setting_save_board_folder,
                    R.string.setting_save_board_folder_description));

            threadFolderSetting = (BooleanSettingView) media.add(new BooleanSettingView(this,
                    ChanSettings.saveThreadFolder,
                    R.string.setting_save_thread_folder,
                    R.string.setting_save_thread_folder_description));

            media.add(new BooleanSettingView(this,
                    ChanSettings.saveOriginalFilename,
                    R.string.setting_save_original_filename,
                    R.string.setting_save_original_filename_description));

            media.add(new BooleanSettingView(this, ChanSettings.videoDefaultMuted,
                    R.string.setting_video_default_muted,
                    R.string.setting_video_default_muted_description));

            media.add(new BooleanSettingView(this, ChanSettings.videoOpenExternal,
                    R.string.setting_video_open_external,
                    R.string.setting_video_open_external_description));

            media.add(new BooleanSettingView(this, ChanSettings.videoUseExoplayer,
                    R.string.setting_video_exoplayer,
                    R.string.setting_video_exoplayer_description));

            media.add(new BooleanSettingView(this,
                    ChanSettings.shareUrl,
                    R.string.setting_share_url, R.string.setting_share_url_description));

            media.add(new BooleanSettingView(this,
                    ChanSettings.revealImageSpoilers,
                    R.string.settings_reveal_image_spoilers,
                    R.string.settings_reveal_image_spoilers_description));

            groups.add(media);
        }

        // Loading group
        {
            SettingsGroup loading = new SettingsGroup(R.string.settings_group_media_loading);

            setupMediaLoadTypesSetting(loading);

            loading.add(new BooleanSettingView(this,
                    ChanSettings.videoAutoLoop,
                    R.string.setting_video_auto_loop,
                    R.string.setting_video_auto_loop_description));

            groups.add(loading);
        }
    }

    private void setupMediaLoadTypesSetting(SettingsGroup loading) {
        List<ListSettingView.Item> imageAutoLoadTypes = new ArrayList<>();
        List<ListSettingView.Item> videoAutoLoadTypes = new ArrayList<>();
        for (ChanSettings.MediaAutoLoadMode mode : ChanSettings.MediaAutoLoadMode.values()) {
            int name = 0;
            switch (mode) {
                case ALL:
                    name = R.string.setting_image_auto_load_all;
                    break;
                case WIFI:
                    name = R.string.setting_image_auto_load_wifi;
                    break;
                case NONE:
                    name = R.string.setting_image_auto_load_none;
                    break;
            }

            imageAutoLoadTypes.add(new ListSettingView.Item<>(getString(name), mode));
            videoAutoLoadTypes.add(new ListSettingView.Item<>(getString(name), mode));
        }

        imageAutoLoadView = new ListSettingView<>(this,
                ChanSettings.imageAutoLoadNetwork, R.string.setting_image_auto_load,
                imageAutoLoadTypes);
        loading.add(imageAutoLoadView);

        videoAutoLoadView = new ListSettingView<>(this,
                ChanSettings.videoAutoLoadNetwork, R.string.setting_video_auto_load,
                videoAutoLoadTypes);
        loading.add(videoAutoLoadView);

        updateVideoLoadModes();
    }

    private void updateVideoLoadModes() {
        ChanSettings.MediaAutoLoadMode currentImageLoadMode = ChanSettings.imageAutoLoadNetwork.get();
        ChanSettings.MediaAutoLoadMode[] modes = ChanSettings.MediaAutoLoadMode.values();
        boolean enabled = false;
        boolean resetVideoMode = false;
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].getKey().equals(currentImageLoadMode.getKey())) {
                enabled = true;
                if (resetVideoMode) {
                    ChanSettings.videoAutoLoadNetwork.set(modes[i]);
                    videoAutoLoadView.updateSelection();
                    onPreferenceChange(videoAutoLoadView);
                }
            }
            videoAutoLoadView.items.get(i).enabled = enabled;
            if (!enabled && ChanSettings.videoAutoLoadNetwork.get().getKey()
                    .equals(modes[i].getKey())) {
                resetVideoMode = true;
            }
        }
    }

    private void setupSaveLocationSetting(SettingsGroup media) {
        saveLocation = (LinkSettingView) media.add(new LinkSettingView(this,
                R.string.save_location_screen, 0,
                v -> navigationController.pushController(new SaveLocationController(context))));
        updateSaveLocationSetting();
    }

    private void updateThreadFolderSetting() {
        if (ChanSettings.saveBoardFolder.get()) {
            threadFolderSetting.setEnabled(true);
        } else if (!ChanSettings.saveBoardFolder.get()) {
            if (ChanSettings.saveThreadFolder.get()) {
                threadFolderSetting.onClick(threadFolderSetting.view);
            }
            threadFolderSetting.setEnabled(false);
            ChanSettings.saveThreadFolder.set(false);
        }
    }

    private void updateSaveLocationSetting() {
        saveLocation.setDescription(ChanSettings.saveLocation.get());
    }
}
