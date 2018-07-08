package org.floens.chan.core.storage;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Abstraction of the storage APIs available in Android.
 * <p>
 * This is used primarily for saving images, especially on removable storage.
 * <p>
 * First, a good read:
 * https://commonsware.com/blog/2017/11/13/storage-situation-internal-storage.html
 * https://commonsware.com/blog/2017/11/14/storage-situation-external-storage.html
 * https://commonsware.com/blog/2017/11/15/storage-situation-removable-storage.html
 * <p>
 * The Android Storage Access Framework can be used from Android 5.0 and higher. Since Android 5.0
 * it has support for granting permissions for a directory, which we want to save our files to.
 * <p>
 * Otherwise a fallback is provided for only saving on the primary volume with the older APIs.
 */
@Singleton
public class Storage {
    private StorageImpl impl;

    @Inject
    public Storage(Context applicationContext) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            impl = new BaseStorageImpl(applicationContext);
        } else {
            impl = new LollipopStorageImpl(applicationContext);
        }
    }

    public boolean supportsExternalStorage() {
        return impl.supportsExternalStorage();
    }

    public Intent getOpenTreeIntent() {
        return impl.getOpenTreeIntent();
    }

    public void handleOpenTreeIntent(Uri uri) {
        impl.handleOpenTreeIntent(uri);
    }

    public StorageFile obtainStorageFileForName(String name) {
        return impl.obtainStorageFileForName(name);
    }

    public String currentStorageName() {
        return impl.currentStorageName();
    }
}
