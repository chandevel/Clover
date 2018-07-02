package org.floens.chan.core.storage;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;

import java.util.Objects;

public class Storage {
    private static final Storage instance;

    static {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            instance = new Storage(new BaseStorageImpl());
        } else {
            instance = new Storage(new NougatStorageImpl());
        }

    }

    public static Storage getInstance() {
        return instance;
    }

    private StorageImpl impl;

    public Storage(StorageImpl impl) {
        this.impl = impl;
    }

    public Intent requestExternalPermission(Context applicationContext) {
        return impl.requestExternalPermission(applicationContext);
    }

    public interface StorageImpl {
        Intent requestExternalPermission(Context applicationContext);
    }

    public static class BaseStorageImpl implements StorageImpl {
        @Override
        public Intent requestExternalPermission(Context applicationContext) {
            throw new UnsupportedOperationException();
        }
    }

    @TargetApi(Build.VERSION_CODES.N)
    public static class NougatStorageImpl extends BaseStorageImpl {
        @Override
        public Intent requestExternalPermission(Context applicationContext) {
            StorageManager sm = (StorageManager)
                    applicationContext.getSystemService(Context.STORAGE_SERVICE);
            Objects.requireNonNull(sm);
            for (StorageVolume storageVolume : sm.getStorageVolumes()) {
                if (!storageVolume.isPrimary()) {
                    Intent accessIntent = storageVolume.createAccessIntent(null);
                    return accessIntent;
                }
            }

            return null;
        }
    }
}
