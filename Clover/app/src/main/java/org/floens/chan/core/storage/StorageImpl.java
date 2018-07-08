package org.floens.chan.core.storage;

import android.content.Intent;
import android.net.Uri;

public interface StorageImpl {
    boolean supportsExternalStorage();

    Intent getOpenTreeIntent();

    void handleOpenTreeIntent(Uri uri);

    StorageFile obtainStorageFileForName(String name);

    String currentStorageName();
}
