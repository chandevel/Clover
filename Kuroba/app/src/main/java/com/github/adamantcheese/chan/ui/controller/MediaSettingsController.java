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

import android.app.AlertDialog;
import android.content.Context;
import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.presenter.MediaSettingsControllerPresenter;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.settings.BooleanSettingView;
import com.github.adamantcheese.chan.ui.settings.LinkSettingView;
import com.github.adamantcheese.chan.ui.settings.ListSettingView;
import com.github.adamantcheese.chan.ui.settings.SettingView;
import com.github.adamantcheese.chan.ui.settings.SettingsController;
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
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

public class MediaSettingsController
        extends SettingsController
        implements MediaSettingsControllerPresenter.MediaSettingsControllerCallbacks {
    private static final String TAG = "MediaSettingsController";

    // Special setting views
    private BooleanSettingView boardFolderSetting;
    private BooleanSettingView threadFolderSetting;
    private LinkSettingView saveLocation;
    private LinkSettingView localThreadsLocation;
    private ListSettingView<ChanSettings.MediaAutoLoadMode> imageAutoLoadView;
    private ListSettingView<ChanSettings.MediaAutoLoadMode> videoAutoLoadView;

    private LoadingViewController loadingViewController;
    private MediaSettingsControllerPresenter presenter;

    @Inject
    FileManager fileManager;
    @Inject
    FileChooser fileChooser;
    @Inject
    DatabaseManager databaseManager;

    public MediaSettingsController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        inject(this);

        EventBus.getDefault().register(this);
        navigation.setTitle(R.string.settings_screen_media);

        presenter = new MediaSettingsControllerPresenter(
                getAppContext(),
                fileManager,
                fileChooser,
                this
        );

        setupLayout();
        populatePreferences();
        buildPreferences();

        onPreferenceChange(imageAutoLoadView);

        threadFolderSetting.setEnabled(ChanSettings.saveBoardFolder.get());
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
        }
    }

    @Subscribe
    public void onEvent(ChanSettings.SettingChanged<?> setting) {
        if (setting.setting == ChanSettings.saveLocationUri) {
            // Image save location (SAF) was chosen
            String defaultDir = ChanSettings.getDefaultSaveLocationDir();

            ChanSettings.saveLocation.setNoUpdate(defaultDir);
            saveLocation.setDescription(ChanSettings.saveLocationUri.get());
        } else if (setting.setting == ChanSettings.localThreadsLocationUri) {
            // Local threads location (SAF) was chosen
            String defaultDir = ChanSettings.getDefaultLocalThreadsLocation();

            ChanSettings.localThreadLocation.setNoUpdate(defaultDir);
            localThreadsLocation.setDescription(ChanSettings.localThreadsLocationUri.get());
        } else if (setting.setting == ChanSettings.saveLocation) {
            // Image save location (Java File API) was chosen
            ChanSettings.saveLocationUri.setNoUpdate("");
            saveLocation.setDescription(ChanSettings.saveLocation.get());
        } else if (setting.setting == ChanSettings.localThreadLocation) {
            // Local threads location (Java File API) was chosen
            ChanSettings.localThreadsLocationUri.setNoUpdate("");
            localThreadsLocation.setDescription(ChanSettings.localThreadLocation.get());
        }
    }

    private void populatePreferences() {
        // Media group
        {
            SettingsGroup media = new SettingsGroup(R.string.settings_group_media);

            setupSaveLocationSetting(media);
            setupLocalThreadLocationSetting(media);

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

    /**
     * ==============================================
     * Setup Local Threads location
     * ==============================================
     */

    private void setupLocalThreadLocationSetting(SettingsGroup media) {
        if (!ChanSettings.incrementalThreadDownloadingEnabled.get()) {
            Logger.d(TAG, "setupLocalThreadLocationSetting() " +
                    "incrementalThreadDownloadingEnabled is disabled");
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
        new AlertDialog.Builder(context)
                .setTitle("There are " + downloadingThreadsCount + " threads being downloaded")
                .setMessage("You have to stop all the threads that are being downloaded before changing local threads base directory!")
                .setPositiveButton("OK", ((dialog, which) -> dialog.dismiss()))
                .create()
                .show();
    }

    private String getLocalThreadsLocation() {
        if (!ChanSettings.localThreadsLocationUri.get().isEmpty()) {
            return ChanSettings.localThreadsLocationUri.get();
        }

        return ChanSettings.localThreadLocation.get();
    }

    private void showUseSAFOrOldAPIForLocalThreadsLocationDialog() {
        long downloadingThreadsCount = databaseManager.runTask(() -> {
            return databaseManager.getDatabaseSavedThreadManager().countDownloadingThreads().call();
        });

        if (downloadingThreadsCount > 0) {
            showStopAllDownloadingThreadsDialog(downloadingThreadsCount);
            return;
        }

        AlertDialog alertDialog = new AlertDialog.Builder(context)
                .setTitle(R.string.use_saf_for_local_threads_location_dialog_title)
                .setMessage(R.string.use_saf_for_local_threads_location_dialog_message)
                .setPositiveButton(R.string.use_saf_dialog_positive_button_text, (dialog, which) -> {
                    presenter.onLocalThreadsLocationUseSAFClicked();
                })
                .setNegativeButton(R.string.use_saf_dialog_negative_button_text, (dialog, which) -> {
                    onLocalThreadsLocationUseOldApiClicked();
                    dialog.dismiss();
                })
                .create();

        alertDialog.show();
    }

    /**
     * Select a directory where local threads will be stored via the old Java File API
     */
    private void onLocalThreadsLocationUseOldApiClicked() {
        SaveLocationController saveLocationController = new SaveLocationController(
                context,
                SaveLocationController.SaveLocationControllerMode.LocalThreadsSaveLocation,
                dirPath -> {
                    presenter.onLocalThreadsLocationChosen(dirPath);
                });

        navigationController.pushController(saveLocationController);
    }

    /**
     * ==============================================
     * Setup Save Files location
     * ==============================================
     */

    private void setupSaveLocationSetting(SettingsGroup media) {
        LinkSettingView chooseSaveLocationSetting = new LinkSettingView(this,
                R.string.save_location_screen,
                0,
                v -> showUseSAFOrOldAPIForSaveLocationDialog());

        saveLocation = (LinkSettingView) media.add(chooseSaveLocationSetting);
        saveLocation.setDescription(getSaveLocation());
    }

    private String getSaveLocation() {
        if (!ChanSettings.saveLocationUri.get().isEmpty()) {
            return ChanSettings.saveLocationUri.get();
        }

        return ChanSettings.saveLocation.get();
    }

    private void showUseSAFOrOldAPIForSaveLocationDialog() {
        AlertDialog alertDialog = new AlertDialog.Builder(context)
                .setTitle(R.string.use_saf_for_save_location_dialog_title)
                .setMessage(R.string.use_saf_for_save_location_dialog_message)
                .setPositiveButton(R.string.use_saf_dialog_positive_button_text, (dialog, which) -> {
                    presenter.onSaveLocationUseSAFClicked();
                })
                .setNegativeButton(R.string.use_saf_dialog_negative_button_text, (dialog, which) -> {
                    onSaveLocationUseOldApiClicked();
                    dialog.dismiss();
                })
                .create();

        alertDialog.show();
    }

    /**
     * Select a directory where saved images will be stored via the old Java File API
     */
    private void onSaveLocationUseOldApiClicked() {
        SaveLocationController saveLocationController = new SaveLocationController(
                context,
                SaveLocationController.SaveLocationControllerMode.ImageSaveLocation,
                dirPath -> {
                    presenter.onSaveLocationChosen(dirPath);
                });

        navigationController.pushController(saveLocationController);
    }

    /**
     * ==============================================
     * Presenter callbacks
     * ==============================================
     */

    @Override
    public void askUserIfTheyWantToMoveOldThreadsToTheNewDirectory(
            @NonNull AbstractFile oldBaseDirectory,
            @NonNull AbstractFile newBaseDirectory
    ) {
        AlertDialog alertDialog = new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.media_settings_move_threads_to_new_dir))
                .setMessage(context.getString(R.string.media_settings_operation_may_take_some_time))
                .setPositiveButton(
                        context.getString(R.string.media_settings_move_threads),
                        (dialog, which) -> {
                            presenter.moveOldFilesToTheNewDirectory(
                                    oldBaseDirectory,
                                    newBaseDirectory
                            );
                        })
                .setNegativeButton(
                        context.getString(R.string.media_settings_do_not_move_threads),
                        (dialog, which) -> {
                            dialog.dismiss();
                        }
                )
                .create();

        alertDialog.show();
    }

    @Override
    public void askUserIfTheyWantToMoveOldSavedFilesToTheNewDirectory(
            @NotNull AbstractFile oldBaseDirectory,
            @NotNull AbstractFile newBaseDirectory
    ) {
        AlertDialog alertDialog = new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.media_settings_move_saved_file_to_new_dir))
                .setMessage(context.getString(R.string.media_settings_operation_may_take_some_time))
                .setPositiveButton(
                        context.getString(R.string.media_settings_move_saved_files),
                        (dialog, which) -> {
                            presenter.moveOldFilesToTheNewDirectory(
                                    oldBaseDirectory,
                                    newBaseDirectory
                            );
                        })
                .setNegativeButton(
                        context.getString(R.string.media_settings_do_not_move_saved_files),
                        (dialog, which) -> {
                            dialog.dismiss();
                        }
                )
                .create();

        alertDialog.show();
    }

    @Override
    public void onCopyDirectoryEnded(
            @NonNull AbstractFile oldBaseDirectory,
            @NonNull AbstractFile newBaseDirectory,
            boolean result
    ) {
        BackgroundUtils.ensureMainThread();

        if (loadingViewController == null) {
            throw new IllegalStateException("LoadingViewController was not shown beforehand!");
        }

        loadingViewController.stopPresenting();
        loadingViewController = null;

        if (!result) {
            showToast(context.getString(R.string.media_settings_couldnot_copy_files), Toast.LENGTH_LONG);
        } else {
            showDeleteOldDirectoryDialog(oldBaseDirectory);
            showToast(context.getString(R.string.media_settings_files_copied), Toast.LENGTH_LONG);
        }
    }

    private void showDeleteOldDirectoryDialog(
            @NonNull AbstractFile oldBaseDirectory
    ) {
        AlertDialog alertDialog = new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.media_settings_would_you_life_to_delete_old_dir))
                .setMessage(context.getString(R.string.media_settings_file_have_been_copied))
                .setPositiveButton(
                        context.getString(R.string.media_settings_delete_button_name),
                        (dialog, which) -> {
                            if (!fileManager.delete(oldBaseDirectory)) {
                                showToast(
                                        context.getString(R.string.media_settings_couldnot_delete_old_dir),
                                        Toast.LENGTH_LONG
                                );
                                return;
                            }

                            if (oldBaseDirectory instanceof ExternalFile) {
                                forgetPreviousExternalBaseDirectory(oldBaseDirectory);
                            }

                            showToast(
                                    context.getString(R.string.media_settings_old_dir_deleted),
                                    Toast.LENGTH_LONG
                            );
                        })
                .setNegativeButton(
                        context.getString(R.string.media_settings_do_not_delete),
                        (dialog, which) -> {
                            if (oldBaseDirectory instanceof ExternalFile) {
                                forgetPreviousExternalBaseDirectory(oldBaseDirectory);
                            }

                            dialog.dismiss();
                        })
                .create();

        alertDialog.show();
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
    public void showToast(@NotNull String message, int length) {
        BackgroundUtils.ensureMainThread();
        Toast.makeText(context, message, length).show();
    }

    @Override
    public void updateLocalThreadsLocation(@NotNull String newLocation) {
        BackgroundUtils.ensureMainThread();
        localThreadsLocation.setDescription(newLocation);
    }

    @Override
    public void showCopyFilesDialog(
            int filesCount,
            @NotNull AbstractFile oldBaseDirectory,
            @NotNull AbstractFile newBaseDirectory
    ) {
        BackgroundUtils.ensureMainThread();

        if (loadingViewController != null) {
            throw new IllegalStateException(
                    "Previous loadingViewController was not destroyed"
            );
        }

        AlertDialog alertDialog = new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.media_settings_copy_files))
                .setMessage(
                        context.getString(R.string.media_settings_do_you_want_to_copy_files,
                                filesCount)
                )
                .setPositiveButton(
                        context.getString(R.string.media_settings_copy_files),
                        (dialog, which) -> {
                            loadingViewController = new LoadingViewController(
                                    context,
                                    false
                            );

                            navigationController.presentController(loadingViewController);

                            presenter.moveFilesInternal(
                                    oldBaseDirectory,
                                    newBaseDirectory
                            );
                        })
                .setNegativeButton(
                        context.getString(R.string.media_settings_do_not_copy_files),
                        (dialog, which) -> {
                            dialog.dismiss();
                        }
                )
                .create();

        alertDialog.show();
    }

    /**
     * ==============================================
     * Other methods
     * ==============================================
     */

    private void forgetPreviousExternalBaseDirectory(
            @NonNull AbstractFile oldLocalThreadsDirectory
    ) {
        if (oldLocalThreadsDirectory instanceof ExternalFile) {
            Uri safTreeUri = oldLocalThreadsDirectory
                    .<CachingDocumentFile>getFileRoot().getHolder().uri();

            if (!fileChooser.forgetSAFTree(safTreeUri)) {
                showToast(
                        context.getString(R.string.media_settings_could_not_release_uri_permissions),
                        Toast.LENGTH_SHORT
                );
            }
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
