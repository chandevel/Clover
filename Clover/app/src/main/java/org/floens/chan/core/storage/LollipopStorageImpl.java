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
import org.floens.chan.utils.IOUtils;
import org.floens.chan.utils.Logger;

import java.io.FileNotFoundException;

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
public class LollipopStorageImpl extends BaseStorageImpl {
    private static final String TAG = "LollipopStorageImpl";

    public LollipopStorageImpl(Context applicationContext) {
        super(applicationContext);
    }

    @Override
    public boolean supportsExternalStorage() {
        return true;
    }

    @Override
    public Intent getOpenTreeIntent() {
        return new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
    }

    @Override
    public void handleOpenTreeIntent(Uri uri) {
        String documentId = DocumentsContract.getTreeDocumentId(uri);
        Uri treeDocumentUri = DocumentsContract.buildDocumentUriUsingTree(uri, documentId);

        ChanSettings.saveLocationTreeUri.set(treeDocumentUri.toString());
    }

    @Override
    public StorageFile obtainStorageFileForName(String name) {
        String uriString = ChanSettings.saveLocationTreeUri.get();
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

    @Override
    public String currentStorageName() {
        String uriString = ChanSettings.saveLocationTreeUri.get();
        if (uriString.isEmpty()) {
            return null;
        }

        Uri treeUri = Uri.parse(uriString);
        return queryTreeName(treeUri);
    }

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
