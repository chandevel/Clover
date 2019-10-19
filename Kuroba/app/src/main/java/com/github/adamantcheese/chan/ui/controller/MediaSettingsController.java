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
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.settings.BooleanSettingView;
import com.github.adamantcheese.chan.ui.settings.LinkSettingView;
import com.github.adamantcheese.chan.ui.settings.ListSettingView;
import com.github.adamantcheese.chan.ui.settings.SettingView;
import com.github.adamantcheese.chan.ui.settings.SettingsController;
import com.github.adamantcheese.chan.ui.settings.SettingsGroup;
import com.github.adamantcheese.chan.ui.settings.base_directory.LocalThreadsBaseDirectory;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.k1rakishou.fsaf.FileChooser;
import com.github.k1rakishou.fsaf.FileManager;
import com.github.k1rakishou.fsaf.callback.DirectoryChooserCallback;
import com.github.k1rakishou.fsaf.document_file.CachingDocumentFile;
import com.github.k1rakishou.fsaf.file.AbstractFile;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import kotlin.Unit;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.runOnUiThread;

public class MediaSettingsController extends SettingsController {
    private static final String TAG = "MediaSettingsController";

    // Special setting views
    private BooleanSettingView boardFolderSetting;
    private BooleanSettingView threadFolderSetting;
    private LinkSettingView saveLocation;
    private LinkSettingView localThreadsLocation;
    private ListSettingView<ChanSettings.MediaAutoLoadMode> imageAutoLoadView;
    private ListSettingView<ChanSettings.MediaAutoLoadMode> videoAutoLoadView;

    private Executor fileCopyingExecutor = Executors.newSingleThreadExecutor();

    @Inject
    FileManager fileManager;
    @Inject
    FileChooser fileChooser;

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

    private void setupLocalThreadLocationSetting(SettingsGroup media) {
        if (!ChanSettings.incrementalThreadDownloadingEnabled.get()) {
            Logger.d(TAG, "setupLocalThreadLocationSetting() incrementalThreadDownloadingEnabled is disabled");
            return;
        }

        LinkSettingView localThreadsLocationSetting = new LinkSettingView(this,
                R.string.media_settings_local_threads_location_title,
                0,
                v -> showUseSAFOrOldAPIForLocalThreadsLocationDialog());


        localThreadsLocation = (LinkSettingView) media.add(localThreadsLocationSetting);
        localThreadsLocation.setDescription(getLocalThreadsLocation());
    }

    private String getLocalThreadsLocation() {
        if (!ChanSettings.localThreadsLocationUri.get().isEmpty()) {
            return ChanSettings.localThreadsLocationUri.get();
        }

        return ChanSettings.localThreadLocation.get();
    }

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

    private void showUseSAFOrOldAPIForLocalThreadsLocationDialog() {
        AlertDialog alertDialog = new AlertDialog.Builder(context)
                .setTitle(R.string.use_saf_for_local_threads_location_dialog_title)
                .setMessage(R.string.use_saf_for_local_threads_location_dialog_message)
                .setPositiveButton(R.string.use_saf_dialog_positive_button_text, (dialog, which) -> {
                    onLocalThreadsLocationUseSAFClicked();
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
                    AbstractFile oldLocalThreadsDirectory = fileManager.newBaseDirectoryFile(
                            LocalThreadsBaseDirectory.class
                    );

                    Logger.d(TAG, "SaveLocationController with LocalThreadsSaveLocation mode returned dir "
                            + dirPath);

                    // Supa hack to get the callback called
                    ChanSettings.localThreadLocation.setSync("");
                    ChanSettings.localThreadLocation.setSync(dirPath);

                    AbstractFile newLocalThreadsDirectory = fileManager.newBaseDirectoryFile(
                            LocalThreadsBaseDirectory.class
                    );

                    askUserIfTheyWantToMoveOldThreadsToTheNewDirectory(
                            oldLocalThreadsDirectory,
                            newLocalThreadsDirectory);
                });

        navigationController.pushController(saveLocationController);
    }

