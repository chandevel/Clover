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

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;

import androidx.annotation.RequiresApi;

import android.util.Pair;

import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.core.settings.StringSetting;
import org.floens.chan.ui.activity.ActivityResultHelper;
import org.floens.chan.utils.IOUtils;
import org.floens.chan.utils.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Abstraction of the storage APIs available in Android. It's generally a mess because it
 * juggles between the old file api for legacy support and the newer SAF APIs, which have
 * different semantics in different Android APIs.
 * <p>
 * This is used primarily for saving images, especially on removable storage.
 * <p>
 * First, a good read:
 * https://commonsware.com/blog/2019/10/06/storage-situation-internal-storage.html
 * https://commonsware.com/blog/2019/10/08/storage-situation-external-storage.html
 * https://commonsware.com/blog/2019/10/11/storage-situation-removable-storage.html
 * <p>
 * The Android Storage Access Framework is used from Android 5.0 and higher. Since Android 5.0
 * it has support for granting permissions for a directory, which we want to save our files to.
 * <p>
 * Otherwise a fallback is provided for only saving on the primary volume with the older APIs.
 */
@Singleton
public class Storage {
    private static final String TAG = "Storage";

    private static final String DEFAULT_DIRECTORY_NAME = "Clover";

    private static final Pattern REPEATED_UNDERSCORES_PATTERN = Pattern.compile("_+");
    private static final Pattern SAFE_CHARACTERS_PATTERN = Pattern.compile("[^a-zA-Z0-9._]");
    private static final int MAX_RENAME_TRIES = 500;

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
    private ActivityResultHelper results;

    private final StringSetting saveLocation;
    private final StringSetting saveLocationTreeUri;
    private String saveLocationTreeUriFolder;

    public static String filterName(String name) {
        name = name.replace(' ', '_');
        name = SAFE_CHARACTERS_PATTERN.matcher(name).replaceAll("");
        name = REPEATED_UNDERSCORES_PATTERN.matcher(name).replaceAll("_");
        if (name.length() == 0) {
            name = "_";
        }
        return name;
    }

    @Inject
    public Storage(Context applicationContext, ActivityResultHelper results) {
        this.applicationContext = applicationContext;
        this.results = results;

        saveLocation = ChanSettings.saveLocation;
        saveLocationTreeUri = ChanSettings.saveLocationTreeUri;
    }

