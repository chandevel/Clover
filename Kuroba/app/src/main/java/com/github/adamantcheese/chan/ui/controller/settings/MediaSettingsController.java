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
import android.app.AlertDialog;
import android.content.Context;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.manager.ThreadSaveManager;
import com.github.adamantcheese.chan.core.manager.WatchManager;
import com.github.adamantcheese.chan.core.presenter.MediaSettingsControllerPresenter;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.controller.LoadingViewController;
import com.github.adamantcheese.chan.ui.controller.SaveLocationController;
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
import com.github.k1rakishou.fsaf.document_file.CachingDocumentFile;
import com.github.k1rakishou.fsaf.file.AbstractFile;
import com.github.k1rakishou.fsaf.file.ExternalFile;

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
        implements MediaSettingsControllerCallbacks {
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

    private LoadingViewController loadingViewController;
    private MediaSettingsControllerPresenter presenter;
    private RuntimePermissionsHelper runtimePermissionsHelper;

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

        presenter = new MediaSettingsControllerPresenter(fileManager, fileChooser, this);

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
                v -> showUseSAFOrOldAPIForLocalThreadsLocationDialog()
        );

        localThreadsLocation = (LinkSettingView) media.add(localThreadsLocationSetting);
        localThreadsLocation.setDescription(getLocalThreadsLocation());
    }

    private void showStopAllDownloadingThreadsDialog(long downloadingThreadsCount) {
        String title = getString(R.string.media_settings_there_are_active_downloads, downloadingThreadsCount);
        String message = getString(R.string.media_settings_you_have_to_stop_all_downloads);

        new AlertDialog.Builder(context).setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.ok, (dialog, which) -> dialog.dismiss())
                .create()
                .show();
    }

    private String getLocalThreadsLocation() {
        if (ChanSettings.localThreadLocation.isSafDirActive()) {
            return ChanSettings.localThreadLocation.getSafBaseDir().get();
        }

        return ChanSettings.localThreadLocation.getFileApiBaseDir().get();
    }

    private void showUseSAFOrOldAPIForLocalThreadsLocationDialog() {
        if (!runtimePermissionsHelper.hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            runtimePermissionsHelper.requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, granted -> {
                if (!granted) {
                    showToast(R.string.media_settings_cannot_continue_write_permission, Toast.LENGTH_LONG);
                } else {
                    showUseSAFOrOldAPIForLocalThreadsLocationDialog();
                }
            });

            return;
        }

        long downloadingThreadsCount = databaseManager.runTask(() -> databaseManager.getDatabaseSavedThreadManager()
                .countDownloadingThreads()
                .call());

        if (downloadingThreadsCount > 0) {
            showStopAllDownloadingThreadsDialog(downloadingThreadsCount);
            return;
        }

        boolean areThereActiveDownloads = threadSaveManager.isThereAtLeastOneActiveDownload();
        if (areThereActiveDownloads) {
            showSomeDownloadsAreStillBeingProcessed();
            return;
        }

        AlertDialog alertDialog =
                new AlertDialog.Builder(context).setTitle(R.string.media_settings_use_saf_for_local_threads_location_dialog_title)
                        .setMessage(R.string.media_settings_use_saf_for_local_threads_location_dialog_message)
                        .setPositiveButton(R.string.media_settings_use_saf_dialog_positive_button_text,
                                (dialog, which) -> presenter.onLocalThreadsLocationUseSAFClicked()
                        )
                        .setNegativeButton(R.string.media_settings_use_saf_dialog_negative_button_text,
                                (dialog, which) -> {
                                    onLocalThreadsLocationUseOldApiClicked();
                                    dialog.dismiss();
                                }
                        )
                        .create();

        alertDialog.show();
    }

    private void showSomeDownloadsAreStillBeingProcessed() {
        AlertDialog alertDialog =
                new AlertDialog.Builder(context).setTitle(R.string.media_settings_some_thread_downloads_are_still_processed)
                        .setMessage(R.string.media_settings_do_not_terminate_the_app_manually)
                        .setPositiveButton(R.string.media_settings_use_saf_dialog_positive_button_text,
                                (dialog, which) -> presenter.onLocalThreadsLocationUseSAFClicked()
                        )
                        .setNegativeButton(R.string.media_settings_use_saf_dialog_negative_button_text,
                                (dialog, which) -> {
                                    onLocalThreadsLocationUseOldApiClicked();
                                    dialog.dismiss();
                                }
                        )
                        .create();

        alertDialog.show();
    }

    /**
     * Select a directory where local threads will be stored via the old Java File API
     */
    private void onLocalThreadsLocationUseOldApiClicked() {
        SaveLocationController saveLocationController = new SaveLocationController(context,
                SaveLocationController.SaveLocationControllerMode.LocalThreadsSaveLocation,
                dirPath -> presenter.onLocalThreadsLocationChosen(dirPath)
        );

        navigationController.pushController(saveLocationController);
    }
    //endregion

    //region Setup Save Files location

    private void setupSaveLocationSetting(SettingsGroup media) {
        LinkSettingView chooseSaveLocationSetting = new LinkSettingView(this,
                R.string.save_location_screen,
                0,
                v -> showUseSAFOrOldAPIForSaveLocationDialog()
        );

        saveLocation = (LinkSettingView) media.add(chooseSaveLocationSetting);
        saveLocation.setDescription(getSaveLocation());
    }

    private String getSaveLocation() {
        if (ChanSettings.saveLocation.isSafDirActive()) {
            return ChanSettings.saveLocation.getSafBaseDir().get();
        }

        return ChanSettings.saveLocation.getFileApiBaseDir().get();
    }

    private void showUseSAFOrOldAPIForSaveLocationDialog() {
        if (!runtimePermissionsHelper.hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            runtimePermissionsHelper.requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, granted -> {
                if (!granted) {
                    showToast(R.string.media_settings_cannot_continue_write_permission, Toast.LENGTH_LONG);
                } else {
                    showUseSAFOrOldAPIForSaveLocationDialog();
                }
            });

            return;
        }

        AlertDialog alertDialog =
                new AlertDialog.Builder(context).setTitle(R.string.media_settings_use_saf_for_save_location_dialog_title)
                        .setMessage(R.string.media_settings_use_saf_for_save_location_dialog_message)
                        .setPositiveButton(R.string.media_settings_use_saf_dialog_positive_button_text,
                                (dialog, which) -> presenter.onSaveLocationUseSAFClicked()
                        )
                        .setNegativeButton(R.string.media_settings_use_saf_dialog_negative_button_text,
                                (dialog, which) -> {
                                    onSaveLocationUseOldApiClicked();
                                    dialog.dismiss();
                                }
                        )
                        .create();

        alertDialog.show();
    }

    /**
     * Select a directory where saved images will be stored via the old Java File API
     */
    private void onSaveLocationUseOldApiClicked() {
        SaveLocationController saveLocationController = new SaveLocationController(context,
                SaveLocationController.SaveLocationControllerMode.ImageSaveLocation,
                dirPath -> presenter.onSaveLocationChosen(dirPath)
        );

        navigationController.pushController(saveLocationController);
    }
    //endregion

    //region Presenter callbacks
    @Override
    public void askUserIfTheyWantToMoveOldThreadsToTheNewDirectory(
            @Nullable AbstractFile oldBaseDirectory, @NonNull AbstractFile newBaseDirectory
    ) {
        if (oldBaseDirectory == null) {
            showToast(R.string.done, Toast.LENGTH_LONG);
            return;
        }

        if (fileManager.areTheSame(oldBaseDirectory, newBaseDirectory)) {
            forgetOldSAFBaseDirectory(oldBaseDirectory);

            showToast(R.string.done, Toast.LENGTH_LONG);
            return;
        }

        AlertDialog alertDialog =
                new AlertDialog.Builder(context).setTitle(getString(R.string.media_settings_move_threads_to_new_dir))
                        .setMessage(getString(R.string.media_settings_operation_may_take_some_time))
                        .setPositiveButton(R.string.move,
                                (dialog, which) -> presenter.moveOldFilesToTheNewDirectory(oldBaseDirectory,
                                        newBaseDirectory
                                )
                        )
                        .setNegativeButton(R.string.do_not, (dialog, which) -> dialog.dismiss())
                        .create();

        alertDialog.show();
    }

    private void forgetOldSAFBaseDirectory(@NonNull AbstractFile oldBaseDirectory) {
        if (oldBaseDirectory instanceof ExternalFile) {
            forgetPreviousExternalBaseDirectory(oldBaseDirectory);
        }
    }

    private void forgetPreviousExternalBaseDirectory(
            @NonNull AbstractFile oldLocalThreadsDirectory
    ) {
        if (oldLocalThreadsDirectory instanceof ExternalFile) {
            Uri safTreeUri = oldLocalThreadsDirectory.<CachingDocumentFile>getFileRoot().getHolder().uri();

            if (!fileChooser.forgetSAFTree(safTreeUri)) {
                showToast(R.string.media_settings_could_not_release_uri_permissions, Toast.LENGTH_SHORT);
            }
        }
    }

    @Override
    public void askUserIfTheyWantToMoveOldSavedFilesToTheNewDirectory(
            @Nullable AbstractFile oldBaseDirectory, @NotNull AbstractFile newBaseDirectory
    ) {
        if (oldBaseDirectory == null) {
            showToast(R.string.done, Toast.LENGTH_LONG);
            return;
        }

        if (fileManager.areTheSame(oldBaseDirectory, newBaseDirectory)) {
            forgetOldSAFBaseDirectory(oldBaseDirectory);

            showToast(R.string.done, Toast.LENGTH_LONG);
            return;
        }

        AlertDialog alertDialog =
                new AlertDialog.Builder(context).setTitle(getString(R.string.media_settings_move_saved_file_to_new_dir))
                        .setMessage(getString(R.string.media_settings_operation_may_take_some_time))
                        .setPositiveButton(R.string.move,
                                (dialog, which) -> presenter.moveOldFilesToTheNewDirectory(oldBaseDirectory,
                                        newBaseDirectory
                                )
                        )
                        .setNegativeButton(R.string.do_not, (dialog, which) -> dialog.dismiss())
                        .create();

        alertDialog.show();
    }

    @Override
    public void onCopyDirectoryEnded(
            @NonNull AbstractFile oldBaseDirectory, @NonNull AbstractFile newBaseDirectory, boolean result
    ) {
        BackgroundUtils.ensureMainThread();

        if (loadingViewController != null) {
            loadingViewController.stopPresenting();
            loadingViewController = null;
        }

        if (!result) {
            showToast(R.string.media_settings_could_not_copy_files, Toast.LENGTH_LONG);
        } else {
            if (fileManager.isChildOfDirectory(oldBaseDirectory, newBaseDirectory)) {
                forgetOldSAFBaseDirectory(oldBaseDirectory);

                showToast(R.string.done, Toast.LENGTH_LONG);
                return;
            }

            showDeleteOldFilesDialog(oldBaseDirectory);
            showToast(R.string.media_settings_files_copied, Toast.LENGTH_LONG);
        }
    }

    private void showDeleteOldFilesDialog(
            @NonNull AbstractFile oldBaseDirectory
    ) {
        AlertDialog alertDialog =
                new AlertDialog.Builder(context).setTitle(getString(R.string.media_settings_would_you_like_to_delete_file_in_old_dir))
                        .setMessage(getString(R.string.media_settings_file_have_been_copied))
                        .setPositiveButton(R.string.delete,
                                (dialog, which) -> onDeleteOldFilesClicked(oldBaseDirectory)
                        )
                        .setNegativeButton(R.string.do_not, (dialog, which) -> {
                            forgetOldSAFBaseDirectory(oldBaseDirectory);
                            dialog.dismiss();
                        })
                        .create();

        alertDialog.show();
    }

    private void onDeleteOldFilesClicked(@NonNull AbstractFile oldBaseDirectory) {
        if (!fileManager.deleteContent(oldBaseDirectory)) {
            showToast(R.string.media_settings_could_not_delete_files_in_old_dir, Toast.LENGTH_LONG);
            return;
        }

        forgetOldSAFBaseDirectory(oldBaseDirectory);

        showToast(R.string.media_settings_old_files_deleted, Toast.LENGTH_LONG);
    }

    @Override
    public void updateLoadingViewText(@NotNull String text) {
        BackgroundUtils.ensureMainThread();

        if (loadingViewController != null) {
            loadingViewController.updateWithText(text);
        }
    }

    @Override
    public void updateSaveLocationViewText(@NotNull String newLocation) {
        BackgroundUtils.ensureMainThread();
        saveLocation.setDescription(newLocation);
    }

    @Override
    public void updateLocalThreadsLocation(@NotNull String newLocation) {
        BackgroundUtils.ensureMainThread();
        localThreadsLocation.setDescription(newLocation);
    }

    @Override
    public void showCopyFilesDialog(
            int filesCount, @NotNull AbstractFile oldBaseDirectory, @NotNull AbstractFile newBaseDirectory
    ) {
        BackgroundUtils.ensureMainThread();

        if (loadingViewController != null) {
            loadingViewController.stopPresenting();
            loadingViewController = null;
        }

        AlertDialog alertDialog =
                new AlertDialog.Builder(context).setTitle(getString(R.string.media_settings_copy_files))
                        .setMessage(getString(R.string.media_settings_do_you_want_to_copy_files, filesCount))
                        .setPositiveButton(R.string.media_settings_copy_files, (dialog, which) -> {
                            loadingViewController = new LoadingViewController(context, false);

                            navigationController.presentController(loadingViewController);

                            presenter.moveFilesInternal(oldBaseDirectory, newBaseDirectory);
                        })
                        .setNegativeButton(R.string.do_not, (dialog, which) -> dialog.dismiss())
                        .create();

        alertDialog.show();
    }
    //endregion

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
