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
package org.floens.chan.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import org.floens.chan.Chan;
import org.floens.chan.core.http.ReplyManager;
import org.floens.chan.utils.IOUtils;
import org.floens.chan.utils.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ImagePickActivity extends Activity implements Runnable {
    private static final String TAG = "ImagePickActivity";

    private static final int IMAGE_RESULT = 1;
    private static final long MAX_FILE_SIZE = 15 * 1024 * 1024;

    private ReplyManager replyManager;
    private Uri uri;
    private String fileName = "file";
    private boolean success = false;
    private File cacheFile;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        replyManager = Chan.getReplyManager();

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");

        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, IMAGE_RESULT);
        } else {
            Logger.e(TAG, "No activity found to get file with");
            replyManager._onFilePickError(false);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        boolean ok = false;
        boolean cancelled = false;
        if (requestCode == IMAGE_RESULT) {
            if (resultCode == RESULT_OK && data != null) {
                uri = data.getData();

                Cursor returnCursor = getContentResolver().query(uri, null, null, null, null);
                if (returnCursor != null) {
                    int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    returnCursor.moveToFirst();
                    if (nameIndex > -1) {
                        fileName = returnCursor.getString(nameIndex);
                    }

                    returnCursor.close();
                }

                replyManager._onFilePickLoading();

                new Thread(this).start();
                ok = true;
            } else if (resultCode == RESULT_CANCELED) {
                cancelled = true;
            }
        }

        if (!ok) {
            replyManager._onFilePickError(cancelled);
        }

        finish();
    }

    @Override
    public void run() {
        cacheFile = replyManager.getPickFile();

        ParcelFileDescriptor fileDescriptor = null;
        InputStream is = null;
        OutputStream os = null;
        try {
            fileDescriptor = getContentResolver().openFileDescriptor(uri, "r");
            is = new FileInputStream(fileDescriptor.getFileDescriptor());
            os = new FileOutputStream(cacheFile);
            boolean fullyCopied = IOUtils.copy(is, os, MAX_FILE_SIZE);
            if (fullyCopied) {
                success = true;
            }
        } catch (IOException | SecurityException e) {
            Logger.e(TAG, "Error copying file from the file descriptor", e);
        } finally {
            IOUtils.closeQuietly(fileDescriptor);
            IOUtils.closeQuietly(is);
            IOUtils.closeQuietly(os);
        }

        if (!success) {
            if (!cacheFile.delete()) {
                Logger.e(TAG, "Could not delete picked_file after copy fail");
            }
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (success) {
                    replyManager._onFilePicked(fileName, cacheFile);
                } else {
                    replyManager._onFilePickError(false);
                }
            }
        });
    }
}
