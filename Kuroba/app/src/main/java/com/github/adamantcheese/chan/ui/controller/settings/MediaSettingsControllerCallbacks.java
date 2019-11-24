package com.github.adamantcheese.chan.ui.controller.settings;

import com.github.k1rakishou.fsaf.file.AbstractFile;

public interface MediaSettingsControllerCallbacks {

    void showToast(String message, int length);

    void showToast(String message);

    void updateLocalThreadsLocation(String newLocation);

    void askUserIfTheyWantToMoveOldThreadsToTheNewDirectory(AbstractFile oldBaseDirectory,
                                                            AbstractFile newBaseDirectory
    );

    void askUserIfTheyWantToMoveOldSavedFilesToTheNewDirectory(AbstractFile oldBaseDirectory,
                                                               AbstractFile newBaseDirectory
    );

    void updateLoadingViewText(String text);

    void updateSaveLocationViewText(String newLocation);

    void showCopyFilesDialog(int filesCount, AbstractFile oldBaseDirectory, AbstractFile newBaseDirectory);

    void onCopyDirectoryEnded(AbstractFile oldBaseDirectory, AbstractFile newBaseDirectory, boolean result);
}