    /**
     * Select a directory where local threads will be stored via the SAF
     */
    private void onLocalThreadsLocationUseSAFClicked() {
        fileChooser.openChooseDirectoryDialog(new DirectoryChooserCallback() {
            @Override
            public void onResult(@NotNull Uri uri) {
                // TODO: check that there are no files in the directory and warn the user that something
                //  might go wrong in this case
                AbstractFile oldLocalThreadsDirectory = fileManager.newBaseDirectoryFile(
                        LocalThreadsBaseDirectory.class
                );

                ChanSettings.localThreadsLocationUri.set(uri.toString());
                String defaultDir = ChanSettings.getDefaultLocalThreadsLocation();

                ChanSettings.localThreadLocation.setNoUpdate(defaultDir);
                localThreadsLocation.setDescription(uri.toString());

                AbstractFile newLocalThreadsDirectory = fileManager.newBaseDirectoryFile(
                        LocalThreadsBaseDirectory.class
                );

                askUserIfTheyWantToMoveOldThreadsToTheNewDirectory(
                        oldLocalThreadsDirectory,
                        newLocalThreadsDirectory);
            }

            @Override
            public void onCancel(@NotNull String reason) {
                Toast.makeText(context, reason, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void askUserIfTheyWantToMoveOldThreadsToTheNewDirectory(
            AbstractFile oldLocalThreadsDirectory,
            AbstractFile newLocalThreadsDirectory) {

        // TODO: strings
        AlertDialog alertDialog = new AlertDialog.Builder(context)
                .setTitle("Move old local threads to the new directory?")
                .setMessage("This operation may take quite some time. Once started this operation shouldn't be canceled, otherwise something may break")
                .setPositiveButton("Move", (dialog, which) -> {
                    moveOldFilesToTheNewDirectory(oldLocalThreadsDirectory, newLocalThreadsDirectory);
                })
                .setNegativeButton("Do not", (dialog, which) -> dialog.dismiss())
                .create();

        alertDialog.show();
    }

    private void moveOldFilesToTheNewDirectory(
            @Nullable AbstractFile oldLocalThreadsDirectory,
            @Nullable AbstractFile newLocalThreadsDirectory) {
        if (oldLocalThreadsDirectory == null || newLocalThreadsDirectory == null) {
            Logger.e(TAG, "One of the directories is null, cannot copy " +
                    "(oldLocalThreadsDirectory is null == " + (oldLocalThreadsDirectory == null) + ")" +
                    ", newLocalThreadsDirectory is null == " + (newLocalThreadsDirectory == null) + ")");
            return;
        }

        Logger.d(TAG, "oldLocalThreadsDirectory = " + oldLocalThreadsDirectory.getFullPath()
                + ", newLocalThreadsDirectory = " + newLocalThreadsDirectory.getFullPath());

        int filesCount = fileManager.listFiles(oldLocalThreadsDirectory).size();
        if (filesCount == 0) {
            Toast.makeText(context, "No files to copy", Toast.LENGTH_SHORT).show();
            return;
        }

        // TODO: Strings
        AlertDialog alertDialog = new AlertDialog.Builder(context)
                .setTitle("Copy files")
                .setMessage("Do you want to copy " + filesCount + " from an old directory to the new one?")
                .setPositiveButton("Copy", ((dialog, which) -> {
                    LoadingViewController loadingViewController = new LoadingViewController(
                            context,
                            false);
                    navigationController.pushController(loadingViewController);

                    fileCopyingExecutor.execute(() -> {
                        moveFilesInternal(
                                oldLocalThreadsDirectory,
                                newLocalThreadsDirectory,
                                loadingViewController);
                    });
                }))
                .setNegativeButton("Do not", ((dialog, which) -> dialog.dismiss()))
                .create();

        alertDialog.show();
    }

    private void moveFilesInternal(
            @NonNull AbstractFile oldLocalThreadsDirectory,
            @NonNull AbstractFile newLocalThreadsDirectory,
            LoadingViewController loadingViewController) {
        boolean result = fileManager.copyDirectoryWithContent(
                oldLocalThreadsDirectory,
                newLocalThreadsDirectory,
                false,
                (fileIndex, totalFilesCount) -> {
                    runOnUiThread(() -> {
                        // TODO: strings
                        String text = String.format(
                                Locale.US,
                                // TODO: strings
                                "Copying file %d out of %d",
                                fileIndex,
                                totalFilesCount);

                        loadingViewController.updateWithText(text);
                    });

                    return Unit.INSTANCE;
                });

        runOnUiThread(() -> {
            navigationController.popController();

            if (!result) {
                // TODO: strings
                Toast.makeText(
                        context,
                        "Could not copy one directory's file into another one",
                        Toast.LENGTH_LONG
                ).show();
            } else {
                Uri safTreeuri = oldLocalThreadsDirectory
                        .<CachingDocumentFile>getFileRoot().getHolder().getUri();

                fileChooser.forgetSAFTree(safTreeuri);

                // TODO: delete old directory dialog

                // TODO: strings
                Toast.makeText(
                        context,
                        "Successfully copied files",
                        Toast.LENGTH_LONG
                ).show();
            }
        });
    }

    private void showUseSAFOrOldAPIForSaveLocationDialog() {
        AlertDialog alertDialog = new AlertDialog.Builder(context)
                .setTitle(R.string.use_saf_for_save_location_dialog_title)
                .setMessage(R.string.use_saf_for_save_location_dialog_message)
                .setPositiveButton(R.string.use_saf_dialog_positive_button_text, (dialog, which) -> {
                    onSaveLocationUseSAFClicked();
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
                    Logger.d(TAG, "SaveLocationController with ImageSaveLocation mode returned dir "
                            + dirPath);

                    // Supa hack to get the callback called
                    ChanSettings.saveLocation.setSync("");
                    ChanSettings.saveLocation.setSync(dirPath);
                });

        navigationController.pushController(saveLocationController);
    }

    /**
     * Select a directory where saved images will be stored via the SAF
     */
    private void onSaveLocationUseSAFClicked() {
        fileChooser.openChooseDirectoryDialog(new DirectoryChooserCallback() {
            @Override
            public void onResult(@NotNull Uri uri) {
                // TODO: check that there are no files in the directory at warn user that something
                //  might go wrong in this case
                ChanSettings.saveLocationUri.set(uri.toString());

                String defaultDir = ChanSettings.getDefaultSaveLocationDir();
                ChanSettings.saveLocation.setNoUpdate(defaultDir);
                saveLocation.setDescription(uri.toString());
            }

            @Override
            public void onCancel(@NotNull String reason) {
                Toast.makeText(context, reason, Toast.LENGTH_LONG).show();
            }
        });
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
