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

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.manager.ThreadSaveManager;
import com.github.adamantcheese.chan.core.manager.WatchManager;
import com.github.adamantcheese.chan.core.presenter.MediaSettingsControllerPresenter;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.controller.LoadingViewController;
import com.github.adamantcheese.chan.ui.controller.SaveLocationController;
import com.github.adamantcheese.chan.ui.controller.settings.base_directory.SaveLocationSetupDelegate;
import com.github.adamantcheese.chan.ui.controller.settings.base_directory.SharedLocationSetupDelegate;
import com.github.adamantcheese.chan.ui.controller.settings.base_directory.ThreadsLocationSetupDelegate;
import com.github.adamantcheese.chan.ui.helper.RuntimePermissionsHelper;
import com.github.adamantcheese.chan.ui.settings.BooleanSettingView;
import com.github.adamantcheese.chan.ui.settings.LinkSettingView;
import com.github.adamantcheese.chan.ui.settings.ListSettingView;
import com.github.adamantcheese.chan.ui.settings.SettingView;
import com.github.adamantcheese.chan.ui.settings.SettingsGroup;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.k1rakishou.fsaf.FileChooser;
import com.github.k1rakishou.fsaf.FileManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.showToast;

public class MediaSettingsController
        extends SettingsController
        implements SaveLocationSetupDelegate.MediaControllerCallbacks,
        ThreadsLocationSetupDelegate.MediaControllerCallbacks {
    private static final String TAG = "MediaSettingsController";

    // Special setting views
    private BooleanSettingView boardFolderSetting;
    private BooleanSettingView threadFolderSetting;
    private BooleanSettingView videoDefaultMutedSetting;
    private BooleanSettingView headsetDefaultMutedSetting;
    private LinkSettingView saveLocation;
    private LinkSettingView localThreadsLocation;
    private ListSettingView<ChanSettings.MediaAutoLoadMode> imageAutoLoadView;
    private ListSettingView<ChanSettings.MediaAutoLoadMode> videoAutoLoadView;
    private BooleanSettingView incrementalThreadDownloadingSetting;

    private MediaSettingsControllerPresenter presenter;
    private RuntimePermissionsHelper runtimePermissionsHelper;
    private SharedLocationSetupDelegate sharedLocationSetupDelegate;
    private SaveLocationSetupDelegate saveLocationSetupDelegate;
    private ThreadsLocationSetupDelegate threadsLocationSetupDelegate;

    @Inject
    FileManager fileManager;
    @Inject
    FileChooser fileChooser;
    @Inject
    DatabaseManager databaseManager;
    @Inject
    ThreadSaveManager threadSaveManager;
    @Inject
    WatchManager watchManager;

    public MediaSettingsController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        inject(this);

        runtimePermissionsHelper = ((StartActivity) context).getRuntimePermissionsHelper();
        EventBus.getDefault().register(this);
        navigation.setTitle(R.string.settings_screen_media);

        presenter = new MediaSettingsControllerPresenter(
                fileManager,
                fileChooser,
                context
        );
        sharedLocationSetupDelegate = new SharedLocationSetupDelegate(
                context,
                this,
                presenter,
                fileManager
        );
        saveLocationSetupDelegate = new SaveLocationSetupDelegate(
                context,
                this,
                presenter
        );
        threadsLocationSetupDelegate = new ThreadsLocationSetupDelegate(
                context,
                this,
                presenter,
                databaseManager,
                threadSaveManager
        );
        presenter.onCreate(sharedLocationSetupDelegate);

        setupLayout();
        populatePreferences();
        buildPreferences();

        onPreferenceChange(imageAutoLoadView);

        threadFolderSetting.setEnabled(ChanSettings.saveBoardFolder.get());
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
        } else if (item == boardFolderSetting) {
            updateThreadFolderSetting();
        } else if (item == videoDefaultMutedSetting) {
            updateHeadsetDefaultMutedSetting();
        } else if (item == incrementalThreadDownloadingSetting
                && !ChanSettings.incrementalThreadDownloadingEnabled.get()) {
            watchManager.stopSavingAllThread();
        }
    }

    @Subscribe
    public void onEvent(ChanSettings.SettingChanged<?> setting) {
        if (setting.setting == ChanSettings.saveLocation.getSafBaseDir()) {
            // Image save location (SAF) was chosen
            ChanSettings.saveLocation.resetFileDir();
            saveLocation.setDescription(ChanSettings.saveLocation.getSafBaseDir().get());
        } else if (setting.setting == ChanSettings.localThreadLocation.getSafBaseDir()) {
            // Local threads location (SAF) was chosen
            ChanSettings.localThreadLocation.resetFileDir();
            localThreadsLocation.setDescription(ChanSettings.localThreadLocation.getSafBaseDir().get());
        } else if (setting.setting == ChanSettings.saveLocation.getFileApiBaseDir()) {
            // Image save location (Java File API) was chosen
            saveLocation.setDescription(ChanSettings.saveLocation.getFileApiBaseDir().get());
        } else if (setting.setting == ChanSettings.localThreadLocation.getFileApiBaseDir()) {
            // Local threads location (Java File API) was chosen
            localThreadsLocation.setDescription(ChanSettings.localThreadLocation.getFileApiBaseDir().get());
        }
    }

    private void populatePreferences() {
        // Media group
        {
            SettingsGroup media = new SettingsGroup(R.string.settings_group_saving);

            //Save locations
            setupSaveLocationSetting(media);
            setupLocalThreadLocationSetting(media);

            //Save modifications
            boardFolderSetting = (BooleanSettingView) media.add(new BooleanSettingView(this,
                    ChanSettings.saveBoardFolder,
                    R.string.setting_save_board_folder,
                    R.string.setting_save_board_folder_description
            ));

            threadFolderSetting = (BooleanSettingView) media.add(new BooleanSettingView(this,
                    ChanSettings.saveThreadFolder,
                    R.string.setting_save_thread_folder,
                    R.string.setting_save_thread_folder_description
            ));

            media.add(new BooleanSettingView(this,
                    ChanSettings.saveServerFilename,
                    R.string.setting_save_server_filename,
                    R.string.setting_save_server_filename_description
            ));

            incrementalThreadDownloadingSetting = new BooleanSettingView(this,
                    ChanSettings.incrementalThreadDownloadingEnabled,
                    R.string.incremental_thread_downloading_title,
                    R.string.incremental_thread_downloading_description
            );
            requiresRestart.add(media.add(incrementalThreadDownloadingSetting));

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

            videoDefaultMutedSetting = (BooleanSettingView) video.add(new BooleanSettingView(this,
                    ChanSettings.videoDefaultMuted,
                    R.string.setting_video_default_muted,
                    R.string.setting_video_default_muted_description
            ));

            headsetDefaultMutedSetting = (BooleanSettingView) video.add(new BooleanSettingView(this,
                    ChanSettings.headsetDefaultMuted,
                    R.string.setting_headset_default_muted,
                    R.string.setting_headset_default_muted_description
            ));

            video.add(new BooleanSettingView(this,
                    ChanSettings.videoOpenExternal,
                    R.string.setting_video_open_external,
                    R.string.setting_video_open_external_description
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

            groups.add(loading);
        }
    }

    private void setupImagePreloadStrategySetting(SettingsGroup preloading) {
        List<ListSettingView.Item> items = new ArrayList<>();
        for (ChanSettings.ImageClickPreloadStrategy setting : ChanSettings.ImageClickPreloadStrategy.values()) {
            items.add(new ListSettingView.Item<>(setting.getKey(), setting));
        }

        preloading.add(new ListSettingView<ChanSettings.ImageClickPreloadStrategy>(this,
                ChanSettings.imageClickPreloadStrategy,
                getString(R.string.media_settings_image_click_preload_strategy_name),
                items
        ) {
            @Override
            public String getBottomDescription() {
                return getString(R.string.media_settings_image_click_preload_strategy_description) + "\n\n" + items.get(
                        selected).name;
            }
        });
    }

    //region Setup Local Threads location
    private void setupLocalThreadLocationSetting(SettingsGroup media) {
        if (!ChanSettings.incrementalThreadDownloadingEnabled.get()) {
            Logger.d(TAG, "setupLocalThreadLocationSetting() incrementalThreadDownloadingEnabled is disabled");
            return;
        }

        LinkSettingView localThreadsLocationSetting = new LinkSettingView(this,
                R.string.media_settings_local_threads_location_title,
                0,
                v -> threadsLocationSetupDelegate.showUseSAFOrOldAPIForLocalThreadsLocationDialog()
        );

        localThreadsLocation = (LinkSettingView) media.add(localThreadsLocationSetting);
        localThreadsLocation.setDescription(threadsLocationSetupDelegate.getLocalThreadsLocation());
    }

    private void setupSaveLocationSetting(SettingsGroup media) {
        LinkSettingView chooseSaveLocationSetting = new LinkSettingView(this,
                R.string.save_location_screen,
                0,
                v -> saveLocationSetupDelegate.showUseSAFOrOldAPIForSaveLocationDialog()
        );

        saveLocation = (LinkSettingView) media.add(chooseSaveLocationSetting);
        saveLocation.setDescription(saveLocationSetupDelegate.getSaveLocation());
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
                showToast(
                        context,
                        R.string.media_settings_cannot_continue_write_permission,
                        Toast.LENGTH_LONG
                );
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
    public void setDescription(@NonNull String newLocation) {
        BackgroundUtils.ensureMainThread();
        localThreadsLocation.setDescription(newLocation);
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
        showToast(context, R.string.media_settings_base_dir_reset_message);
    }

    @Override
    public void onLocalThreadsBaseDirectoryReset() {
        BackgroundUtils.ensureMainThread();
        showToast(context, R.string.media_settings_base_dir_reset_message);
    }

    @Override
    public void onCouldNotCreateDefaultBaseDir(@NotNull String path) {
        BackgroundUtils.ensureMainThread();
        showToast(
                context,
                context.getResources().getString(
                        R.string.media_settings_could_not_create_default_baseDir,
                        path
                )
        );
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
            if (!enabled && ChanSettings.videoAutoLoadNetwork.get().getKey().equals(modes[i].getKey())) {
                resetVideoMode = true;
            }
        }
    }

    private void updateThreadFolderSetting() {
        if (ChanSettings.saveBoardFolder.get()) {
            threadFolderSetting.setEnabled(true);
        } else {
            if (ChanSettings.saveThreadFolder.get()) {
                threadFolderSetting.onClick(threadFolderSetting.view);
            }
            threadFolderSetting.setEnabled(false);
            ChanSettings.saveThreadFolder.set(false);
        }
    }

    private void updateHeadsetDefaultMutedSetting() {
        headsetDefaultMutedSetting.setEnabled(ChanSettings.videoDefaultMuted.get());
    }
    //endregion
}
