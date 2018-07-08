package org.floens.chan.core.storage;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

public class BaseStorageImpl implements StorageImpl {
    protected Context applicationContext;

    public BaseStorageImpl(Context applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public boolean supportsExternalStorage() {
        return false;
    }

    @Override
    public Intent getOpenTreeIntent() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void handleOpenTreeIntent(Uri uri) {
        throw new UnsupportedOperationException();
    }

    @Override
    public StorageFile obtainStorageFileForName(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String currentStorageName() {
        throw new UnsupportedOperationException();
    }
}
