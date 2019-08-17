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
import android.net.Uri;
import android.widget.Toast;

import androidx.documentfile.provider.DocumentFile;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.saf.FileManager;
import com.github.adamantcheese.chan.core.saf.callback.DirectoryChooserCallback;
import com.github.adamantcheese.chan.core.saf.file.AbstractFile;
import com.github.adamantcheese.chan.core.saf.file.ExternalFile;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.settings.BooleanSettingView;
import com.github.adamantcheese.chan.ui.settings.LinkSettingView;
import com.github.adamantcheese.chan.ui.settings.ListSettingView;
import com.github.adamantcheese.chan.ui.settings.SettingView;
import com.github.adamantcheese.chan.ui.settings.SettingsController;
import com.github.adamantcheese.chan.ui.settings.SettingsGroup;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

public class MediaSettingsController extends SettingsController {
    // Special setting views
    private BooleanSettingView boardFolderSetting;
    private BooleanSettingView threadFolderSetting;
    private LinkSettingView saveLocation;
    private ListSettingView<ChanSettings.MediaAutoLoadMode> imageAutoLoadView;
    private ListSettingView<ChanSettings.MediaAutoLoadMode> videoAutoLoadView;

    @Inject
    FileManager fileManager;

    public MediaSettingsController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        inject(this);

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

    @Subscribe
    public void onEvent(ChanSettings.SettingChanged setting) {
        if (setting.setting == ChanSettings.saveLocationUri) {
            saveLocation.setDescription(ChanSettings.saveLocationUri.get());
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
                    ChanSettings.saveServerFilename,
                    R.string.setting_save_server_filename,
                    R.string.setting_save_server_filename_description));

            media.add(new BooleanSettingView(this, ChanSettings.videoDefaultMuted,
                    R.string.setting_video_default_muted,
                    R.string.setting_video_default_muted_description));

            media.add(new BooleanSettingView(this, ChanSettings.videoOpenExternal,
                    R.string.setting_video_open_external,
                    R.string.setting_video_open_external_description));

            media.add(new BooleanSettingView(this,
                    ChanSettings.shareUrl,
                    R.string.setting_share_url, R.string.setting_share_url_description));

            media.add(new BooleanSettingView(this,
                    ChanSettings.revealImageSpoilers,
                    R.string.settings_reveal_image_spoilers,
                    R.string.settings_reveal_image_spoilers_description));

            media.add(new BooleanSettingView(this,
                    ChanSettings.allowMediaScannerToScanLocalThreads,
                    R.string.settings_allow_media_scanner_scan_local_threads_title,
                    R.string.settings_allow_media_scanner_scan_local_threads_description));

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

            requiresRestart.add(loading.add(new BooleanSettingView(this,
                    ChanSettings.autoLoadThreadImages,
                    R.string.setting_auto_load_thread_images,
                    R.string.setting_auto_load_thread_images_description)));

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
        LinkSettingView chooseSaveLocationSetting = new LinkSettingView(this,
                R.string.save_location_screen,
                0,
                v -> handleDirChoose());

        saveLocation = (LinkSettingView) media.add(chooseSaveLocationSetting);
        saveLocation.setDescription(ChanSettings.saveLocationUri.get());
    }

    private void handleDirChoose() {
        boolean result = fileManager.openChooseDirectoryDialog(new DirectoryChooserCallback() {
            @Override
            public void onResult(@NotNull Uri uri) {
                ChanSettings.saveLocationUri.set(uri.toString());

                // We won't use it anymore
                ChanSettings.saveLocation.set("");

                saveLocation.setDescription(uri.toString());

                testMethod(uri);
            }

            @Override
            public void onCancel(@NotNull String reason) {
                Toast.makeText(context, reason, Toast.LENGTH_LONG).show();
            }
        });

        if (!result) {
            Toast.makeText(context, "Could not start activity for result", Toast.LENGTH_SHORT).show();
        }
    }

    private void testMethod(@NotNull Uri uri) {
        {
            ExternalFile externalFile = fileManager.fromUri(uri)
                    .appendSubDirSegment("123")
                    .appendSubDirSegment("456")
                    .appendSubDirSegment("789")
                    .appendFileNameSegment("test123.txt")
                    .createNew();

            if (externalFile == null || !externalFile.exists()) {
                throw new RuntimeException("Couldn't create test123.txt");
            }

            if (!externalFile.name().equals("test123.txt")) {
                throw new RuntimeException("externalFile name != test123.txt");
            }

            boolean externalFile2Exists = fileManager.fromUri(uri)
                    .appendSubDirSegment("123")
                    .appendSubDirSegment("456")
                    .appendSubDirSegment("789")
                    .exists();

            if (!externalFile2Exists) {
                throw new RuntimeException("789 directory does not exist");
            }

            if (!externalFile.delete() && externalFile.exists()) {
                throw new RuntimeException("Couldn't delete test123.txt");
            }

            AbstractFile parent1 = externalFile.getParent();
            if (!parent1.delete() && parent1.exists()) {
                throw new RuntimeException("Couldn't delete 789");
            }

            AbstractFile parent2 = parent1.getParent();
            if (!parent2.delete() && parent2.exists()) {
                throw new RuntimeException("Couldn't delete 456");
            }

            AbstractFile parent3 = parent2.getParent();
            if (!parent3.delete() && parent3.exists()) {
                throw new RuntimeException("Couldn't delete 123");
            }
        }

        {
            AbstractFile externalFile = fileManager.newFile()
                    .appendSubDirSegment("1234")
                    .appendSubDirSegment("4566")
                    .appendFileNameSegment("filename.json")
                    .createNew();

            if (externalFile == null || !externalFile.exists()) {
                throw new RuntimeException("Couldn't create filename.json");
            }

            if (!externalFile.name().equals("filename.json")) {
                throw new RuntimeException("externalFile1 name != filename.json");
            }

            AbstractFile dir = fileManager.newFile()
                    .appendSubDirSegment("1234")
                    .appendSubDirSegment("4566");

            DocumentFile foundFile = dir.findFile("filename.json");
            if (foundFile == null || !foundFile.exists()) {
                throw new RuntimeException("Couldn't find filename.json");
            }

            AbstractFile parent = externalFile.getParent().getParent();
            if (!parent.delete() && parent.exists()) {
                throw new RuntimeException("Couldn't delete /1234/4566/filename.json");
            }
        }

        {
            ExternalFile externalFile = fileManager.fromUri(uri);
            if (externalFile.getParent() != null) {
                throw new RuntimeException("Root directory parent is not null!");
            }
        }

        System.out.println("All tests passed!");
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
}
