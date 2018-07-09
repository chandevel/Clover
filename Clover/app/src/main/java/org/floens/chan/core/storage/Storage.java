/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
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
package org.floens.chan.core.storage;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.support.annotation.RequiresApi;

import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.core.settings.StringSetting;
import org.floens.chan.utils.IOUtils;
import org.floens.chan.utils.Logger;

import java.io.FileNotFoundException;

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
    private static final String TAG = "Storage";

    /**
     * The current mode of the Storage.
     */
    public enum Mode {
        /**
         * File mode is used on devices under Lollipop, or by default when the user hasn't changed
         * the save locaton yet.
         * <p>
         * Uses the File api for internal/external storage.
         */
        FILE,

        /**
         * Used on lollipop and higher.
         * <p>
         * Uses the Android Storage Access Framework for internal, external and removable storage.
         */
        STORAGE_ACCESS_FRAMEWORK
    }

    private Context applicationContext;

    private final StringSetting saveLocation;
    private final StringSetting saveLocationTreeUri;

    @Inject
    public Storage(Context applicationContext) {
        this.applicationContext = applicationContext;

        saveLocation = ChanSettings.saveLocation;
        saveLocationTreeUri = ChanSettings.saveLocationTreeUri;
    }

    /**
     * The mode of storage changes depending on the api level and if the user migrated their
     * legacy install settings.
     */
    public Mode mode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return Mode.FILE;
        }

        if (!saveLocation.get().isEmpty()) {
            return Mode.FILE;
        }

        return Mode.STORAGE_ACCESS_FRAMEWORK;
    }

    public String currentStorageName() {
        switch (mode()) {
            case FILE: {
                return saveLocation.get();
            }
            case STORAGE_ACCESS_FRAMEWORK: {
                String uriString = saveLocationTreeUri.get();
                Uri treeUri = Uri.parse(uriString);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    return queryTreeName(treeUri);
                }
            }
        }
        throw new IllegalStateException();
    }

    public Mode getModeForNewLocation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return Mode.FILE;
        } else {
            return Mode.STORAGE_ACCESS_FRAMEWORK;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public Intent getOpenTreeIntent() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
                Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
//        intent.addCategory(Intent.CATEGORY_OPENABLE);
        return intent;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void handleOpenTreeIntent(Intent intent) {
        boolean read = (intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0;
        boolean write = (intent.getFlags() & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0;

        if (!read) {
            Logger.e(TAG, "No grant read uri permission given");
            return;
        }

        if (!write) {
            Logger.e(TAG, "No grant write uri permission given");
            return;
        }

        Uri uri = intent.getData();
        if (uri == null) {
            Logger.e(TAG, "intent.getData() == null");
            return;
        }

        String documentId = DocumentsContract.getTreeDocumentId(uri);
        Uri treeDocumentUri = DocumentsContract.buildDocumentUriUsingTree(uri, documentId);

        ContentResolver contentResolver = applicationContext.getContentResolver();

        int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        contentResolver.takePersistableUriPermission(uri, flags);

        saveLocationTreeUri.set(treeDocumentUri.toString());
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public StorageFile obtainStorageFileForName(String name) {
        String uriString = saveLocationTreeUri.get();
        if (uriString.isEmpty()) {
            return null;
        }

        Uri treeUri = Uri.parse(uriString);

        ContentResolver contentResolver = applicationContext.getContentResolver();

        String documentId = DocumentsContract.getTreeDocumentId(treeUri);
        Uri treeDocumentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId);

        Uri docUri;
        try {
            docUri = DocumentsContract.createDocument(contentResolver, treeDocumentUri,
                    "text", name);
        } catch (FileNotFoundException e) {
            Logger.e(TAG, "obtainStorageFileForName createDocument", e);
            return null;
        }

        return StorageFile.fromUri(contentResolver, docUri);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private String queryTreeName(Uri uri) {
        ContentResolver contentResolver = applicationContext.getContentResolver();

        Cursor c = null;
        String name = null;
        try {
            c = contentResolver.query(uri, new String[]{
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
            }, null, null, null);

            if (c != null && c.moveToNext()) {
                name = c.getString(0);
                // mime = c.getString(1);
            }

            return name;
        } catch (Exception e) {
            Logger.e(TAG, "queryTreeName", e);
        } finally {
            IOUtils.closeQuietly(c);
        }

        return null;
    }
}
