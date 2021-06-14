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
package com.github.adamantcheese.chan.ui.controller.settings;

import android.Manifest;
import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.core.manager.WatchManager;
import com.github.adamantcheese.chan.core.presenter.MediaSettingsControllerPresenter;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.settings.ChanSettings.ImageClickPreloadStrategy;
import com.github.adamantcheese.chan.core.settings.ChanSettings.MediaAutoLoadMode;
import com.github.adamantcheese.chan.ui.controller.LoadingViewController;
import com.github.adamantcheese.chan.ui.controller.SaveLocationController;
import com.github.adamantcheese.chan.ui.controller.settings.base_directory.SaveLocationSetupDelegate;
import com.github.adamantcheese.chan.ui.controller.settings.base_directory.SharedLocationSetupDelegate;
import com.github.adamantcheese.chan.ui.helper.RuntimePermissionsHelper;
import com.github.adamantcheese.chan.ui.settings.BooleanSettingView;
import com.github.adamantcheese.chan.ui.settings.IntegerSettingView;
import com.github.adamantcheese.chan.ui.settings.LinkSettingView;
import com.github.adamantcheese.chan.ui.settings.ListSettingView;
import com.github.adamantcheese.chan.ui.settings.ListSettingView.Item;
import com.github.adamantcheese.chan.ui.settings.SettingView;
import com.github.adamantcheese.chan.ui.settings.SettingsGroup;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.k1rakishou.fsaf.FileChooser;
import com.github.k1rakishou.fsaf.FileManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

