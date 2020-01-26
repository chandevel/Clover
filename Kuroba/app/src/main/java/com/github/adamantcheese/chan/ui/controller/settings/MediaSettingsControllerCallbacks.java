package com.github.adamantcheese.chan.ui.controller.settings;

import androidx.annotation.Nullable;

import com.github.k1rakishou.fsaf.file.AbstractFile;

public interface MediaSettingsControllerCallbacks {
    void updateLocalThreadsLocation(String newLocation);

    void askUserIfTheyWantToMoveOldThreadsToTheNewDirectory(
            @Nullable AbstractFile oldBaseDirectory, AbstractFile newBaseDirectory
    );

    void askUserIfTheyWantToMoveOldSavedFilesToTheNewDirectory(
            @Nullable AbstractFile oldBaseDirectory, AbstractFile newBaseDirectory
    );

    void updateLoadingViewText(String text);

    void updateSaveLocationViewText(String newLocation);

    void showCopyFilesDialog(int filesCount, AbstractFile oldBaseDirectory, AbstractFile newBaseDirectory);

    void onCopyDirectoryEnded(AbstractFile oldBaseDirectory, AbstractFile newBaseDirectory, boolean result);
}