    /**
     * The mode of storage changes depending on the api level.
     */
    public Mode mode() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ?
                Mode.FILE : Mode.STORAGE_ACCESS_FRAMEWORK;
    }

    // Settings controller:
    public String getFileSaveLocation() {
        prepareDefaultFileSaveLocation();
        return saveLocation.get();
    }

    // Settings controller:
    public void setFileSaveLocation(String location) {
        saveLocation.set(location);
        saveLocationTreeUri.set("");
    }

    // Settings controller:
    public String currentStorageName() {
        switch (mode()) {
            case FILE: {
                return saveLocation.get();
            }
            case STORAGE_ACCESS_FRAMEWORK: {
                String uriString = saveLocationTreeUri.get();
                Uri treeUri = Uri.parse(uriString);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) { // lint
                    return queryTreeName(treeUri);
                }
            }
        }
        throw new IllegalStateException();
    }

    // For the settings controller:

    /**
     * Starts up a screen where the user can select a location on their storage.
     * If the user accepts the location, the uri is persisted (so that we have access
     * in the future). The callback is called if everything goes right and we have
     * persistable access to the picked uri.
     *
     * @param handled called if it was picked and persisted.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void startOpenTreeIntentAndHandle(StoragePreparedCallback handled) {
        Intent openTreeIntent = getOpenTreeIntent();
        results.getResultFromIntent(openTreeIntent, (resultCode, result) -> {
            if (resultCode == Activity.RESULT_OK) {
                String uri = handleOpenTreeIntent(result);
                if (uri != null) {
                    ChanSettings.saveLocationTreeUri.set(uri);
                    if (handled != null) {
                        handled.onPrepared();
                    }
                }
            }
        });
    }

    // When using FILE mode, create the directory.
    private void prepareDefaultFileSaveLocation() {
        if (saveLocation.get().isEmpty()) {
            File pictures = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES);
            File directory = new File(pictures, DEFAULT_DIRECTORY_NAME);
            String absolutePath = directory.getAbsolutePath();
            saveLocation.set(absolutePath);
        }
    }

    public void prepareForSave(String folder, StoragePreparedCallback callback) {
        if (mode() == Mode.FILE) {
            prepareDefaultFileSaveLocation();
            callback.onPrepared();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (folder == null) {
                if (saveLocationTreeUri.get().isEmpty()) {
                    startOpenTreeIntentAndHandle(callback);
                } else {
                    callback.onPrepared();
                }

            } else {
                saveLocationTreeUriFolder = null;
                if (saveLocationTreeUri.get().isEmpty()) {
                    startOpenTreeIntentAndHandle(() -> {
                        saveLocationTreeUriFolder = createDirectoryForSafUri(Uri.parse(saveLocationTreeUri.get()), folder);
                        if (saveLocationTreeUriFolder == null) {
                            throw new IllegalStateException("Failed to create subdir in normal dir for folder saving");
                        }
                        callback.onPrepared();
                    });
                } else {
                    saveLocationTreeUriFolder = createDirectoryForSafUri(Uri.parse(saveLocationTreeUri.get()), folder);
                    if (saveLocationTreeUriFolder == null) {
                        throw new IllegalStateException("Failed to create subdir in normal dir for folder saving");
                    }
                    callback.onPrepared();
                }

//                if (false && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//                    StorageManager sm = (StorageManager)
//                            applicationContext.getSystemService(Context.STORAGE_SERVICE);
//                    StorageVolume primaryStorageVolume = sm.getPrimaryStorageVolume();
//                    Intent accessIntent =
//                            primaryStorageVolume.createAccessIntent(Environment.DIRECTORY_PICTURES);
//
//                    Logger.i(TAG, "Requesting access to pictures with scoped saf");
//
//                    results.getResultFromIntent(accessIntent, (resultCode, result) -> {
//                        if (resultCode == Activity.RESULT_OK) {
//                            String uri = handleOpenTreeIntent(result);
//                            if (uri != null) {
//
//                            }
//                        } else {
//                            Logger.e(TAG, "Failed to access storage, code: %d", resultCode);
//                            startOpenTreeIntentAndHandle(callback);
//                        }
//                    });
//                } else {
//                    // Api 21-23 SAF can't have such a popup, open the normal selection screen.
////                    startOpenTreeIntentAndHandle(null);
//                }
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private Intent getOpenTreeIntent() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
                Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
//        intent.addCategory(Intent.CATEGORY_OPENABLE);
        return intent;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private String handleOpenTreeIntent(Intent intent) {
        boolean read = (intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0;
        boolean write = (intent.getFlags() & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0;

        if (!read) {
            Logger.e(TAG, "No grant read uri permission given");
            return null;
        }

        if (!write) {
            Logger.e(TAG, "No grant write uri permission given");
            return null;
        }

        Uri uri = intent.getData();
        if (uri == null) {
            Logger.e(TAG, "intent.getData() == null");
            return null;
        }

        Logger.i(TAG, "handle open (" + uri.toString() + ")");

        String documentId = DocumentsContract.getTreeDocumentId(uri);
        Uri treeDocumentUri = DocumentsContract.buildDocumentUriUsingTree(uri, documentId);

        Logger.i(TAG, "documentId = " + documentId);
        Logger.i(TAG, "treeDocumentUri = " + treeDocumentUri.toString());

        ContentResolver contentResolver = applicationContext.getContentResolver();

        int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        contentResolver.takePersistableUriPermission(uri, flags);

        Logger.i(TAG, "saving as " + treeDocumentUri.toString());
        return treeDocumentUri.toString();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private String createDirectoryForSafUri(Uri uri, String name) {
        Logger.i(TAG, "appending base dir");
        String documentId = DocumentsContract.getTreeDocumentId(uri);
        Uri treeDocumentUri = DocumentsContract.buildDocumentUriUsingTree(uri, documentId);
        Uri subTree = null;

        ContentResolver contentResolver = applicationContext.getContentResolver();
        try {
            documentId = DocumentsContract.getTreeDocumentId(treeDocumentUri);
            Uri treeDocUri = DocumentsContract.buildDocumentUriUsingTree(treeDocumentUri, documentId);

            List<Pair<String, String>> files = listTree(treeDocumentUri);
            boolean createSubdir = true;
            for (Pair<String, String> file : files) {
                if (file.second.equals(name)) {
                    subTree = DocumentsContract.buildDocumentUriUsingTree(treeDocumentUri, file.first);
                    createSubdir = false;
                    break;
                }
            }
            if (createSubdir) {
                subTree = DocumentsContract.createDocument(
                        contentResolver, treeDocUri,
                        DocumentsContract.Document.MIME_TYPE_DIR, name);
            }

            Logger.i(TAG, "documentId = " + documentId);
            Logger.i(TAG, "treeDocumentUri = " + treeDocumentUri);
        } catch (FileNotFoundException e) {
            Logger.e(TAG, "Could not create subdir", e);
        }

        return subTree == null ? null : subTree.toString();
    }

    public StorageFile obtainLegacyStorageFileForName(String folder, String name) {
        File directory;
        if (folder == null) {
            directory = new File(ChanSettings.saveLocation.get());
        } else {
            directory = new File(ChanSettings.saveLocation.get(), folder);
        }

        String base;
        String extension;

        String[] splitted = filterName(name).split("\\.(?=[^.]+$)");
        if (splitted.length == 2) {
            base = splitted[0];
            extension = "." + splitted[1];
        } else {
            base = splitted[0];
            extension = ".";
        }

        File test = new File(directory, base + extension);
        int index = 0;
        int tries = 0;
        while (test.exists() && tries++ < MAX_RENAME_TRIES) {
            test = new File(directory, base + "_" + index + extension);
            index++;
        }

        return StorageFile.fromLegacyFile(test);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public StorageFile obtainStorageFileForName(String folder, String name) {
        String uriString = folder != null ? saveLocationTreeUriFolder : saveLocationTreeUri.get();
        if (uriString.isEmpty()) {
            return null;
        }

        Uri treeUri = Uri.parse(uriString);

        ContentResolver contentResolver = applicationContext.getContentResolver();

        String documentId = DocumentsContract.getDocumentId(treeUri);
        Uri treeDocumentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId);

        Logger.i(TAG, "saving, documentId = " + documentId + ", treeDocumentUri = " + treeDocumentUri);

        Uri docUri;
        String finalName = filterName(name);
        try {
            int fileNumberSuffix = 0;
            List<Pair<String, String>> list = listTree(treeDocumentUri);
            out:
            while (true) {
                for (Pair<String, String> file : list) {
                    if (file.second.equals(finalName)) {
                        finalName = fileNameWithSuffix(name, ++fileNumberSuffix);
                        continue out;
                    }
                }
                break;
            }

            docUri = DocumentsContract.createDocument(contentResolver, treeDocumentUri, "text", finalName);
        } catch (FileNotFoundException e) {
            Logger.e(TAG, "obtainStorageFileForName createDocument", e);
            return null;
        }

        return StorageFile.fromUri(contentResolver, docUri, finalName);
    }

    private String fileNameWithSuffix(String name, int suffix) {
        String finalName;
        if (suffix == 0) {
            return name;
        } else {
            String[] splitted = name.split("\\.(?=[^.]+$)");
            return splitted[0] + " (" + suffix + ")." + splitted[1];
        }
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

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private List<Pair<String, String>> listTree(Uri tree) {
        ContentResolver contentResolver = applicationContext.getContentResolver();

        String documentId = DocumentsContract.getTreeDocumentId(tree);

        try (Cursor c = contentResolver.query(
                DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId),
                new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                null, null, null)) {
            if (c == null) return Collections.emptyList();

            List<Pair<String, String>> result = new ArrayList<>();
            while (c.moveToNext()) {
                result.add(new Pair<>(c.getString(0), c.getString(1)));
            }
            return result;
        }
    }

    public interface StoragePreparedCallback {
        void onPrepared();
    }
}