public class MediaSettingsController
        extends SettingsController
        implements SaveLocationSetupDelegate.MediaControllerCallbacks {
    // Special setting views
    private BooleanSettingView imageBoardFolderSetting;
    private BooleanSettingView imageThreadFolderSetting;
    private BooleanSettingView albumBoardFolderSetting;
    private BooleanSettingView albumThreadFolderSetting;
    private BooleanSettingView videoDefaultMutedSetting;
    private BooleanSettingView headsetDefaultMutedSetting;
    private LinkSettingView saveLocation;
    private ListSettingView<MediaAutoLoadMode> imageAutoLoadView;
    private ListSettingView<MediaAutoLoadMode> videoAutoLoadView;

    private final MediaSettingsControllerPresenter presenter;
    private final RuntimePermissionsHelper runtimePermissionsHelper;
    private final SaveLocationSetupDelegate saveLocationSetupDelegate;

    @Inject
    FileManager fileManager;
    @Inject
    FileChooser fileChooser;
    @Inject
    WatchManager watchManager;

    public MediaSettingsController(Context context) {
        super(context);

        runtimePermissionsHelper = ((StartActivity) context).getRuntimePermissionsHelper();
        presenter = new MediaSettingsControllerPresenter(fileManager, fileChooser, context);
        SharedLocationSetupDelegate sharedLocationSetupDelegate =
                new SharedLocationSetupDelegate(context, this, presenter, fileManager);
        saveLocationSetupDelegate = new SaveLocationSetupDelegate(context, this, presenter);
        presenter.onCreate(sharedLocationSetupDelegate);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        EventBus.getDefault().register(this);
        navigation.setTitle(R.string.settings_screen_media);

        updateVideoLoadModes();

        imageThreadFolderSetting.setEnabled(ChanSettings.saveImageBoardFolder.get());
        albumThreadFolderSetting.setEnabled(ChanSettings.saveAlbumBoardFolder.get());
        headsetDefaultMutedSetting.setEnabled(ChanSettings.videoDefaultMuted.get());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        presenter.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onPreferenceChange(SettingView item) {
        super.onPreferenceChange(item);

        if (item == imageAutoLoadView) {
            updateVideoLoadModes();
        } else if (item == imageBoardFolderSetting) {
            updateImageThreadFolderSetting();
        } else if (item == albumBoardFolderSetting) {
            updateAlbumThreadFolderSetting();
        } else if (item == videoDefaultMutedSetting) {
            headsetDefaultMutedSetting.setEnabled(ChanSettings.videoDefaultMuted.get());
        }
    }

    @Subscribe
    public void onEvent(ChanSettings.SettingChanged<?> setting) {
        if (setting.setting == ChanSettings.saveLocation.getSafBaseDir()) {
            // Image save location (SAF) was chosen
            ChanSettings.saveLocation.resetFileDir();
            saveLocation.setDescription(ChanSettings.saveLocation.getSafBaseDir().get());
        } else if (setting.setting == ChanSettings.saveLocation.getFileApiBaseDir()) {
            // Image save location (Java File API) was chosen
            saveLocation.setDescription(ChanSettings.saveLocation.getFileApiBaseDir().get());
        }
    }

    @Override
    protected void populatePreferences() {
        // Media group
        {
            SettingsGroup media = new SettingsGroup(R.string.settings_group_saving);

            //Save locations
            saveLocation = media.add(new LinkSettingView(this,
                    getString(R.string.save_location_screen),
                    saveLocationSetupDelegate.getSaveLocation(),
                    (v, sv) -> saveLocationSetupDelegate.showUseSAFOrOldAPIForSaveLocationDialog()
            ));

            //Save modifications
            imageBoardFolderSetting = media.add(new BooleanSettingView(this,
                    ChanSettings.saveImageBoardFolder,
                    R.string.setting_save_image_board_folder,
                    R.string.setting_save_image_board_folder_description
            ));

            imageThreadFolderSetting = media.add(new BooleanSettingView(this,
                    ChanSettings.saveImageThreadFolder,
                    R.string.setting_save_image_thread_folder,
                    R.string.setting_save_image_thread_folder_description
            ));

            albumBoardFolderSetting = media.add(new BooleanSettingView(this,
                    ChanSettings.saveAlbumBoardFolder,
                    R.string.setting_save_album_board_folder,
                    R.string.setting_save_album_board_folder_description
            ));

            albumThreadFolderSetting = media.add(new BooleanSettingView(this,
                    ChanSettings.saveAlbumThreadFolder,
                    R.string.setting_save_album_thread_folder,
                    R.string.setting_save_album_thread_folder_description
            ));

            media.add(new BooleanSettingView(this,
                    ChanSettings.saveServerFilename,
                    R.string.setting_save_server_filename,
                    R.string.setting_save_server_filename_description
            ));

            groups.add(media);
        }

        {
            //Video options
            SettingsGroup video = new SettingsGroup("Video settings");
            video.add(new BooleanSettingView(this,
                    ChanSettings.videoAutoLoop,
                    R.string.setting_video_auto_loop,
                    R.string.setting_video_auto_loop_description
            ));

            videoDefaultMutedSetting = video.add(new BooleanSettingView(this,
                    ChanSettings.videoDefaultMuted,
                    R.string.setting_video_default_muted,
                    R.string.setting_video_default_muted_description
            ));

            headsetDefaultMutedSetting = video.add(new BooleanSettingView(this,
                    ChanSettings.headsetDefaultMuted,
                    R.string.setting_headset_default_muted,
                    R.string.setting_headset_default_muted_description
            ));

            video.add(new BooleanSettingView(this,
                    ChanSettings.neverShowWebmControls,
                    "Never show WEBM controls",
                    "Treats WEBMs like GIFs; tap to close, double tap to play/pause, always automatically loops."
            ));

            video.add(new BooleanSettingView(this,
                    ChanSettings.enableSoundposts,
                    "Enable soundposts",
                    "Treats images with a filename embedded audio URL as a video, or adds sound to existing videos."
            ));

            groups.add(video);
        }

        // Loading group (media specific loading behavior)
        {
            SettingsGroup loading = new SettingsGroup(R.string.settings_group_media_loading);

            setupMediaLoadTypesSetting(loading);
            setupImagePreloadStrategySetting(loading);

            requiresRestart.add(loading.add(new BooleanSettingView(this,
                    ChanSettings.autoLoadThreadImages,
                    R.string.setting_auto_load_thread_images,
                    R.string.setting_auto_load_thread_images_description
            )));

            requiresRestart.add(loading.add(new IntegerSettingView(this,
                    ChanSettings.fileCacheSize,
                    "File cache size (in MB)",
                    "File cache size in MB\n(x2 if prefetch enabled)",
                    new Pair<>(100, 2000)
            )));

            groups.add(loading);
        }
    }

    private void setupImagePreloadStrategySetting(SettingsGroup preloading) {
        List<Item<ImageClickPreloadStrategy>> items = new ArrayList<>();
        for (ImageClickPreloadStrategy setting : ImageClickPreloadStrategy.values()) {
            items.add(new Item<>(setting.getKey(), setting));
        }

        preloading.add(new ListSettingView<ImageClickPreloadStrategy>(this,
                ChanSettings.imageClickPreloadStrategy,
                getString(R.string.media_settings_image_click_preload_strategy_name),
                items
        ) {
            @Override
            public String getBottomDescription() {
                return getString(R.string.media_settings_image_click_preload_strategy_description) + "\n\n"
                        + selected.name;
            }
        });
    }

    @Override
    public void runWithWritePermissionsOrShowErrorToast(@NonNull Runnable func) {
        BackgroundUtils.ensureMainThread();

        if (runtimePermissionsHelper.hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            func.run();
            return;
        }

        runtimePermissionsHelper.requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, granted -> {
            if (!granted) {
                showToast(context, R.string.media_settings_cannot_continue_write_permission, Toast.LENGTH_LONG);
            } else {
                func.run();
            }
        });
    }

    @Override
    public void pushController(@NonNull SaveLocationController saveLocationController) {
        BackgroundUtils.ensureMainThread();
        navigationController.pushController(saveLocationController);
    }

    @Override
    public void updateSaveLocationViewText(@NonNull String newLocation) {
        BackgroundUtils.ensureMainThread();
        saveLocation.setDescription(newLocation);
    }

    @Override
    public void presentController(@NotNull LoadingViewController loadingViewController) {
        BackgroundUtils.ensureMainThread();
        navigationController.presentController(loadingViewController);
    }

    @Override
    public void onFilesBaseDirectoryReset() {
        BackgroundUtils.ensureMainThread();
        showToast(context, R.string.media_settings_base_dir_reset);
    }

    @Override
    public void onCouldNotCreateDefaultBaseDir(@NotNull String path) {
        BackgroundUtils.ensureMainThread();
        showToast(context, getString(R.string.media_settings_could_not_create_default_baseDir, path));
    }

    private void setupMediaLoadTypesSetting(SettingsGroup loading) {
        List<Item<MediaAutoLoadMode>> imageAutoLoadTypes = new ArrayList<>();
        List<Item<MediaAutoLoadMode>> videoAutoLoadTypes = new ArrayList<>();
        for (MediaAutoLoadMode mode : MediaAutoLoadMode.values()) {
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

            imageAutoLoadTypes.add(new Item<>(getString(name), mode));
            videoAutoLoadTypes.add(new Item<>(getString(name), mode));
        }

        imageAutoLoadView = new ListSettingView<>(this,
                ChanSettings.imageAutoLoadNetwork,
                R.string.setting_image_auto_load,
                imageAutoLoadTypes
        );
        loading.add(imageAutoLoadView);

        videoAutoLoadView = new ListSettingView<>(this,
                ChanSettings.videoAutoLoadNetwork,
                R.string.setting_video_auto_load,
                videoAutoLoadTypes
        );
        loading.add(videoAutoLoadView);

        updateVideoLoadModes();
    }

    //region Setting update methods
    private void updateVideoLoadModes() {
        MediaAutoLoadMode currentImageLoadMode = ChanSettings.imageAutoLoadNetwork.get();
        MediaAutoLoadMode[] modes = MediaAutoLoadMode.values();
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
            if (!enabled && ChanSettings.videoAutoLoadNetwork.get().getKey().equals(modes[i].getKey())) {
                resetVideoMode = true;
            }
        }
    }

    private void updateImageThreadFolderSetting() {
        if (ChanSettings.saveImageBoardFolder.get()) {
            imageThreadFolderSetting.setEnabled(true);
        } else {
            if (ChanSettings.saveImageThreadFolder.get()) {
                imageThreadFolderSetting.onClick(imageThreadFolderSetting.view);
            }
            imageThreadFolderSetting.setEnabled(false);
            ChanSettings.saveImageThreadFolder.set(false);
        }
    }

    private void updateAlbumThreadFolderSetting() {
        if (ChanSettings.saveAlbumBoardFolder.get()) {
            albumThreadFolderSetting.setEnabled(true);
        } else {
            if (ChanSettings.saveAlbumThreadFolder.get()) {
                albumThreadFolderSetting.onClick(albumThreadFolderSetting.view);
            }
            albumThreadFolderSetting.setEnabled(false);
            ChanSettings.saveAlbumThreadFolder.set(false);
        }
    }
    //endregion
}
